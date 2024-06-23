package core.backend.mem

import core._
import core.mmu._
import utils._
import utility._
import chisel3._
import chisel3.util._
import core.backend._
import org.chipsalliance.cde.config._

class StorePipe(implicit p: Parameters) extends FuncUnit(FuType.lsu) with HasMemHelper {
  class StorePipeIO() extends FuBaseIO() {
    val tlb = new Bundle {
      val ptw     = Flipped(new TlbPtwIO())
      val replace = Flipped(new TlbReplaceIO())
    }

    val wbuffer = new Bundle {
      val w     = new WBufferStoreIO()
      val order = new WBufferOrderIO()
    }

    val pmpUpdate    = new PMPUpdateIO()
    val loadMiss     = new MemMissIO()
    val oldestRobPtr = RobPtr()
  }
  override val io = IO(new StorePipeIO())

  val tlb = TLB()
  tlb.ptw <> io.tlb.ptw
  tlb.replace <> io.tlb.replace
  val pmp = PMP()
  pmp.update <> io.pmpUpdate

  def wbufferAllow(robIdx: RobPtr): Bool = {
    val order = io.wbuffer.order
    order.allowLoad && RobPtr().isAfter(order.oldestStore, robIdx)
  }

  val s0 = new MemDelegate {
    val in = fuIn
    val out = Decoupled(new MicroOp {
      val vaddr = UInt(VAddrBits.W)
      val wdata = UWord()
    })
    Connect.byType(out.bits, in.bits)
    Connect.pipeReadyValid(out, in)

    out.bits.vaddr := in.bits.srcs(0) + in.bits.srcs(1)

    val tlbReq = Decoupled(TlbReq.load(out.bits.vaddr, out.bits.fuOp, out.bits.debugHW))
    tlbReq.valid := in.valid

    out.bits.wdata := in.bits.srcs(1)

    out.valid := tlbReq.ready
  }
  // override for ReadOpStage PipeConnect
  override val s0OutFire = s0.out.fire

  val s1 = new MemDelegate {
    val out = Decoupled(new MicroOp {
      import DCacheHelper._
      val wmask     = Vec(XBYTE, Bool())
      val wdata     = UWord()
      val tlbResp   = new TlbResp
      val tlb2ndReq = new TlbReq
    })

    val in = PipelineNext(s0.out, out.fire, io.redirect.valid)
    Connect.byType(out.bits, in.bits)
    Connect.pipeReadyValid(out, in)

    out.bits.tlbResp := HoldUnless(tlb.requestor.resp.bits, in.valid)
    AssertWhen(in.valid, tlb.requestor.resp.valid)
    out.bits.tlb2ndReq := TlbReq.load(in.bits.vaddr, in.bits.fuOp, in.bits.debugHW)
    out.bits.wmask     := MemType.mask(in.bits.fuOp, in.bits.vaddr(2, 0))
  }

  val s2 = new MemDelegate {
    val out = Decoupled(new MicroOp {
      val paddr = UInt(PAddrBits.W)
      val wmask = Vec(XBYTE, Bool())
      val wdata = UWord()
      val pmp   = new PMPRespIO
    })
    val in = PipelineNext(s1.out, out.fire, io.redirect.valid)
    Connect.byType(out.bits, in.bits)
    Connect.pipeReadyValid(out, in)

    val tlbAutomata = new MemDelegate {
      val hit1st :: waitHitResp :: hit2nd :: Nil = Enum(4)

      val state = RegInit(hit1st)

      /* when set, hitResp.miss must be clear */
      val isHit   = !in.bits.tlbResp.miss || state === hit2nd
      val resp2nd = RegEnable(tlb.requestor.resp.bits, RegNext(tlb.requestor.req.fire))

      /* need to be arbited */
      val req2nd = Decoupled(in.bits.tlb2ndReq)
      req2nd.valid := !isHit

      when(in.valid && in.bits.tlbResp.miss && state === hit1st) {
        state := waitHitResp
      }

      when(state === waitHitResp) {
        state := Mux(resp2nd.miss, waitHitResp, hit2nd)
      }

      when(out.fire || io.redirect.valid) {
        state        := hit1st
        resp2nd.miss := true.B
      }
      val hitResp = Mux(in.bits.tlbResp.miss, resp2nd, in.bits.tlbResp)
    }

    out.valid := in.valid && tlbAutomata.isHit

    pmp.req(tlbAutomata.hitResp.paddr, in.bits.fuOp)
    out.bits.pmp <> pmp.resp

    out.bits.excptVec.pageFault(tlbAutomata.hitResp.excp.pf)
    out.bits.excptVec.accessFault(tlbAutomata.hitResp.excp.af)

    out.bits.paddr := tlbAutomata.hitResp.paddr
  }

  val s3 = new MemDelegate {
    val out = Decoupled(new MicroOp {
      val paddr = UInt(PAddrBits.W)
      val wmask = Vec(XBYTE, Bool())
      val pmp   = new PMPRespIO
    })
    val in = PipelineNext(s2.out, out.fire, io.redirect.valid)
    Connect.byType(out.bits, in.bits)
    Connect.pipeReadyValid(out, in)

    out.bits.excptVec.accessFault(in.bits.pmp)
    val isExcpt   = out.bits.excptVec.hasException()
    val isMMIO    = in.bits.pmp.mmio
    val isWBuffer = !isExcpt && !isMMIO

    val wbufferBack = RegNext(io.wbuffer.w.req.fire)

    val idle :: loadMissReq :: loadMissResp :: loadMissBack :: Nil = Enum(4)

    val state = RegInit(idle)
    when(state === idle && (io.oldestRobPtr === in.bits.robIdx) && !isExcpt) {
      state := Mux(isMMIO, loadMissReq, idle)
    }
    when(state === loadMissReq && io.loadMiss.req.fire) {
      state := loadMissResp
    }
    when(state === loadMissResp && io.loadMiss.resp.fire) {
      state := loadMissResp
    }
    // clear state for new pipein
    when(out.fire || io.redirect.valid) {
      state       := idle
      wbufferBack := false.B
    }

    io.wbuffer.w.req.valid      := in.valid && isWBuffer
    io.wbuffer.w.req.bits.paddr := in.bits.paddr
    io.wbuffer.w.req.bits.wmask := in.bits.wmask
    io.wbuffer.w.req.bits.datas := UInt8.toVec(in.bits.wdata)
    val wbufferFire = io.wbuffer.w.req.fire || wbufferBack

    in.valid := in.valid && (isExcpt || Mux(isMMIO, state === loadMissBack, wbufferFire))
  }
}
