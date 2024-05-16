package core.backend.mem

import chisel3._
import chisel3.util._
import utils._
import utility._
import core._
import core.mmu._
import core.backend._
import org.chipsalliance.cde.config._

class WBufferLoadRespIO(implicit p: Parameters) extends MemBundle {
  val datas = Vec((XLEN / 8), UInt8())
  val hits  = Vec((XLEN / 8), Bool())
}

// only logic, req.ready === resp.valid
// when req.fire in T0, check ptr change in T1
class WBufferLoadIO(implicit p: Parameters) extends MemBundle {
  val req = Decoupled(new Bundle {
    val paddr = UInt(PAddrBits.W)
    val rmask = Vec(XLEN / 8, Bool())
  })
  val resp = Flipped(Valid(new WBufferLoadRespIO()))
}

class WBufferStoreIO(implicit p: Parameters) extends MemBundle {
  val req = Decoupled(new Bundle {
    val paddr = UInt(PAddrBits.W)
    val wmask = UInt((XLEN / 8).W)
    val datas = Vec((XLEN / 8), UInt8())
  })
  val resp = Flipped(Valid(UInt(0.W)))
}

class WBufferOrderIO(implicit p: Parameters) extends MemBundle {
  val allowLoad   = Bool()
  val oldestStore = RobPtr()
}

class WBufferWBackIO(implicit p: Parameters) extends MemBundle {}

/**
  * ready专门用一个寄存器表示，如果T0的写请求的不命中，
  * 需要替换，内部状态机切换到忙
  * 则T1允许一个请求进入，并且T2不允许请求进入
  * 直到T0的请求被替换写入后，重新按照相同逻辑处理T1的请求
  *
  * 写回请求发送给MissUnit，它负责写回
  * @param p
  */
class WBuffer(implicit p: Parameters) extends MemDelegate {
  val w     = Flipped(new WBufferStoreIO())
  val r     = Flipped(new WBufferLoadIO())
  val order = Vec(loadPipeNum + storePipeNum, new WBufferOrderIO())
  val wback = new WBufferWBackIO()
}
