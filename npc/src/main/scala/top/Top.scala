package top

import core._
import org.chipsalliance.cde.config._

object TopMain extends App {
  val (config, firrtlOpts, firtoolOpts) = ArgParser.parse(args)

  def top = new Core()(config)
  import firrtl.FirrtlProtos.Firrtl.Module.ExternalModule.Parameter
  val generator = Seq(chisel3.stage.ChiselGeneratorAnnotation(() => top))

  (new MyStage()).execute(Array("-td", "build"), generator)
}
