package top

import core._
import org.chipsalliance.cde.config._

object TopMain extends App {
  val (config, firrtlOpts, firtoolOpts) = ArgParser.parse(args)

  def top       = new Core()(config)
  val generator = Seq(chisel3.stage.ChiselGeneratorAnnotation(() => top))

  (new MyStage()).execute(Array("-td", "build"), generator)
}

import utils._
import chisel3._
import chisel3.util._

class Inner {
  def gen = UInt(4.W)
  val io = Wire(new Bundle {
    val input  = Flipped(Decoupled(gen))
    val output = Decoupled(gen)
  })

  val reg   = Reg(gen)
  val valid = RegInit(false.B)
  io.input.ready  := !valid
  io.output.valid := valid
  io.output.bits  := reg

  when(io.input.fire) {
    reg   := io.input.bits
    valid := true.B
  }

  when(io.output.fire) {
    valid := false.B
  }
}

class Outer extends Module {
  def gen = UInt(4.W)
  val io = IO(new Bundle {
    val input  = Flipped(Decoupled(gen))
    val output = Decoupled(gen)
  })
  val valid = IO(Output(Bool()))
  val reg   = IO(Output(gen))

  val inner = new Inner

  inner.io <> io

  valid := inner.valid
  reg   := inner.reg
}

class MemTest extends Module {
  class MyBundle extends Bundle {
    val foo = UInt(32.W)
    val bar = Bool()
  }

  val io = IO(
    Vec(
      2,
      new Bundle {
        val read = new Bundle {
          val addr = Input(UInt(5.W))
          val data = Output(new MyBundle)
        }
        val write = new Bundle {
          val wen  = Input(Bool())
          val addr = Input(UInt(5.W))
          val data = Input(new MyBundle)
        }
      }
    )
  )

  val flush = IO(Input(Bool()))

  val regs = Reg(Vec(32, new MyBundle))
  val mems = Mem(32, new MyBundle)
  when(reset.asBool || flush) {
    (0 until 32).foreach { i =>
      val init = Wire(new MyBundle)
      init.foo := i.U
      init.bar := false.B
      mems.write(i.U, init)
    }
  }

  val foo = mems.read(io(0).read.addr)
  foo             := 123.U.asTypeOf(new MyBundle)
  io(0).read.data := foo
  when(io(0).write.wen) {
    mems.write(io(0).write.addr, io(0).write.data)
  }

  io(1).read.data := regs(io(1).read.addr)

  when(io(1).write.wen) {
    regs(io(1).write.addr) := io(0).write.data
  }

  def change(inputs: UInt) = {
    val ret = new MyBundle
    ret.foo := inputs
    ret.bar := false.B
    ret
  }
  // val testOut = IO(Output(new MyBundle))
  // testOut := change(io(0).read.data.asUInt(32, 1))
}

object FooMain extends App {
  def top       = new MemTest
  val generator = Seq(chisel3.stage.ChiselGeneratorAnnotation(() => top))
  (new MyStage()).execute(Array("-td", "build"), generator)
}

object BufferMain extends App {
  import scala.collection.mutable.Queue
  val base   = Queue((0 until 8): _*)
  val cloned = base.clone()
  println("base init", base)
  println("clone init", cloned)
  base.dequeue()
  println("base after deq", base)
  println("clone after deq", cloned)
}
