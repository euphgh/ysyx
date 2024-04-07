package core

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import utility._
import core._
import freechips.rocketchip.tile.CoreParams

object NumSrcReg extends ChiselEnum {
  val zero, one, two = Value
}

object HasDstReg extends ChiselEnum {
  val no, yes = Value
}

object HFuType extends ChiselEnum {
  val alu, lsu, mdu = Value
}

abstract class FuOpType extends ChiselEnum {
  def apply() = UInt(256.W).asTypeOf(Type())
  def X       = BitPat("b???????")
}

object MduType extends FuOpType {
  val mul, mulh, mulhu, mulhsu, mulw         = Value
  val div, divu, divw, divuw                 = Value
  val rem, remu, remw, remuw                 = Value
  val csrrw, csrrs, csrrc                    = Value
  val ecall, ebreak, wfi, sfence, sret, mret = Value
}

object MemType extends FuOpType {
  val fence, fencei  = Value
  val ld, lw, lh, lb = Value
  val lwu, lhu, lbu  = Value
  val sd, sw, sb, sh = Value
  val sc, lr         = Value

  def isLoad(op: MemType.Type) = {
    op.isOneOf(lb, lbu, lh, lhu, lw, lwu, ld, lr)
  }
  def isStore(op: MemType.Type) = {
    op.isOneOf(sb, sh, sw, sd, sc)
  }
}

object AluType extends FuOpType {
  val lui, slt, sltu, xor, or, and    = Value
  val sll, srl, sra, sllw, sraw, srlw = Value
  val add, sub, addw, subw            = Value
}

//not need now
object BlockType extends ChiselEnum {
  val CACHEINST, SYNC, MFC0, NON = Value
}

object BranchType extends FuOpType {
  val none, beq, bne, blt, bge, bltu, bgeu, jal, jalr = Value
  def isB(op: BranchType.Type): Bool = op.isOneOf(beq, bne, blt, bge, bltu, bgeu)

  // //cond
  // def eq(op:     BranchType.Type): Bool = op === BEQ
  // def ne(op:     BranchType.Type): Bool = op === BNE
  // def gez(op:    BranchType.Type): Bool = op === BGEZ || op === BGEZAL
  // def gtz(op:    BranchType.Type): Bool = op === BGTZ
  // def lez(op:    BranchType.Type): Bool = op === BLEZ
  // def ltz(op:    BranchType.Type): Bool = op === BLTZ || op === BLTZAL
  // def noCond(op: BranchType.Type): Bool = isJ(op) || isJr(op)
  // //trans

  import BtbType.isLink
  def toBtbType(op: BranchType.Type, rs1: UInt, rd: UInt): BtbType.Type =
    MuxCase(
      BtbType.none,
      Seq(
        isB(op) -> BtbType.branch,
        (!isLink(rd) && !isLink(rs1)) -> BtbType.none,
        (!isLink(rd) && isLink(rs1)) -> BtbType.pop,
        (isLink(rd) && !isLink(rs1)) -> BtbType.push,
        (isLink(rd) && isLink(rs1) && (rs1 =/= rd)) -> BtbType.both,
        (isLink(rd) && isLink(rs1) && (rs1 === rd)) -> BtbType.push
      )
    )

  case class BrDest(avaliable: Bool, dest: UInt)
  def calDest(brType: BranchType.Type, instr: RInstr, pc: UInt)(implicit p: Parameters): BrDest = {
    val scope = new CoreScope {
      def deleg(brType: BranchType.Type, instr: RInstr, pc: UInt): BrDest = {
        require(instr.getWidth == 32)
        require(pc.getWidth == VAddrBits)
        val imm4to1   = Mux(isB(brType), instr(11, 8), instr(24, 21))
        val imm11Bits = Mux(isB(brType), instr(7), instr(20))
        val imm19to12 = Mux(isB(brType), instr(19, 12), Fill(19 - 12, instr(31)))
        val imm       = Cat(instr(31), imm19to12, imm11Bits, instr(30, 25), imm4to1, 0.U(2.W))
        BrDest(!brType.isOneOf(jalr, none), pc + SignExt(imm, VAddrBits))
      }
    }
    scope.deleg(brType, instr, pc)
  }
}

object FuType extends Enumeration {
  type t = Value
  // 自动赋值枚举成员
  val Alu, Lsu, Mdu = Value
  def needOBpIn(input: Value) = input == Alu
  def needSBpIn(input: Value) = input == Alu || input == Lsu
  def needBpIn(input:  Value) = needOBpIn(input) || needSBpIn(input)

  def oBpNum(input: Value): Int = input match {
    case alu => 2
    case Lsu => 0
    case Mdu => 0
  }
  def bpNum(input: Value): Int =
    if (needSBpIn(input)) (oBpNum(input) + 1) else oBpNum(input)
}

object BtbType extends ChiselEnum {
  val none, branch = Value
  val jump         = Value("b100".U)
  val push         = Value("b110".U)
  val pop          = Value("b101".U)
  val both         = Value("b111".U)
  def isJump(brType: BtbType.Type) = brType.asUInt(2).asBool
  def isLink(index:  UInt)         = index === 1.U || index === 5.U
}

object CCAttr extends ChiselEnum {
  val zero     = Value("b000".U)
  val Uncached = Value("b010".U)
  val Cached   = Value("b011".U)
  val other    = Value("b100".U)
  def isUnCache(attr: UInt) = attr =/= Cached.asUInt
}

object CacheOp extends ChiselEnum {
  val IndexInvalidI          = Value("b00000".U)
  val IndexWriteBackInvalidD = Value("b00001".U)
  val IndexStoreTagI         = Value("b01000".U)
  val IndexStoreTagD         = Value("b01001".U)
  val HitInvalidI            = Value("b10000".U)
  val HitInvalidD            = Value("b10001".U)
  val HitWriteBackInvalidD   = Value("b10101".U)
  def isDop(op: CacheOp.Type) = {
    op.isOneOf(IndexWriteBackInvalidD, IndexStoreTagD, HitInvalidD, HitWriteBackInvalidD)
  }
  def isIop(op: CacheOp.Type) = {
    !isDop(op)
  }
  def isIdxInv(op: CacheOp.Type) = {
    op.isOneOf(IndexInvalidI, IndexWriteBackInvalidD)
  }
  def isIdxStoreTag(op: CacheOp.Type) = {
    op.isOneOf(IndexStoreTagD, IndexStoreTagI)
  }
  def isHitInv(op: CacheOp.Type) = {
    op.isOneOf(HitInvalidI, HitInvalidD, HitWriteBackInvalidD)
  }
}

class ExcCode(implicit p: Parameters) extends CoreBundle {
  val excCode = ExcCode()
  val refill  = Bool()
}

object ExcCode extends ChiselEnum {
  val Int  = Value(0x00.U)
  val Mod  = Value(0x01.U)
  val TLBL = Value(0x02.U)
  val TLBS = Value(0x03.U)
  val AdEL = Value(0x04.U)
  val AdES = Value(0x05.U)
  val IBE  = Value(0x06.U)
  val DBE  = Value(0x07.U)
  val Sys  = Value(0x08.U)
  val Bp   = Value(0x09.U)
  val RI   = Value(0x0a.U)
  val CpU  = Value(0x0b.U)
  val Ov   = Value(0x0c.U)
  val Tr   = Value(0x0d.U)
  def canRe(op: ExcCode.Type) = {
    op.isOneOf(Sys, Bp, Tr)
  }
}

object FrontExcCode extends ChiselEnum {
  val NONE, AdEL, InvalidTLBL, RefillTLBL = Value
  def happen(code:   FrontExcCode.Type): Bool         = code =/= NONE
  def isRefill(code: FrontExcCode.Type): Bool         = code === RefillTLBL
  def trans(code:    FrontExcCode.Type): ExcCode.Type = Mux(code === AdEL, ExcCode.AdEL, ExcCode.TLBL)
}
