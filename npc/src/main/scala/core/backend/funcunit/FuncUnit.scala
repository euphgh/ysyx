package core.backend

import chisel3._
import chisel3.util._
import utility._
import utils._
import core._
import org.chipsalliance.cde.config._

class FuBaseIO(implicit p: Parameters) extends CoreBundle {
  val in  = Flipped(Decoupled(new MicroOp))
  val out = Decoupled(new FunctionUnitOutIO)
  /* used by pipeline connect RS and Fu */
  val roOutFire = Output(Bool())
  val redirect  = Redirect.input()

  val fromPrf = Vec(srcRegNum, Input(UInt(dataWidth.W)))
}

abstract class FuncUnit(fuType: FuType.Type)(implicit p: Parameters) extends CoreModule {

  val io = IO(new FuBaseIO)

  val ros = (new ReadOpStage(fuType)).io
  ros.in <> io.in
  ros.redirect <> io.redirect
  io.roOutFire := ros.out.fire
  ros.fromPrf  := io.fromPrf

  /* could be override for pipeline Function Unit */
  val s0OutFire = io.out.fire

  val fuIn = PipelineNext(ros.out, s0OutFire, io.redirect.valid)

  def initFuOut() = {
    val ret = Decoupled(new FunctionUnitOutIO)
    ret.bits.currADest      := ros.out.bits.currADest
    ret.bits.wPrf.pDest     := ros.out.bits.currPDest
    ret.bits.wbRob.robIdx   := ros.out.bits.robIdx
    ret.bits.wbRob.excptVec := ros.out.bits.excptVec
    ret
  }

  /**
    * send only once req when fuIn.valid continue is high
    * clear when fu pipeline flush or out.fire
    * @param req decoupledIO req should be send
    */
  def decoupledValid[T <: Data](req: DecoupledIO[T]) = {
    val received = RegInit(false.B)
    req.valid := fuIn.valid && !received
    when(req.fire) { received := true.B }
    /* flush and out.fire, have higher priority */
    when(io.redirect.valid || io.out.fire) { received := false.B }
    req
  }
}
