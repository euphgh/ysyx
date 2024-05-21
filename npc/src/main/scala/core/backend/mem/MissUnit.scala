package core.backend.mem

import core._
import core.mmu._
import utils._
import utility._
import chisel3._
import chisel3.util._
import core.backend._
import org.chipsalliance.cde.config._
import core.dram.DramIO

class MissUnit(implicit p: Parameters) extends MemModule {
  val io = IO(new Bundle {
    val wbuffer = Flipped(new WBufferWBackIO)
    val load    = new LoadMissIO()
    val refill  = new RefillIO()
    val mem     = new DramIO()
  })
}
