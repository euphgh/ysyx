package core.mmu

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core.CoreBundle
import freechips.rocketchip.tile

class TLBEntry(implicit p: Parameters) extends CoreBundle {
  val g    = Bool()
  val v0   = Bool()
  val v1   = Bool()
  val d0   = Bool()
  val d1   = Bool()
  val c0   = UInt(3.W)
  val c1   = UInt(3.W)
  val pfn0 = UInt(20.W)
  val pfn1 = UInt(20.W)
  val vpn2 = UInt(19.W)
  val asid = UInt(8.W)
}

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
