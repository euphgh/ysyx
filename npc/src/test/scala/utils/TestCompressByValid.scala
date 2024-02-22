package utils

import utils._
import utility._
import chisel3._
import chiseltest._
import chisel3.util._
import scala.collection.mutable.Queue
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest.simulator.VerilatorFlags

class TestCompressByValid extends AnyFlatSpec with ChiselScalatestTester with TestUtils {

  case class Port(valid: Boolean, value: Int, ready: Boolean)

  def pokeValid[T <: Data](port: ValidIO[T], data: T) = {
    port.valid.poke(true.B)
    port.bits.poke(data)
  }

  def pokeDecoupled[T <: Data](port: DecoupledIO[T], data: T) = {
    port.valid.poke(true.B)
    port.bits.poke(data)
  }

  def pokeInvalid[T <: Data](port: ValidIO[T]) = {
    port.valid.poke(false.B)
  }

  val verilator = Seq(
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

  val rand = new scala.util.Random()

  def testData(n: Int) = {
    val in = (0 until n).map { i =>
      if (rand.nextBoolean()) rand.between(1, 1024)
      else 0
    }
    val out = in.filter(_ != 0) ++ Seq.fill(in.count(_ == 0))(0)
    require(in.length == n)
    require(out.length == n)
    (in, out)
  }

  def decoupledData(n: Int) = {

    case class Inner(valid: Boolean, value: Int, inIdx: Int, outIdx: Int)

    def inner2Port(inner: Seq[Inner], limit: Int): Seq[Port] = inner.map {
      case Inner(valid, value, _, outIdx) =>
        Port(valid, value, outIdx < limit)
    }

    var cnt = 0

    val inInners = (0 until n).map { inIdx =>
      val value = rand.between(1, 1024)
      val valid = rand.nextBoolean()
      val outIdx = if (valid) {
        cnt = cnt + 1
        cnt - 1
      } else n
      Inner(valid, value, inIdx, outIdx)
    }
    require(inInners.length == n)

    val outInners = inInners.filter(_.valid) ++ (cnt until n).map(outIdx => Inner(false, 0, n, outIdx))
    require(outInners.length == n)

    val numOutReady = rand.nextInt(n + 1)

    (inner2Port(inInners, numOutReady), inner2Port(outInners, numOutReady))
  }

  behavior.of("CompressByValid")

  it should "compress ValidIO" in {
    test(new CompressByValid.CompressValidDut(16)).withAnnotations(treadle) { dut =>
      val n = dut.valid.in.length

      for (round <- (0 until 256)) {
        val (inDatas, outDatas) = testData(n)

        dut.valid.in.map(_.initSource())

        dut.valid.in.indices.map { i =>
          if (inDatas(i) != 0) {
            pokeValid(dut.valid.in(i), inDatas(i).U)
          }
        }

        dut.valid.out.indices.map { i =>
          if (outDatas(i) != 0) {
            dut.valid.out(i).bits.expect(outDatas(i).U)
            dut.valid.out(i).valid.expect(true.B)
          } else {
            dut.valid.out(i).valid.expect(false.B)
          }
        }

        dut.clock.step()
      }
    }
  }
  it should "compress DecoupledIO" in {
    test(new CompressByValid.CompressDecoupledDut(16)).withAnnotations(treadle) { dut =>
      val n = dut.decoupled.in.length

      for (round <- (0 until 256)) {
        val (inDatas, outDatas) = decoupledData(n)

        dut.decoupled.in.map(_.initSource())
        dut.decoupled.out.map(_.initSink())

        // Poke
        (0 until n).map { i =>
          if (inDatas(i).valid) {
            pokeDecoupled(dut.decoupled.in(i), inDatas(i).value.U)
          }
          if (outDatas(i).ready) {
            dut.decoupled.out(i).ready.poke(true)
          }
        }

        // Expect
        (0 until n).map { i =>
          if (inDatas(i).valid) {
            dut.decoupled.in(i).ready.expect(inDatas(i).ready)
          }
          if (outDatas(i).valid) {
            dut.decoupled.out(i).bits.expect(outDatas(i).value.U)
            dut.decoupled.out(i).valid.expect(true)
          } else {
            dut.decoupled.out(i).valid.expect(false)
          }
        }

        dut.clock.step()
      }
    }
  }
}
