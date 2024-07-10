package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core.mmu._
import core.dram._

class Frontend(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val back = new FrontBackIO()
    val imem = new DramReadIO()
  })

  val instFetch  = Module(new InstFetch)
  val instBuffer = Module(new InstBuffer)

  instFetch.io.backendBPU := io.back.bpuUpdateIn
  instFetch.io.imem <> io.imem
  instFetch.io.redirect := io.back.redirect
  instFetch.io.ptw <> io.back.ptw

  instFetch.io.out <> instBuffer.io.in //not pipeline connect
  io.back.instructions <> instBuffer.io.out
  instBuffer.io.flush := io.back.redirect.valid
}
