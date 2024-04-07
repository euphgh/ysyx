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
  override val allInstr = Seq()
  def default(): List[chisel3.ChiselEnum#Type] = {
    List()
  }

  val rv64i: InstrPat = Array(
    LUI -> List(NumSrcReg.zero, HasDstReg.yes, HFuType.alu, AluType.lui),
    AUIPC -> List(NumSrcReg.zero, HasDstReg.yes, HFuType.alu, AluType.add),
    JAL -> List(NumSrcReg.zero, HasDstReg.yes, HFuType.mdu, BranchType.jal),
    JALR -> List(NumSrcReg.one, HasDstReg.yes, HFuType.mdu, BranchType.jalr),
    BEQ -> List(NumSrcReg.two, HasDstReg.no, HFuType.mdu, BranchType.beq),
    BNE -> List(NumSrcReg.two, HasDstReg.no, HFuType.mdu, BranchType.bne),
    BLT -> List(NumSrcReg.two, HasDstReg.no, HFuType.mdu, BranchType.blt),
    BGE -> List(NumSrcReg.two, HasDstReg.no, HFuType.mdu, BranchType.bge),
    BLTU -> List(NumSrcReg.two, HasDstReg.no, HFuType.mdu, BranchType.bltu),
    BGEU -> List(NumSrcReg.two, HasDstReg.no, HFuType.mdu, BranchType.bgeu),
    LB -> List(NumSrcReg.one, HasDstReg.yes, HFuType.lsu, MemType.lb),
    LH -> List(NumSrcReg.one, HasDstReg.yes, HFuType.lsu, MemType.lh),
    LW -> List(NumSrcReg.one, HasDstReg.yes, HFuType.lsu, MemType.lw),
    LD -> List(NumSrcReg.one, HasDstReg.yes, HFuType.lsu, MemType.ld),
    LBU -> List(NumSrcReg.one, HasDstReg.yes, HFuType.lsu, MemType.lbu),
    LHU -> List(NumSrcReg.one, HasDstReg.yes, HFuType.lsu, MemType.lhu),
    LWU -> List(NumSrcReg.one, HasDstReg.yes, HFuType.lsu, MemType.lwu),
    FENCE -> List(NumSrcReg.one, HasDstReg.yes, HFuType.lsu, MemType.fence),
    FENCE_I -> List(NumSrcReg.one, HasDstReg.yes, HFuType.lsu, MemType.fencei),
    SB -> List(NumSrcReg.two, HasDstReg.no, HFuType.lsu, MemType.sd),
    SH -> List(NumSrcReg.two, HasDstReg.no, HFuType.lsu, MemType.sw),
    SW -> List(NumSrcReg.two, HasDstReg.no, HFuType.lsu, MemType.sh),
    SD -> List(NumSrcReg.two, HasDstReg.no, HFuType.lsu, MemType.sb),
    ADDI -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.add),
    SLTI -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.slt),
    SLTIU -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.sltu),
    XORI -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.xor),
    ORI -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.or),
    ANDI -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.and),
    SLLI -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.sll),
    SRLI -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.srl),
    SRAI -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.sra),
    ADD -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.add),
    SUB -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.sub),
    SLL -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.sll),
    SLT -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.slt),
    SLTU -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.sltu),
    XOR -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.xor),
    SRL -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.srl),
    SRA -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.sra),
    OR -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.or),
    AND -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.and),
    ADDIW -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.srlw),
    SLLIW -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.sraw),
    SRAIW -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.sllw),
    SRLIW -> List(NumSrcReg.one, HasDstReg.yes, HFuType.alu, AluType.addw),
    ADDW -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.addw),
    SUBW -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.subw),
    SLLW -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.sllw),
    SRAW -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.sraw),
    SRLW -> List(NumSrcReg.two, HasDstReg.yes, HFuType.alu, AluType.srlw)
  )

  val zicsr: InstrPat = Array(
    CSRRW -> List(NumSrcReg.one, HasDstReg.yes, HFuType.mdu, MduType.csrrw),
    CSRRS -> List(NumSrcReg.one, HasDstReg.yes, HFuType.mdu, MduType.csrrs),
    CSRRC -> List(NumSrcReg.one, HasDstReg.yes, HFuType.mdu, MduType.csrrc),
    CSRRWI -> List(NumSrcReg.zero, HasDstReg.yes, HFuType.mdu, MduType.csrrw),
    CSRRSI -> List(NumSrcReg.zero, HasDstReg.yes, HFuType.mdu, MduType.csrrs),
    CSRRCI -> List(NumSrcReg.zero, HasDstReg.yes, HFuType.mdu, MduType.csrrc)
  )

  val machine: InstrPat = Array(
    ECALL -> List(NumSrcReg.zero, HasDstReg.no, HFuType.mdu, MduType.ecall),
    EBREAK -> List(NumSrcReg.zero, HasDstReg.no, HFuType.mdu, MduType.ebreak),
    WFI -> List(NumSrcReg.zero, HasDstReg.no, HFuType.mdu, MduType.wfi),
    SFENCE_VMA -> List(NumSrcReg.two, HasDstReg.no, HFuType.mdu, MduType.sfence),
    SRET -> List(NumSrcReg.zero, HasDstReg.no, HFuType.mdu, MduType.sret),
    MRET -> List(NumSrcReg.zero, HasDstReg.no, HFuType.mdu, MduType.mret)
  )

  val mext: InstrPat = Array(
    MUL -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.mul),
    MULH -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.mulh),
    MULHSU -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.mulhsu),
    MULHU -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.mulhu),
    MULW -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.mulw),
    DIV -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.div),
    DIVU -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.divu),
    REM -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.rem),
    REMU -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.remu),
    DIVW -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.divw),
    DIVUW -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.divuw),
    REMW -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.remw),
    REMUW -> List(NumSrcReg.two, HasDstReg.yes, HFuType.mdu, MduType.remuw)
  )
}
