package core

import chisel3._
import org.chipsalliance.cde.config._
import macros.decode._
import core.frontend._

abstract class CoreBundle(implicit val p: Parameters) extends Bundle with HasMyParams
abstract class CoreModule(implicit val p: Parameters) extends Module with HasMyParams
class Core(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val in  = Input(UInt(32.W))
    val out = Input(UInt(32.W))
  })

  // @ MacroDecode
  class IBdecodeOut(implicit p: Parameters) extends CoreBundle {
    val srcType = SRCType()
    val dstType = DSTType()
    val whichFu = ChiselFuType()
  }

  import chisel3.util.experimental.decode.QMCMinimizer
  val subDecode = Wire(new IBdecodeOut)
  subDecode.elements

  // subDecode.decode(io.in, AllInsts(), AllInsts.default(), QMCMinimizer)

  io.out := subDecode.asUInt
}
