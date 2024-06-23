package top
import chisel3.stage._
object Generator {
  def execute(args: Array[String], mod: => chisel3.RawModule, firtoolOpts: Array[String]) = {
    (new ChiselStage).execute(args, ChiselGeneratorAnnotation(mod _))
  }
}
