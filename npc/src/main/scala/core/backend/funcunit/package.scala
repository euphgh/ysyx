package core.backend

import core._
import utils._
import chisel3._
import chisel3.util._
import utility._
import core.backend._
import org.chipsalliance.cde.config._

package object funcunit {
  class DecoupledAluIO(implicit p: Parameters) extends CoreBundle {
    val req = Flipped(Decoupled(new Bundle {
      val srcs = Vec(2, UInt(XLEN.W))
      val op   = Input(FuOpType())
    }))
    val resp     = Decoupled(UInt(XLEN.W))
    val isUsed   = Output(Bool())
    val redirect = Redirect.input()
  }

  class DecoupledAlu(name: String)(implicit p: Parameters) extends CoreDelegate {
    val io = Wire(new DecoupledAluIO)
    if (name != "") io.suggestName(s"${name}_DecoupledAlu_io")
  }

  class LogicAluIO(implicit p: Parameters) extends CoreBundle {
    val in = new Bundle {
      val srcs = Input(Vec(2, UInt(XLEN.W)))
      val op   = Input(FuOpType())
    }
    val out    = Output(UInt(XLEN.W))
    val isUsed = Output(Bool())
  }

  class LogicAlu(name: String)(implicit p: Parameters) extends CoreDelegate {
    val io = Wire(new LogicAluIO)
    if (name != "") io.suggestName(s"${name}_LogicAlu_io")

    val (src0, src1, op) = (io.in.srcs(0), io.in.srcs(1), io.in.op)

    val aluOp = AluType.safe(op.asUInt)._1
    val mduOp = MduType.safe(op.asUInt)._1
    val bruOp = BranchType.safe(op.asUInt)._1
    val lsuOp = MemType.safe(op.asUInt)._1
  }
}
