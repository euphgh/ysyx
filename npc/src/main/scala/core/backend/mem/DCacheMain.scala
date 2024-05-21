package core.backend.mem

import core._
import core.mmu._
import utils._
import utility._
import chisel3._
import chisel3.util._
import core.backend._
import org.chipsalliance.cde.config._

/**
  * 需要自动机，确定是否在堵住期间有来自missUnit的写，如果存在，需要重读
  * @param p
  */
class DCacheMain(implicit p: Parameters) extends MemModule {
  val r      = new DCacheRIO
  val refill = Flipped(new RefillIO)
}
