package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class Frontend(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val redirect = Flipped(new FrontRedirctIO)
    val out      = Vec(decodeNum, Decoupled(new InstBufferOutIO))

    val tlbSearch   = new TLBSearchIO
    val imem        = new DramReadIO
    val bpuUpdateIn = Flipped(new BpuUpdateIO)
  })

  val instFetch  = Module(new InstFetch)
  val instBuffer = Module(new InstBuffer)

  asg(instFetch.io.bpuUpdateIn, io.bpuUpdateIn)
  instFetch.io.imem <> io.imem
  asg(instFetch.io.redirect, io.redirect)
  instFetch.io.tlb <> io.tlbSearch

  instFetch.io.out <> instBuffer.io.in //not pipeline connect
  io.out <> instBuffer.io.out
  asg(instBuffer.io.flush, io.redirect.flush)
}
