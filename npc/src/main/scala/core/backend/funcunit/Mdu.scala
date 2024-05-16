package core.backend.funcunit

import core._
import utils._
import chisel3._
import chisel3.util._
import utility._
import core.backend._
import org.chipsalliance.cde.config._

class BrJmpLogic(implicit p: Parameters) extends LogicAlu("BranchJump") {
  import BranchType._

  val equal = src0 === src1
  val slt   = src0.asSInt < src1.asSInt
  val sltu  = src0 < src1
  val take = LookupEnumDefault(bruOp, true.B)(
    Seq(
      beq -> equal,
      bne -> !equal,
      blt -> slt,
      bltu -> sltu,
      bge -> !slt,
      bge -> !sltu
    )
  )
  val imm    = Wire(UInt(XLEN.W))
  val pc     = Wire(UInt(XLEN.W))
  val target = Mux(bruOp === BranchType.jalr, src0, pc) + imm
  io.out := pc + 4.U
}

class Mdu(implicit p: Parameters) extends FuncUnit(FuType.mdu) {
  class MduIO extends FuBaseIO {
    val csr = new CSRInstrRequestIO
  }
  override val io = IO(new MduIO)

  val idle :: waitReq :: waitResp :: Nil = Enum(3)

  val isBranchJump :: isMul :: isDiv :: isCSR :: Nil = Enum(4)

  val brJmpLogic = new BrJmpLogic
  brJmpLogic.io.in.op   := fuIn.bits.fuOp
  brJmpLogic.io.in.srcs := fuIn.bits.srcs
  val brJmpOut = initFuOut()
  brJmpOut.valid            := fuIn.valid
  brJmpOut.bits.wPrf.result := brJmpLogic.io.out
  brJmpOut.bits.wbRob.excptVec.append(brJmpLogic.target(1, 0) =/= 0.U, ExceptionNO.instrAddrMisaligned);

  val mulAlu          = (new MulAlu).io
  val divAlu          = (new DivAlu).io
  val decoupledAlu    = Seq(mulAlu, divAlu)
  val decoupledAluOut = initFuOut()
  decoupledAlu.foreach { alu =>
    /* could use RegNext for better timing result */
    alu.redirect <> io.redirect.valid
    decoupledValid(alu.req)
    alu.req.bits.op   := fuIn.bits.fuOp
    alu.req.bits.srcs := fuIn.bits.srcs
    alu.resp.ready    := io.out.ready
  }
  decoupledAluOut.bits.wPrf.result := Mux1H(decoupledAlu.map(_.resp.valid), decoupledAlu.map(_.resp.bits))
  decoupledAluOut.valid            := decoupledAlu.map(_.resp.valid).reduce(_ || _)

  decoupledValid(fuIn)
  Connect.byType(io.csr.req.bits, fuIn.bits)
  io.csr.resp.ready := io.out.ready
  val csrOut = initFuOut()
  csrOut.bits.wPrf.result := io.csr.resp.bits
  csrOut.valid            := io.csr.resp.valid

  val allOut = Seq(brJmpOut, decoupledAluOut, csrOut)
  io.out.valid := allOut.map(_.valid).reduce(_ || _)
  io.out.bits  := Mux1H(allOut.map(_.valid), allOut.map(_.bits))

  /* all fu ready or not valid, branch jump unit always ready */
  fuIn.ready := io.csr.req.ready && decoupledAlu.map(_.req.ready).reduce(_ && _) && io.out.ready || !fuIn.valid
}
