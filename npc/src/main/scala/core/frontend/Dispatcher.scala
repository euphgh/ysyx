package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import chisel3.experimental.conversions._

class Decoder(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val in = new Bundle {
      val instr     = UInt32
      val exception = FrontExcCode()
    }
    val out = new CtrlFlow
  })
}

class dispatchSlot(implicit p: Parameters) extends CoreBundle {
  val inst  = new InstBufferOutIO
  val valid = Output(Bool())

  val pDestOk  = Output(Bool()) //not need pdest/related fl slot pop valid
  val robReady = Output(Bool()) //rob splot avaliable
  val rsReady  = Output(Bool()) // rs avaliable
  val readyGo  = Output(Bool())

  val sratPsrcs     = Vec(srcRegNum, new SrcRegMeta)
  val sratPrevPDest = Output(PRegIdx())
  val grpPsrcs      = Vec(srcRegNum, new SrcRegMeta)
  val grpPrevPDest  = Output(PRegIdx())
  val grpWaw        = Output(Bool())

  val toRsBasic = new RsBasicEntry
  val decoded   = new DecodeInstInfoBundle
  val prevPDest = Output(PRegIdx())
}

/**
  * 1. instantiate MultiQueue as freeList in Dispatcher
  * 2. instantiate 3 Decoder
  * 3. instantiate SRAT, code solve WAW/RAW should write in it.
  *    SRAT should listen to write prf signal to
  *    update SRATEntry' inPrf
  *
  * Dispatcher may block for blow reason
  * 1. rob not enough
  * 2. freelist not enough
  * 3. rs conflict
  * 4. cache inst, cp0 read inst<block inst>
  * 5. mispredict
  */

class Dispatcher(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val in = new Bundle {
      val fromIBuffer = Vec(renameNum, Flipped(Valid(new InstBufferOutIO)))
      val fuWbSrat    = Vec(wBNum, Flipped(Valid(new RATWriteBackIO)))
      val robIdx      = Input(RobPtr())
    }

    val fronRedirect = new FrontRedirct

    val recover = new Bundle {
      val SpecRAT  = Flipped(Valid(Vec(aRegNum, new SrcRegMeta)))
      val freeList = Flipped(Valid(UInt(12.W)))
    }

    val out = new Bundle {
      val toAluRS = Vec(renameNum, Decoupled(new MicroOp))
      val toMduRs = Vec(renameNum, Decoupled(new MicroOp))
      val toLsuRs = Vec(renameNum, Decoupled(new MicroOp))
      val toRob   = Vec(renameNum, Decoupled(new DispatchToRobIO))
    }
  })

  val decoders = List.fill(renameNum)(Module(new Decoder()))
  val freeList = Module(new FreeList)
  val srat     = Module(new SpecRAT)

  val slots = Wire(Vec(renameNum, new MicroOp))
  val fuWb  = io.in.fuWbSrat

  srat.io.recover <> io.recover.SpecRAT
  srat.io.wb <> io.in.fuWbSrat

  val fromIBuffer = io.in.fromIBuffer.map(_.bits)

  val allRS         = Seq(io.out.toAluRS, io.out.toMduRs, io.out.toLsuRs)
  val rsReady       = allRS.map(_.head.ready).asUInt.andR
  val freeListReady = freeList.io.pop.map(_.valid).asUInt.andR
  val robReady      = io.out.toRob.map(_.ready).asUInt.andR
  val allReady      = rsReady && freeListReady && robReady

  (0 until renameNum).foreach { index =>
    val fromIBuffer = io.in.fromIBuffer(index).bits
    val decoder     = decoders(index)
    val slot        = slots(index)

    decoder.io.in.instr     := fromIBuffer.basicInstInfo.instr
    decoder.io.in.exception := fromIBuffer.exception

    srat.io.src(index).zipWithIndex.foreach {
      case (rport, rindex) =>
        rport.in       := fromIBuffer.aRegsIdx.srcs(rindex)
        slot.pRegsMeta := rport.out
    }
    val freeListPopIndex = PopCount((0 until index).map { j =>
      val valid    = io.in.fromIBuffer(j).valid
      val needDest = io.in.fromIBuffer(j).bits.aRegsIdx.dest =/= 0.U
      valid && needDest
    })

    val sratDest = srat.io.dest(index)

    sratDest.currADest       := fromIBuffer.aRegsIdx.dest
    sratDest.currPDest.bits  := freeList.io.pop(freeListPopIndex).bits.pRegIdx
    sratDest.currPDest.valid := allReady
    slot.prevPDest           := sratDest.prevPDest
    slot.currPDest           := freeList.io.pop(freeListPopIndex).bits.pRegIdx
    slot.robIdx              := io.in.robIdx + (index.U)
  }

  val validIOSlots = io.in.fromIBuffer.zipWithIndex.map {
    case (fromIBuffer, index) =>
      val validIOSlot = Wire(Valid(slots(index)))
      validIOSlot.bits  := slots(index)
      validIOSlot.valid := fromIBuffer.valid
      validIOSlot
  }
  def connectRS(fuType: FuType.Type) = {
    Compress.Valid(validIOSlots, io.in.fromIBuffer(_).bits.whichFu === fuType)
  }

  io.out.toAluRS <> connectRS(FuType.alu)
  io.out.toMduRs <> connectRS(FuType.mdu)
  io.out.toLsuRs <> connectRS(FuType.lsu)

  val toRobValidIO = validIOSlots.zip(fromIBuffer).map {
    case (validIOSlot, ibuffer) =>
      val toRob = Wire(Valid(new DispatchToRobIO))
      toRob.bits.destRegMeta.currPDest := validIOSlot.bits.currPDest
      toRob.bits.destRegMeta.prevPDest := validIOSlot.bits.prevPDest
      toRob.bits.destRegMeta.currADest := ibuffer.aRegsIdx.dest
      toRob
  }
  io.out.toRob <> Compress.Valid(toRobValidIO)
}
