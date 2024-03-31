package core.frontend

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class RATWriteBackIO(implicit p: Parameters) extends CoreBundle {
  val aDest = ARegIdx
  val pDest = PRegIdx
}

class MispreSignal(implicit p: Parameters) extends CoreBundle {
  val happen     = Output(Bool())
  val realTarget = Output(UWord)
  val robIdx     = Output(ROBIdx)
}

/**
  * inst just read from srat
  *   WB and GRP write srat
  *   inst take related info
  *     WB->inprf
  *     GRP->pdest|inprf|prevpdest
  *       TODO:for now,not take the prevPdest and pDest to Rs
  *             choose the right prevPdest and pDest
  */
class SpecRAT(implicit p: Parameters) extends CoreModule {
  val io = IO(new Bundle {
    val src = Vec(
      renameNum,
      Vec(
        srcDataNum,
        new Bundle {
          val in  = Input(ARegIdx) //to get srcs p
          val out = Output(new SrcRegMeta) //srcs p
        }
      )
    )
    val dest = Vec(
      renameNum,
      new Bundle {
        val currPDest = Flipped(Valid(PRegIdx)) //to write in
        val currADest = Input(ARegIdx) //to get prev
        val prevPDest = Output(PRegIdx) //prev
      }
    )
    val wb      = Vec(wBNum, Flipped(Valid(new RATWriteBackIO)))
    val recover = Flipped(Valid(Vec(aRegNum, new SrcRegMeta)))
  })

  val pIdxMap = RegInit(VecInit((0 until aRegNum).map(i => i.U(pRegAddrWidth.W))))
  val inPrf   = RegInit(VecInit(Seq.fill(aRegNum)(true.B)))

  //read from srat
  //num0 areg will get preg=0,inprf=1
  (0 until renameNum).foreach(i => {
    (0 until srcDataNum).foreach(j => {
      io.src(i)(j).out.inPrf := inPrf(io.src(i)(j).in)
      io.src(i)(j).out.pIdx  := pIdxMap(io.src(i)(j).in)
    })
    io.dest(i).prevPDest := pIdxMap(io.dest(i).currADest)
  })

  //wb change inprf:low priority
  (0 until wBNum).foreach(i =>
    when(io.wb(i).valid && (pIdxMap(io.wb(i).bits.aDest) === io.wb(i).bits.pDest)) {
      inPrf(io.wb(i).bits.aDest) := true.B

      // bypass write back
      (0 until renameNum).foreach { renameIdx =>
        (0 until srcDataNum).foreach { srcsNumIdx =>
          when(io.wb(i).bits.aDest === io.src(renameIdx)(srcsNumIdx).in) {
            io.src(renameIdx)(srcsNumIdx).out.inPrf := true.B
          }
        }
      }
    }
  )

  //dest change inprf:high priority
  //io.dest.currPDest.valid = dper.slot.out.fire
  //if io.dest.currADest===0 ,means !wen
  //WAW(only last pDest write in)
  (0 until renameNum).foreach(i => {
    when(io.dest(i).currPDest.valid & io.dest(i).currADest =/= 0.U) {
      pIdxMap(io.dest(i).currADest) := io.dest(i).currPDest.bits
      inPrf(io.dest(i).currADest)   := false.B
      // bypass write back
      (i until renameNum).foreach { renameIdx =>
        (0 until srcDataNum).foreach { srcsNumIdx =>
          when(io.dest(i).currADest === io.src(renameIdx)(srcsNumIdx).in) {
            io.src(renameIdx)(srcsNumIdx).out.pIdx  := io.dest(i).currPDest.bits
            io.src(renameIdx)(srcsNumIdx).out.inPrf := false.B
          }
        }
        when(io.dest(i).currADest === io.dest(renameIdx).currADest) {
          io.dest(renameIdx).prevPDest := io.dest(i).currPDest.bits
        }
      }
    }
  })
  when(io.recover.valid) {
    (0 until aRegNum).foreach(i => {
      pIdxMap(i) := io.recover.bits(i).pIdx
      inPrf(i)   := io.recover.bits(i).inPrf
    })
  }
}
