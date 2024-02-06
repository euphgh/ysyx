package core.mmu

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class TLB(implicit p: Parameters) extends CoreModule {
  val search = IO(Vec(2, Flipped(new TLBSearchIO)))
}
