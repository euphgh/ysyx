package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core.cache._
import macros.decode._
import bpu._

class BtbWIO(implicit p: Parameters) extends CoreBundle {
  val valid    = Vec(fetchNum, Bool())
  val tagIdx   = UInt((VAddrBits - iCacheBlkPt).W)
  val instrOff = Vec(fetchNum, UInt((iCacheBlkPt - instrPt).W))
  val brType   = Vec(fetchNum, BranchType())
  val instr    = Vec(fetchNum, UInt32())
  def fromIf2Out(out: IfStage2OutIO) = {
    tagIdx   := out.basicInstInfo(0).pcVal(VAddrBits - 1, iCacheBlkPt)
    instrOff := VecInit(out.basicInstInfo.map(_.pcVal(iCacheBlkPt - 1, instrPt)))
    brType   := out.realBrType
    valid    := out.validMask
    instr    := VecInit(out.basicInstInfo.map(_.instr))
  }
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
    val backFlush = Input(Bool())

    val icache = new Bundle {
      val resp   = Flipped(Decoupled(new ICache.Resp))
      val excVec = new FrontExcVec()
    }

    // must in this stage, becasue it use first valid btbType
    val writeBack = new Bundle {
      val bCache = Valid(new BCacheWIO())
      val ras = Valid(new RetAddrStack.WriteBackIO())
      val btb = Decoupled(new BtbWIO())
    }
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
  // select by first valid branch
  def getByVB[T <: Data](x: Seq[T]) = ParallelPriorityMux(validBranch.zip(x))

  // BPU ==================================
  val predDst       = getByVB(bpuout.map(_.target))
  val hasBranch     = ParallelOR(validBranch) // make sure Priority can not be zero
  val beforeBr      = getByVB(Seq("b0001".U(4.W), "b0011".U(4.W), "b0111".U(4.W), "b1111".U(4.W)))
  val bCacheHit     = getByVB(bCMask)
  val firstPredTake = VecInit(PriorityEncoderOH(validBranch))
  // Update RAS ==================================
  val firValidBtbType = getByVB(bpuout.map(_.btbType))
  io.writeBack.ras.valid := BtbType.isRAS(firValidBtbType) && io.out.fire && hasBranch
  io.writeBack.ras.bits.pushDst := getByVB((1 to fetchNum).map(i => pc + (i << instrPt).U))
  io.writeBack.ras.bits.btbType := firValidBtbType

  def getIntrBrType(instr: UInt): ICache.UserData = {
    require(instr.getWidth == VAddrBits)
    import chisel3.util.experimental.decode.QMCMinimizer
    RV64I.decode(instr, new ICache.UserData)
  }

  // ===========================================================
  // ================ io.out assign logic ======================
  // ===========================================================
  val inValidMask = Mux(hasBranch, beforeBr & alignMask, alignMask)
  (0 until fetchNum).foreach { i =>
    val outBasic = outBits.basicInstInfo(i)
    val inPcVal  = inBits.pcVal
    outBasic.pcVal := Cat(
      inPcVal(VAddrBits - 1, iCacheBlkPt),
      inPcVal(iCacheBlkPt - 1, instrPt) + i.U,
      inPcVal(instrPt - 1, 0)
    )
    outBasic.instr       := io.icache.resp.bits.data
    outBits.validMask(i) := inValidMask(i)
  }
  outBits.excVec       := io.icache.excVec
  outBits.bCacheHit    := bCacheHit
  io.out.valid         := io.icache.resp.valid
  io.icache.resp.ready := io.out.ready || !io.in.valid

  //predecode
  (0 until fetchNum).foreach { i =>
    val instr = io.out.bits.basicInstInfo(i).instr

    val realBrType = io.icache.resp.bits.toUser(i)
    when(io.icache.resp.valid) {
      assert(realBrType === getIntrBrType(instr))
    }
    outBits.realBrType(i) := realBrType
  }
  outBits.isFirPreTake := firstPredTake
  if (verilator) {
    //TODO: fix Difftest
    // val frontPreDiff = Module(new DifftestFrontPred)
    // frontPreDiff.io.clock := clock
    // asg(frontPreDiff.io.debugHW, VecInit(io.out.bits.basicInstInfo.map(_.pcVal)))
    // asg(frontPreDiff.io.predType, VecInit(bpuout.map(_.btbType.asUInt)))
    // asg(frontPreDiff.io.realType, VecInit(io.out.bits.realBrType.map(_.asUInt)))
    // asg(frontPreDiff.io.en, io.out.fire)
  }

  io.writeBack.btb <> {
    val buffer = Module(new Queue(gen = new BtbWIO, entries = 2, hasFlush = false))
    buffer.io.enq.valid := io.out.fire
    buffer.io.enq.bits.fromIf2Out(outBits)
    buffer.io.deq
  }

  // ===========================================================
  // =========== redirect and BCache recover logic =============
  // ===========================================================
  // redirect assign init
  io.out.bits.redirect       := DontCare
  io.out.bits.redirect.valid := false.B
  def setRedirect(pc: UInt) = {
    io.out.bits.redirect.valid       := true.B
    io.out.bits.redirect.bits.target := pc
  }

  // bCache recover init
  io.writeBack.bCache       := DontCare
  io.writeBack.bCache.valid := false.B
  def setBCache(pc: UInt, dst: UInt = 0.U) = {
    io.writeBack.bCache.valid    := true.B
    io.writeBack.bCache.bits.pc  := pc
    io.writeBack.bCache.bits.dst := dst
  }

  // redirect and BCache recover condition
  when(hasBranch && io.in.valid) {
    when(!bCacheHit) {
      setRedirect(predDst)
      setBCache(inBits.pcVal, predDst)
    }
  }.elsewhen(io.in.valid) {
    when(inBits.bCacheDst.valid) {
      setRedirect(getAlignPC(inBits.pcVal))
      setBCache(cleanMask & inBits.pcVal)
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
