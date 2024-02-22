package utils

import chisel3._
import chisel3.util._
import core.ALUOpType
import scala.collection.SeqMap
import dataclass.data
import utility.ParallelPriorityMux

object CompressByValid {

  case class Inner(popCounts: Seq[UInt], selectors: Seq[Seq[Bool]])

  def prepare(valids: Seq[Bool]) = {
    val popCounts = valids.indices.map(i => PopCount(valids.take(i + 1)))
    val selectors = valids.indices.map { idx =>
      val equals = (idx until valids.length).map(j => popCounts(j) === (idx + 1).U)
      equals.indices.map(i => equals(i) && !(if (i == 0) false.B else equals(i - 1)))
    }
    Inner(popCounts, selectors)
  }

  def apply[T <: Data](in: Vec[ValidIO[T]]): Vec[ValidIO[T]] = {
    val Inner(popCounts, selectors) = prepare(in.map(_.valid))

    val out = Wire(Vec(in.length, in.head.cloneType))
    in.indices.map { idx =>
      out(idx).bits  := Mux1H(selectors(idx).zip(in.map(_.bits).drop(idx)))
      out(idx).valid := idx.U < popCounts.last
    }
    out
  }

  def apply[T <: Data](in: Seq[DecoupledIO[T]]): Vec[DecoupledIO[T]] = {
    val Inner(popCounts, selectors) = prepare(in.map(_.valid))

    val out = Wire(Vec(in.length, in.head.cloneType))
    in.indices.map { idx =>
      out(idx).bits  := Mux1H(selectors(idx).zip(in.map(_.bits).drop(idx)))
      out(idx).valid := idx.U < popCounts.last
      in(idx).ready  := out(popCounts(idx) - 1.U).ready
    }
    out
  }

  class CompressValidDut(width: Int) extends Module {
    val valid = IO(new Bundle {
      val in  = Vec(width, Flipped(Valid(UInt(32.W))))
      val out = Vec(width, Valid(UInt(32.W)))
    })
    valid.out <> apply(valid.in)
  }

  class CompressDecoupledDut(width: Int) extends Module {
    val decoupled = IO(new Bundle {
      val in  = Vec(width, Flipped(Decoupled(UInt(32.W))))
      val out = Vec(width, Decoupled(UInt(32.W)))
    })
    decoupled.out <> apply(decoupled.in)
  }
}

/**
  * all the port should at least be fmt "1..0.."
  * all the gen in Q is "valid"
  *
  * push(x).valid can be different according to input,
  * but push(x).ready from MultiQueue must be same.
  * MultiQueue will accept them at same cycle when it has enough space(enqNum)
  *   or give x ready when it has x blank space(x<=enqNum)
  *
  * pop(x).valid also can be different according to
  * MultiQueue has enough data or not.
  * but pop(x).ready must be same,
  * only when (0 to deqNum).foldLeft(true.B)(pop(_).fire && _) === true.B
  * MultiQueue update it pop
  *   not need to be same,but should be "1...0..." form
  *
  * MultiQueue can be used in InstBuffer, FreeList(a little weird), ROB ,STQ
  *
  * @param enqNum enqueue number in one cycle
  * @param deqNum enqueue number in one cycle
  * @param gen data type
  * @param size Queue size
  * @param allIn True:   if MultiQueue dont't have space accepts all enq elements, it will not assert any enq.ready
  *              false:  it will assert some enq.ready
  */

class MultiQueue[T <: Data](
  enqNum: Int,
  deqNum: Int,
  gen:    T,
  size:   Int     = 32,
  isFL:   Boolean = false)
    extends Module {
  require(isPow2(size))
  val counterWidth = log2Ceil(size)
  val ptrWidth     = counterWidth + 1

  val io = IO(new Bundle {
    val push    = Vec(enqNum, Flipped(Decoupled(gen)))
    val pop     = Vec(deqNum, Decoupled(gen))
    val flush   = Input(Bool())
    val tailPtr = Output(UInt(ptrWidth.W))
    val headPtr = Output(UInt(ptrWidth.W))
  })

  val initZero = Seq.fill(size)(0.U.asTypeOf(gen))
  val initFl   = (0 until size).map(i => (i + 32).U.asTypeOf(gen))

  def ramInit(index: Int): T = 0.U.asTypeOf(gen)
  private val ringBuffer = RegInit(VecInit.tabulate(size)(ramInit(_)))

  def headInit: UInt = {
    val normal   = 0.U
    val freeList = size.U
    normal
  }

  val headPtr    = RegInit(UInt(ptrWidth.W), if (isFL) size.U else 0.U)
  val tailPtr    = RegInit(UInt(ptrWidth.W), 0.U)
  val deqFireNum = PopCount(io.pop.map(_.fire))
  val enqFireNum = PopCount(io.push.map(_.fire))
  def overflow(add:  UInt) = (headPtr - tailPtr + add - deqFireNum)(counterWidth) //must look ahead a cycle
  def underflow(sub: UInt) = (headPtr - tailPtr - sub)(counterWidth) //only considerate current
  val nextBasicNum = RegNext(Mux(io.flush, 0.U(ptrWidth.W), (headPtr + enqFireNum - tailPtr - deqFireNum)))
  def overflowR(add:  UInt) = (nextBasicNum + add)(counterWidth) //must look ahead a cycle
  def underflowR(sub: UInt) = (nextBasicNum - sub)(counterWidth) //only considerate current
  val pushIndex = RegNext(VecInit((0 until enqNum).map(i => {
    val startLine = Mux(io.flush, 0.U(counterWidth.W), headPtr + enqFireNum)
    (startLine + i.U)(counterWidth - 1, 0)
  })))
  val popIndex = RegNext(VecInit((0 until deqNum).map(i => {
    val startLine = Mux(io.flush, 0.U(counterWidth.W), tailPtr + deqFireNum)
    (startLine + i.U)(counterWidth - 1, 0)
  })))
  val counterMatch = headPtr(counterWidth - 1, 0) === tailPtr(counterWidth - 1, 0)
  val signMatch    = headPtr(ptrWidth - 1) === tailPtr(ptrWidth - 1)
  val empty        = counterMatch && signMatch
  val full         = counterMatch && !signMatch
  io.headPtr := headPtr
  io.tailPtr := tailPtr

  def pushReady(index: Int): Bool = {
    val reductSpace = !(overflowR((enqNum - 1).U))
    val enoughSpace = !(overflowR(index.U))
    reductSpace
  }

  (0 until enqNum).foreach(i => io.push(i).ready := pushReady(i))

  List.tabulate(enqNum)(i => {
    when(io.push(i).fire) {
      ringBuffer(pushIndex(i)) := io.push(i).bits
    }
  })
  headPtr := headPtr + enqFireNum

  //pop
  (0 until deqNum).foreach(i => (io.pop(i).valid := !underflowR((i + 1).U)))
  tailPtr := tailPtr + deqFireNum
  (0 until deqNum).foreach(i => {
    io.pop(i).bits := ringBuffer(popIndex(i))
  })

  when(io.flush) {
    headPtr := 0.U
    tailPtr := 0.U
  }
}

import utility._

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
class BaseMultiPortBuffer[D <: Data, P <: CircularQueuePtr[P]](
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

  val buffer = VecMem(bufferSize, dataGen)

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

    val isDeq   = i.U < validEntries + numValidOnly
    val deqIdx  = i.U - numValidOnly
    val deqData = buffer.read(deqPtrVec(deqIdx).value)

    val bypIdx  = i.U - numValidOnly - validEntries
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
      buffer.write(wAddr, io.in(i).bits)
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
