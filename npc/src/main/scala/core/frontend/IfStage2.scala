package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core.cache._
import core.dram._
import macros.decode._

class BtbWIO(implicit p: Parameters) extends CoreBundle {
  val valid    = Vec(4, Bool())
  val tagIdx   = UInt((32 - log2Ceil(IcachLineBytes)).W)
  val instrOff = Vec(4, UInt(instrOffWidth.W))
  val brType   = Vec(fetchNum, BranchType())
  val instr    = Vec(fetchNum, UWord)
}

/**
  * out.predictResult := stage1.out.bpuOut(bpuOut.takenMask become a "taken" bit)
  *
  * out.basicInstInfo := Instructs from I-Cache
  *
  * out.validNum := func(alignMask, bpuOut.takenMask)
  *
  * out.exception := tlb.res
  *
  * pass abort signal to cacheStage2 in this stage
  * if tlb is miss or pc is not aligned
  *
  * pay attention
  * 1. cache must keep data until instBuffer has space
  * 2. ready can not be set from in.iCache.cacheInst.valid to cache redirect
  *
  * when miss,use DramReadIO to connect Dram
  * this connect to IfStage1 can use pipeline connect
  * so all io.in is from regs
  */
class IfStage2(implicit p: Parameters) extends CoreModule with BCacheHelp {
  val io = IO(new Bundle {
    val in        = Flipped(Decoupled(new IfStage1OutIO))
    val out       = Decoupled(new IfStage2OutIO)
    val imem      = new DramReadIO
    val btbDeq    = Decoupled(new BtbWIO)
    val dsGoIf2   = Input(Bool())
    val backFlush = Input(Bool())
    // must in this stage, becasue it use first valid btbType
    val rasPush = Valid(UWord)
    val rasPop  = Output(Bool())
    val bCacheW = Valid(new BCacheWIO)
  })

  // alias ==================================
  val inBits      = io.in.bits
  val outBits     = io.out.bits
  val alignMask   = inBits.alMask
  val pc          = inBits.pcVal
  val bpuout      = VecInit.tabulate(fetchNum)(inBits.bpuOut(_))
  val bCMask      = VecInit.tabulate(fetchNum)(inBits.bCacheHit(_))
  val takeMask    = VecInit.tabulate(fetchNum)(i => inBits.bpuOut(i).taken)
  val validBranch = VecInit.tabulate(fetchNum)(i => takeMask(i) && inBits.alMask(i))
  def getByVB[T <: Data](x: Seq[T]) = ParallelPriorityMux(validBranch.zip(x))

  // BPU ==================================
  val predDst       = getByVB(bpuout.map(_.target))
  val hasBranch     = ParallelOR(validBranch) // make sure Priority can not be zero
  val dsFetch       = !getByVB((1 until fetchNum).map(alignMask(_)) :+ false.B) && hasBranch
  val beforeBr      = getByVB(Seq("b0001".U(4.W), "b0011".U(4.W), "b0111".U(4.W), "b1111".U(4.W)))
  val bCacheHit     = getByVB(bCMask)
  val firstPredTake = VecInit(PriorityEncoderOH(validBranch))
  // Update RAS ==================================
  val firValidBtbType = getByVB(bpuout.map(_.btbType))
  io.rasPush.valid := firValidBtbType === BtbType.jcall && io.out.fire && hasBranch
  io.rasPop        := firValidBtbType === BtbType.jret && io.out.fire && hasBranch
  io.rasPush.bits  := getByVB((0 until fetchNum).map(i => Cat((pc(XLEN - 1, 2) + i.U + 2.U), pc(1, 0))))

  def getIntrBrType(instr: UInt): BranchType.Type = {
    @DecodeMacro
    class IF2PreDecodeOut extends DCBundle {
      val brType = BranchType.NON
    }
    import chisel3.util.experimental.decode.QMCMinimizer
    require(instr.getWidth == 32)
    RV64I.decode(instr, new IF2PreDecodeOut).brType
  }

  val inValidMask = Mux(hasBranch, beforeBr & alignMask, alignMask)
  val icache2     = Module(new CacheStage2(IcachRoads, IcachLineBytes, false, BranchType())(getIntrBrType))
  icache2.io.in.valid           := io.in.valid
  io.in.ready                   := icache2.io.in.ready
  icache2.io.in.bits.fromStage1 := io.in.bits.iCache
  icache2.io.in.bits.cancel     := io.in.bits.exception =/= FrontExcCode.NONE
  icache2.io.in.bits.isUncached := io.in.bits.isUncached
  icache2.io.in.bits.ptag       := io.in.bits.tagOfInstGroup
  icache2.io.in.bits.imask.get  := VecInit(inValidMask.asBools)
  val iCacheInst = Wire(Flipped(Valid(new ICacheInstIO)))
  iCacheInst.valid := false.B
  iCacheInst.bits  := DontCare
  io.imem.ar <> icache2.dram.ar
  io.imem.r <> icache2.dram.r
  icache2.dram.aw <> DontCare
  icache2.dram.w <> DontCare
  icache2.dram.b <> DontCare
  (0 until fetchNum).foreach(i => {
    val outBasic = outBits.basicInstInfo(i)
    val inPcVal  = inBits.pcVal
    outBasic.pcVal       := Cat(inPcVal(XLEN - 1, instrOffMsb + 1), inPcVal(instrOffMsb, instrOffLsb) + i.U, inPcVal(1, 0))
    outBasic.instr       := icache2.io.out.bits.idata.get(i)
    outBits.validMask(i) := inValidMask(i)
  })
  outBits.exception    := inBits.exception
  io.out.valid         := icache2.io.out.valid
  icache2.io.out.ready := io.out.ready || !io.in.valid

  //predecode
  (0 until fetchNum).foreach(i => {
    val instr = io.out.bits.basicInstInfo(i).instr

    val realBrType = icache2.io.out.bits.toUser(i)
    when(icache2.io.out.valid) {
      assert(realBrType === getIntrBrType(instr))
    }
    outBits.realBrType(i) := realBrType
  })
  outBits.isFirPreTake := firstPredTake
  if (verilator) {
    //TODO: fix Difftest
    // val frontPreDiff = Module(new DifftestFrontPred)
    // frontPreDiff.io.clock := clock
    // asg(frontPreDiff.io.debugPC, VecInit(io.out.bits.basicInstInfo.map(_.pcVal)))
    // asg(frontPreDiff.io.predType, VecInit(bpuout.map(_.btbType.asUInt)))
    // asg(frontPreDiff.io.realType, VecInit(io.out.bits.realBrType.map(_.asUInt)))
    // asg(frontPreDiff.io.en, io.out.fire)
  }

  val bpuWQ = Module(new Queue(gen = new BtbWIO, entries = 2, hasFlush = false))
  bpuWQ.io.enq.valid         := io.out.fire
  bpuWQ.io.enq.bits.tagIdx   := outBits.basicInstInfo(0).pcVal(XLEN - 1, instrOffMsb + 1)
  bpuWQ.io.enq.bits.instrOff := VecInit(outBits.basicInstInfo.map(_.pcVal(instrOffMsb, instrOffLsb)))
  bpuWQ.io.enq.bits.brType   := outBits.realBrType
  bpuWQ.io.enq.bits.valid    := outBits.validMask
  bpuWQ.io.enq.bits.instr    := VecInit(outBits.basicInstInfo.map(_.instr))
  io.btbDeq <> bpuWQ.io.deq

  val savedPreDst = Reg(UWord)
  val redirSet    = io.out.bits.redirect.flush
  val redirDst    = io.out.bits.redirect.target
  val alignPC     = getAlignPC(inBits.pcVal)
  redirSet            := false.B
  redirDst            := DontCare
  io.bCacheW.valid    := false.B
  io.bCacheW.bits.pc  := DontCare
  io.bCacheW.bits.dst := DontCare
  when(hasBranch && io.in.valid) {
    when(!bCacheHit) {
      redirDst := predDst
      redirSet := true.B
      // Write BCache
      io.bCacheW.valid    := true.B
      io.bCacheW.bits.pc  := inBits.pcVal
      io.bCacheW.bits.dst := predDst
    }
  }.elsewhen(io.in.valid) {
    when(inBits.bCacheDst.valid) {
      redirDst := alignPC
      redirSet := true.B
      // Write BCache
      io.bCacheW.valid   := true.B
      io.bCacheW.bits.pc := cleanMask & inBits.pcVal
    }
  }
  if (verilator) {
    //TODO: fix Difftest
    // val diffBCache = Module(new DifftestBCache)
    // asg(diffBCache.io.clock, clock)
    // asg(diffBCache.io.en, true.B)
    // val if2FireIn = Wire(Bool())
    // //TODO: fix BoringUtils
    // // addSink(if2FireIn, "If2FireIn")
    // asg(diffBCache.io.fireIn, RegNext(if2FireIn))
    // when(diffBCache.io.fireIn) {
    //   assert(io.in.valid)
    // }
    // asg(diffBCache.io.state, predState)
    // asg(diffBCache.io.bCacheDst, inBits.bCacheDst.bits)
    // asg(diffBCache.io.predictDst, predDst)
    // asg(diffBCache.io.bCacheHit, bCacheHit)
    // asg(diffBCache.io.wDst, io.bCacheW.bits.dst)
    // asg(diffBCache.io.wPC, io.bCacheW.bits.pc)
    // asg(diffBCache.io.wen, io.bCacheW.valid)
    // asg(diffBCache.io.pcVal, inBits.pcVal)
    // asg(diffBCache.io.bCacheUse, inBits.bCacheDst.valid)
    // asg(diffBCache.io.dsFetch, dsFetch)
    // asg(diffBCache.io.hasBranch, hasBranch)
  }
}
