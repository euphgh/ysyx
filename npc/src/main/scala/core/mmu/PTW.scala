package core.mmu

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class PTWIO(implicit p: Parameters) extends CoreBundle {
  val tlbs = Vec(ptwReqNum, new TlbPtwIO())

  def loadPtwReq  = tlbs.take(loadPipeNum)
  def storePtwReq = tlbs.drop(loadPipeNum).take(storePipeNum)
  def frontPtwReq = tlbs.drop(loadPipeNum + storePipeNum)
}

class PTW(implicit p: Parameters) extends CoreModule {
  val io = IO(new PTWIO())
}
