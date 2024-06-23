package core.backend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class Prf(implicit p: Parameters) extends CoreDelegate {

  val regs = RegInit(VecInit.fill(pRegNum)(0.U(XLEN.W)))

  def read(raddr: UInt): UInt = regs(raddr)
  def write(wen:  Bool, waddr: UInt, wdata: UInt) = when(wen) {
    regs(waddr) := wdata
  }

  //TODO: DiffTest ===================================================
  // if (verilator) {
  //   val pRegNumOfArchReg = Wire(Vec(aRegNum, PRegIdx))
  //   addSink(pRegNumOfArchReg, s"DiffArchRegNum")
  //   val checkArchRegs = Module(new DifftestArchIntRegState)
  //   checkArchRegs.io.clock := clock
  //   (0 until 32).foreach(i => {
  //     checkArchRegs.io.gpr(i) := phyRegs(pRegNumOfArchReg(i))
  //   })
  //   addSink(checkArchRegs.io.en, "hasValidRetire")
  // }
}
