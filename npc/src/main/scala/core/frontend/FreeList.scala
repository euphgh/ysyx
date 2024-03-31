package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class FreeList(implicit p: Parameters) extends CoreModule {
  val gen     = PRegIdx
  val ptrType = UInt(12.W)
  val io = IO(new Bundle {
    val push = Vec(retireNum, Flipped(Decoupled(gen)))
    val pop = Vec(
      renameNum,
      Decoupled(new Bundle {
        val pRegIdx = gen
        val ptr     = ptrType
      })
    )
    val recover = Flipped(Valid((ptrType)))
  })
}
