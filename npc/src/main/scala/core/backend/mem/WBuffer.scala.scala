package core.backend.mem

import chisel3._
import chisel3.util._
import utils._
import utility._
import core._
import core.mmu._
import core.backend._
import org.chipsalliance.cde.config._
import chisel3.experimental.conversions._
import freechips.rocketchip.tile.XLen

class WBufferLoadRespIO(implicit p: Parameters) extends MemBundle {
  val datas = Vec((XBYTE), UInt8())
  val hits  = Vec((XBYTE), Bool())
}

// only logic, req.ready === resp.valid
// when req.fire in T0, check ptr change in T1
class WBufferLoadIO(implicit p: Parameters) extends MemBundle {
  val req = Decoupled(new Bundle {
    val paddr  = UInt(PAddrBits.W)
    val rmask  = Vec(XBYTE, Bool())
    val robPtr = RobPtr()
  })
  val resp = Flipped(Valid(new Bundle {
    val datas = Vec((XBYTE), UInt8())
    val hits  = Vec((XBYTE), Bool())
  }))
}

class WBufferStoreIO(implicit p: Parameters) extends MemBundle {
  val req = Decoupled(new Bundle {
    val paddr  = UInt(PAddrBits.W)
    val wmask  = UInt((XBYTE).W)
    val datas  = Vec((XBYTE), UInt8())
    val robPtr = RobPtr()
  })
  val resp = Flipped(Valid(UInt(0.W)))
}

class WBufferOrderIO(implicit p: Parameters) extends MemBundle {
  val allowLoad   = Bool()
  val oldestStore = RobPtr()
}

class WBufferWBackIO()(implicit p: Parameters) extends MemBundle {
  import WBufferHelper._
  val req = Decoupled(new Bundle {
    val datas = Vec(nBytes, UInt8())
    val wmask = Vec(nBytes, Bool())
  })
  val resp = Flipped(Valid(UInt(0.W)))
}

/**
  * ready专门用一个寄存器表示，如果T0的写请求的不命中，
  * 需要替换，内部状态机切换到忙
  * 则T1允许一个请求进入，并且T2不允许请求进入
  * 直到T0的请求被替换写入后，重新按照相同逻辑处理T1的请求
  *
  * 写回请求发送给MissUnit，它负责写回
  * @param p
  */
class WBuffer()(implicit p: Parameters) extends MemModule {
  import WBufferHelper._
  val ptrSize = 24

  val io = IO(new Bundle {
    val w      = Vec(storePipeNum, Flipped(new WBufferStoreIO()))
    val r      = Vec(loadPipeNum, Flipped(new WBufferLoadIO()))
    val orders = Vec(loadPipeNum + storePipeNum, new WBufferOrderIO())
    val wback  = new WBufferWBackIO()
    val enq    = Vec(renameNum, Decoupled(new MicroOp()))

    val redirect = Redirect.input()
    val retires  = Vec(retireNum, Valid(RobPtr()))
  })

  object ByteStatu extends ChiselEnum {
    val none, dirty, retired = Value
  }

  class Entry extends MemBundle {
    val tag     = UInt(pTagWidth.W)
    val datas   = Vec(nWords, Vec(XBYTE, UInt8()))
    val status  = Vec(nWords, Vec(XBYTE, ByteStatu()))
    val robPtrs = Vec(nWords, Vec(XBYTE, RobPtr()))

    def datasBytesVec()  = VecInit.tabulate(nBytes) { i => datas(i / XBYTE)(i % XBYTE) }
    def statusBytesVec() = VecInit.tabulate(nBytes) { i => status(i / XBYTE)(i % XBYTE) }

  }

  val entries = Vec(nWays, new Entry())

  val idle :: wMissInit :: wBackReq :: wBackResp :: Nil = Enum(2)

  val state = RegInit(idle)

  val wReqLock = Reg(Valid(new Bundle {
    val paddr = UInt(PAddrBits.W)
    val wmask = UInt((XBYTE).W)
    val datas = Vec((XBYTE), UInt8())
    val ways  = UInt(nWays.W)
  }))

  val replacer = ReplacementPolicy.fromString("plru", nWays, 1)

  io.w.map(_.req.ready := state === idle)

  // =====================================================
  // =============== write data to cache =================
  // =====================================================
  when(io.w.head.req.fire) {
    assert(state === idle)
    val wReq   = io.w.head.req.bits
    val wPaddr = wReq.paddr
    val hitVec = entries.map(_.tag === getTag(wPaddr)).asUInt
    when(!hitVec.orR) {
      state          := wMissInit
      wReqLock.valid := true.B
      Connect.byType(wReqLock.bits, wReq)
      wReqLock.bits.ways := UIntToOH(replacer.way(1.U))
    }.otherwise {
      assert(PopCount(hitVec) === 1.U)
      replacer.access(1.U, OHToUInt(hitVec))
      entries.zipWithIndex.map {
        case (entry, index) =>
          when(hitVec(index)) {
            val wOffset = getOffset(wPaddr) >> log2Ceil(XBYTE)
            (0 until XBYTE).foreach { bIndex =>
              when(wReq.wmask(bIndex)) {
                entry.robPtrs(wOffset)(bIndex) := wReq.robPtr
                entry.status(wOffset)(bIndex)  := ByteStatu.dirty
                entry.datas(wOffset)(bIndex)   := wReq.datas(bIndex)
              }
            }
          }
      }
    }
  }

  // =====================================================
  // ==============  automata for write back =============
  // =====================================================
  // wait for replaced block, all bytes need retire
  when(state === wMissInit) {
    val retiredVec = entries(wReqLock.bits.ways).status.map(_.map(_ =/= ByteStatu.dirty).asUInt).asUInt
    val waitRetire = retiredVec.orR
    state := Mux(waitRetire, wMissInit, wBackReq)
  }.elsewhen(state === wBackReq) {
    io.wback.req.bits.datas := entries(wReqLock.bits.ways).datasBytesVec()
    io.wback.req.bits.wmask := entries(wReqLock.bits.ways).statusBytesVec()
    io.wback.req.valid      := true.B
    state                   := Mux(io.wback.req.fire, wBackReq, wBackResp)
  }.elsewhen(state === wBackResp) {
    state := Mux(io.wback.resp.fire, idle, wBackResp)
  }

  // =====================================================
  // ==========  retire change status by robPtr ==========
  // =====================================================
  entries.foreach { entry =>
    (0 until nWords).foreach { wIndex =>
      (0 until XBYTE).foreach { bIndex =>
        val retiresVec = io.retires.map(r => (entry.robPtrs(wIndex)(bIndex) === r.bits) && r.valid)
        when(retiresVec.asUInt.orR) {
          entry.status(wIndex)(bIndex) := ByteStatu.retired
        }
      }
    }
  }

  // =====================================================
  // =============== read data from cache ================
  // =====================================================
  io.r.foreach { r =>
    r.req.ready  := state === idle
    r.resp.valid := state === idle
    val wIndex   = getOffset(r.req.bits.paddr) >> log2Ceil(XBYTE)
    val hitVec   = entries.map(_.tag === getTag(r.req.bits.paddr)).asUInt
    val dirtyVec = Mux1H(hitVec, entries.map(_.status(wIndex).map(_ =/= ByteStatu.none).asUInt))
    r.resp.bits.hits  := Fill(XBYTE, hitVec.orR) & dirtyVec
    r.resp.bits.datas := Mux1H(hitVec, entries.map(_.datas(wIndex)))
  }

  val orderChecker = new MemDelegate {
    val enqs     = io.enq
    val slotSize = ptrSize
    val entryGen = Bool()

    val deqs = io.r.map { r =>
      val ret = Valid(RobPtr())
      ret.valid := r.req.fire
      ret.bits  := r.req.bits.robPtr
      ret
    } ++ io.w.map { w =>
      val ret = Valid(RobPtr())
      ret.valid := w.req.fire
      ret.bits  := w.req.bits.robPtr
      ret
    }

    def isDeq(robPtr: RobPtr): Bool = {
      val isDeqVec = deqs.map { deq =>
        deq.valid && deq.bits === robPtr
      }
      isDeqVec.asUInt.orR
    }

    // true when is store
    val entries = Vec(slotSize, entryGen)
    def isStore(index: Int)  = entries(index)
    def isStore(index: UInt) = entries(index)

    val allRobPtrs  = Vec(slotSize, RobPtr())
    val slotsValid  = RegInit(VecInit(Seq.fill(slotSize)(false.B)))
    val leftSlotNum = PopCount(slotsValid)
    // receive when has renameNum slot avaliable
    enqs.map(_.ready := leftSlotNum > renameNum.U)

    val enqSelectors = OneHotMatrix.colView(MultiPriority.oneHots(renameNum, slotsValid.map(!_)))
    val validEnq     = enqs.head.valid && leftSlotNum > renameNum.U

    when(validEnq) {
      (0 until slotSize).map { index =>
        when(enqSelectors(index).asUInt.orR) {
          slotsValid(index) := Mux1H(enqSelectors(index), enqs.map(_.valid))
          allRobPtrs(index) := Mux1H(enqSelectors(index), enqs.map(_.bits.robIdx))
        }
      }
    }

    val ageMask = allRobPtrs.map { l =>
      allRobPtrs.map { r =>
        l < r && !isDeq(r)
      }
    }

    val storeMask = allRobPtrs.zipWithIndex.map {
      case (lPtr, lIndex) =>
        (allRobPtrs).zipWithIndex.map {
          case (rPtr, rIndex) =>
            lPtr < rPtr && !isDeq(rPtr) && isStore(rIndex)
        }
    }

    // generate isLoad
    val oldestValidOneHot = ageMask.zipWithIndex.map {
      case (younger, index) =>
        val existOlder = (younger.asUInt & slotsValid.asUInt)
        slotsValid(index) && !isDeq(allRobPtrs(index)) && !existOlder.orR
    }

    // generate oldest store
    val oldestStoreOneHot = storeMask.zipWithIndex.map {
      case (younger, index) =>
        val existOlder = (younger.asUInt & slotsValid.asUInt)
        isStore(index) && slotsValid(index) && !isDeq(allRobPtrs(index)) && !existOlder.orR
    }

    val oldestStorePtr = RegNext(Mux1H(oldestStoreOneHot, allRobPtrs))
    val oldestIsStore  = RegNext(Mux1H(oldestValidOneHot, entries))

    io.orders.map(_.allowLoad   := !oldestIsStore)
    io.orders.map(_.oldestStore := oldestStorePtr)
  }

}
