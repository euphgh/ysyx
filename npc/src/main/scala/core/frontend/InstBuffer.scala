package core.frontend

import core._
import utils._
import macros._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import macros.decode._

/**
  * implement instbuffer in this stage, can not use queue api
  * because api only support single in and single out
  *
  * move the tail according to the "validNum"
  * write fetchNum insts in instBuffer at one cycle
  * decode the dequeue insts(combination logic) at current cycle
  * but decode logic can not too long because deq is a 16to1mux
  * only decode opcode, src1, src2, dest
  * opcode is used to dispatch select, src and dest are used to rename
  * more complex decode is placed in next cycle
  *
  * To simplify rename and dispatch, InstBuffer only issue 3 or 2 inst,
  * can not chage issue number by decode result
  * in rename stage, if 3 insts can not dispatch or rename for resouce conflict
  * InstBuffer will stop issue until 3 inst has been dispatched
  * it can simplify InstBuffer issue logic from 48to1mux to 16to1 mux
  * the logic of reg should be write in this module
  *
  * out[x].ready are come from one singal, if ready, will issue all valid inst
  */
class InstBuffer(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val in    = Flipped(Decoupled(new IfStage2OutIO))
    val out   = Vec(renameNum, Decoupled(new InstBufferOutIO))
    val flush = Input(Bool())
  })
  // sub decode ==================================================
  // val ib = Module(new MultiQueue(fetchNum, renameNum, new InstBufferEntry, 8, true))
  val ib = new BaseMultiPortBuffer(fetchNum, renameNum, 8, new InstBufferEntry) {}

  // avoid icReq(if1) -> iCache data out(if2) -> instbuffer full ->
  // dispatch lsu -> lsu wait iCache instr finish
  ib.io.flush := io.flush
  // input ========================================================
  (0 until fetchNum).foreach(i => {
    val pushBits = ib.io.in(i).bits
    val inBits   = io.in.bits
    ib.io.in(i).valid      := inBits.validMask(i) && io.in.valid
    pushBits.basicInstInfo := inBits.basicInstInfo(i)
    pushBits.predictResult := inBits.predictResult(i)
    pushBits.excVec        := inBits.excVec
    pushBits.realBrType    := inBits.realBrType(i)
  })
  io.in.ready := ib.io.in(0).ready // any number is ok

  // >> Assert ========================================================
  val validMask = io.in.bits.validMask.asUInt
  when(io.in.valid) {
    assert(PriorityCount.consecutive(validMask))
  }
  val ibRdy = VecInit.tabulate(fetchNum)(i => ib.io.in(i).ready)
  assert(ibRdy(0) === ibRdy(1))
  assume(ibRdy(0) === ibRdy(2))
  assert(ibRdy(0) === ibRdy(3))

  // output ========================================================
  List.tabulate(renameNum)(i => {
    Connect.byType(io.out(i).bits, ib.io.out(i).bits)
    io.out(i).valid    := ib.io.out(i).valid
    ib.io.out(i).ready := io.out(i).ready
  })

  @DecodeMacro
  class IBdecodeOut extends DCBundle {
    val srcType = NumSrcReg()
    val dstType = HasDstReg()
    val whichFu = FuType()
  }
  import chisel3.util.experimental.decode.QMCMinimizer
  val subDecode = Wire(Vec(renameNum, new IBdecodeOut))

  (0 until renameNum).foreach(i => {
    val outBits    = io.out(i).bits
    val outAregIdx = outBits.aRegsIdx
    val instr      = outBits.basicInstInfo.instr

    val (rs1, rs2, rd) = (instr(19, 15), instr(24, 20), instr(11, 7))

    RV64I.decode(instr, subDecode(i))
    outBits.whichFu := subDecode(i).whichFu

    outAregIdx.srcs(0) := Mux(subDecode(i).srcType.isOneOf(NumSrcReg.one, NumSrcReg.two), rs1, 0.U)
    outAregIdx.srcs(1) := Mux(subDecode(i).srcType === NumSrcReg.one, rs2, 0.U)
    outAregIdx.dest    := Mux(subDecode(i).dstType === HasDstReg.yes, rd, 0.U)
  })

}
