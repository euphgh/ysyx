package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class FreeList(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val push = Vec(retireNum, Flipped(Decoupled(PRegIdx())))
    val pop = Vec(
      renameNum,
      Decoupled(new Bundle {
        val pRegIdx = PRegIdx()
        val ptr     = FreeListPtr()
      })
    )
    val recover = Flipped(Valid((FreeListPtr())))
  })
}
