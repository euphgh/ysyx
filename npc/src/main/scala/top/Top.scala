package top

import core._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import utility._

abstract class ChiselApp extends App {
  def run(module: (Parameters) => RawModule, args: Array[String] = Array()) = {
    val (config, firrtlOpts, firtoolOpts) = ArgParser.parse(args)
    Generator(firrtlOpts ++ Array("-td", "build"), module(config), firtoolOpts)
  }
}

object TopMain extends ChiselApp { run(config => new Core()(config)) }

object SRAMMain extends ChiselApp {
  run(config => new SRAMTemplate(UInt(32.W), 64, 4, singlePort = true))
}

object Foo extends ChiselApp {
  import freechips.rocketchip.diplomacy._
  run { config => LazyModule(new AdderTestHarness()(Parameters.empty)).module }
}
