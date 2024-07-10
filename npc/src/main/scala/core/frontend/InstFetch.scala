package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core.dram._
import core.mmu._
import core.cache.ICache
import bpu._

class InstFetch(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {

    val redirect = FrontRedirct.input()
    val out      = Decoupled(new IfStage2OutIO)

    val ptw        = new TlbPtwIO()
    val imem       = new DramReadIO()
    val backendBPU = Flipped(new BpuUpdateIO)
  })

  val preIfStage = Module(new PreIf)
  val ifStage1   = Module(new IfStage1)
  val ifStage2   = Module(new IfStage2)
  val icache     = Module(new ICache)

  ifStage2.io.icache.resp <> icache.io.resp
  ifStage1.io.icache.req <> icache.io.req

  preIfStage.io.redirect := FrontRedirct.merge(io.redirect, ifStage2.io.out.bits.redirect)

  //If1 in
  ifStage1.io.in := preIfStage.io.out

  //IF2 in
  PipelineConnect(
    ifStage1.io.out,
    ifStage2.io.in,
    ifStage2.io.out.fire,
    io.redirect.valid
  )
  ifStage2.io.backFlush := io.redirect.valid
  io.out <> ifStage2.io.out

  // ifStage2 update BPU
  class BtbAssignBundle(implicit p: Parameters) extends CoreBundle {
    val tagIdx   = UInt((32 - log2Ceil(iCacheBlkBytes)).W)
    val instrOff = Vec(fetchNum, UInt(instrOffWidth.W))
    val wen      = Vec(fetchNum, Bool())
    val data     = Vec(fetchNum, new BtbOutIO)
    def passToUpdateIO(updateIO: BtbUpdateIO) = {
      updateIO.tagIdx   := tagIdx
      updateIO.instrOff := instrOff
      (0 until fetchNum).foreach(i => {
        updateIO.data(i).valid := wen(i)
        updateIO.data(i).bits  := data(i)
      })
    }
  }

  val if2Wio       = ifStage2.io.writeBack.btb.bits
  val if2AssignBtb = Wire(new BtbAssignBundle)
  if2AssignBtb.tagIdx := if2Wio.tagIdx
  val if2OutWen      = Wire(Vec(fetchNum, Bool()))
  val if2OutTarget   = Wire(Vec(fetchNum, UInt32()))
  val if2OutInstType = Wire(Vec(fetchNum, BtbType()))
  (0 until fetchNum).foreach { i =>
    import BranchType._
    val pc = Cat(if2Wio.tagIdx, if2Wio.instrOff(i), 0.U(2.W))
    val instr: RInstr = if2Wio.instr(i).asTypeOf(new RInstr()(p))
    val brDest = calDest(if2Wio.brType(i), instr, pc)
    require(pc.getWidth == VAddrBits)
    if2OutWen(i)      := ifStage2.io.btbDeq.valid && if2Wio.valid(i) && (brDest.avaliable)
    if2OutTarget(i)   := brDest.dest
    if2OutInstType(i) := toBtbType(if2Wio.brType(i), instr.rs1, instr.rd)
  }

  (0 until fetchNum).map(i => {
    val sel = VecInit((0 until fetchNum).map(j => { if2Wio.instrOff(j)(1, 0) === i.U }))
    when(ifStage2.io.btbDeq.valid) { PopCount(sel.asUInt === 1.U) }
    if2AssignBtb.instrOff(i)      := Mux1H(sel, if2Wio.instrOff)
    if2AssignBtb.wen(i)           := Mux1H(sel, if2OutWen)
    if2AssignBtb.data(i).target   := Mux1H(sel, if2OutTarget)
    if2AssignBtb.data(i).instType := Mux1H(sel, if2OutInstType)
  })

  val if2NoBr  = if2OutWen.asUInt.orR === false.B
  val backWbtb = io.backendBPU.btb.valid
  ifStage2.io.btbDeq.ready := if2NoBr || !backWbtb

  // backend update
  val backAssignBtb = Wire(new BtbAssignBundle)
  val if2PhtIO      = Wire(new PhtUpdateIO)
  val if2BtbIO      = Wire(new BtbUpdateIO)
  val tagIdx        = io.backendBPU.pc(VAddrBits - 1, iCacheBlkPt)
  val instrOff      = io.backendBPU.pc(iCacheBlkPt - 1, instrPt)
  val selValid      = VecInit.tabulate(fetchNum)(i => instrOff(1, 0) === i.U)
  backAssignBtb.tagIdx   := tagIdx
  backAssignBtb.instrOff := VecInit.fill(fetchNum)(instrOff)
  (0 until fetchNum).foreach(i => {
    backAssignBtb.wen(i)  := selValid(i) && io.backendBPU.btb.valid
    backAssignBtb.data(i) := io.backendBPU.btb.bits
  })
  if2PhtIO.tagIdx   := tagIdx
  if2PhtIO.instrOff := VecInit.fill(fetchNum)(instrOff)
  (0 until fetchNum).foreach(i => {
    if2PhtIO.data(i).valid := selValid(i) && io.backendBPU.pht.valid
    if2PhtIO.data(i).bits  := io.backendBPU.pht.bits
  })
  if2AssignBtb.passToUpdateIO(if2BtbIO)
  when(backWbtb) { backAssignBtb.passToUpdateIO(if2BtbIO) }

  // >> bpu ===============================================
  ifStage1.io.writeback.bCache <> ifStage2.io.writeBack.bCache 
  ifStage1.io.writeback.ras := ifStage2.io.writeBack.ras
  ifStage1.io.writeback.btb := if2BtbIO
  ifStage1.io.writeback.pht := if2PhtIO
  if (verilator) {
    // val if2FireIn = WireInit(ifStage1.io.out.fire && !if2Flush)
    //TODO: fix BoringUtils
    // addSource(if2FireIn, "If2FireIn")
  }
}
