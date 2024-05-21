package core.cache

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import core.mmu._
import org.chipsalliance.cde.config._
import core.dram.DramIO
import macros.decode.DCBundle
import macros.decode.DecodeMacro

case class ICacheParams(userData: Data = BranchType())

class ICache(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val req  = Flipped(Decoupled(new ICache.Req))
    val resp = Decoupled(new ICache.Resp)
    val ctrl = Flipped(new ICache.Ctrl)
    val tlb  = new TlbRequestIO
    val mem  = new DramIO
  })
}

object ICache {
  class Req(implicit p: Parameters) extends CoreBundle {
    val vaddr = UInt(VAddrBits.W)
  }

  @DecodeMacro
  class UserData(implicit p: Parameters) extends DCBundle {
    val brType = BranchType()
  }
  class Resp(implicit p: Parameters) extends CoreBundle {
    val toUser = Output(Vec(fetchNum, new UserData))
    val data   = Output(Vec(fetchNum, UWord()))
  }
  class Ctrl(implicit p: Parameters) extends CoreBundle {
    val stageFire = Input(Bool())
  }
}
