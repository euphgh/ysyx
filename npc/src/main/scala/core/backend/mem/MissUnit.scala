package core.backend.mem

import core._
import core.mmu._
import utils._
import utility._
import chisel3._
import chisel3.util._
import core.backend._
import org.chipsalliance.cde.config._
import core.dram._
import chisel3.experimental.conversions._

class MemReadIO(implicit p: Parameters) extends MemBundle {
  import DCacheHelper._
  val req = Decoupled(new Bundle {
    val paddr = UInt(PAddrBits.W)
    val rmask = Vec(nBytes, Bool())
  })
  val resp = Flipped(Valid(new Bundle {
    val datas = Vec(nBytes, UInt8()).asUInt
  }))
}
class MemWriteIO(implicit p: Parameters) extends MemBundle {
  import DCacheHelper._
  val req = Decoupled(new Bundle {
    val paddr = UInt(PAddrBits.W)
    val datas = Vec(nBytes, UInt8()).asUInt
    val wmask = Vec(nBytes, Bool()).asUInt
  })
  val resp = Flipped(Valid(UInt(0.W)))
}

class MemProtocal(implicit p: Parameters) extends MemModule {
  import DCacheHelper._
  val write = Flipped(new MemWriteIO())
  val read  = Flipped(new MemReadIO())
  val mem   = new DramIO()
}

class MissUnit(implicit p: Parameters) extends MemModule {
  val io = IO(new Bundle {
    val busy    = Bool()
    val wbuffer = Flipped(new WBufferWBackIO)
    val misses  = Vec(3, new MemMissIO()) //must be one hot, wait until oldest
    val refill  = new RefillIO()
    val mem = new Bundle {
      val write = new MemWriteIO()
      val read  = new MemReadIO()
    }

    val hits = Vec(loadPipeNum, new DCacheHitIO())
  })

  import DCacheHelper._
  val replacer = ReplacementPolicy.fromString("setplru", nWays, nSets)
  replacer.access(io.hits.map(_.setIdx), io.hits.map(_.way))

  val wbReq = io.wbuffer.req

  val missArb = {
    val arb    = new Arbiter(io.misses.head.req.bits.cloneType, io.misses.length)
    val enable = io.misses.map(_.req.fire).asUInt.orR
    io.misses.zipWithIndex.map {
      case (load, index) =>
        arb.io.in(index) <> load
    }
    arb
  }

  val missPort  = RegEnable(missArb.io.out.bits, missArb.io.out.fire)
  val missWay   = RegEnable(missArb.io.chosen, missArb.io.out.fire)
  val missValid = RegNext(missArb.io.out.fire)

  import DCacheHelper._
  val isBusy = RegNext(io.misses.map(_.req.fire).asUInt.orR || wbReq.fire, false.B)
  io.busy := isBusy
  // load port has higher priority
  io.misses.map(_.req.ready := !isBusy)
  // wbuffer priority is lower
  wbReq.ready := !isBusy && !(io.misses.map(_.req.valid).asUInt.orR)

  // hardware interface
  val hif = new MemDelegate {

    val memWR = new MemDelegate {

      // write wdata to mem, load a Cache block from rPaddr
      private val idle :: writeReq :: writeResp :: readReq :: readResp :: Nil = Enum(5)

      private val state = RegInit(idle)

      private val wdata = Reg(new DCacheResp)
      private val paddr = Reg(UInt(PAddrBits.W))

      when(state === writeReq) {
        io.mem.write.req.valid      := true.B
        io.mem.write.req.bits.datas := wdata.data
        io.mem.write.req.bits.wmask := Fill(nBytes, true.B)
        io.mem.write.req.bits.paddr := paddr
        when(io.mem.write.req.fire) {
          state := writeResp
        }
      }
      when(state === writeResp) {
        when(io.mem.write.resp.fire) {
          state := readReq
        }
      }
      when(state === readReq) {
        io.mem.read.req.valid      := true.B
        io.mem.read.req.bits.rmask := Fill(nBytes, true.B)
        io.mem.read.req.bits.paddr := paddr
        when(io.mem.read.req.fire) {
          state := readResp
        }
      }
      val ret = Valid(new DCacheResp())
      when(state === readResp) {
        when(io.mem.read.resp.fire) {
          ret.bits.data := io.mem.read.resp.bits.datas
          ret.bits.meta := {
            val meta = new DCacheMeta
            meta.statu := DCacheLineStatu.clean
            meta.tag   := getTag(paddr)
            meta
          }
          ret.valid := true.B
          state     := idle
        }
      }
      def apply(wdata: Valid[DCacheResp], paddr: UInt) = {
        state := Mux(wdata.valid, writeReq, readReq)
        wdata := wdata.bits
        paddr := paddr
        ret
      }
    }

    val cacheR = new MemDelegate {
      val ret = Valid(io.refill.r.resp)
      ret.valid := RegNext(io.refill.r.req.fire)

      private val start = RegInit(false.B)
      private val index = Reg(UInt(indexWidth.W))

      io.refill.r.req.valid := start
      io.refill.r.req.bits  := index

      when(start) {
        when(io.refill.r.req.fire) {
          start := false.B
        }
      }

      def apply(paddr: UInt) = {
        start := true.B
        index := getIndex(paddr)
        ret
      }
    }

    val cacheW = new MemDelegate {
      val ret = Valid(UInt(0.W))
      ret.valid := RegNext(io.refill.w.fire)

      private val start = RegInit(false.B)
      private val lock = Reg(new Bundle {
        val index = Reg(UInt(indexWidth.W))
        val wdata = Reg(new DCacheResp())
        val wWay  = Reg(UInt(nWays.W))
      })

      io.refill.w.valid := start
      Connect.byType(io.refill.w.bits, lock)

      when(start) {
        when(io.refill.w.fire) {
          start := false.B
        }
      }

      def apply(wdata: DCacheResp, wWay: UInt, paddr: UInt) = {
        start      := true.B
        lock.index := getIndex(paddr)
        lock.wdata := wdata
        lock.wWay  := wWay
        ret
      }
    }
  }
  val writeback = new MemDelegate {

    val idle :: cacheRead :: hitJudge :: memWR :: writeCache :: Nil = Enum(6)

    val state = RegInit(idle)

    val wbPaddr = io.wbuffer.req.bits.paddr

    val respLock = Reg(io.refill.r.resp.cloneType)

    def start() = {
      state := cacheRead
      hif.cacheR(wbPaddr)
    }

    when(state === cacheRead) {
      when(hif.cacheR.ret.valid) {
        state    := hitJudge
        respLock := hif.cacheR.ret.bits
      }
    }

    val wbLine =
      RegEnable(Fill(DCacheHelper.nBytes / WBufferHelper.nBytes, wbReq.bits.datas.asUInt), io.wbuffer.req.fire)
    val wbMask = RegEnable(
      VecInit
        .tabulate(nBytes) { i =>
          // offset must align for WBuffer.nBytes
          val off = getOffset(wbPaddr)
          // or operate equal to off + WBuffer.nBytes-1
          val inRange = off <= i.U && i.U <= (off | (WBufferHelper.nBytes - 1).U)
          wbReq.bits.wmask(i % WBufferHelper.nBytes) && inRange
        }
        .asUInt,
      io.wbuffer.req.fire
    )
    def mergeWriteCache(load: DCacheResp, wWay: UInt) = {
      val wdata = Wire(new DCacheResp)
      wdata.data := UInt8.merge(wbMask.asUInt, wbLine, load.data)
      wdata.meta.dirty(load.meta)
      state := writeCache
      hif.cacheW(wdata, wWay, wbPaddr)
    }

    when(state === hitJudge) {

      val wbHitVec = respLock.map { resp =>
        resp.meta.tag === getTag(wbPaddr) && resp.meta.statu =/= DCacheLineStatu.none
      }.asUInt
      val wmask = VecInit.tabulate(nBytes) { i =>
        // offset must align for WBuffer.nBytes
        val off = getOffset(wbPaddr)
        // or operate equal to off + WBuffer.nBytes-1
        val inRange = off <= i.U && i.U <= (off | (WBufferHelper.nBytes - 1).U)
        wbReq.bits.wmask(i % WBufferHelper.nBytes) && inRange
      }

      // if DCache Write Hit, goto next state
      when(wbHitVec.orR) {
        val load = Mux1H(wbHitVec, respLock)
        mergeWriteCache(load, wbHitVec)
      }.otherwise {
        val wdata = Valid(new DCacheResp)
        wdata.bits.data := Mux1H(replacer.way(getIndex(wbPaddr)), respLock.map(_.data))
        wdata.valid     := true.B
        hif.memWR(wdata, wbPaddr)
        state := memWR
      }
    }

    when(state === memWR) {
      val ret = hif.memWR.ret
      when(ret.valid) {
        mergeWriteCache(ret.bits, replacer.way(getIndex(wbPaddr)))
        state := writeCache
      }
    }

    when(state === writeCache) {
      when(hif.cacheW.ret.valid) {
        state  := idle
        isBusy := false.B
      }
    }
  }

  val loadMiss = new MemDelegate {
    val idle :: cacheR :: memWR :: writeCache :: Nil = Enum(4)

    val state = RegInit(idle)
    val rWay  = Reg(UInt(nWays.W))

    def start() = {
      state := cacheR
      hif.cacheR(missPort.paddr)
    }

    when(state === cacheR) {
      val ret = hif.cacheR.ret
      when(ret.valid) {
        rWay := replacer.way(getIndex(missPort.paddr))
        val wdata = Valid(Mux1H(replacer.way(getIndex(missPort.paddr)), ret.bits))
        wdata.valid := wdata.bits.meta.statu === DCacheLineStatu.dirty
        hif.memWR(wdata, missPort.paddr)
        state := memWR
      }
    }

    when(state === memWR) {
      val ret = hif.memWR.ret
      when(ret.valid) {
        hif.cacheW(ret.bits, rWay, missPort.paddr)
        state := writeCache
      }
    }

    when(state === writeCache) {
      when(hif.cacheW.ret.valid) {
        state := idle
      }
    }
  }

  val mmio = new MemDelegate {
    val idle :: rReq :: rResp :: wReq :: wResp :: Nil = Enum(5)

    val rio = io.mem.read
    val wio = io.mem.write

    val state = RegInit(idle)
    when(state === rReq) {
      rio.req.bits.paddr := missPort.paddr
      rio.req.bits.rmask := missPort.mask
      rio.req.valid      := true.B
      when(rio.req.fire) {
        state := rResp
      }
    }
    when(state === rResp) {
      io.misses(missWay).resp.bits := UWord.toVec(io.mem.read.resp.bits.datas)(0)
      when(rio.resp.valid) {
        io.misses(missWay).resp.valid := true.B
        state                         := idle
      }
    }
    when(state === wReq) {
      wio.req.bits.paddr := missPort.paddr
      wio.req.bits.wmask := missPort.mask
      wio.req.bits.datas := VecInit.fill(nBytes / XBYTE)(missPort.wdata)
      wio.req.valid      := true.B
      when(wio.req.fire) {
        state := wResp
      }
    }
    when(state === wResp) {
      when(wio.resp.valid) {
        state := idle
      }
    }

    def read() = { state := rReq }
    def write() = { state := wReq }
  }

  when(isBusy) {
    when(wbReq.fire) {
      writeback.start()
    }
    when(missValid) {
      when(missPort.mmio) {
        when(missPort.write) {
          mmio.write()
        }.otherwise {
          mmio.read()
        }
      }.otherwise {
        loadMiss.start()
      }
    }
  }
}
