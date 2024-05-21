package utils
import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util._

object PriorityVec {
  def apply(inputs: Vec[UInt]): UInt = {
    val or    = inputs.foldRight(0.U)(_ | _)
    val inPri = inputs.map(PriorityEncoder(_))
    val orPri = PriorityEncoder(or)
    VecInit((0 until inputs.size).map(inPri(_) === orPri)).asUInt
  }
}

object PriorityMask {
  def apply(input: UInt): UInt = {
    val n = input.getWidth
    val pair = (0 until n).map(i => {
      val left  = "b" + "?" * (n - i - 1) + "1" + "0" * i
      val right = "b" + "1" * (n - i) + "0" * i
      BitPat(left) -> BitPat(right)
    })
    decoder(QMCMinimizer, input, TruthTable(pair, BitPat("b" + "0" * n)))
  }
}

/**
  * 计算从低位到高位连续1的个数
  * 输入必须保证如果有1只能从0位开始，且必须连续
  */
object PriorityCount {
  def apply(input: UInt): UInt = {
    val n        = input.getWidth
    val outWidth = log2Ceil(n) + 1
    val pair = (0 to n).map(i => {
      val left  = "b" + "0" * (n - i) + "1" * i
      val right = i.U(outWidth.W)
      BitPat(left) -> BitPat(right)
    })
    decoder(QMCMinimizer, input, TruthTable(pair, BitPat("b" + "?" * outWidth)))
  }
  def apply(input: Vec[Bool]): UInt = {
    apply(input.asUInt)
  }
  // return trun when is consecutive 1 else false
  def consecutive(input: UInt): Bool = {
    val n = input.getWidth
    val pair = (0 to n).map(i => {
      val left  = "b" + "0" * (n - i) + "1" * i
      val right = true.B
      BitPat(left) -> BitPat(right)
    })
    decoder(QMCMinimizer, input, TruthTable(pair, BitPat(false.B))).asBool
  }
  def consecutive(input: Vec[Bool]): Bool = {
    consecutive(input.asUInt)
  }
}

object SecondPriEncoder {
  def apply(input: UInt): UInt = {
    val pri = PriorityEncoderOH(input)
    PriorityEncoderOH((~pri) & input)
  }
}

object CountMask {

  /** Count input 1 number and generate a UInt has consecutive 1 from bit 0
    * @example {{{
    * CountMask("b000") = b000
    * CountMask("b001") = b001
    * CountMask("b101") = b011
    * CountMask("b110") = b011
    * CountMask("b111") = b111
    * }}}
    */
  def apply(input: UInt): UInt = {
    val n      = input.getWidth
    val bitStr = (0 until math.pow(2, n).toInt).map(Integer.toBinaryString(_))
    val bitPats = bitStr
      .map(str => {
        val onesCount = str.count(_ == '1')
        (
          BitPat("b" + str.reverse.padTo(n, '0').reverse),
          BitPat("b" + "0" * (n - onesCount) + "1" * (onesCount))
        )
      })
      .toList
    decoder(QMCMinimizer, input, TruthTable(bitPats, BitPat("b" + "?" * n)))
  }

  /** generate oneHot version not Priority
    * @example {{{
    * CountMask("b000") = b000
    * CountMask("b001") = b001
    * CountMask("b101") = b010
    * CountMask("b110") = b010
    * CountMask("b111") = b100
    * }}}
    */
  def oneHot(input: UInt): UInt = {
    val n      = input.getWidth
    val bitStr = (0 until math.pow(2, n).toInt).map(Integer.toBinaryString(_))
    val bitPats = bitStr
      .map(str => {
        val onesCount = str.count(_ == '1')
        val low       = if (onesCount > 0) ("1" + "0 " * (onesCount - 1)) else ""
        (
          BitPat("b" + str.reverse.padTo(n, '0').reverse),
          BitPat("b" + "0" * (n - onesCount) + low)
        )
      })
      .toList
    decoder(QMCMinimizer, input, TruthTable(bitPats, BitPat("b" + "?" * n)))
  }
}

object MultiPriority {

  /**
    * select first $num one, return
    *
    * @param num
    * @param data
    * @return Seq($num, UInt(data.length.W)) saying the first $num's positio
    *         if not exist, will return 0.U
    */
  def oneHots(num: Int, data: Seq[Bool]) = {
    Seq(VecInit(false.B, true.B), VecInit(false.B, true.B))
  }

  def oneHots(num: Int, data: UInt) = {
    Seq(VecInit(false.B, true.B), VecInit(false.B, true.B))
  }
}

object OneHotMatrix {
  def rowView(arr: Seq[Seq[Bool]]): Vec[UInt] = {
    VecInit(arr.map(VecInit(_).asUInt))
  }
  def colView(arr: Seq[Seq[Bool]]): Vec[UInt] = {
    VecInit(arr.head.indices.map { index =>
      VecInit(arr.map(_(index))).asUInt
    })
  }
}
