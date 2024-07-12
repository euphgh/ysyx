package top

import circt.stage._
import chisel3.stage.ChiselGeneratorAnnotation

object Generator {
  def apply(
    args: Array[String],
    mod: => chisel3.RawModule,
    // XiangShan project default options
    firtoolOpts: Array[String] = Array(
      "-O=release",
      // Ignore unknown annotations when parsing
      "--disable-annotation-unknown",
      // control verilog code style, not use sv
      "--lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing",
    ),
  ) = {
    val annotations = Seq(CIRCTTargetAnnotation(CIRCTTarget.Verilog)) ++ firtoolOpts.map(FirtoolOption.apply)
    (new ChiselStage).execute(args, ChiselGeneratorAnnotation(mod _) +: annotations)
  }
}
