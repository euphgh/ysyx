package core.backend

import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import chisel3.experimental.conversions._

import core._
import core.mmu._
import core.dram._
import core.frontend._
import core.backend.mem._
import core.backend.funcunit._

class Backend(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val front = Flipped(new FrontBackIO())
    val dmem  = new DramIO()
  })
  // Dispatcher Connect with RS and Rob
  val dispatcher = Module(new Dispatcher()).io
  val archRAT    = Module(new ArchRAT()).io
  dispatcher.in.fromIBuffer <> io.front.instructions

  val aluRS = RS(16, aluNum)
  val mduRS = RS(8, 1)
  val lsuRS = RS(16, 3, ooo = false)

  dispatcher.out.toAluRS <> aluRS.in.fromDispatcher
  dispatcher.out.toMduRs <> mduRS.in.fromDispatcher
  dispatcher.out.toLsuRs <> lsuRS.in.fromDispatcher

  val csr = Module(new CSRFile()).io
  val rob = Module(new Rob()).io
  val prf = new Prf()
  csr.exception <> rob.out.exception
  dispatcher.out.toRob <> rob.in.dispatcher
  dispatcher.recover.freeList <> rob.out.freeListRc
  dispatcher.in.enqRobPtr <> rob.out.enqRobPtr
  val redirect = Redirect.merge(csr.redirect, rob.out.redirect)

  // Fuction Unit Connect With RS
  val alus = Seq.fill(aluNum)(Module(new Alu()).io)
  alus.zipWithIndex.map {
    case (fu, index) =>
      PipelineConnect(aluRS.out(index), fu.in, fu.roOutFire, redirect.valid)
  }
  val mdu = Module(new Mdu()).io
  PipelineConnect(mduRS.out.head, mdu.in, mdu.roOutFire, redirect.valid)
  val loadPipes = Seq.fill(loadPipeNum)(Module(new LoadPipe()).io)
  loadPipes.zipWithIndex.map {
    case (fu, index) =>
      PipelineConnect(lsuRS.out(index), fu.in, fu.roOutFire, redirect.valid)
  }
  val storePipe = Module(new StorePipe()).io
  PipelineConnect(lsuRS.out.last, storePipe.in, storePipe.roOutFire, redirect.valid)

  // connect mdu and csr
  mdu.csr <> csr.instr

  // MemBlock Connect
  val wbuffer = Module(new WBuffer()).io
  wbuffer.enq <> dispatcher.out.toLsuRs
  wbuffer.redirect <> redirect
  val missUnit = Module(new MissUnit()).io
  val dcache   = Module(new DCacheMain()).io
  val ptw      = Module(new PTW()).io
  wbuffer.wback <> missUnit.wbuffer
  dcache.refill <> missUnit.refill
  loadPipes.zipWithIndex.foreach {
    case (fu, index) =>
      fu.dcache <> dcache.r(index)
      fu.loadMiss <> missUnit.misses(index)
      fu.oldestRobPtr <> rob.out.oldestPtr
      fu.wbuffer.r <> wbuffer.r(index)
      fu.wbuffer.order <> wbuffer.orders(index)
      missUnit.hits(index) <> fu.hit
      fu.byPass := missUnit.byPass
      fu.tlb.ptw <> ptw.loadPtwReq(index)
  }
  storePipe.loadMiss <> missUnit.misses.last
  storePipe.oldestRobPtr <> rob.out.oldestPtr
  storePipe.wbuffer.w <> wbuffer.w
  storePipe.wbuffer.order <> wbuffer.orders.last
  storePipe.tlb.ptw <> ptw.storePtwReq.head

  val allFus = loadPipes ++ alus :+ mdu :+ storePipe

  /* read regfile from prf */
  allFus.foreach { fu =>
    fu.fromPrf.zip(fu.in.bits.pRegsMeta).foreach {
      case (dest, srcMeta) =>
        dest := prf.read(srcMeta.pIdx)
    }
  }

  /* fu writeback to rob */
  rob.in.writeback.zipWithIndex.map {
    case (wb, index) =>
      wb.valid := allFus(index).out.valid
      wb.bits  := allFus(index).out.bits.wbRob
  }

  /* rob retire write prf and ArchRAT */
  rob.out.retires.zip(archRAT.retire).foreach {
    case (src, dest) =>
      dest.valid := src.valid
      dest.bits  := src.bits.toARAT
  }
  rob.out.retires.zipWithIndex.foreach {
    case (retire, index) =>
      archRAT.retire(index).valid := retire.valid
      archRAT.retire(index).bits  := retire.bits.toARAT

      wbuffer.retires(index).valid := retire.valid && retire.bits.isStore
      wbuffer.retires(index).bits  := retire.bits.robPtr
  }
}
