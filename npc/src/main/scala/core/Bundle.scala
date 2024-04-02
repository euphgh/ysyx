package core

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core.cache._
import macros.decode._

/*==================== BASIC BUNDLE ====================*/
class BasicExInfoBundle(implicit p: Parameters) extends CoreBundle {
  val pc   = Output(UWord)
  val isBd = Output(Bool())
}
class DetectExInfoBundle(implicit p: Parameters) extends CoreBundle {
  val happen  = Output(Bool())
  val excCode = Output(ExcCode())
  val refill  = Output(Bool())
}
class ExCommitBundle(implicit p: Parameters) extends CoreBundle {
  val basic    = new BasicExInfoBundle
  val detect   = new DetectExInfoBundle
  val badVaddr = Output(UWord)
}

//bpu info for per inst
class PredictResultBundle(implicit p: Parameters) extends CoreBundle {
  val counter = UInt(2.W)
  val btbType = BtbType()
  val target  = UInt(VAddrBits.W)
  val taken   = Bool()
}

class BasicInstInfoBundle(implicit p: Parameters) extends CoreBundle {
  val instr = Output(UInt(instrWidth.W))
  val pcVal = Output(UInt(VAddrBits.W))
}

/**
  * aluType ->  [Rs][RO][Exe]  mAlu sAlu
  * memType ->  [Rs][Ro][Mem1][Mem2] lsu
  * mduType ->  [Rs][Ro][Exe]mdu
  * specialType -> ROB
  */
@DecodeMacro
class DecodeInstInfoBundle extends DCBundle {
  val aluType = AluType()
  val memType = MemType()
  val mduType = MduType()
}

//no need a wen,pDest===0 means !wen
class WPrfBundle(implicit p: Parameters) extends CoreBundle {
  val pDest  = PRegIdx
  val result = UWord
  val wmask  = UInt(4.W)
}

class WbRobBundle(implicit p: Parameters) extends CoreBundle {
  val robIndex     = Output(UInt(robIndexWidth.W))
  val exDetect     = new DetectExInfoBundle
  val isMispredict = Output(Bool())
  val debugPC      = if (EnableHardDebug) Some(UWord) else None
}

/*==================== 流水级OUT接口，不带valid-rdy ====================*/
class InstARegsIdxBundle(implicit p: Parameters) extends CoreBundle {
  val srcs = Vec(srcDataNum, ARegIdx)
  val dest = ARegIdx
}
class InstBufferEntry(implicit p: Parameters) extends CoreBundle {
  val predictResult = new PredictResultBundle
  val realBrType    = BranchType()
  val basicInstInfo = new BasicInstInfoBundle
  val exception     = FrontExcCode()
}
class InstBufferOutIO(implicit p: Parameters) extends InstBufferEntry {
  val whichFu  = HFuType()
  val aRegsIdx = new InstARegsIdxBundle
}

class SrcRegMeta(implicit p: Parameters) extends CoreBundle {
  val pIdx  = Output(PRegIdx)
  val inPrf = Output(Bool())
}

//rsBasicEntry < rsOutIO(each rs may has extra)
class RsBasicEntry(implicit p: Parameters) extends CoreBundle {
  val exDetect     = new DetectExInfoBundle
  val destAregAddr = Output(ARegIdx)
  val destPregAddr = Output(UInt(pRegAddrWidth.W))

  val robIndex = Output(ROBIdx)
  val debugPC  = if (EnableHardDebug) Some(UWord) else None

  val pSrcs     = Vec(srcDataNum, Output(PRegIdx))
  val prevPDest = Output(PRegIdx)
  val sratInPrf = Vec(srcDataNum, Output(Bool()))
  val wbInPrf   = Vec(srcDataNum, Output(Bool()))
  val grpInPrf  = Vec(srcDataNum, Output(Bool()))

  val wbInfo = Vec(wBNum, Output(PRegIdx))
}

/**
  * Lsu/sAlu extra:imm
  *   sAlu:may select as src2
  *   ldst:count addr(take to mem1)
  *
  * mAlu extra:
  *   branch:dsPC,low26,predictRes
  *     predictRes take to EXE
  *     dsPc and low26 count a target,take to EXE
  *     for "AL" branch,we notice that it don't need src2
  *       so we can take the link addr in src2
  *   notice that for I-inst,it should take low16 bit of low26 as its src2
  */
class MicroOp(implicit p: Parameters) extends CtrlFlow {
  val currPDest = Output(PRegIdx)
  val prevPDest = Output(PRegIdx)

  val robIndex = Output(ROBIdx)
  // val debugPC  = if (debug) Some(UWord) else None

  val pSrcs = Vec(srcDataNum, new SrcRegMeta)
}

class RsRealOutIO(kind: FuType.t)(implicit p: Parameters) extends CoreBundle {
  // val origin    = new RsOutIO
  val inPrf     = Output(Vec(srcDataNum, Bool()))
  val mayNeedBp = Output(Vec(srcDataNum, Bool()))
}

class RobSavedUop(implicit p: Parameters) extends CoreBundle {
  val prevPDest = PRegIdx // free when retire
  val currPDest = PRegIdx // updata A-RAT when retire
  val currADest = ARegIdx // updata A-RAT when retire
  val isSingle  = Bool()
}

class DestRegMeta(implicit p: Parameters) extends CoreBundle {
  val prevPDest = PRegIdx // free when retire
  val currPDest = PRegIdx // updata A-RAT when retire
  val currADest = ARegIdx // updata A-RAT when retire
}

class DispatchToRobIO(implicit p: Parameters) extends CoreBundle {
  val destRegMeta = new DestRegMeta
}

/**
  * to rob
  *  robIndex
  *  exception
  *  isMispredict
  *  memReqVaddr
  * to prf
  *   dest
  *   wen
  *   data(alu/mdu:cal load:load store:DontCare)
  *   wmask:only load care
  * to srat
  *   destAregAddr
  *   destPregAddr
  */
class FunctionUnitOutIO(implicit p: Parameters) extends CoreBundle {
  val wbRob        = new WbRobBundle
  val wPrf         = new WPrfBundle
  val destAregAddr = Output(ARegIdx)
}

/**
  * exception for wbRob
  * robIndex for wbRob
  * destPregAddr is for Wprf/srat
  * destAregAddr is for srat
  *
  * srcDatas:=mux(pregData,Bypass)
  *
  * the mem is for lsu
  *   index/offset:12 bit cal
  *   memType:take from decode
  *   wWord:load dont care/store read src
  *   size:gen in Rostage
  *   wStrb:load dont care/actually store dont care at this stage
  *
  * the branch is for branch in mAlu
  *
  * special:
  *   link addr->src2
  *   imm->src2
  *   sa->src1
  *   {0.U(24.W),c0Addr}->src1
  */
class ReadOpStageOutIO(kind: FuType.t)(implicit p: Parameters) extends CoreBundle {
  val robIndex     = Output(UInt(robIndexWidth.W))
  val exDetect     = new DetectExInfoBundle
  val destPregAddr = Output(UInt(pRegAddrWidth.W))
  val destAregAddr = Output(ARegIdx)
  val prevPDest    = Output(PRegIdx)
  val prevData     = Output(UWord)
  val debugPC      = if (EnableHardDebug) Some(Output(UWord)) else None

  val uOp = new Bundle {
    val brType  = if (kind == FuType.Alu) Some(Output(BranchType())) else None
    val aluType = if (kind == FuType.Alu || kind == FuType.Alu) Some(Output(AluType())) else None
    val memType = if (kind == FuType.Lsu) Some(Output(MemType())) else None
    val mduType = if (kind == FuType.Mdu) Some(Output(MduType())) else None
  }
  val srcData = Vec(2, Output(UInt(dataWidth.W)))

  val branch =
    if (kind == FuType.Alu) Some(new Bundle {
      val realTarget  = Output(UWord)
      val realBtbType = Output(BtbType())
      val predict     = new PredictResultBundle
      val pcVal       = Output(UWord) //for update bpu
    })
    else None
  val mem =
    if (kind == FuType.Lsu) Some(Output(new Bundle {
      // val cache    = Output(new CacheStage1In(true, DcachLineBytes)) //cache.rwReq.wWord is just src2?
      val dirCattr = Output(CCAttr())
      // val rLowAddr = Output(new CacheLowAddr(DcachLineBytes))
      // val mipOut   = Valid(new IndexPredictor.MIPOutIO)
      val isDir   = Output(Bool())
      val pcVal   = Output(UWord)
      val idxMiss = Output(Bool())

      val carryOut  = Output(Bool())
      val immOffset = Output(UInt(16.W))
    }))
    else None
}

//just use to instantiate exeStageIO in alu/mdu
class ExeStageIO(fuKind: FuType.t)(implicit p: Parameters) extends CoreBundle {
  val in  = Flipped(Decoupled(new ReadOpStageOutIO(kind = fuKind)))
  val out = Decoupled(new FunctionUnitOutIO)
}
