package utils

import utils._
import utility._
import chisel3._
import chiseltest._
import chisel3.util._
import scala.collection.mutable.Queue
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest.simulator.VerilatorFlags

trait TestUtils {

  def initRount(dut: MultiBufferDut) = {
    dut.io.in.foreach(_.setSourceClock(dut.clock))
    dut.io.out.foreach(_.setSinkClock(dut.clock))

    dut.io.in.foreach(_.initSource())
    dut.io.out.foreach(_.initSink())

    dut.io.out.foreach(_.expectInvalid())
  }

  def finishRount(dut: MultiBufferDut) = {
    // start assert flush one cycle
    dut.io.flush.poke(true)
    dut.clock.step()

    // check deq.valid and enq.ready
    dut.io.out.foreach(_.expectInvalid())
    dut.io.in.foreach(_.ready.expect(true))

    // unset enq valid and deq ready one cycle
    dut.io.in.foreach(_.initSource())
    dut.io.out.foreach(_.initSink())
    dut.clock.step()

    // check deq.valid and enq.ready
    dut.io.out.foreach(_.expectInvalid())
    dut.io.in.foreach(_.ready.expect(true))

    // flush finish cycle
    dut.io.flush.poke(false.B)
    dut.clock.step()

    // check deq.valid and enq.ready
    dut.io.out.foreach(_.expectInvalid())
    dut.io.in.foreach(_.ready.expect(true))
  }

  def exec(dut: MultiBufferDut, test: MultiBufferDut => Unit) = {
    initRount(dut)
    test(dut)
    finishRount(dut)
  }

  def parallinks(run: Seq[() => Unit]) = {
    val forkLinks = run.foldLeft(fork {}) {
      case (lastFork, func) => lastFork.fork { func() }
    }
    forkLinks.join()
  }

  def enqDatas(dut: MultiBufferDut, enqDatas: Queue[Int], randEnq: => Boolean = true) = {
    val datas    = enqDatas.clone()
    val numTotal = datas.size
    while (!datas.isEmpty) {
      parallinks(dut.io.in.zipWithIndex.map {
        case (in, idx) =>
          () => {
            if (randEnq)
              in.enqueue(datas.dequeue().U)
          }
      })
    }
  }

  def deqDatas(dut: MultiBufferDut, deqDatas: Queue[Int], numDeq: => Int = Int.MaxValue) = {
    val datas    = deqDatas.clone()
    val numTotal = datas.size
    while (!datas.isEmpty) {
      val numLimit = numDeq
      parallinks(dut.io.out.zipWithIndex.map {
        case (out, idx) =>
          () => {
            if (idx < numLimit)
              out.expectDequeue(datas.dequeue().U)
          }
      })
    }
  }
}

class MulitBufferTest extends AnyFlatSpec with ChiselScalatestTester with TestUtils {

  def singleElementTest(dut: MultiBufferDut) = {
    dut.io.in(0).enqueueNow(42.U)
    parallel(
      dut.io.out(0).expectDequeueNow(42.U),
      dut.io.in(0).enqueueNow(43.U)
    )
    dut.io.out(0).expectDequeueNow(43.U)
  }

  def multEnqDeqByPassTest(dut: MultiBufferDut) = {
    val inData  = Queue((0 until 16): _*)
    val outData = Queue((0 until 16): _*)

    parallinks(dut.io.in.zipWithIndex.map {
      case (in, idx) =>
        () => in.enqueueNow(inData.dequeue().U)
    })

    parallinks(dut.io.in.zipWithIndex.map {
      case (in, idx) =>
        () => in.enqueue(inData.dequeue.U)
    })

    parallel(
      dut.io.out(0).expectDequeueNow(outData.dequeue().U),
      dut.io.out(1).expectDequeueNow(outData.dequeue().U)
    )

    parallel(
      dut.io.out(0).expectDequeueNow(outData.dequeue().U),
      dut.io.out(1).expectDequeueNow(outData.dequeue().U)
    )

    parallel(
      dut.io.out(0).expectDequeueNow(outData.dequeue().U),
      dut.io.out(1).expectDequeueNow(outData.dequeue().U)
    )
  }

  def fullEnqFullDeqTest(numTotal: Int)(dut: MultiBufferDut) = {
    val data: Queue[Int] = Queue((0 until numTotal): _*)
    fork {
      enqDatas(dut, data)
    }.fork {
      deqDatas(dut, data)
    }.join()
  }

  def randEnqFullDeqTest(numTotal: Int)(dut: MultiBufferDut) = {
    val rand = new scala.util.Random()
    val data: Queue[Int] = Queue((0 until numTotal): _*)
    initRount(dut)
    fork {
      enqDatas(dut, data, rand.nextBoolean())
    }.fork {
      deqDatas(dut, data)
    }.join()
  }

  def fullEnqRandDeqTest(numTotal: Int)(dut: MultiBufferDut) = {
    val rand = new scala.util.Random()
    val data: Queue[Int] = Queue((0 until numTotal): _*)
    initRount(dut)
    fork {
      enqDatas(dut, data)
    }.fork {
      deqDatas(dut, data, rand.nextInt(dut.io.out.length + 1))
    }.join()
  }

  def randEnqRandDeqTest(numTotal: Int)(dut: MultiBufferDut) = {
    val rand = new scala.util.Random()
    val data: Queue[Int] = Queue((0 until numTotal): _*)
    initRount(dut)
    fork {
      enqDatas(dut, data, rand.nextBoolean())
    }.fork {
      deqDatas(dut, data, rand.nextInt(dut.io.out.length + 1))
    }.join()
  }

  val verilaotr = Seq(
    VerilatorBackendAnnotation,
    WriteVcdAnnotation,
    VerilatorFlags(
      Seq(
        "+define+RANDOMIZE_REG_INIT",
        "+define+RANDOMIZE_MEM_INIT"
      )
    )
  )
  val treadle = Seq(WriteVcdAnnotation)

  behavior.of("MulitBuffer")

  it should "like single port queue" in {
    test(new MultiBufferDut(4, 2, 8)).withAnnotations(verilaotr) { dut =>
      exec(dut, singleElementTest)
    }
  }

  it should "bypass work" in {
    test(new MultiBufferDut(4, 2, 8)).withAnnotations(verilaotr) { dut =>
      exec(dut, multEnqDeqByPassTest)
    }
  }

  it should "full enq full deq" in {
    test(new MultiBufferDut(4, 2, 8)).withAnnotations(verilaotr) { dut =>
      exec(dut, fullEnqFullDeqTest(64))
    }
  }
  it should "full enq rand deq" in {
    test(new MultiBufferDut(4, 2, 8)).withAnnotations(verilaotr) { dut =>
      exec(dut, fullEnqRandDeqTest(64))
    }
  }
  it should "rand enq full deq" in {
    test(new MultiBufferDut(4, 2, 8)).withAnnotations(verilaotr) { dut =>
      exec(dut, randEnqFullDeqTest(64))
    }
  }
  it should "rand enq rand deq" in {
    test(new MultiBufferDut(4, 2, 8)).withAnnotations(verilaotr) { dut =>
      exec(dut, randEnqRandDeqTest(64))
    }
  }
}
