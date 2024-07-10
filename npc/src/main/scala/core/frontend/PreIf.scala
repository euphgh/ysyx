package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

/**
  * out.bits is calculated by alignMask, fromBpu and redirect
  * if redirect.flush = true then out.bits = redirect.target
  * else out.bits = bpu predict target
  * not need Decouple. becasue valid = 1
  * ready not change select logic
  *
  * not need valid because always valid
  * not need ready becasue input will not change when ready is 0
  * but need flush when redirect happen
  *
  * need a automat in it to save bpu result when only valid
  * branch or jump but not valid delay branch
  * set automat status to use ds pc in next cycle when first branch valid is "10"
  * set automat status to give up ds pc when delaySlotOK is set
  *
  * flush:
  *      exception/eret   ===> retire
  *      mispredict occur ===> exe
  *      delayslot/target ===> ifStage2
  */
class PreIf(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val resetVector = Input(VAddr())
    val redirect    = FrontRedirct.input()
    val fromIf1     = Flipped(new IfStage1ToPreIf)
    val out         = Decoupled(VAddr())
  })
  val PCRegister = RegInit(io.resetVector)
  val normalNext = Mux(io.fromIf1.predictRes.valid, io.fromIf1.predictRes.bits, getAlignPC(io.fromIf1.pcVal))

  when(io.out.fire) {
    PCRegister := normalNext
  }.elsewhen(io.redirect.valid) {
    PCRegister := io.redirect.bits.target
  }

  val registerValid = RegInit(true.B)
  when(io.out.fire) {
    registerValid := false.B
  }.elsewhen(io.out.valid && !io.out.ready) {
    registerValid := true.B
  }.elsewhen(io.redirect.valid) {
    registerValid := true.B
  }

  io.out.bits := Mux(registerValid, PCRegister, normalNext)

  io.out.valid := true.B
}
