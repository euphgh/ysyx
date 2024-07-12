package top

import chisel3.stage._

object Generator {
  def apply(
    args: Array[String],
    mod: => chisel3.RawModule,
    // chisel3 version not use firtoolOpts
    firtoolOpts: Array[String] = Array(),
  ) = {
    (new ChiselStage).execute(args, Seq(ChiselGeneratorAnnotation(mod _)))
  }
}
