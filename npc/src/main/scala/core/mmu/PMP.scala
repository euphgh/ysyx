package core.mmu

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import core.backend._
import org.chipsalliance.cde.config._

case object PMParameKey extends Field[PMParameters]

/* Memory Mapped PMA */
case class MMPMAConfig(
  address:   BigInt,
  mask:      BigInt,
  lgMaxSize: Int,
  sameCycle: Boolean,
  num:       Int)

case class PMParameters(
  NumPMP:        Int         = 16,
  NumPMA:        Int         = 16,
  PlatformGrain: Int         = log2Ceil(4 * 1024), // 4KB, a normal page
  mmpma:         MMPMAConfig = MMPMAConfig(address = 0x38021000, mask = 0xfff, lgMaxSize = 3, sameCycle = true, num = 2))
//trait HasPMParameters extends PMParameters
trait HasPMParameters {
  implicit val p: Parameters

  val PMPAddrBits = p(SoCParamsKey).PAddrBits
  val PMXLEN      = p(CoreParamsKey).XLEN
  val pmParams    = p(PMParameKey)
  val NumPMP      = pmParams.NumPMP
  val NumPMA      = pmParams.NumPMA

  val PlatformGrain = pmParams.PlatformGrain
  val mmpma         = pmParams.mmpma
}

trait PMPConst extends HasPMParameters {
  val PMPOffBits = 2 // minimal 4bytes
  val CoarserGrain: Boolean = PlatformGrain > PMPOffBits
}

abstract class PMPBundle(implicit val p: Parameters) extends Bundle with PMPConst
abstract class PMPModule(implicit val p: Parameters) extends Module with PMPConst

class PMPReqIO()(implicit p: Parameters) extends PMPBundle {
  val addr = Output(UInt(PMPAddrBits.W))
  val size = Output(MemDataSize())
  val cmd  = Output(TlbCmd())

  def apply(addr: UInt, size: MemDataSize.Type, cmd: UInt) {
    this.addr := addr
    this.size := size
    this.cmd  := cmd
  }

  def apply(addr: UInt, fuOp: UInt) {
    this.addr := addr
    this.size := MemType.size(fuOp)
    this.cmd  := MemType.tlbCmd(fuOp)
  }

}

class PMPRespIO(implicit p: Parameters) extends PMPBundle {
  val ld    = Output(Bool())
  val st    = Output(Bool())
  val instr = Output(Bool())
  val mmio  = Output(Bool())

  def |(resp: PMPRespIO): PMPRespIO = {
    val res = Wire(new PMPRespIO())
    res.ld    := this.ld || resp.ld
    res.st    := this.st || resp.st
    res.instr := this.instr || resp.instr
    res.mmio  := this.mmio || resp.mmio
    res
  }
}

class PMPUpdateIO(implicit p: Parameters) extends PMPBundle

class PMP()(implicit p: Parameters) {
  val io = IO(new Bundle {
    val req    = new PMPReqIO
    val resp   = new PMPRespIO
    val update = new PMPUpdateIO
  })
}

object PMP {
  def apply()(implicit p: Parameters) = (new PMP()).io
}
