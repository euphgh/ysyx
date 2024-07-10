package core.frontend

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core._
import core.cache._
import core.mmu._
import utility._
import utils._
import dram._
import bpu._
import core.ExceptionVec
import core.ExceptionVec

class PreIfOutIO(implicit p: Parameters) extends CoreBundle {
  val npc   = Output(VAddr())
  val force = Output(Bool())
}

//should be fast, because in one cycle
class IfStage1ToPreIf(implicit p: Parameters) extends CoreBundle {
  val pcVal      = Output(VAddr())
  val predictRes = Output(Valid(VAddr()))
}

//can be slow, register will stage them
class IfStage1OutIO(implicit p: Parameters) extends CoreBundle {
  val alMask = Output(UInt(fetchNum.W))
  // not order waiting
  val bpuOut         = Vec(fetchNum, Output(new PredictResultBundle))
  val bCacheHit      = Input(Vec(fetchNum, Bool()))
  val pcVal          = Output(VAddr())
  val tagOfInstGroup = Output(UInt(12.W))
  val isUncached     = Output(Bool())
  val iCache         = new CacheStage1OutIO(IcachRoads, iCacheBlkBytes / 4, false)
  val bCacheDst      = Output(Valid(VAddr()))
  val tlbResp        = Valid(new TlbResp)
}

class IfStage2OutIO(implicit p: Parameters) extends CoreBundle {
  val isFirPreTake  = Vec(fetchNum, Bool())
  val predictResult = Vec(fetchNum, new PredictResultBundle)
  val realBrType    = Vec(fetchNum, BranchType())
  val basicInstInfo = Vec(fetchNum, new BasicInstInfoBundle)
  val validMask     = Vec(fetchNum, Bool())
  val excVec        = new FrontExcVec()
  val redirect      = FrontRedirct.output()
  val bCacheHit     = Bool()
}

class FrontBackIO(implicit p: Parameters) extends CoreBundle {
  val redirect     = FrontRedirct.input()
  val instructions = Vec(renameNum, Decoupled(new InstBufferOutIO))
  val ptw          = new TlbPtwIO()
  val bpuUpdateIn  = Flipped(new BpuUpdateIO)
}
