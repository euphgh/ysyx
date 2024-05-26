package utils
import chisel3._
abstract class UIntLen {
  val len     = 8
  def apply() = UInt(len.W)
  def toVec(in: UInt) = {
    val width = in.getWidth
    require(width % len == 0)
    require(width > len)
    VecInit.tabulate(width / len)(i => in((i + 1) * len - 1, i * len))
  }
  def merge(cond: Seq[Bool], con: Seq[UInt], alt: Seq[UInt]): Vec[UInt] = {
    // cond bit num equal to vec length
    require(cond.length == con.length)
    require(alt.length == con.length)
    // element is byte
    require(con.head.getWidth == len)
    require(alt.head.getWidth == len)
    VecInit.tabulate(cond.length)(i => Mux(cond(i), con(i), alt(i)))
  }

  def merge(cond: UInt, con: Seq[UInt], alt: Seq[UInt]): Vec[UInt] = {
    merge(cond.asBools, con, alt)
  }
  def merge(cond: UInt, con: UInt, alt: UInt): Vec[UInt] = {
    merge(cond.asBools, toVec(con), toVec(alt))
  }
  def merge(cond: Seq[Bool], con: UInt, alt: UInt): Vec[UInt] = {
    merge(cond, toVec(con), toVec(alt))
  }
}

object UInt8 extends UIntLen {
  override val len = 8
}
object UInt16 extends UIntLen {
  override val len = 16
}
object UInt32 extends UIntLen {
  override val len = 32
}
object UInt64 extends UIntLen {
  override val len = 64
}

object BytesWordUtils {
  def maskWord(bytes: Vec[UInt], mask: UInt): Vec[UInt] = {
    require(bytes.length == mask.getWidth)
    VecInit.tabulate(bytes.length)(i => Mux(mask(i), bytes(i), "h00".U))
  }

  def mergeWords(oldWord: UInt, newWord: UInt, oldMask: UInt): UInt = {
    maskWord(UInt8.toVec(oldWord), oldMask).asUInt |
      maskWord(UInt8.toVec(newWord), ~oldMask).asUInt
  }
  def mergeWords(old: Vec[UInt], now: Vec[UInt], oldMask: UInt): Vec[UInt] = {
    UInt8.toVec(maskWord(old, oldMask).asUInt | maskWord(now, ~oldMask).asUInt)
  }
}
