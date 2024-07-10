package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core.mmu._
import core.cache._
import bpu._
import chisel3.experimental.conversions._

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
    // IFU
    val in      = Flipped(Decoupled(VAddr()))
    val out     = Decoupled(new IfStage1OutIO)
    val toPreIf = new IfStage1ToPreIf

    val icache = new Bundle {
      val req   = Decoupled(new ICache.Req)
      val stall = Input(Bool())
    }

    // BPU
    val writeback = new Bundle {
      val bCache = Flipped(Valid(new BCacheWIO))
      val btb    = new BtbUpdateIO()
      val pht    = new PhtUpdateIO()
      val ras    = Valid(new RetAddrStack.WriteBackIO())
    }
  })

  // alias ===============================================
  val outBits = io.out.bits
  val npc     = io.in.bits

  // stage regs ==========================================
  val pc = RegEnable(npc, io.in.fire)
  def pcSel(pc: UInt) = pc(fetchPt - 1, instrPt)
  val bpuSel = VecInit.tabulate(fetchNum)(i => RegEnable(pcSel(npc) + i.U, io.in.fire))

  // BPU and BCache ===========================================
  val ras = new RetAddrStack(true, retAddrStackSize)
  val btb = new BranchTargetBuffer()
  val pht = new PatternHistoryTable()
  val lht = new LocHisTab()
  // BPU module out to ifu stage1
  val bpuRes = Wire(Vec(fetchNum, new PredictResultBundle))
  // BPU module out to ifu stage2, need reorder by pc

  // search BCache in ifu stage1
  val bCache  = Module(new BCache)
  val bHitRes = Wire(Vec(fetchNum, Bool()))
  (0 until fetchNum).foreach { i =>
    bpuRes(i).btbType := btb.resp(i).bits.instType
    bpuRes(i).target  := Mux(BtbType.needPop(bpuRes(i).btbType), btb.resp(i).bits.target, ras.io.topData)
    bpuRes(i).counter := pht.resp(i).bits.cnt
    val brTake   = Mux(lht.readRes(i).cnt < bpu.LocHisTab.cntLimit.U, pht.resp(i).bits.take, lht.readRes(i).take)
    val isTakeBr = brTake && bpuRes(i).btbType === BtbType.branch
    val isTakeJp = BtbType.isJump(bpuRes(i).btbType)
    bpuRes(i).taken := isTakeJp || isTakeBr
    bHitRes(i)      := bpuRes(i).target === bCache.io.readRes.bits && bCache.io.readRes.valid
  }
  // reoder by pc(fetchPt, instrPt)
  (0 until fetchNum).foreach { i =>
    outBits.bpuOut(i)    := bpuRes(bpuSel(i))
    outBits.bCacheHit(i) := bHitRes(bpuSel(i))
  }

  // ======================================================
  // ============ BCache Connect Bundle ===================
  // ======================================================
  bCache.io.write <> io.writeback.bCache
  bCache.io.readAddr.bits  := npc
  bCache.io.readAddr.valid := io.in.fire
  io.toPreIf.predictRes    := bCache.io.readRes
  io.out.bits.bCacheDst    := bCache.io.readRes

  // BPU update port connect
  btb.update.tagIdx   := io.writeback.btb.tagIdx
  btb.update.instrOff := io.writeback.btb.instrOff
  btb.update.data     := io.writeback.btb.data
  pht.update.tagIdx   := io.writeback.pht.tagIdx
  pht.update.instrOff := io.writeback.pht.instrOff
  lht.update.tagIdx   := io.writeback.pht.tagIdx
  lht.update.instrOff := io.writeback.pht.instrOff
  ras.io.writeback <> io.writeback.ras
  (0 until fetchNum).map { i =>
    pht.update.data(i).valid := io.writeback.pht.data(i).valid
    pht.update.data(i).bits  := io.writeback.pht.data(i).bits.cnt
    lht.update.data(i).valid := io.writeback.pht.data(i).valid
    lht.update.data(i).bits  := io.writeback.pht.data(i).bits.take
  }
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
  val bpuReadys = (0 until fetchNum).map { i =>
    val offMsb = log2Ceil(iCacheBlkBytes / 4) + 2
    val mid    = Wire(UInt((offMsb - 2).W))
    mid := RingBits(npc(offMsb - 1, 2), fetchNum, i)
    val searchAddr = Cat(npc(VAddrBits - 1, offMsb), mid, 0.U(2.W))
    val btbReady   = btb.req(io.in.valid, searchAddr, i)
    val phtReady   = pht.req(io.in.valid, searchAddr, i)
    val lhtReady   = lht.req(io.in.valid, searchAddr, i)
    btbReady && phtReady && lhtReady
  }

  // use wire io.in, not RegNext =======================

  // =============================================================================
  // ================================ Cache ======================================
  // =============================================================================
  io.icache.req.valid := io.in.valid
  io.icache.req.bits  := npc
  val iCacheReady = io.icache.req.ready
  io.in.ready := io.icache.req.ready && bpuReadys.asUInt.andR

  // Mask and Dest
  import chisel3.util.experimental.decode._

  io.out.bits.alMask := decoder(
    pc(iCacheBlkPt - 1, instrPt),
    TruthTable(
      {
        // for fetch num == 4
        Seq(
          BitPat("b" + "1" * (instrOffWidth - 2) + "0" + "1") -> BitPat("b0111"),
          BitPat("b" + "1" * (instrOffWidth - 2) + "1" + "0") -> BitPat("b0011"),
          BitPat("b" + "1" * (instrOffWidth - 2) + "1" + "1") -> BitPat("b0001")
        )
        // for fetch num == any
        (1 until fetchNum).map { i =>
          val dst = BitPat("b" + "0" * (fetchNum - i) + "1" * fetchNum)
          val src = BitPat((math.pow(2, instrWidth).toInt - fetchNum).U(instrOffWidth.W))
          src -> dst
        }
      },
      BitPat("b" + "1" * fetchNum)
    )
  )

  io.toPreIf.pcVal  := pc
  io.out.bits.pcVal := pc

  // pipeline out
  io.out.valid := !io.icache.stall
}
