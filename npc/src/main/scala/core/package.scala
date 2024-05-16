import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core.mmu.TlbExceptionBundle
import core.mmu.PMPRespIO

package object core {
  class RInstr(implicit p: Parameters) extends CoreBundle {
    val opcode = UInt(7.W)
    val rd     = UInt(5.W)
    val funct3 = UInt(3.W)
    val rs1    = UInt(5.W)
    val rs2    = UInt(5.W)
    val funct7 = UInt(7.W)
    def apply(index: Int) = this.asUInt(index)
    def apply(msb:   Int, lsb: Int) = this.asUInt(msb, lsb)
  }

  object RInstr {
    def fromUInt(uint: UInt)(implicit p: Parameters): RInstr = {
      require(uint.getWidth == 32)
      uint.asTypeOf(new RInstr)
    }
  }

  object SrcState {
    def busy = "b0".U
    def rdy  = "b1".U
    // def specRdy = "b10".U // speculative ready, for future use
    def apply() = UInt(1.W)
  }

  // object FuType {
  //   def jmp   = "b0000".U
  //   def i2f   = "b0001".U
  //   def csr   = "b0010".U
  //   def alu   = "b0110".U
  //   def mul   = "b0100".U
  //   def div   = "b0101".U
  //   def fence = "b0011".U
  //   def bku   = "b0111".U

  //   def fmac     = "b1000".U
  //   def fmisc    = "b1011".U
  //   def fDivSqrt = "b1010".U

  //   def ldu = "b1100".U
  //   def stu = "b1101".U
  //   def mou = "b1111".U // for amo, lr, sc, fence

  //   def X = BitPat("b????")

  //   def num = 14

  //   def apply() = UInt(log2Up(num).W)

  //   def isIntExu(fuType:       UInt) = !fuType(3)
  //   def isJumpExu(fuType:      UInt) = fuType === jmp
  //   def isFpExu(fuType:        UInt) = fuType(3, 2) === "b10".U
  //   def isMemExu(fuType:       UInt) = fuType(3, 2) === "b11".U
  //   def isLoadStore(fuType:    UInt) = isMemExu(fuType) && !fuType(1)
  //   def isStoreExu(fuType:     UInt) = isMemExu(fuType) && fuType(0)
  //   def isAMO(fuType:          UInt) = fuType(1)
  //   def isFence(fuType:        UInt) = fuType === fence
  //   def isSvinvalBegin(fuType: UInt, func: UInt, flush: Bool) =
  //     isFence(fuType) && func === FenceOpType.nofence && !flush
  //   def isSvinval(fuType:    UInt, func: UInt, flush: Bool) = isFence(fuType) && func === FenceOpType.sfence && !flush
  //   def isSvinvalEnd(fuType: UInt, func: UInt, flush: Bool) = isFence(fuType) && func === FenceOpType.nofence && flush

  //   def jmpCanAccept(fuType: UInt) = !fuType(2)
  //   def mduCanAccept(fuType: UInt) = fuType(2) && !fuType(1) || fuType(2) && fuType(1) && fuType(0)
  //   def aluCanAccept(fuType: UInt) = fuType(2) && fuType(1) && !fuType(0)

  //   def fmacCanAccept(fuType:  UInt) = !fuType(1)
  //   def fmiscCanAccept(fuType: UInt) = fuType(1)

  //   def loadCanAccept(fuType:  UInt) = !fuType(0)
  //   def storeCanAccept(fuType: UInt) = fuType(0)

  //   def storeIsAMO(fuType: UInt) = fuType(1)

  //   val functionNameMap = Map(
  //     jmp.litValue -> "jmp",
  //     i2f.litValue -> "int_to_float",
  //     csr.litValue -> "csr",
  //     alu.litValue -> "alu",
  //     mul.litValue -> "mul",
  //     div.litValue -> "div",
  //     fence.litValue -> "fence",
  //     bku.litValue -> "bku",
  //     fmac.litValue -> "fmac",
  //     fmisc.litValue -> "fmisc",
  //     fDivSqrt.litValue -> "fdiv_fsqrt",
  //     ldu.litValue -> "load",
  //     stu.litValue -> "store",
  //     mou.litValue -> "mou"
  //   )
  // }

  object CommitType {
    def NORMAL = "b000".U // int/fp
    def BRANCH = "b001".U // branch
    def LOAD   = "b010".U // load
    def STORE  = "b011".U // store

    def apply() = UInt(3.W)
    def isFused(commitType:       UInt): Bool = commitType(2)
    def isLoadStore(commitType:   UInt): Bool = !isFused(commitType) && commitType(1)
    def lsInstIsStore(commitType: UInt): Bool = commitType(0)
    def isStore(commitType:       UInt): Bool = isLoadStore(commitType) && lsInstIsStore(commitType)
    def isBranch(commitType:      UInt): Bool = commitType(0) && !commitType(1) && !isFused(commitType)
  }

  object RedirectLevel {
    def flushAfter = "b0".U
    def flush      = "b1".U

    def apply() = UInt(1.W)
    // def isUnconditional(level: UInt) = level(1)
    def flushItself(level: UInt) = level(0)
    // def isException(level: UInt) = level(1) && level(0)
  }

  class ExceptionVec extends Bundle {
    val vec = Vec(ExceptionVec.ExceptionVecSize, Bool())
    def merge(that: ExceptionVec) = {
      vec.zipWithIndex.foreach {
        case (bit, index) =>
          bit := bit || that(index)
      }
      this
    }
    def pageFault(that: TlbExceptionBundle) {
      this.append(that.instr, ExceptionNO.instrPageFault)
      this.append(that.ld, ExceptionNO.loadPageFault)
      this.append(that.st, ExceptionNO.storePageFault)
    }
    def accessFault(that: TlbExceptionBundle) {
      this.append(that.instr, ExceptionNO.instrAccessFault)
      this.append(that.ld, ExceptionNO.loadAccessFault)
      this.append(that.st, ExceptionNO.storeAccessFault)
    }
    def accessFault(that: PMPRespIO) {
      this.append(that.instr, ExceptionNO.instrAccessFault)
      this.append(that.ld, ExceptionNO.loadAccessFault)
      this.append(that.st, ExceptionNO.storeAccessFault)
    }
    def append(cond: Bool, number: Int) = {
      vec(number) := vec(number) | cond
      this
    }
    def apply(index: Int) = vec(index)
    def asBools        = vec.toSeq
    def hasException() = vec.asUInt.orR
  }
  object ExceptionVec {
    val ExceptionVecSize = 24
    def apply()          = new ExceptionVec()
  }

  object PMAMode {
    def R        = "b1".U << 0 //readable
    def W        = "b1".U << 1 //writeable
    def X        = "b1".U << 2 //executable
    def I        = "b1".U << 3 //cacheable: icache
    def D        = "b1".U << 4 //cacheable: dcache
    def S        = "b1".U << 5 //enable speculative access
    def A        = "b1".U << 6 //enable atomic operation, A imply R & W
    def C        = "b1".U << 7 //if it is cacheable is configable
    def Reserved = "b0".U

    def apply() = UInt(7.W)

    def read(mode:             UInt) = mode(0)
    def write(mode:            UInt) = mode(1)
    def execute(mode:          UInt) = mode(2)
    def icache(mode:           UInt) = mode(3)
    def dcache(mode:           UInt) = mode(4)
    def speculate(mode:        UInt) = mode(5)
    def atomic(mode:           UInt) = mode(6)
    def configable_cache(mode: UInt) = mode(7)

    def strToMode(s: String) = {
      var result = 0.U(8.W)
      if (s.toUpperCase.indexOf("R") >= 0) result = result + R
      if (s.toUpperCase.indexOf("W") >= 0) result = result + W
      if (s.toUpperCase.indexOf("X") >= 0) result = result + X
      if (s.toUpperCase.indexOf("I") >= 0) result = result + I
      if (s.toUpperCase.indexOf("D") >= 0) result = result + D
      if (s.toUpperCase.indexOf("S") >= 0) result = result + S
      if (s.toUpperCase.indexOf("A") >= 0) result = result + A
      if (s.toUpperCase.indexOf("C") >= 0) result = result + C
      result
    }
  }

  object CSROpType {
    def jmp  = "b000".U
    def wrt  = "b001".U
    def set  = "b010".U
    def clr  = "b011".U
    def wfi  = "b100".U
    def wrti = "b101".U
    def seti = "b110".U
    def clri = "b111".U
    def needAccess(op: UInt): Bool = op(1, 0) =/= 0.U
  }

  // jump
  object JumpOpType {
    def jal   = "b00".U
    def jalr  = "b01".U
    def auipc = "b10".U
//    def call = "b11_011".U
//    def ret  = "b11_100".U
    def jumpOpisJalr(op:  UInt) = op(0)
    def jumpOpisAuipc(op: UInt) = op(1)
  }

  object FenceOpType {
    def fence    = "b10000".U
    def sfence   = "b10001".U
    def fencei   = "b10010".U
    def hfence_v = "b10011".U
    def hfence_g = "b10100".U
    def nofence  = "b00000".U
  }

  object ALUOpType {
    // shift optype
    def slliuw = "b000_0000".U // slliuw: ZEXT(src1[31:0]) << shamt
    def sll    = "b000_0001".U // sll:     src1 << src2

    def bclr = "b000_0010".U // bclr:    src1 & ~(1 << src2[5:0])
    def bset = "b000_0011".U // bset:    src1 | (1 << src2[5:0])
    def binv = "b000_0100".U // binv:    src1 ^ ~(1 << src2[5:0])

    def srl  = "b000_0101".U // srl:     src1 >> src2
    def bext = "b000_0110".U // bext:    (src1 >> src2)[0]
    def sra  = "b000_0111".U // sra:     src1 >> src2 (arithmetic)

    def rol = "b000_1001".U // rol:     (src1 << src2) | (src1 >> (xlen - src2))
    def ror = "b000_1011".U // ror:     (src1 >> src2) | (src1 << (xlen - src2))

    // RV64 32bit optype
    def addw    = "b001_0000".U // addw:      SEXT((src1 + src2)[31:0])
    def oddaddw = "b001_0001".U // oddaddw:   SEXT((src1[0] + src2)[31:0])
    def subw    = "b001_0010".U // subw:      SEXT((src1 - src2)[31:0])

    def addwbit   = "b001_0100".U // addwbit:   (src1 + src2)[0]
    def addwbyte  = "b001_0101".U // addwbyte:  (src1 + src2)[7:0]
    def addwzexth = "b001_0110".U // addwzexth: ZEXT((src1  + src2)[15:0])
    def addwsexth = "b001_0111".U // addwsexth: SEXT((src1  + src2)[15:0])

    def sllw = "b001_1000".U // sllw:     SEXT((src1 << src2)[31:0])
    def srlw = "b001_1001".U // srlw:     SEXT((src1[31:0] >> src2)[31:0])
    def sraw = "b001_1010".U // sraw:     SEXT((src1[31:0] >> src2)[31:0])
    def rolw = "b001_1100".U
    def rorw = "b001_1101".U

    // ADD-op
    def adduw  = "b010_0000".U // adduw:  src1[31:0]  + src2
    def add    = "b010_0001".U // add:     src1        + src2
    def oddadd = "b010_0010".U // oddadd:  src1[0]     + src2

    def sr29add = "b010_0100".U // sr29add: src1[63:29] + src2
    def sr30add = "b010_0101".U // sr30add: src1[63:30] + src2
    def sr31add = "b010_0110".U // sr31add: src1[63:31] + src2
    def sr32add = "b010_0111".U // sr32add: src1[63:32] + src2

    def sh1adduw = "b010_1000".U // sh1adduw: {src1[31:0], 1'b0} + src2
    def sh1add   = "b010_1001".U // sh1add: {src1[62:0], 1'b0} + src2
    def sh2adduw = "b010_1010".U // sh2add_uw: {src1[31:0], 2'b0} + src2
    def sh2add   = "b010_1011".U // sh2add: {src1[61:0], 2'b0} + src2
    def sh3adduw = "b010_1100".U // sh3add_uw: {src1[31:0], 3'b0} + src2
    def sh3add   = "b010_1101".U // sh3add: {src1[60:0], 3'b0} + src2
    def sh4add   = "b010_1111".U // sh4add: {src1[59:0], 4'b0} + src2

    // SUB-op: src1 - src2
    def sub  = "b011_0000".U
    def sltu = "b011_0001".U
    def slt  = "b011_0010".U
    def maxu = "b011_0100".U
    def minu = "b011_0101".U
    def max  = "b011_0110".U
    def min  = "b011_0111".U

    // branch
    def beq  = "b111_0000".U
    def bne  = "b111_0010".U
    def blt  = "b111_1000".U
    def bge  = "b111_1010".U
    def bltu = "b111_1100".U
    def bgeu = "b111_1110".U

    // misc optype
    def and  = "b100_0000".U
    def andn = "b100_0001".U
    def or   = "b100_0010".U
    def orn  = "b100_0011".U
    def xor  = "b100_0100".U
    def xnor = "b100_0101".U
    def orcb = "b100_0110".U

    def sextb = "b100_1000".U
    def packh = "b100_1001".U
    def sexth = "b100_1010".U
    def packw = "b100_1011".U

    def revb  = "b101_0000".U
    def rev8  = "b101_0001".U
    def pack  = "b101_0010".U
    def orh48 = "b101_0011".U

    def szewl1 = "b101_1000".U
    def szewl2 = "b101_1001".U
    def szewl3 = "b101_1010".U
    def byte2  = "b101_1011".U

    def andlsb    = "b110_0000".U
    def andzexth  = "b110_0001".U
    def orlsb     = "b110_0010".U
    def orzexth   = "b110_0011".U
    def xorlsb    = "b110_0100".U
    def xorzexth  = "b110_0101".U
    def orcblsb   = "b110_0110".U
    def orcbzexth = "b110_0111".U

    def isAddw(func:         UInt) = func(6, 4) === "b001".U && !func(3) && !func(1)
    def isSimpleLogic(func:  UInt) = func(6, 4) === "b100".U && !func(0)
    def logicToLsb(func:     UInt) = Cat("b110".U(3.W), func(3, 1), 0.U(1.W))
    def logicToZexth(func:   UInt) = Cat("b110".U(3.W), func(3, 1), 1.U(1.W))
    def isBranch(func:       UInt) = func(6, 4) === "b111".U
    def getBranchType(func:  UInt) = func(3, 2)
    def isBranchInvert(func: UInt) = func(1)

    def apply() = UInt(7.W)
  }

  object MDUOpType {
    // mul
    // bit encoding: | type (2bit) | isWord(1bit) | opcode(2bit) |
    def mul    = "b00000".U
    def mulh   = "b00001".U
    def mulhsu = "b00010".U
    def mulhu  = "b00011".U
    def mulw   = "b00100".U

    def mulw7 = "b01100".U

    // div
    // bit encoding: | type (2bit) | isWord(1bit) | isSign(1bit) | opcode(1bit) |
    def div  = "b10000".U
    def divu = "b10010".U
    def rem  = "b10001".U
    def remu = "b10011".U

    def divw  = "b10100".U
    def divuw = "b10110".U
    def remw  = "b10101".U
    def remuw = "b10111".U

    def isMul(op: UInt) = !op(4)
    def isDiv(op: UInt) = op(4)

    def isDivSign(op: UInt) = isDiv(op) && !op(1)
    def isW(op:       UInt) = op(2)
    def isH(op:       UInt) = (isDiv(op) && op(0)) || (isMul(op) && op(1, 0) =/= 0.U)
    def getMulOp(op:  UInt) = op(1, 0)
  }

  object LSUOpType {
    // load pipeline

    // normal load
    // Note: bit(1, 0) are size, DO NOT CHANGE
    // bit encoding: | load 0 | is unsigned(1bit) | size(2bit) |
    def lb  = "b0000".U
    def lh  = "b0001".U
    def lw  = "b0010".U
    def ld  = "b0011".U
    def lbu = "b0100".U
    def lhu = "b0101".U
    def lwu = "b0110".U
    // hypervior load
    // bit encoding: | hlvx 1 | hlv 1 | load 0 | is unsigned(1bit) | size(2bit) |
    def hlvb   = "b10000".U
    def hlvh   = "b10001".U
    def hlvw   = "b10010".U
    def hlvd   = "b10011".U
    def hlvbu  = "b10100".U
    def hlvhu  = "b10101".U
    def hlvwu  = "b10110".U
    def hlvxhu = "b110101".U
    def hlvxwu = "b110110".U
    def isHlv(op:  UInt): Bool = op(4)
    def isHlvx(op: UInt): Bool = op(5)

    // Zicbop software prefetch
    // bit encoding: | prefetch 1 | 0 | prefetch type (2bit) |
    def prefetch_i = "b1000".U // TODO
    def prefetch_r = "b1001".U
    def prefetch_w = "b1010".U

    def isPrefetch(op: UInt): Bool = op(3)

    // store pipeline
    // normal store
    // bit encoding: | store 00 | size(2bit) |
    def sb = "b0000".U
    def sh = "b0001".U
    def sw = "b0010".U
    def sd = "b0011".U

    //hypervisor store
    // bit encoding: |hsv 1 | store 00 | size(2bit) |
    def hsvb = "b10000".U
    def hsvh = "b10001".U
    def hsvw = "b10010".U
    def hsvd = "b10011".U
    def isHsv(op: UInt): Bool = op(4)

    // l1 cache op
    // bit encoding: | cbo_zero 01 | size(2bit) 11 |
    def cbo_zero = "b0111".U

    // llc op
    // bit encoding: | prefetch 11 | suboptype(2bit) |
    def cbo_clean = "b1100".U
    def cbo_flush = "b1101".U
    def cbo_inval = "b1110".U

    def isCbo(op: UInt): Bool = op(3, 2) === "b11".U

    // atomics
    // bit(1, 0) are size
    // since atomics use a different fu type
    // so we can safely reuse other load/store's encodings
    // bit encoding: | optype(4bit) | size (2bit) |
    def lr_w      = "b000010".U
    def sc_w      = "b000110".U
    def amoswap_w = "b001010".U
    def amoadd_w  = "b001110".U
    def amoxor_w  = "b010010".U
    def amoand_w  = "b010110".U
    def amoor_w   = "b011010".U
    def amomin_w  = "b011110".U
    def amomax_w  = "b100010".U
    def amominu_w = "b100110".U
    def amomaxu_w = "b101010".U

    def lr_d      = "b000011".U
    def sc_d      = "b000111".U
    def amoswap_d = "b001011".U
    def amoadd_d  = "b001111".U
    def amoxor_d  = "b010011".U
    def amoand_d  = "b010111".U
    def amoor_d   = "b011011".U
    def amomin_d  = "b011111".U
    def amomax_d  = "b100011".U
    def amominu_d = "b100111".U
    def amomaxu_d = "b101011".U

    def size(op: UInt) = op(1, 0)
  }

  object BKUOpType {

    def clmul  = "b000000".U
    def clmulh = "b000001".U
    def clmulr = "b000010".U
    def xpermn = "b000100".U
    def xpermb = "b000101".U

    def clz   = "b001000".U
    def clzw  = "b001001".U
    def ctz   = "b001010".U
    def ctzw  = "b001011".U
    def cpop  = "b001100".U
    def cpopw = "b001101".U

    // 01xxxx is reserve
    def aes64es   = "b100000".U
    def aes64esm  = "b100001".U
    def aes64ds   = "b100010".U
    def aes64dsm  = "b100011".U
    def aes64im   = "b100100".U
    def aes64ks1i = "b100101".U
    def aes64ks2  = "b100110".U

    // merge to two instruction sm4ks & sm4ed
    def sm4ed0 = "b101000".U
    def sm4ed1 = "b101001".U
    def sm4ed2 = "b101010".U
    def sm4ed3 = "b101011".U
    def sm4ks0 = "b101100".U
    def sm4ks1 = "b101101".U
    def sm4ks2 = "b101110".U
    def sm4ks3 = "b101111".U

    def sha256sum0 = "b110000".U
    def sha256sum1 = "b110001".U
    def sha256sig0 = "b110010".U
    def sha256sig1 = "b110011".U
    def sha512sum0 = "b110100".U
    def sha512sum1 = "b110101".U
    def sha512sig0 = "b110110".U
    def sha512sig1 = "b110111".U

    def sm3p0 = "b111000".U
    def sm3p1 = "b111001".U
  }

  object BTBtype {
    def B = "b00".U // branch
    def J = "b01".U // jump
    def I = "b10".U // indirect
    def R = "b11".U // return

    def apply() = UInt(2.W)
  }

  object SelImm {
    def IMM_X         = "b0111".U
    def IMM_S         = "b0000".U
    def IMM_SB        = "b0001".U
    def IMM_U         = "b0010".U
    def IMM_UJ        = "b0011".U
    def IMM_I         = "b0100".U
    def IMM_Z         = "b0101".U
    def INVALID_INSTR = "b0110".U
    def IMM_B6        = "b1000".U

    def X = BitPat("b????")

    def apply() = UInt(4.W)
  }

  object ExceptionNO {
    def instrAddrMisaligned = 0
    def instrAccessFault    = 1
    def illegalInstr        = 2
    def breakPoint          = 3
    def loadAddrMisaligned  = 4
    def loadAccessFault     = 5
    def storeAddrMisaligned = 6
    def storeAccessFault    = 7
    def ecallU              = 8
    def ecallS              = 9
    def ecallVS             = 10
    def ecallM              = 11
    def instrPageFault      = 12
    def loadPageFault       = 13
    // def singleStep          = 14
    def storePageFault      = 15
    def instrGuestPageFault = 20
    def loadGuestPageFault  = 21
    def virtualInstr        = 22
    def storeGuestPageFault = 23
    def priorities = Seq(
      breakPoint, // TODO: different BP has different priority
      instrPageFault,
      instrGuestPageFault,
      instrAccessFault,
      illegalInstr,
      virtualInstr,
      instrAddrMisaligned,
      ecallM,
      ecallS,
      ecallVS,
      ecallU,
      storeAddrMisaligned,
      loadAddrMisaligned,
      storePageFault,
      loadPageFault,
      storeGuestPageFault,
      loadGuestPageFault,
      storeAccessFault,
      loadAccessFault
    )
    def all = priorities.distinct.sorted
    def frontendSet = Seq(
      instrAddrMisaligned,
      instrAccessFault,
      illegalInstr,
      instrPageFault,
      instrGuestPageFault,
      virtualInstr
    )
    def partialSelect(vec: Vec[Bool], select: Seq[Int]): Vec[Bool] = {
      val new_vec = Wire(ExceptionVec())
      new_vec.asBools.foreach(_ := false.B)
      select.foreach(i => new_vec(i) := vec(i))
      new_vec.asBools
    }
    def selectFrontend(vec: Vec[Bool]): Vec[Bool] = partialSelect(vec, frontendSet)
    def selectAll(vec:      Vec[Bool]): Vec[Bool] = partialSelect(vec, ExceptionNO.all)
  }
}
