package core.mmu

import core._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

object MemDataSize extends ChiselEnum {
  val uint8, uint16, uint32, uint64 = Value
}

object TlbCmd extends ChiselEnum {
  val read  = Value("b000".U)
  val write = Value("b001".U)
  val exec  = Value("b010".U)

  val atom_read  = Value("b100".U) // lr
  val atom_write = Value("b101".U) // sc / amo

  def isRead(a:  UInt) = a(1, 0) === read.asUInt
  def isWrite(a: UInt) = a(1, 0) === write.asUInt
  def isExec(a:  UInt) = a(1, 0) === exec.asUInt

  def isAtom(a: UInt) = a(2)
  def isAmo(a:  UInt) = a === atom_write.asUInt // NOTE: sc mixed
}

trait HasMMUConst extends HasMyParams {
  val Level = 3

  val offLen  = 12
  val ppnLen  = PAddrBits - offLen
  val vpnLen  = VAddrBits - offLen // when opening H extention, vpnlen broaden two bits
  val vpnnLen = 9
  val asidLen = 8

  def getPN(addr: UInt) = {
    require(addr.getWidth > offLen)
    addr(addr.getWidth - 1, offLen)
  }
  def getOff(addr: UInt) = {
    require(addr.getWidth > offLen)
    addr(offLen - 1, 0)
  }
}

abstract class MMUBundle(implicit p: Parameters) extends CoreBundle with HasMMUConst

abstract class MMUModule(implicit p: Parameters) extends CoreModule with HasMMUConst
