package core.backend.mem

import core._
import core.mmu._
import utils._
import utility._
import chisel3._
import chisel3.util._
import core.backend._
import org.chipsalliance.cde.config._

class LoadPipe(implicit p: Parameters) extends FuncUnit(FuType.lsu) with HasMemHelper {
  class LoadPipeIO() extends FuBaseIO() {
    val refill = Flipped(new RefillIO())
    val tlb = new Bundle {
      val ptw     = Flipped(new TlbPtwIO())
      val replace = Flipped(new TlbReplaceIO())
    }
    /* copy from wbuffer */
    val wbuffer = new Bundle {
      val r     = new WBufferLoadIO()
      val order = new WBufferOrderIO()
    }
    val pmpUpdate    = new PMPUpdateIO()
    val loadMiss     = new LoadMissIO()
    val oldestRobPtr = RobPtr()
  }

  override val io = IO(new LoadPipeIO())

  val dcache = new DCacheMain()
  dcache.refill <> io.refill

  val tlb = TLB()
  tlb.ptw <> io.tlb.ptw
  tlb.replace <> io.tlb.replace

  val wbuffer = io.wbuffer

  val pmp = PMP()
  pmp.update <> io.pmpUpdate

  def wbufferAllow(robIdx: RobPtr): Bool = {
    val order = wbuffer.order
    order.allowLoad && RobPtr().isAfter(order.oldestStore, robIdx)
  }

  // add imm and src0 to get vaddr, send to TLB and DCache
  // if TLB or DCache not ready, stall
  val s0 = new MemDelegate {
    val in = fuIn
    val out = Decoupled(new MicroOp {
      val vaddr = UInt(VAddrBits.W)
    })
    Connect.byType(out.bits, in.bits)
    Connect.decoupled(out, in)

    out.bits.vaddr     := in.bits.srcs(0) + in.bits.srcs(1)
    dcache.r.req.bits  := DCacheHelper.getIndex(out.bits.vaddr)
    dcache.r.req.valid := in.valid

    val tlbReq = Decoupled(TlbReq.load(out.bits.vaddr, out.bits.fuOp, out.bits.debugHW))
    tlbReq.valid := in.valid

    out.valid := tlbReq.ready && dcache.r.req.ready
  }
  // override for ReadOpStage PipeConnect
  override val s0OutFire = s0.out.fire

  /* search tlb, send paddr to wbuffer, no matter exception */
  val s1 = new MemDelegate {
    val out = Decoupled(new MicroOp {
      import DCacheHelper._
      val meta  = Vec(nWays, new DCacheMeta)
      val data  = Vec(nWays, UWord())
      val rmask = Vec(XLEN / 8, Bool())

      val tlbResp   = new TlbResp
      val tlb2ndReq = new TlbReq
    })

    val in = PipelineNext(s0.out, out.fire, io.redirect.valid)
    Connect.byType(out.bits, in.bits)
    Connect.decoupled(out, in)

    out.bits.tlbResp := HoldUnless(tlb.requestor.resp.bits, in.valid)

    AssertWhen(in.valid, tlb.requestor.resp.valid)

    out.bits.meta := dcache.r.resp.map(_.meta)
    AssertWhen(in.valid, dcache.r.resp.head.debugIndex === DCacheHelper.getIndex(in.bits.vaddr))

    val wordOff = DCacheHelper.getXLEN(in.bits.vaddr)
    out.bits.data.zipWithIndex.foreach {
      case (data, index) =>
        val wordVec = UWord.toVec(dcache.r.resp(index).data)
        data := wordVec(wordOff)
    }

    out.bits.tlb2ndReq := TlbReq.load(in.bits.vaddr, in.bits.fuOp, in.bits.debugHW)

    out.bits.rmask := MemType.mask(in.bits.fuOp, in.bits.vaddr(2, 0))
  }

  val s2 = new MemDelegate {
    val out = Decoupled(new MicroOp {
      val paddr = UInt(PAddrBits.W)
      val rmask = Vec(XLEN / 8, Bool())
      val dCacheResp = new Bundle {
        val hit   = Bool()
        val datas = UWord()
      }
      val wbufferResp = new WBufferLoadRespIO()
      val pmp         = new PMPRespIO
    })
    val in = PipelineNext(s1.out, out.fire, io.redirect.valid)
    Connect.byType(out.bits, in.bits)
    Connect.decoupled(out, in)

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
        state := Mux(hitResp.miss, waitHitResp, hit2nd)
      }

      when(out.fire || io.redirect.valid) {
        state        := hit1st
        hitResp.miss := true.B
      }
      val hitResp = Mux(in.bits.tlbResp.miss, resp2nd, in.bits.tlbResp)
    }

    out.bits.paddr := tlbAutomata.hitResp.paddr

    val wbufferAutomata = new MemDelegate {
      wbuffer.r.req.valid      := wbufferAllow(in.bits.robIdx) && tlbAutomata.isHit
      wbuffer.r.req.bits.paddr := tlbAutomata.hitResp.paddr
      wbuffer.r.req.bits.rmask := in.bits.rmask
      out.bits.wbufferResp     := HoldUnless(wbuffer.r.resp.bits, wbuffer.r.resp.valid)

      val notReq :: respLocked :: Nil = Enum(3)

      val state = RegInit(notReq)
      when(tlbAutomata.isHit) {
        state := Mux(wbuffer.r.req.fire, respLocked, notReq)
      }
      when(state === respLocked) {
        wbuffer.r.req.valid := false.B
      }
      when(out.fire || io.redirect.valid) {
        state := notReq
      }
      val respValid = wbuffer.r.resp.valid || state === respLocked
    }

    out.valid := in.valid && tlbAutomata.isHit && wbufferAutomata.respValid

    val dcacheHitVec = VecInit(in.bits.meta.map { meta =>
      meta.statu =/= DCacheLineStatu.none &&
      meta.tag === DCacheHelper.getTag(tlbAutomata.hitResp.paddr)
    })
    AssertWhen(in.valid, PopCount(dcacheHitVec) <= 1.U)
    out.bits.dCacheResp.hit   := dcacheHitVec.asUInt.orR
    out.bits.dCacheResp.datas := Mux1H(dcacheHitVec, in.bits.data)

    pmp.req(tlbAutomata.hitResp.paddr, in.bits.fuOp)
    out.bits.pmp <> pmp.resp

    out.bits.excptVec.pageFault(tlbAutomata.hitResp.excp.pf)
    out.bits.excptVec.accessFault(tlbAutomata.hitResp.excp.af)
  }

  val s3 = new MemDelegate {
    val out = Decoupled(new MicroOp {
      val res = UWord()
    })
    val in = PipelineNext(s2.out, out.fire, io.redirect.valid)
    Connect.byType(out.bits, in.bits)
    Connect.decoupled(out, in)
    out.bits.excptVec.accessFault(in.bits.pmp)

    val dataMiss = !in.bits.dCacheResp.hit && in.bits.wbufferResp.hits === in.bits.rmask

    val isExcpt = out.bits.excptVec.hasException()
    val isMMIO  = in.bits.pmp.mmio

    val idle :: loadMissReq :: loadMissResp :: loadMissBack :: Nil = Enum(4)

    val state = RegInit(idle)

    val extData = Reg(UWord())

    when(state === idle && (io.oldestRobPtr === in.bits.robIdx) && !isExcpt) {
      state := Mux(dataMiss || isMMIO, loadMissReq, idle)
    }
    when(state === loadMissReq && io.loadMiss.req.fire) {
      state := loadMissResp
    }
    when(state === loadMissResp && io.loadMiss.resp.fire) {
      state   := loadMissResp
      extData := io.loadMiss.resp.bits.asUInt
    }
    when(out.fire || io.redirect.valid) {
      state := idle
    }

    io.loadMiss.req.valid      := state === loadMissReq
    io.loadMiss.req.bits.paddr := in.bits.paddr
    io.loadMiss.req.bits.mask  := in.bits.rmask
    io.loadMiss.req.bits.mmio  := isMMIO

    val s2Data = VecInit(in.bits.wbufferResp.hits.zipWithIndex.map {
      case (hit, index) =>
        Mux(hit, in.bits.wbufferResp.datas(index), UWord.toUInt8s(in.bits.dCacheResp.datas)(index))
    }).asUInt

    val finalData = Mux(state === loadMissBack, extData, s2Data)
    val dataReady = (!dataMiss && !isMMIO) || state === loadMissBack

    out.valid    := dataReady
    out.bits.res := finalData
  }
}
