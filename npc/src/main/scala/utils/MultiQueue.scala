package utils

import core._
import utility._
import chisel3._
import chisel3.util._
import scala.collection.SeqMap

package object Compress {

  private case class InnerInfo(popCounts: Seq[UInt], selectors: Seq[Seq[Bool]])

  private def prepare(valids: Seq[Bool]) = {
    val popCounts = valids.indices.map(i => PopCount(valids.take(i + 1)))
    val selectors = valids.indices.map { idx =>
      val equals = (idx until valids.length).map(j => popCounts(j) === (idx + 1).U)
      equals.indices.map(i => equals(i) && !(if (i == 0) false.B else equals(i - 1)))
    }
    InnerInfo(popCounts, selectors)
  }

  private def selectValidIO[T <: Data](info: InnerInfo, in: Seq[ValidIO[T]]) = {
    val InnerInfo(popCounts, selectors) = info

    val out = Wire(Vec(in.length, in.head.cloneType))
    in.indices.map { idx =>
      out(idx).bits  := Mux1H(selectors(idx).zip(in.map(_.bits).drop(idx)))
      out(idx).valid := idx.U < popCounts.last
    }
    out
  }
  private def selectDecoupledIO[T <: Data](info: InnerInfo, in: Seq[DecoupledIO[T]]) = {
    val InnerInfo(popCounts, selectors) = info

    val out = Wire(Vec(in.length, in.head.cloneType))
    in.indices.map { idx =>
      out(idx).bits  := Mux1H(selectors(idx).zip(in.map(_.bits).drop(idx)))
      out(idx).valid := idx.U < popCounts.last
      in(idx).ready  := out(popCounts(idx) - 1.U).ready
    }
    out
  }

  object Valid {

    def apply[T <: Data](in: Seq[ValidIO[T]]): Vec[ValidIO[T]] = {
      val infoPack = prepare(in.map(_.valid))
      selectValidIO(infoPack, in)
    }

    def apply[T <: Data](in: Seq[ValidIO[T]], cond: Int => Bool): Vec[ValidIO[T]] = {
      val infoPack = prepare(in.zipWithIndex.map { case (port, i) => port.valid && cond(i) })
      selectValidIO(infoPack, in)
    }

    class Dut(width: Int) extends Module {
      val valid = IO(new Bundle {
        val in  = Vec(width, Flipped(ValidIO(UInt(32.W))))
        val out = Vec(width, ValidIO(UInt(32.W)))
      })
      valid.out <> apply(valid.in)
    }
  }
  object Decoupled {

    def apply[T <: Data](in: Seq[DecoupledIO[T]]): Vec[DecoupledIO[T]] = {
      val infoPack = prepare(in.map(_.valid))
      selectDecoupledIO(infoPack, in)
    }

    def apply[T <: Data](in: Seq[DecoupledIO[T]], cond: Int => Bool): Vec[DecoupledIO[T]] = {
      val infoPack = prepare(in.zipWithIndex.map { case (port, i) => port.valid && cond(i) })
      selectDecoupledIO(infoPack, in)
    }

    class Dut(width: Int) extends Module {
      val foo = UInt(32.W)
      val decoupled = IO(new Bundle {
        val in  = Vec(width, Flipped(DecoupledIO(UInt(32.W))))
        val out = Vec(width, DecoupledIO(UInt(32.W)))
      })
      decoupled.out <> apply(decoupled.in)
    }
  }
}

/**
  * inputs interface:
  *   - consecutive valid with consecutive invalid (111..000..)
  *   - inconsecutive (01001100100...)
  * inputs ready:
  *   - timing first: only when buffer have enqNum space, assert true
  *
  * outputs interface: consecutive
  * outputs ready:
  *   - timing first: ready vector assert true same cycle
  *   - efficient first: assert true not cycle, buffer only deq first serveral ready
  *
  * @param enqWidth
  * @param deqWidth
  * @param dataGen
  * @param ptrGen
  */
class BaseMultiPortBuffer[D <: Data](
  enqWidth: Int,
  deqWidth: Int,
  size:     Int,
  dataGen:  D)
    extends HasCircularQueuePtrHelper {

  class CirPtr extends CircularQueuePtr[CirPtr](size)
  val ptrGen = new CirPtr
  def getPtrType: CirPtr = ptrGen

  class BufferIO extends Bundle {
    val in    = Vec(enqWidth, Flipped(Decoupled(dataGen)))
    val out   = Vec(deqWidth, Decoupled(dataGen))
    val flush = Input(Bool())
    val full  = Output(Bool())
  }

  val io = Wire(new BufferIO)

  io.suggestName(s"${enqWidth}enq_${deqWidth}deq_buffer_io")

  def bufferIO = {
    val ret = IO(new BufferIO)
    ret <> io
    ret
  }

  def enqPtrInitValue(idx: Int): CirPtr = idx.U.asTypeOf(ptrGen)
  def deqPtrInitValue(idx: Int): CirPtr = idx.U.asTypeOf(ptrGen)

  val deqPtrVec = RegInit(VecInit.tabulate(deqWidth)(enqPtrInitValue))
  val deqPtr    = deqPtrVec(0)

  val enqPtrVec = RegInit(VecInit.tabulate(enqWidth)(deqPtrInitValue))
  val enqPtr    = enqPtrVec(0)

  val bufferSize = ptrGen.entries

  val buffer = Vec(bufferSize, Reg(dataGen))

  // Bypass wire
  private val bypassEntries = WireDefault(VecInit.fill(deqWidth)(0.U.asTypeOf(Valid(dataGen))))
  // Normal read wire
  private val deqEntries = WireDefault(VecInit.fill(deqWidth)(0.U.asTypeOf(Valid(dataGen))))
  // Output register
  private val outputEntries = RegInit(VecInit.fill(deqWidth)(0.U.asTypeOf(Valid(dataGen))))

  val validEntries = distanceBetween(enqPtr, deqPtr)
  val allowEnq     = RegInit(true.B)
  // if is timing first, exist one fire, will not exist one valid but not ready
  // so deqSlotNum in outputs timing first is deqWidth.U
  val deqSlotNum = PopCount(io.out.map(x => x.fire || !x.valid))
  val useBypass  = deqSlotNum > validEntries
  val byPassNum  = deqSlotNum - validEntries

  //TODO: if inputs' interface is consecutive, cond could be simplified to ConsecutiveCount
  val numFromFetch = PopCount(io.in.map(_.valid))
  val numTryEnq    = WireDefault(0.U)
  //TODO: if inputs' interface is consecutive, cond could be simplified to io.in(0).fire
  val numEnq = Mux(ParallelOR(io.in.map(_.fire)), numTryEnq, 0.U)
  //TODO: outputs is must consecutive

  val numDeq = Mux(validEntries >= deqSlotNum, deqSlotNum, validEntries)

  allowEnq := (bufferSize - enqWidth).U >= validEntries +& numEnq - numDeq // Disable when almost full
  io.full  := !allowEnq

  val enqOffset = VecInit.tabulate(enqWidth)(i => PopCount(io.in.map(_.valid).take(i)))
  val enqData   = VecInit.tabulate(enqWidth)(i => io.in(i).bits)

  // when using bypass, bypassed entries do not enqueue
  when(useBypass) {
    when(numFromFetch >= byPassNum) {
      numTryEnq := numFromFetch - byPassNum
    }.otherwise {
      numTryEnq := 0.U
    }
  }.otherwise {
    numTryEnq := numFromFetch
  }

  // Pointer maintenance
  (0 until enqWidth).foreach { i => enqPtrVec(i) := enqPtrVec(i) + numEnq }
  (0 until deqWidth).foreach { i => deqPtrVec(i) := deqPtrVec(i) + numDeq }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Bypass and Dequeue
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  io.out.zip(outputEntries).foreach {
    case (io, reg) =>
      io.valid := reg.valid
      io.bits  := reg.bits
  }

  //TODO: in timing first mode, numFireOut = numDeq or 0
  // numValidOnly = 0 or numDeq, could be replace by io.ou.head.fire
  val numFireOut   = PopCount(io.out.map(_.fire))
  val numValidOnly = PopCount(io.out.map(d => d.valid && !d.ready))
  (0 until deqWidth).foreach { i =>
    val out = outputEntries

    val isOld  = i.U < numValidOnly
    val oldIdx = i.U + numFireOut

    val isDeq   = i.U < numDeq + numValidOnly
    val deqIdx  = i.U - numValidOnly
    val deqData = buffer(deqPtrVec(deqIdx).value)

    val bypIdx  = i.U - numValidOnly - numDeq
    val bypData = io.in(bypIdx)

    out(i).bits  := Mux(isOld, out(oldIdx).bits, Mux(isDeq, deqData, bypData.bits))
    out(i).valid := Mux(isOld, true.B, Mux(isDeq, true.B, bypData.valid))
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Enqueue
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // enq's Ready
  io.in.foreach(_.ready := allowEnq)

  // enq's Data
  (0 until enqWidth).foreach { i =>
    val wAddrNoByPass   = enqPtrVec(enqOffset(i)).value
    val wAddrWithByPass = enqPtrVec(enqOffset(i) - byPassNum).value
    val wAddr           = Mux(useBypass, wAddrWithByPass, wAddrNoByPass)

    val needEnqWhenByPass = enqOffset(i) >= byPassNum
    when(io.in(i).fire && Mux(useBypass, needEnqWhenByPass, true.B)) {
      buffer(wAddr) := io.in(i).bits
    }
  }

  // Flush
  when(io.flush) {
    allowEnq                      := true.B
    enqPtrVec                     := (0 until enqWidth).map(enqPtrInitValue)
    deqPtrVec                     := (0 until deqWidth).map(deqPtrInitValue)
    outputEntries.foreach(_.valid := false.B)
  }

}

class MultiBufferDut(enqWidth: Int, deqWidth: Int, size: Int) extends Module {
  val inner = new BaseMultiPortBuffer(enqWidth, deqWidth, size, UInt(16.W))

  val io = inner.bufferIO
}
