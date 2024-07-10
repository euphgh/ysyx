package core

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import utility._
import core._
import core.mmu.MemDataSize
import core.mmu.TlbCmd
import chisel3.util.experimental.decode.decoder
import chisel3.util.experimental.decode.TruthTable

object NumSrcReg extends ChiselEnum {
  val zero, one, two = Value
}

object Src1From extends ChiselEnum {
  val pc, reg, none = Value
}

object Src2From extends ChiselEnum {
  val imm, reg, shamt, none = Value
}

object Src3From extends ChiselEnum {
  val reg, none = Value
}

object HasDstReg extends ChiselEnum {
  val no, yes = Value
}

object FuType extends ChiselEnum {
  val alu, lsu, mdu, bru = Value
}

object FuOpType {
  val opXLEN  = Seq(MemType, AluType, BranchType, MduType).map(_.getWidth).max
  def apply() = UInt(opXLEN.W)
}

object MduType extends ChiselEnum {
  val mul, mulh, mulhu, mulhsu, mulw         = Value
  val div, divu, divw, divuw                 = Value
  val rem, remu, remw, remuw                 = Value
  val csrrw, csrrs, csrrc                    = Value
  val ecall, ebreak, wfi, sfence, sret, mret = Value
  def isMul(fuOp: UInt) = MduType.safe(fuOp)._1.isOneOf(mul, mulh, mulhu, mulhsu, mulw)
  def isDiv(fuOp: UInt) = MduType.safe(fuOp)._1.isOneOf(div, divu, divw, divuw, rem, remu, remw, remuw)
  def isCSR(fuOp: UInt) = MduType.safe(fuOp)._2 && !isMul(fuOp) && !isDiv(fuOp)
}

object MemType extends ChiselEnum {
  val fence, fencei  = Value
  val ld, lw, lh, lb = Value
  val lwu, lhu, lbu  = Value
  val sd, sw, sb, sh = Value
  val sc, lr         = Value

  def isLoad(op: UInt) = {
    op.asTypeOf(MemType()).isOneOf(lb, lbu, lh, lhu, lw, lwu, ld, lr)
  }
  def isStore(op: UInt) = {
    op.asTypeOf(MemType()).isOneOf(sb, sh, sw, sd, sc)
  }

  private def parseRules(op: UInt, fmap: Seq[(Seq[Type], UInt)]) = {
    val expanded = fmap.flatMap {
      case (inputs, output) =>
        inputs.map(input => BitPat(input.asUInt) -> BitPat(output))
    }
    val tTable = TruthTable(expanded, BitPat("b" + "?" * fmap.head._2.getWidth))
    decoder(op, tTable)
  }

  def mask(op: UInt, lsb: UInt)(implicit p: Parameters) = {
    val rules = Seq(
      Seq(ld, lr, sd, sc) -> Seq(
        BitPat("b???") -> "b1111_1111".U
      ),
      Seq(lw, lwu, sd) -> Seq(
        BitPat("b0??") -> "b0000_1111".U,
        BitPat("b1??") -> "b1111_0000".U
      ),
      Seq(lh, lhu, sh) -> Seq(
        BitPat("b00?") -> "b0000_0011".U,
        BitPat("b01?") -> "b0000_1100".U,
        BitPat("b10?") -> "b0011_0000".U,
        BitPat("b11?") -> "b1100_0000".U
      ),
      Seq(lb, lbu, sb) -> Seq(
        BitPat("b000") -> "b0000_0001".U,
        BitPat("b001") -> "b0000_0010".U,
        BitPat("b010") -> "b0000_0100".U,
        BitPat("b011") -> "b0000_1000".U,
        BitPat("b100") -> "b0001_0000".U,
        BitPat("b101") -> "b0010_0000".U,
        BitPat("b110") -> "b0100_0000".U,
        BitPat("b111") -> "b1000_0000".U
      )
    )
    val expanded = rules.flatMap {
      case (opSeq, lsbSeq) =>
        lsbSeq.flatMap {
          case (bitPat, rmask) =>
            opSeq.map { op =>
              BitPat(op.asUInt) ## bitPat -> BitPat(rmask)
            }
        }
    }
    decoder(Cat(op, lsb), TruthTable(expanded, BitPat("b????_????")))
  }

  def size(op: UInt)(implicit p: Parameters) = {
    parseRules(
      op,
      Seq(
        (Seq(ld, lr, sd, sc) -> MemDataSize.uint64.asUInt),
        (Seq(lw, sw) -> MemDataSize.uint32.asUInt),
        (Seq(lh, lhu, sh) -> MemDataSize.uint16.asUInt),
        (Seq(lb, lbu, sb) -> MemDataSize.uint8.asUInt)
      )
    )
  }

  def tlbCmd(op: UInt)(implicit p: Parameters) = {
    parseRules(
      op,
      Seq(
        (Seq(ld, lw, lh, lb, lwu, lhu, lbu) -> TlbCmd.read.asUInt),
        (Seq(sd, sw, sb, sh) -> TlbCmd.write.asUInt),
        (Seq(sc) -> TlbCmd.atom_write.asUInt),
        (Seq(lr) -> TlbCmd.atom_read.asUInt)
      )
    )
  }
}

object AluType extends ChiselEnum {
  /* logic */
  val xor, or, and, lui = Value
  /* shift */
  val sll, srl, sra, sllw, sraw, srlw = Value
  /* adder */
  val add, sub, addw, subw, slt, sltu = Value
}

//not need now
object BlockType extends ChiselEnum {
  val CACHEINST, SYNC, MFC0, NON = Value
}

object BranchType extends ChiselEnum {
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
    val deleg = new CoreDelegate {
      def apply(brType: BranchType.Type, instr: RInstr, pc: UInt) = {
        require(instr.getWidth == 32)
        require(pc.getWidth == VAddrBits)
        val imm4to1   = Mux(isB(brType), instr(11, 8), instr(24, 21))
        val imm11Bits = Mux(isB(brType), instr(7), instr(20))
        val imm19to12 = Mux(isB(brType), instr(19, 12), Fill(19 - 12, instr(31)))
        val imm       = Cat(instr(31), imm19to12, imm11Bits, instr(30, 25), imm4to1, 0.U(2.W))
        BrDest(!brType.isOneOf(jalr, none), pc + SignExt(imm, VAddrBits))
      }
    }
    deleg(brType, instr, pc)
  }
}

object BtbType extends ChiselEnum {
  val none, branch = Value
  val jump         = Value("b100".U)
  val push         = Value("b110".U)
  val pop          = Value("b101".U)
  val both         = Value("b111".U) // first pop than push
  def isJump(brType:  BtbType.Type) = brType.asUInt(2).asBool
  def isLink(index:   UInt)         = index === 1.U || index === 5.U
  def needPop(index:  BtbType.Type) = index.isOneOf(pop, both)
  def needPush(index: BtbType.Type) = index.isOneOf(push, both)
  def isRAS(index: BtbType.Type)    = index.isOneOf(push, pop, both)
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

