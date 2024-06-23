package top
import circt.stage._
import chisel3.stage.ChiselGeneratorAnnotation

object Generator {
  def execute(args: Array[String], mod: => chisel3.RawModule, firtoolOpts: Array[String]) = {
    val annotations = Seq(CIRCTTargetAnnotation(CIRCTTarget.Verilog)) ++ firtoolOpts.map(FirtoolOption.apply)
    (new ChiselStage).execute(args, ChiselGeneratorAnnotation(mod _) +: annotations)
  }
}
