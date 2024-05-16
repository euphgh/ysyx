package core.backend.funcunit

import core._
import utils._
import chisel3._
import chisel3.util._
import utility._
import core.backend._
import org.chipsalliance.cde.config._

class Adder(implicit p: Parameters) extends LogicAlu("adder") {
  import AluType._
  val isWord  = aluOp.isOneOf(addw, subw)
  val useAdd  = aluOp.isOneOf(add, addw)
  val useSub  = aluOp.isOneOf(sub, subw)
  val useSlt  = aluOp.isOneOf(slt)
  val useSltu = aluOp.isOneOf(sltu)

  val cin   = Mux((useSub | useSlt | useSltu), 1.U, 0.U)
  val dataA = Mux(isWord, src0(31, 0), src0)
  val dataB = Mux((useSub | useSlt | useSltu), ~src1, src1)
  val topA  = Mux(useSltu, false.B, dataA(XLEN - 1))
  val topB  = Mux(useSltu, false.B, dataB(XLEN - 1))
  val sum   = Wire(UInt((XLEN + 1).W))

  sum := Cat(topA, dataA) + Cat(topB, dataB) + cin
  val cout  = sum(XLEN)
  val dataC = Mux(isWord, SignExt(sum(XLEN - 1, 0), 32), sum)
  val topC  = dataC(XLEN - 1)

  val overflow = cout ^ topC

  val sltRes  = dataC(XLEN) ^ overflow
  val sltuRes = !cout

  io.out := MuxCase(
    dataC,
    Seq(
      useSlt -> sltRes.asUInt(XLEN),
      useSltu -> sltuRes.asUInt(XLEN)
    )
  )
  io.isUsed := aluOp.isOneOf(add, addw, sub, subw, slt, sltu)
}

class Shifter(implicit p: Parameters) extends LogicAlu("shifter") {
  import AluType._
  io.out := LookupEnum(aluOp)(
    Seq(
      sll -> (src0 << src1),
      srl -> (src0 >> src1),
      sra -> (src0.asSInt >> src1),
      sllw -> SignExt(io.in.srcs(0) << io.in.srcs(1), 32),
      srlw -> (ZeroExt(io.in.srcs(0), 32) >> io.in.srcs(1)),
      sraw -> (SignExt(io.in.srcs(0), 32) >> io.in.srcs(1))
    )
  )
  io.isUsed := aluOp.isOneOf(sll, srl, sra, sllw, srlw, sraw)
}

class Logicer(implicit p: Parameters) extends LogicAlu("LogicAlu") {
  import AluType._
  io.out := LookupEnum(aluOp)(
    Seq(
      or -> (src0 | src1),
      xor -> (src0 ^ src1),
      and -> (src0 & src1)
    )
  )
  io.isUsed := aluOp.isOneOf(xor, or, and)
}

class Alu(implicit p: Parameters) extends FuncUnit(FuType.alu) {
  val adder   = (new Adder).io
  val shifter = (new Shifter).io
  val logicer = (new Logicer).io

  val allModule = Seq(adder, shifter, logicer)

  allModule.foreach { io =>
    io.in.op   := fuIn.bits.fuOp
    io.in.srcs := fuIn.bits.srcs
  }

  io.out.bits.wPrf.result := Mux1H(
    allModule.map(io => io.isUsed -> io.out)
  )
}
