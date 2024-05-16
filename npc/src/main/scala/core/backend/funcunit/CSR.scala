package core.backend.funcunit

import core._
import utils._
import chisel3._
import chisel3.util._
import utility._
import core.backend._
import org.chipsalliance.cde.config._

class ExceptionRaiseIO(implicit p: Parameters) extends MicroOp {
  val isInterrupt = Bool()
}

class ExternalInterruptIO(implicit p: Parameters) extends CoreBundle {
  val mtip = Input(Bool())
  val msip = Input(Bool())
  val meip = Input(Bool())
  val seip = Input(Bool())
}

class CSRInstrRequestIO(implicit p: Parameters) extends CoreBundle {
  val req  = Decoupled(new MicroOp)
  val resp = Flipped(Decoupled(UInt(XLEN.W)))
}

class CSRFileIO(implicit p: Parameters) extends CoreBundle {
  /* from rob */
  val exception = Flipped(ValidIO(new ExceptionRaiseIO))

  /* to All CPU */
  val redirect = Redirect.output()

  /* from LSQ */
  val memExceptVAddr = Input(UInt(VAddrBits.W))

  /* from outside cpu,externalInterrupt */
  val externalInterrupt = new ExternalInterruptIO

  /* to TLB */
  val tlb = Output(new TlbCsrBundle)

  /* all instr in mduType will really execuate here, raising redirect.
   * only exceptions and interrupts will passed by Rob, then raise redirect */
  val instr = Flipped(new CSRInstrRequestIO)
}

class CSRFile(implicit p: Parameters) extends CoreModule {
  val io = IO(new CSRFileIO)
}
