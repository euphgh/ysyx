package core.backend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core.backend.funcunit.ExceptionRaiseIO
import core.frontend.FreeList
import core.ExceptionVec

class RobEntry(implicit p: Parameters) extends DispatchToRobIO {
  val isMispredict = Bool()
  val isNoBrMis    = Bool()
  val done         = Bool()
  val debugHW      = DebugHW()

  def enqInit(that: DispatchToRobIO) = RobEntry.enqInit(this, that)
}

object RobEntry {
  def enqInit(self: RobEntry, that: DispatchToRobIO) = {
    self := 0.U.asTypeOf(self)
    Connect.byName(self, that)
  }
}

class RobIO(implicit p: Parameters) extends CoreBundle {
  val in = new Bundle {
    val dispatcher = Vec(renameNum, Flipped(Decoupled(new DispatchToRobIO)))
    val writeback  = Vec(wBNum, Flipped(Valid(new WbRobIO)))
  }
  val out = new Bundle {
    val enqRobPtr = Output(RobPtr()) //to dper
    val oldestPtr = Output(RobPtr()) //for block inst
    //retire port
    val retires = Vec(
      retireNum,
      Valid(new Bundle {
        val toARAT  = new RATWriteBackIO
        val isStore = Output(Bool())
        val debugHW = DebugHW()
        val robPtr  = RobPtr()
      })
    )
    // when send exception. rob should flush itself
    val exception = Valid(new ExceptionRaiseIO())
    val redirect  = Redirect.output()

    val freeListRc = Valid(FreeListPtr())
  }
}
class Rob(implicit p: Parameters) extends CoreModule {
  val io = IO(new RobIO)

  /* 测试某条指令是否需要单独退休 */
  def isSingleRetire(entry: RobEntry): Bool = ???

  /* 两种退休模式
   * 1. Instr(0) ... Instr(i-1)在T0退休，Instr(i)在T1退休，T2重定向后续操作
   * 2. Instr(0) ... Instr(i)在T0退休，  T2重定向后续操作
   */
  val queue = new BaseMultiPortBuffer(renameNum, retireNum, robNum, new RobEntry) {
    val deqDones = deqPtrVec.map(ptr => buffer(ptr.value).done)
    override val numDeq: UInt = PopCount(deqDones)
  }

  queue.io.in.zip(io.in.dispatcher).map {
    case (enq, disp) => Connect.decoupled(enq, disp, RobEntry.enqInit)
  }
}
