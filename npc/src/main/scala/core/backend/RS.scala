package core.backend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import chisel3.experimental.conversions._

/**
  * allocate → writeIn - ready - select → readOp
  *
  * in.fromDispatcher.ready = rs not full
  *
  * in rename stage,we select a slot
  *
  * writeIn
  *     reuse rsOutIO for port <fromDispatcher>
  *     because the info written into Rs all need to take to FU
  *
  * ready
  *     1.already rdy in renameStage
  *     2.listen to wenPRF...next cycle the rdy bit will ↑
  *     3.wake-up：the selected insts broadCast its destPregAddr
  *     inte：
  *         msAlu -> otherRS (ReadOp)<1 bubble>
  *         mAlu<->sAlu (when "selected")<no bubble,need bypass>
  *         abandon now:load -> otherRS (MemStage1)<2 bubble>
  *     intra：
  *         mAlu<->mAlu
  *         sAlu<->sAlu
  *         (when "selected")<no bubble,need bypass>
  *
  * select
  *     use priority to select ready slot
  *         LSU/MDU/br：not any older insts(in-order)
  *         ALU(not br)：not any rdy&older insts(ooo)
  *     the selected insts broadCast its destPregAddr ,and will leave RS the next cycle  when fire()
  *
  * out
  *     RS keep the info that the insts will use in the FU
  *     decoded：srcAreg used in RO,uOps used in EXE,destAreg for s-rat update
  *     exception：record exception happended in FU,need write to ROB
  *     predictRes：br need it to detect mispredict in EXE
  *
  *     use sEntry(psrc+valid) in RO,use pDest in WB
  *     use robIndex in WB
  */
object OneHotMatrix {
  def rowView(arr: Vec[Vec[Bool]]): Vec[UInt] = {
    arr.map(_.asUInt)
  }
  def colView(arr: Vec[Vec[Bool]]): Vec[UInt] = {
    arr.head.indices.map { index =>
      arr.map(_(index)).asUInt
    }
  }
}
object MultiPriority {

  /**
    * select first $num one, return
    *
    * @param num
    * @param data
    * @return Seq($num, UInt(data.length.W)) saying the first $num's positio
    *         if not exist, will return 0.U
    */
  def oneHots(num: Int, data: Vec[Bool]) = {
    Seq(VecInit(false.B, true.B), VecInit(false.B, true.B))
  }

  def oneHots(num: Int, data: UInt) = {
    Seq(VecInit(false.B, true.B), VecInit(false.B, true.B))
  }
}

class FooPtr(implicit p: Parameters) extends CoreBundle {
  val foo = new RobPtr
}
class RS(rsSize: Int, outNum: Int)(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val in = new Bundle {
      val fromDispatcher = Vec(renameNum, Flipped(DecoupledIO(new MicroOp)))
      val wSratPIdx      = Vec(wBNum, Flipped(Valid(PRegIdx())))
      val oldestRobIdx   = Input(RobPtr())
      val stqEmpty       = Input(Bool())
    }
    val out = Vec(outNum, DecoupledIO(new MicroOp))
  })

  def MuxOneHotDefault[T <: Data](oneHot: Seq[Bool], data: Seq[T], default: T) = {
    val useDefault = !oneHot.asUInt.orR
    ParallelMux(oneHot.appended(useDefault), data.appended(default))
  }

  val rsEntries  = Reg(Vec(rsSize, new MicroOp))
  val slotsValid = RegInit(VecInit(Seq.fill(rsSize)(false.B)))

  val leftSlotNum = PopCount(slotsValid)
  io.in.fromDispatcher.map(_.ready := leftSlotNum > renameNum.U)

  val allRobPtr    = VecInit(rsEntries.map(_.robIdx))
  val enqSelectors = OneHotMatrix.colView(MultiPriority.oneHots(renameNum, slotsValid.map(!_)))
  val validEnq     = io.in.fromDispatcher.head.valid && leftSlotNum > renameNum.U
  when(validEnq) {
    (0 until rsSize).map { index =>
      when(enqSelectors(index).asUInt.orR) {
        rsEntries(index)  := Mux1H(enqSelectors(index), io.in.fromDispatcher.map(_.bits))
        slotsValid(index) := Mux1H(enqSelectors(index), io.in.fromDispatcher.map(_.valid))
        allRobPtr(index)  := Mux1H(enqSelectors(index), io.in.fromDispatcher.map(_.bits.robIdx))
      }
    }
  }

  val ageMask = allRobPtr.map(l => allRobPtr.map(r => RegNext(l > r, false.B)))

  val readyArr = (0 until rsSize).map { index =>
    val entry = rsEntries(index)
    def wbWake(psrc: UInt, regNext: Boolean = false) = {
      val wbHit = io.in.wSratPIdx.map { port =>
        val wb = if (regNext) RegNext(port) else port
        wb.valid && wb.bits === psrc
      }
      wbHit.asUInt.orR
    }
    val src0Ready = entry.pRegsMeta(0).inPrf && wbWake(entry.pRegsMeta(0).pIdx)
    val src1Ready = entry.pRegsMeta(1).inPrf && wbWake(entry.pRegsMeta(0).pIdx)
    src1Ready && src0Ready
  }

  /* there exiet **one valid ready** slot which robPtr is younger */
  val oldestOneHot = ageMask.zipWithIndex.map {
    case (younger, index) =>
      val existOlder = (younger.asUInt & slotsValid.asUInt & readyArr.asUInt).orR
      val isValid    = slotsValid(index)
      isValid && !existOlder
  }
  assert(PopCount(oldestOneHot) <= 1.U)

  require(outNum >= 1)

  io.out.head.valid := oldestOneHot.asUInt.orR
  io.out.head.bits  := Mux1H(oldestOneHot, rsEntries)

  var leftDeqOneHot: Seq[Vec[Bool]] = Seq()
  if (outNum > 1) {
    val leftValid = slotsValid.asUInt & ~oldestOneHot.asUInt
    leftDeqOneHot = MultiPriority.oneHots(outNum - 1, readyArr.asUInt & leftValid)
    (1 until outNum).foreach { index =>
      val out = io.out(index)
      out.valid := leftDeqOneHot(index).asUInt.orR
      out.bits  := Mux1H(leftDeqOneHot(index), rsEntries)

    }

    when(!io.out.head.valid) {
      assert((io.out.tail.map(_.valid)).asUInt.orR)
    }
  }

  val deqValids = io.out.map(_.fire)
  when(deqValids.asUInt.orR) {
    val deqSelectors = OneHotMatrix.colView(Seq(VecInit(oldestOneHot)) ++ leftDeqOneHot)
    require(deqSelectors.length == outNum)
    (0 until rsSize).map { index =>
      slotsValid(index) := io.out(index).fire
    }
  }
  when(io.flush) {
    slotsValid.foreach(_ := false.B)
  }
}
