package core.mmu

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core._
import utility._

trait HasTlbConst extends HasMyParams {
  val Level = 3

  val offLen         = 12
  val ppnLen         = PAddrBits - offLen
  val vpnnLen        = 9
  val extendVpnnBits = if (HasHExtension) 2 else 0
  val vpnLen         = VAddrBits - offLen // when opening H extention, vpnlen broaden two bits
  val flagLen        = 8
  val pteResLen      = XLEN - 44 - 2 - flagLen
  val ppnHignLen     = 44 - ppnLen

  val tlbcontiguous  = 8
  val sectortlbwidth = log2Up(tlbcontiguous)
  val sectorppnLen   = ppnLen - sectortlbwidth
  val sectorvpnLen   = vpnLen - sectortlbwidth

  val loadfiltersize     = 16
  val storefiltersize    = 8
  val prefetchfiltersize = 8

  val sramSinglePort = true

  val timeOutThreshold = 10000

  def noS2xlate  = "b00".U
  def allStage   = "b11".U
  def onlyStage1 = "b01".U
  def onlyStage2 = "b10".U

  def get_pn(addr: UInt) = {
    require(addr.getWidth > offLen)
    addr(addr.getWidth - 1, offLen)
  }
  def get_off(addr: UInt) = {
    require(addr.getWidth > offLen)
    addr(offLen - 1, 0)
  }

  def get_set_idx(vpn: UInt, nSets: Int): UInt = {
    require(nSets >= 1)
    vpn(log2Up(nSets) - 1, 0)
  }

  def drop_set_idx(vpn: UInt, nSets: Int): UInt = {
    require(nSets >= 1)
    require(vpn.getWidth > log2Ceil(nSets))
    vpn(vpn.getWidth - 1, log2Ceil(nSets))
  }

  def drop_set_equal(vpn1: UInt, vpn2: UInt, nSets: Int): Bool = {
    require(nSets >= 1)
    require(vpn1.getWidth == vpn2.getWidth)
    if (vpn1.getWidth <= log2Ceil(nSets)) {
      true.B
    } else {
      drop_set_idx(vpn1, nSets) === drop_set_idx(vpn2, nSets)
    }
  }

  def replaceWrapper(v: UInt, lruIdx: UInt): UInt = {
    val width    = v.getWidth
    val emptyIdx = ParallelPriorityMux((0 until width).map(i => (!v(i), i.U(log2Up(width).W))))
    val full     = Cat(v).andR
    Mux(full, lruIdx, emptyIdx)
  }

  def replaceWrapper(v: Seq[Bool], lruIdx: UInt): UInt = {
    replaceWrapper(VecInit(v).asUInt, lruIdx)
  }
}

abstract class TlbBundle(implicit p: Parameters) extends CoreBundle with HasTlbConst

abstract class TlbModule(implicit p: Parameters) extends CoreModule with HasTlbConst

class TLBSearchRes(implicit p: Parameters) extends CoreBundle {
  val pTag   = UInt(tagWidth.W)
  val hit    = Bool()
  val dirty  = Bool() // dirty bits
  val refill = Bool() // no match by addr and asid
}

class TLBSearchIO(implicit p: Parameters) extends CoreBundle {
  val req = Valid(UWord)
  val res = Flipped(Valid(new TLBSearchRes))
}

class TlbReq(implicit p: Parameters) extends TlbBundle {
  val vaddr     = Output(UInt(VAddrBits.W))
  val cmd       = Output(TlbCmd())
  val hyperinst = Output(Bool())
  val hlvx      = Output(Bool())
  val size      = Output(UInt(log2Ceil(log2Ceil(XLEN / 8) + 1).W))
  val kill      = Output(Bool()) // Use for blocked tlb that need sync with other module like icache
  val memidx    = Output(UInt(6.W))
  // do not translate, but still do pmp/pma check
  val no_translate = Output(Bool())
  val debug = new Bundle {
    val pc           = Output(UInt(XLEN.W))
    val robIdx       = Output(UInt(5.W))
    val isFirstIssue = Output(Bool())
  }

  // Maybe Block req needs a kill: for itlb, itlb and icache may not sync, itlb should wait icache to go ahead
  override def toPrintable: Printable = {
    p"vaddr:0x${Hexadecimal(vaddr)} cmd:${cmd} kill:${kill} pc:0x${Hexadecimal(debug.pc)} robIdx:${debug.robIdx}"
  }
}

object TlbReq {
  def apply(pc: UInt)(implicit p: Parameters) = {
    val req = new TlbReq
    req.size               := 3.U
    req.vaddr              := pc
    req.cmd                := TlbCmd.exec
    req.memidx             := DontCare
    req.no_translate       := false.B
    req.hlvx               := false.B
    req.hyperinst          := false.B
    req.kill               := DontCare
    req.debug.pc           := pc
    req.debug.isFirstIssue := DontCare
    req.debug.robIdx       := DontCare
    req
  }
}

object TlbCmd {
  def read  = "b00".U
  def write = "b01".U
  def exec  = "b10".U

  def atom_read  = "b100".U // lr
  def atom_write = "b101".U // sc / amo

  def apply() = UInt(3.W)
  def isRead(a:  UInt) = a(1, 0) === read
  def isWrite(a: UInt) = a(1, 0) === write
  def isExec(a:  UInt) = a(1, 0) === exec

  def isAtom(a: UInt) = a(2)
  def isAmo(a:  UInt) = a === atom_write // NOTE: sc mixed
}
class TlbExceptionBundle(implicit p: Parameters) extends TlbBundle {
  val ld    = Output(Bool())
  val st    = Output(Bool())
  val instr = Output(Bool())
}

class TlbResp(nDups: Int = 1)(implicit p: Parameters) extends TlbBundle {
  val paddr  = Vec(nDups, Output(UInt(PAddrBits.W)))
  val gpaddr = Vec(nDups, Output(UInt(GPAddrBits.W)))
  val miss   = Output(Bool())
  val excp = Vec(
    nDups,
    new Bundle {
      val gpf = new TlbExceptionBundle()
      val pf  = new TlbExceptionBundle()
      val af  = new TlbExceptionBundle()
    }
  )
  val ptwBack = Output(Bool()) // when ptw back, wake up replay rs's state
  val memidx  = Output(UInt(6.W))

  val debugBundle = new Bundle {
    val robIdx       = Output(UInt(5.W))
    val isFirstIssue = Output(Bool())
  }
  override def toPrintable: Printable = {
    p"paddr:0x${Hexadecimal(paddr(0))} miss:${miss} excp.pf: ld:${excp(0).pf.ld} st:${excp(0).pf.st} instr:${excp(0).pf.instr} ptwBack:${ptwBack}"
  }
}

class TlbRequestIO(nRespDups: Int = 1)(implicit p: Parameters) extends TlbBundle {
  val req      = DecoupledIO(new TlbReq)
  val req_kill = Output(Bool())
  val resp     = Flipped(DecoupledIO(new TlbResp(nRespDups)))
}
