package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

import macros.decode._

object RV64I extends DecodeUtils {
  import freechips.rocketchip.rocket.Instructions._
  override val allInstr = rv64i ++ zicsr ++ machine ++ mext
  def default(): List[chisel3.ChiselEnum#Type] = {
    List()
  }

  val rv64i: InstrPat = Array(
    LUI -> List(Src1From.none, Src2From.imm, NumSrcReg.zero, HasDstReg.yes, FuType.alu, AluType.add),
    AUIPC -> List(Src1From.pc, Src2From.imm, NumSrcReg.zero, HasDstReg.yes, FuType.alu, AluType.add),
    JAL -> List(Src1From.pc, Src2From.imm, NumSrcReg.zero, HasDstReg.yes, FuType.mdu, BranchType.jal),
    JALR -> List(Src1From.reg, Src2From.imm, NumSrcReg.one, HasDstReg.yes, FuType.mdu, BranchType.jalr),
    BEQ -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.no, FuType.mdu, BranchType.beq),
    BNE -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.no, FuType.mdu, BranchType.bne),
    BLT -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.no, FuType.mdu, BranchType.blt),
    BGE -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.no, FuType.mdu, BranchType.bge),
    BLTU -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.no, FuType.mdu, BranchType.bltu),
    BGEU -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.no, FuType.mdu, BranchType.bgeu),
    LB -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.lsu, MemType.lb),
    LH -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.lsu, MemType.lh),
    LW -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.lsu, MemType.lw),
    LD -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.lsu, MemType.ld),
    LBU -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.lsu, MemType.lbu),
    LHU -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.lsu, MemType.lhu),
    LWU -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.lsu, MemType.lwu),
    FENCE -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.lsu, MemType.fence),
    FENCE_I -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.lsu, MemType.fencei),
    SB -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.no, FuType.lsu, MemType.sd),
    SH -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.no, FuType.lsu, MemType.sw),
    SW -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.no, FuType.lsu, MemType.sh),
    SD -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.no, FuType.lsu, MemType.sb),
    ADDI -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.add),
    SLTI -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.slt),
    SLTIU -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.sltu),
    XORI -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.xor),
    ORI -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.or),
    ANDI -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.and),
    SLLI -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.sll),
    SRLI -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.srl),
    SRAI -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.sra),
    ADD -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.add),
    SUB -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.sub),
    SLL -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.sll),
    SLT -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.slt),
    SLTU -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.sltu),
    XOR -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.xor),
    SRL -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.srl),
    SRA -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.sra),
    OR -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.or),
    AND -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.and),
    ADDIW -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.srlw),
    SLLIW -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.sraw),
    SRAIW -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.sllw),
    SRLIW -> List(Src1From.none, Src2From.none, NumSrcReg.one, HasDstReg.yes, FuType.alu, AluType.addw),
    ADDW -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.addw),
    SUBW -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.subw),
    SLLW -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.sllw),
    SRAW -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.sraw),
    SRLW -> List(Src1From.none, Src2From.none, NumSrcReg.two, HasDstReg.yes, FuType.alu, AluType.srlw)
  )

  val zicsr: InstrPat = Array(
    CSRRW -> List(NumSrcReg.one, HasDstReg.yes, FuType.mdu, MduType.csrrw),
    CSRRS -> List(NumSrcReg.one, HasDstReg.yes, FuType.mdu, MduType.csrrs),
    CSRRC -> List(NumSrcReg.one, HasDstReg.yes, FuType.mdu, MduType.csrrc),
    CSRRWI -> List(NumSrcReg.zero, HasDstReg.yes, FuType.mdu, MduType.csrrw),
    CSRRSI -> List(NumSrcReg.zero, HasDstReg.yes, FuType.mdu, MduType.csrrs),
    CSRRCI -> List(NumSrcReg.zero, HasDstReg.yes, FuType.mdu, MduType.csrrc)
  )

  val machine: InstrPat = Array(
    ECALL -> List(NumSrcReg.zero, HasDstReg.no, FuType.mdu, MduType.ecall),
    EBREAK -> List(NumSrcReg.zero, HasDstReg.no, FuType.mdu, MduType.ebreak),
    WFI -> List(NumSrcReg.zero, HasDstReg.no, FuType.mdu, MduType.wfi),
    SFENCE_VMA -> List(NumSrcReg.two, HasDstReg.no, FuType.mdu, MduType.sfence),
    SRET -> List(NumSrcReg.zero, HasDstReg.no, FuType.mdu, MduType.sret),
    MRET -> List(NumSrcReg.zero, HasDstReg.no, FuType.mdu, MduType.mret)
  )

  val mext: InstrPat = Array(
    MUL -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.mul),
    MULH -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.mulh),
    MULHSU -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.mulhsu),
    MULHU -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.mulhu),
    MULW -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.mulw),
    DIV -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.div),
    DIVU -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.divu),
    REM -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.rem),
    REMU -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.remu),
    DIVW -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.divw),
    DIVUW -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.divuw),
    REMW -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.remw),
    REMUW -> List(NumSrcReg.two, HasDstReg.yes, FuType.mdu, MduType.remuw)
  )

}
