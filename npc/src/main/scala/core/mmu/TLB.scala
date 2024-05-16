package core.mmu

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class TLB(implicit p: Parameters) extends CoreModule {
  val io = new TlbIO
}

object TLB {
  def apply()(implicit p: Parameters) = (new TLB).io
}
