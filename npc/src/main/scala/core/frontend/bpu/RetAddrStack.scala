package core.frontend.bpu

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

object RetAddrStack {
  val size = 32
  class WriteBackIO(implicit p: Parameters) extends CoreBundle {
    val pushDst = VAddr()
    val btbType = BtbType()
  }

  class RecoverIO()(implicit p: Parameters) extends CoreBundle {
    val stack = Vec(size, VAddr())
    val ptr   = UInt(log2Ceil(size).W)
  }
}

class RetAddrStack(spec: Boolean, size: Int)(implicit p: Parameters) extends CoreModule {
  import RetAddrStack._
  val io = IO(new Bundle {
    val topData   = Output(VAddr())
    val recover   = Flipped(Valid(new RecoverIO()))
    val writeback = Flipped(Valid(new WriteBackIO()))
  })
  require(isPow2(size))
  val maxTop = (size - 1).U
  val stack  = RegInit(VecInit.fill(size)(0.U(VAddrBits.W)))
  val ptr    = RegInit(0.U(log2Ceil(size).W))

  val updatePtr = ptr
  io.topData := stack(ptr - 1.U)
  if (spec) {
    when(io.recover.valid) {
      stack      := io.recover.bits.stack
      ptr        := io.recover.bits.ptr
      io.topData := io.recover.bits.stack(io.recover.bits.ptr - 1.U)
      updatePtr  := io.recover.bits.ptr
    }
    // TODO: fix Difftest
    if (verilator) {
      // val diffSpecRAS = Module(new DifftestSpecRAS)
      // asg(diffSpecRAS.io.clock, clock)
      // asg(diffSpecRAS.io.en, io.pop || io.push.valid || recoverValid)
      // asg(diffSpecRAS.io.pushData, io.push.bits)
      // asg(diffSpecRAS.io.push, io.push.valid)
      // asg(diffSpecRAS.io.pop, io.pop)
      // asg(diffSpecRAS.io.topData, io.topData)
      // asg(diffSpecRAS.io.flush, recoverValid)
    }
  }
  when(io.writeback.bits.btbType === BtbType.push) {
    ptr              := updatePtr + 1.U
    stack(updatePtr) := io.writeback.bits.pushDst
  }.elsewhen(io.writeback.bits.btbType === BtbType.pop) {
    ptr := updatePtr - 1.U
  }.elsewhen(io.writeback.bits.btbType === BtbType.both) {
    stack(ptr - 1.U) := io.writeback.bits.pushDst
  }
}
