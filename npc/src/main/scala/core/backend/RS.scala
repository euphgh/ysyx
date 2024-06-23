package core.backend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import chisel3.experimental.conversions._
import freechips.rocketchip.util.MultiPortQueue

class ReservationStationIO(outNum: Int)(implicit p: Parameters) extends CoreBundle {
  val flush = Input(Bool())
  val in = new Bundle {
    val fromDispatcher = Vec(renameNum, Flipped(DecoupledIO(new MicroOp)))
    val wSratPIdx      = Vec(wBNum, Flipped(Valid(PRegIdx())))
    val oldestRobIdx   = Input(RobPtr())
    val stqEmpty       = Input(Bool())
  }
  val out = Vec(outNum, DecoupledIO(new MicroOp))
}

class InOrderReservationStation(rsSize: Int, outNum: Int)(implicit p: Parameters) extends CoreDelegate {
  val io = Wire(new ReservationStationIO(outNum))

  val queue = new BaseMultiPortBuffer(renameNum, outNum, rsSize, new MicroOp) {
    buffer(deqPtrVec(0))
    buffer(deqPtrVec(1))
    buffer(deqPtrVec(outNum))
    override val numDeq: UInt = ???
  }

  queue.io.flush <> io.flush
  queue.io.in <> io.in.fromDispatcher
  queue.io.out <> io.out
}

class OutOfOrderReservationStation(rsSize: Int, outNum: Int)(implicit p: Parameters) extends CoreDelegate {
  val io = Wire(new ReservationStationIO(outNum))

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

  val ageMask = allRobPtr.map(l => allRobPtr.map(r => RegNext(l < r, false.B)))

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
object RS {
  def apply(rsSize: Int, outNum: Int, ooo: Boolean = true)(implicit p: Parameters) = {
    if (ooo) (new OutOfOrderReservationStation(rsSize, outNum)).io
    else (new InOrderReservationStation(rsSize, outNum)).io
  }
}
