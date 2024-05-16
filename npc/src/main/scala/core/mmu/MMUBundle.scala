package core.mmu

import core._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class TlbReq(implicit p: Parameters) extends MMUBundle {
  val vaddr   = UInt(VAddrBits.W)
  val cmd     = TlbCmd()
  val size    = MemDataSize()
  val debugHW = DebugHW()
}

object TlbReq {
  def apply()(implicit p: Parameters) = new TlbReq()
  def fetch(pc: UInt)(implicit p: Parameters): TlbReq = {
    val req = new TlbReq
    req.size  := MemDataSize.uint32
    req.vaddr := pc
    req.cmd   := TlbCmd.exec
    req.debugHW.set(DebugHW.dontCare())
    req.debugHW.set(pc)
    req
  }
  def store(
    pc:      UInt,
    fuOp:    UInt,
    debugHW: DebugHW
  )(
    implicit p: Parameters
  ): TlbReq = {
    val req = new TlbReq
    req.size  := MemType.size(fuOp)
    req.vaddr := pc
    req.cmd   := TlbCmd.write
    req.debugHW.set(debugHW)
    req
  }
  def load(
    pc:      UInt,
    fuOp:    UInt,
    debugHW: DebugHW
  )(
    implicit p: Parameters
  ): TlbReq = {
    val req = new TlbReq
    req.size  := MemType.size(fuOp)
    req.vaddr := pc
    req.cmd   := TlbCmd.read
    req.debugHW.set(debugHW)
    req
  }
}

class PtePermBundle(implicit p: Parameters) extends MMUBundle {
  val d = Bool()
  val a = Bool()
  val g = Bool()
  val u = Bool()
  val x = Bool()
  val w = Bool()
  val r = Bool()

  override def toPrintable: Printable =
    p"d:${d} a:${a} g:${g} u:${u} x:${x} w:${w} r:${r}"

}

class TlbPermBundle(implicit p: Parameters) extends PtePermBundle {
  val pf = Bool() //NOTE: if this is true, just raise pf
  val af = Bool() //NOTE: if this is true, just raise af
  // pagetable perm (software defined)

  override def toPrintable: Printable =
    p"pf:${pf} af:${af} d:${d} a:${a} g:${g} u:${u} x:${x} w:${w} r:${r} "

}

class TlbExceptionBundle(implicit p: Parameters) extends MMUBundle {
  val ld    = Output(Bool())
  val st    = Output(Bool())
  val instr = Output(Bool())
}

class TlbResp()(implicit p: Parameters) extends MMUBundle {
  val paddr = Output(UInt(PAddrBits.W))
  val miss  = Output(Bool())
  val excp = Output(new Bundle {
    val pf = new TlbExceptionBundle()
    val af = new TlbExceptionBundle()
  })
  val debugHW = DebugHW()
}

class TlbRequestIO()(implicit p: Parameters) extends MMUBundle {
  val req  = DecoupledIO(new TlbReq)
  val resp = Flipped(Valid(new TlbResp()))
}

class PtwReq(implicit p: Parameters) extends MMUBundle {
  val vpn = UInt(vpnLen.W)
  override def toPrintable: Printable = {
    p"vpn:0x${Hexadecimal(vpn)}"
  }
}

class PtwResp(implicit p: Parameters) extends MMUBundle {
  val valididx = Bool()
  val pteidx   = Bool()
  val pf       = Bool()
  val af       = Bool()
}

class TlbPtwIO(implicit p: Parameters) extends MMUBundle {
  val req  = Decoupled(new PtwReq)
  val resp = Flipped(Decoupled(new PtwResp))

  override def toPrintable: Printable = {
    p"req(0):${req.valid} ${req.ready} ${req.bits} | resp:${resp.valid} ${resp.ready} ${resp.bits}"
  }
}

class SfenceBundle(implicit p: Parameters) extends MMUBundle {
  val valid = Bool()
  val bits = new Bundle {
    val rs1  = Bool()
    val rs2  = Bool()
    val addr = UInt(VAddrBits.W)
    val id   = UInt(asidLen.W) // asid or vmid
  }

  override def toPrintable: Printable = {
    p"valid:0x${Hexadecimal(valid)} rs1:${bits.rs1} rs2:${bits.rs2} addr:${Hexadecimal(bits.addr)}"
  }
}

class MMUIOBaseBundle(implicit p: Parameters) extends MMUBundle {
  val sfence = Input(new SfenceBundle)
  val csr    = Input(new TlbCsrBundle)

  def base_connect(sfence: SfenceBundle, csr: TlbCsrBundle): Unit = {
    this.sfence <> sfence
    this.csr <> csr
  }

  // overwrite satp. write satp will cause flushpipe but csr.priv won't
  // satp will be dealyed several cycles from writing, but csr.priv won't
  // so inside mmu, these two signals should be divided
  def base_connect(sfence: SfenceBundle, csr: TlbCsrBundle, satp: TlbSatpBundle) = {
    this.sfence <> sfence
    this.csr <> csr
    this.csr.satp := satp
  }
}

class TlbReplaceIO(implicit p: Parameters) extends MMUBundle {}

class TlbIO(nRespDups: Int = 1)(implicit p: Parameters) extends MMUIOBaseBundle {
  val requestor = Flipped(new TlbRequestIO())
  val ptw       = new TlbPtwIO()
  val replace   = new TlbReplaceIO
}
