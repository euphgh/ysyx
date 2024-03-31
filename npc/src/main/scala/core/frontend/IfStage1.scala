package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core.mmu._
import core.cache._

class ICacheInstIO(implicit p: Parameters) extends CoreBundle {
  val op    = CacheOp()
  val taglo = UWord
  val index = UInt(IcacheIndexWidth.W)
}

/**
  * not connect by pipeline
  * out.pcVal = regEnable(preIFOutIO.npc, fire)
  *
  * out.bpuOut give predict result after fire posedge
  * should keep result until next fire posedge
  *
  * out.alignMask is calculate by out.pcVal
  *
  * out.tagOfInstGroup is calculate by out.pcVal
  * connect TLB module by tlb bundle
  * tlb.req = out.pcVal
  * out.tagOfInstGroup  = tlb.back
  *
  * out.exception != NONE when tlb exception or address error
  *
  * instantiate I-cache stage1 in this module
  * all out should not change until next fire posedge
  * when in.flush || IcacheInst.valid = true
  * stage1 must update all out in next cycle
  *
  * instantiate BPU in this module, let out.bpuout = bpu.out
  */
class IfStage1(implicit p: Parameters) extends CoreModule with BCacheHelp {
  val io = IO(new Bundle {
    val resetVector = Input(UInt(PAddrBits.W))

    // IFU
    val in      = Flipped(new PreIfOutIO)
    val out     = Decoupled(new IfStage1OutIO)
    val toPreIf = new IfStage1ToPreIf

    val icache = new Bundle {
      val req  = Decoupled(new ICache.Req)
      val ctrl = new ICache.Ctrl
    }

    // BPU
    val bCacheW = Flipped(Valid(new BCacheWIO))
    val btbWen  = new BtbUpdateIO
    val phtWen  = new PhtUpdateIO
    val rasPush = Flipped(Valid(UWord))
    val rasPop  = Input(Bool())
  })

  // alias ===============================================
  val outBits = io.out.bits
  val npc     = io.in.npc
  val PCReset = io.resetVector

  // stage regs ==========================================
  val update  = WireInit(io.in.flush || io.out.ready)
  val bpuRreq = io.in.flush || io.out.ready
  val pc      = RegEnable(npc, PCReset, update)
  val bpuSel  = VecInit.tabulate(fetchNum)(i => RegEnable(npc(3, 2) + i.U, PCReset(3, 2), update))

  // BPU and BCache ===========================================
  val ras = Module(new RetAddrStack(true, retAddrStackSize))
  val btb = Module(new BranchTargetBuffer())
  val pht = Module(new PatternHistoryTable())
  val lht = Module(new LocHisTab())
  // BPU module out to ifu stage1
  val bpuRes = Wire(Vec(fetchNum, new PredictResultBundle))
  // BPU module out to ifu stage2, need reorder by pc
  val bpuOut = outBits.bpuOut

  // search BCache in ifu stage1
  val bCache  = Module(new BCache)
  val bHitRes = Wire(Vec(fetchNum, Bool()))
  val bHitOut = outBits.bCacheHit
  (0 until fetchNum).foreach(i => {
    bpuRes(i).btbType := btb.readRes(i).instType
    bpuRes(i).target  := Mux(bpuRes(i).btbType =/= BtbType.pop, btb.readRes(i).target, ras.io.topData)
    bpuRes(i).counter := pht.readRes(i)
    val brTake   = Mux(lht.readRes(i).cnt < 14.U, pht.readRes(i) > 1.U, lht.readRes(i).take)
    val isTakeBr = brTake && bpuRes(i).btbType === BtbType.branch
    val isTakeJp = BtbType.isJump(bpuRes(i).btbType)
    bpuRes(i).taken := isTakeJp || isTakeBr
    bHitRes(i)      := bpuRes(i).target === bCache.io.readRes.bits && bCache.io.readRes.valid
  })
  (0 until fetchNum).foreach(i => {
    bpuOut(i)  := bpuRes(bpuSel(i))
    bHitOut(i) := bHitRes(bpuSel(i))
  })
  bCache.io.write <> io.bCacheW

  // connect BCache read address
  bCache.io.readAddr.bits  := npc
  bCache.io.readAddr.valid := bpuRreq
  io.toPreIf.predictRes    := bCache.io.readRes
  io.out.bits.bCacheDst    := bCache.io.readRes

  // BPU update port connect
  btb.update.tagIdx   := io.btbWen.tagIdx
  btb.update.instrOff := io.btbWen.instrOff
  btb.update.data     := io.btbWen.data
  pht.update.tagIdx   := io.phtWen.tagIdx
  pht.update.instrOff := io.phtWen.instrOff
  lht.update.tagIdx   := io.phtWen.tagIdx
  lht.update.instrOff := io.phtWen.instrOff
  ras.io.push <> io.rasPush
  ras.io.pop := io.rasPop
  (0 until fetchNum).map(i => {
    pht.update.data(i).valid := io.phtWen.data(i).valid
    pht.update.data(i).bits  := io.phtWen.data(i).bits.cnt
    lht.update.data(i).valid := io.phtWen.data(i).valid
    lht.update.data(i).bits  := io.phtWen.data(i).bits.take
  })
  if (verilator) {
    // TODO: fix difftest
    // val btbDiff = Module(new DifftestBTBWrite)
    // asg(btbDiff.io.clock, clock)
    // asg(btbDiff.io.en, btbDiff.io.wen.asUInt.orR)
    // asg(btbDiff.io.tagIdx, io.btbWen.tagIdx)
    // asg(btbDiff.io.instrOff, io.btbWen.instrOff)
    // asg(btbDiff.io.wen, VecInit(io.btbWen.data.map(_.valid)))
    // asg(btbDiff.io.target, VecInit(io.btbWen.data.map(_.bits.target)))
    // asg(btbDiff.io.btbType, VecInit(io.btbWen.data.map(_.bits.instType.asUInt)))

    // val phtDiff = Module(new DifftestPHTWrite)
    // asg(phtDiff.io.clock, clock)
    // val phtWen = phtDiff.io.wen.asUInt.orR
    // asg(phtDiff.io.en, (phtWen || RegNext(phtWen)) && !reset.asBool)
    // asg(phtDiff.io.tagIdx, io.phtWen.tagIdx)
    // asg(phtDiff.io.instrOff, io.phtWen.instrOff)
    // asg(phtDiff.io.wen, VecInit(io.phtWen.data.map(_.valid)))
    // asg(phtDiff.io.count, VecInit(io.phtWen.data.map(_.bits.cnt)))
    // asg(phtDiff.io.take, VecInit(io.phtWen.data.map(_.bits.take)))
  }

  // connect BPU read address
  (0 until fetchNum).foreach(i => {
    val offMsb = log2Ceil(IcachLineBytes / 4) + 2
    val mid    = Wire(UInt((offMsb - 2).W))
    mid := RingBits(npc(offMsb - 1, 2), fetchNum, i)
    val searchAddr = Cat(npc(31, offMsb), mid, 0.U(2.W))
    btb.readAddr(i).bits  := searchAddr
    pht.readAddr(i).bits  := searchAddr
    lht.readAddr(i).bits  := searchAddr
    btb.readAddr(i).valid := bpuRreq
    pht.readAddr(i).valid := bpuRreq
    lht.readAddr(i).valid := bpuRreq
  })

  // use wire io.in, not RegNext =======================

  // =============================================================================
  // ================================ Cache ======================================
  // =============================================================================
  io.icache.req.valid := update
  io.icache.req.bits  := npc

  // Mask and Dest
  import chisel3.util.experimental.decode._
  io.out.bits.alMask := decoder(
    pc(instrOffMsb, 2),
    TruthTable(
      Seq(
        BitPat("b" + "1" * (instrOffWidth - 2) + "0" + "1") -> BitPat("b0111"),
        BitPat("b" + "1" * (instrOffWidth - 2) + "1" + "0") -> BitPat("b0011"),
        BitPat("b" + "1" * (instrOffWidth - 2) + "1" + "1") -> BitPat("b0001")
      ),
      BitPat("b1111")
    )
  )

  // Cache Output
  val addrError = pc(1, 0).orR
  io.out.bits.exception := Mux(addrError, FrontExcCode.AdEL, FrontExcCode.NONE)
  io.toPreIf.pcVal      := pc
  io.out.bits.pcVal     := pc

  // pipeline out
  io.out.valid := io.icache.ctrl

  // Reset for 512
  val resetCnt = Counter(512)
  when(resetCnt.inc()) {
    io.out.valid := false.B
    update       := false.B
  }
}
