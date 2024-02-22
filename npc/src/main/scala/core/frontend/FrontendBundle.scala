package core.frontend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core._
import core.cache._
import core.mmu._
import core.RedirectLevel
import utility.ParallelOR
import utility.ParallelPriorityMux

class PreIfOutIO(implicit p: Parameters) extends CoreBundle {
  val npc         = Output(UInt(VAddrBits.W))
  val isDelaySlot = Output(Bool()) // tell stage1 alignMask should be b0001
  val flush       = Output(Bool())
}

//should be fast, because in one cycle
class IfStage1ToPreIf(implicit p: Parameters) extends CoreBundle {
  val pcVal      = Output(UInt(VAddrBits.W))
  val predictRes = Output(Valid(UWord))
}

class FrontRedirctIO(implicit p: Parameters) extends CoreBundle {
  val target = Output(UInt(VAddrBits.W))
  val flush  = Output(Bool())
}

object FrontRedirctIO {
  def apply(flush: Bool, target: UInt)(implicit p: Parameters) = {
    val ret = new FrontRedirctIO
    ret.flush  := flush
    ret.target := target
    ret
  }

  // Merge by priority, first parameters has highest priority
  def merge(redirects: FrontRedirctIO*)(implicit p: Parameters) = {
    val flush  = ParallelOR(redirects.map(_.flush))
    val target = ParallelPriorityMux(redirects.map(r => (r.flush, r.target)))
    apply(flush, target)
  }
}

//can be slow, register will stage them
class IfStage1OutIO(implicit p: Parameters) extends CoreBundle {
  val alMask = Output(UInt(fetchNum.W))
  // not order waiting
  val bpuOut         = Vec(fetchNum, Output(new PredictResultBundle))
  val bCacheHit      = Input(Vec(fetchNum, Bool()))
  val pcVal          = Output(UInt(VAddrBits.W))
  val tagOfInstGroup = Output(UInt(tagWidth.W))
  val isUncached     = Output(Bool())
  val exception      = Output(FrontExcCode())
  val iCache         = new CacheStage1OutIO(IcachRoads, IcachLineBytes / 4, false)
  val bCacheDst      = Output(Valid(UWord))
  val tlbResp        = Valid(new TlbResp)
}

class IfStage2OutIO(implicit p: Parameters) extends CoreBundle {
  val isFirPreTake  = Vec(fetchNum, Bool())
  val predictResult = Vec(fetchNum, new PredictResultBundle)
  val realBrType    = Vec(fetchNum, BranchType())
  val basicInstInfo = Vec(fetchNum, new BasicInstInfoBundle)
  val validMask     = Vec(fetchNum, Bool())
  val exception     = FrontExcCode()
  val redirect      = new FrontRedirctIO
}
