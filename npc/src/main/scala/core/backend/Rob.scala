package core.backend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class SingleRetireBundle(implicit p: Parameters) extends CoreBundle {
  val muldiv = Output(Bool())
  val mtlo   = Output(Bool())
  val mthi   = Output(Bool())
  val mtc0   = Output(Bool()) //to CP0
}

class RobEntry(implicit p: Parameters) extends CoreBundle {
  val uOp = new RobSavedUop
  val exception = new Bundle {
    val basic    = new BasicExInfoBundle
    val excptVec = ExceptionVec()
  }
  val isMispredict = Bool()
  val isNoBrMis    = Bool()
  val isFirPreTake = Bool()
  val done         = Bool()
  val debugHW      = DebugHW()
}

class Rob(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val in = new Bundle {
      val fromDispatcher  = Vec(renameNum, Flipped(Decoupled(new DispatchToRobIO)))
      val wbRob           = Vec(wBNum, Flipped(Valid(new WbRobBundle)))
      val misPredictIdx   = Input(RobPtr())
      val fromAluIsMisPre = Input(Bool())
    }
    val out = new Bundle {
      val robIndex  = Output(RobPtr()) //to dper
      val dsAllow   = Output(Bool()) //to dper
      val oldestIdx = Output(RobPtr()) //for block inst
      //retire port
      val singleRetire = Valid(new SingleRetireBundle)
      val multiRetire = Vec(
        retireNum,
        Valid(new Bundle {
          val toArat  = new RATWriteBackIO
          val scommit = Output(Bool())
          val debugHW = DebugHW()
        })
      )
      //to CP0
      val eretFlush = Output(Bool())
      val exCommit  = Valid(new ExCommitBundle)
      //recover|flush|redirect
      val flRecover          = Vec(retireNum, Valid(PRegIdx())) // FreeList recover Ports
      val mispreFlushBackend = Output(Bool()) //mispredict only FlushBackend
      val flushAll           = Output(Bool()) //serve as recover rat and hilo
      val robRedirect        = Output(new FrontRedirct) //serve as recover rat and hilo
    }
  })

}
