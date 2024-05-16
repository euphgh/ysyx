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
    val redirect = FrontRedirct.input()
    val out      = Vec(renameNum, Decoupled(new InstBufferOutIO))

    val ptw         = new TlbPtwIO()
    val imem        = new DramReadIO()
    val bpuUpdateIn = Flipped(new BpuUpdateIO)
  })

  val instFetch  = Module(new InstFetch)
  val instBuffer = Module(new InstBuffer)

  instFetch.io.bpuUpdateIn := io.bpuUpdateIn
  instFetch.io.imem <> io.imem
  instFetch.io.redirect := io.redirect
  instFetch.io.ptw <> io.ptw

  instFetch.io.out <> instBuffer.io.in //not pipeline connect
  io.out <> instBuffer.io.out
  instBuffer.io.flush := io.redirect.valid
}
