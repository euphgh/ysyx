package core.backend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class ArchRAT(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val retire  = Vec(retireNum, Flipped(Valid(new RATWriteBackIO)))
    val recover = Vec(aRegNum, new SrcRegMeta)
  })
  val pIdxMap = RegInit(VecInit((0 until aRegNum).map(i => i.U(pRegAddrWidth.W))))
  io.retire.indices.map { retireID =>
    when(io.retire(retireID).valid) {
      pIdxMap(io.retire(retireID).bits.aDest) := io.retire(retireID).bits.pDest
    }
  }
  (0 until aRegNum).foreach(i => {
    io.recover(i).inPrf := true.B
    io.recover(i).pIdx  := pIdxMap(i)
  })
}
