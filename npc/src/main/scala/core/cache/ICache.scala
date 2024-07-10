package core.cache

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import core.mmu._
import org.chipsalliance.cde.config._
import core.dram._
import macros.decode._
import core.backend.mem.MemModule
import core.backend.mem.MemBundle
import core.backend.mem.ICacheBundle

class ICache(implicit p: Parameters) extends MemModule {
  val io = IO(new Bundle {
    val req  = Flipped(Decoupled(new ICache.Req))
    val resp = Decoupled(new ICache.Resp())
    // val ctrl = Flipped(new ICache.Ctrl())
    val ptw = new TlbPtwIO()
    val mem = new DramIO()
  })
}

object ICache {
  class Req(implicit p: Parameters) extends MemBundle {
    val vaddr = UInt(VAddrBits.W)
  }

  object BlockStatu extends ChiselEnum {
    val none, valid = Value
  }

  class Meta(implicit p: Parameters) extends ICacheBundle {
    val tag   = UInt(pTagWidth.W)
    val statu = BlockStatu()

    def isValid() = statu === BlockStatu.valid
    def Invalid() = statu := BlockStatu.none
  }

  @DecodeMacro
  class UserData(implicit p: Parameters) extends DCBundle {
    val brType = BranchType()
  }
  class Resp(implicit p: Parameters) extends MemBundle {
    val toUser = Output(Vec(fetchNum, new UserData))
    val data   = Output(Vec(fetchNum, UWord()))
  }
}
