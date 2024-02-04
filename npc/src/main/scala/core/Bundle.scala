package core

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

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
  val target  = UInt(vaddrWidth.W)
  val taken   = Bool()
}

class BasicInstInfoBundle(implicit p: Parameters) extends CoreBundle {
  val instr = Output(UInt(instrWidth.W))
  val pcVal = Output(UInt(vaddrWidth.W))
}

/**
  * aluType ->  [Rs][RO][Exe]  mAlu sAlu
  * memType ->  [Rs][Ro][Mem1][Mem2] lsu
  * mduType ->  [Rs][Ro][Exe]mdu
  * specialType -> ROB
  */
// @MacroDecode
// class DecodeInstInfoBundle(implicit p: Parameters) extends CoreBundle {
//   val specialType   = SpecialType.NON //带有Non，ROB里啥都有
//   val aluType       = AluType.NON //带有Non，因为mAlu里不止走aluInst
//   val memType       = MemType.NON //不带Non
//   val mduType       = MduType.NON //不带Non
//   val decodeExcType = DeExType.NON //靠解码就可以得到的例外，需要NON
// }

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
  val debugPC      = if (debug.EnableDebug) Some(UWord) else None
}

/*==================== 流水级OUT接口，不带valid-rdy ====================*/

class PreIfOutIO(implicit p: Parameters) extends CoreBundle {
  val npc         = Output(UInt(vaddrWidth.W))
  val isDelaySlot = Output(Bool()) // tell stage1 alignMask should be b0001
  val flush       = Output(Bool())
}

//should be fast, because in one cycle
class IfStage1ToPreIf(implicit p: Parameters) extends CoreBundle {
  val pcVal      = Output(UInt(vaddrWidth.W))
  val predictRes = Output(Valid(UWord))
}

class FrontRedirctIO(implicit p: Parameters) extends CoreBundle {
  val target = Output(UInt(vaddrWidth.W))
  val flush  = Output(Bool())
}

//can be slow, register will stage them
class IfStage1OutIO(implicit p: Parameters) extends CoreBundle {
  val alMask = Output(UInt(fetchNum.W))
  // not order waiting
  val bpuOut         = Vec(fetchNum, Output(new PredictResultBundle))
  val bCacheHit      = Input(Vec(fetchNum, Bool()))
  val pcVal          = Output(UInt(vaddrWidth.W))
  val tagOfInstGroup = Output(UInt(tagWidth.W))
  val isUncached     = Output(Bool())
  val exception      = Output(FrontExcCode())
  // val iCache         = new CacheStage1OutIO(IcachRoads, IcachLineBytes / 4, false)
  val bCacheDst = Output(Valid(UWord))
}

class IfStage2OutIO(implicit p: Parameters) extends CoreBundle {
  val isBd          = Vec(fetchNum, Bool())
  val isFirPreTake  = Vec(fetchNum, Bool())
  val predictResult = Vec(fetchNum, new PredictResultBundle)
  val realBrType    = Vec(fetchNum, BranchType())
  val basicInstInfo = Vec(fetchNum, new BasicInstInfoBundle)
  val validMask     = Vec(fetchNum, Bool())
  val exception     = FrontExcCode()
  val dsDstRedir    = new FrontRedirctIO
  val isDSredir     = Bool()
}

class InstARegsIdxBundle(implicit p: Parameters) extends CoreBundle {
  val (src0, src1, dest) = (ARegIdx, ARegIdx, ARegIdx)
}
class InstBufferEntry(implicit p: Parameters) extends CoreBundle {
  val predictResult = new PredictResultBundle
  val realBrType    = BranchType()
  val basicInstInfo = new BasicInstInfoBundle
  val exception     = FrontExcCode()
  val isBd          = Output(Bool())
  val isFirPreTake  = Output(Bool())
}
class InstBufferOutIO(implicit p: Parameters) extends InstBufferEntry {
  val whichFu  = ChiselFuType()
  val aRegsIdx = new InstARegsIdxBundle
}

class SRATEntry(implicit p: Parameters) extends CoreBundle {
  val pIdx  = Output(PRegIdx)
  val inPrf = Output(Bool())
}

//rsBasicEntry < rsOutIO(each rs may has extra)
class RsBasicEntry(implicit p: Parameters) extends CoreBundle {
  val exDetect     = new DetectExInfoBundle
  val destAregAddr = Output(ARegIdx)
  val destPregAddr = Output(UInt(pRegAddrWidth.W))

  val robIndex = Output(ROBIdx)
  // val debugPC  = if (debug) Some(UWord) else None

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
class RsOutIO(kind: FuType.t)(implicit p: Parameters) extends CoreBundle {
  val basic = new RsBasicEntry

  val uOp = new Bundle {
    val brType  = if (kind == FuType.MainAlu) Some(Output(BranchType())) else None
    val aluType = if (kind == FuType.MainAlu || kind == FuType.SubAlu) Some(Output(AluType())) else None
    val memType = if (kind == FuType.Lsu) Some(Output(MemType())) else None
    val mduType = if (kind == FuType.Mdu) Some(Output(MduType())) else None
  }

  val c0Addr    = if (kind == FuType.Mdu) Some(Output(CP0Idx)) else None
  val immOffset = if (kind == FuType.Lsu || kind == FuType.SubAlu) Some(Output(UInt(immWidth.W))) else None
  val cacheOp   = if (kind == FuType.Lsu) Some(Output(CacheOp())) else None
  val pcVal     = if (kind == FuType.Lsu) Some(Output(UWord)) else None
  val mAluExtra =
    if (kind == FuType.MainAlu) Some(new Bundle {
      val pcVal         = Output(UWord) //用于updateBpu.pc 以及计算 dsPc
      val low26         = Output(UInt(26.W)) //携带有immoffset
      val predictResult = new PredictResultBundle
    })
    else None
}
class RsRealOutIO(kind: FuType.t)(implicit p: Parameters) extends CoreBundle {
  val origin    = new RsOutIO(kind: FuType.t)
  val inPrf     = Output(Vec(srcDataNum, Bool()))
  val mayNeedBp = Output(Vec(srcDataNum, Bool()))
}

class RobSavedUop(implicit p: Parameters) extends CoreBundle {
  val prevPDest   = PRegIdx // free when retire
  val currPDest   = PRegIdx // updata A-RAT when retire
  val currADest   = ARegIdx // updata A-RAT when retire
  val specialType = SpecialType()
  val isSingle    = Bool()
}
class DispatchToRobBundle(implicit p: Parameters) extends CoreBundle {
  val basicExInfo  = new BasicExInfoBundle //PC ALSO use as difftest check execution flow
  val uOp          = new RobSavedUop
  val isNoBrMis    = Output(Bool())
  val isFirPreTake = Output(Bool())
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
  //val prevPDest    = Output(PRegIdx)
  val prevData = Output(UWord)
  // val debugPC  = if (debug) Some(Output(UWord)) else None

  val uOp = new Bundle {
    val brType  = if (kind == FuType.MainAlu) Some(Output(BranchType())) else None
    val aluType = if (kind == FuType.MainAlu || kind == FuType.SubAlu) Some(Output(AluType())) else None
    val memType = if (kind == FuType.Lsu) Some(Output(MemType())) else None
    val mduType = if (kind == FuType.Mdu) Some(Output(MduType())) else None
  }
  val srcData = Vec(2, Output(UInt(dataWidth.W)))

  val branch =
    if (kind == FuType.MainAlu) Some(new Bundle {
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