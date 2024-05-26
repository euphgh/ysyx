package core.backend.mem

import core._
import core.mmu._
import utils._
import utility._
import chisel3._
import chisel3.util._
import core.backend._
import org.chipsalliance.cde.config._
import chisel3.experimental.conversions._

class MulPortSRAM[T <: Data](private val gen: T, val set: Int, val way: Int = 1, nRead: Int = 1)(implicit p: Parameters)
    extends MemDelegate {
  val r = Vec(nRead, Flipped(new SRAMReadBus(gen, set, way)))
  val w = Flipped(new SRAMWriteBus(gen, set, way))

  val nBank = math.pow(2, log2Down(nRead)).toInt
  assert(set % nBank == 0)
  val srams = Seq.fill(nBank)(new SRAMTemplate(gen, set / nBank, way, singlePort = true))
  val arbs  = Seq.fill(nBank)(new Arbiter(new SRAMBundleA(set / nBank), nRead)) //TODO: use random arbiter

  val bankIdxWidth = log2Ceil(nBank)
  def getBankID(setIdx:  UInt) = setIdx(bankIdxWidth - 1, 0)
  def getBankIdx(setIdx: UInt) = setIdx(setIdx.getWidth - 1, bankIdxWidth)

  // =================================================
  // ============ from r.req to SRAM Bank ============
  // =================================================
  val rReadys = (0 until nBank).map { bankID =>
    val bankVec = r.map(r => getBankID(r.req.bits.setIdx) === bankID.U)
    val IdxVec  = r.map(r => getBankIdx(r.req.bits.setIdx))
    val bankIdx = Mux1H(bankVec, IdxVec)

    val bankReqs = r.indices.map { rID =>
      val ret = Decoupled(new SRAMBundleA(set / nBank))
      ret.valid       := r(rID).req.valid && bankVec(rID)
      ret.bits.setIdx := getBankIdx(r(rID).req.bits.setIdx)
      ret
    }
    arbs(bankID).io.in <> bankReqs
    arbs(bankID).io.out <> srams(bankID).io.r.req
    bankReqs.map(_.ready)
  }
  r.zipWithIndex.foreach {
    case (r, rID) =>
      r.req.ready := rReadys.map(_(rID)).asUInt.orR
  }

  // =================================================
  // ============ from SRAM Bank to r.resp ===========
  // =================================================
  val chosens = arbs.map(a => UIntToOH(a.io.chosen))
  val bankToROH = r.indices.map { rID =>
    val oh = chosens.map(_(rID))
    assert(PopCount(oh) <= 1.U)
    oh
  }
  r.zipWithIndex.foreach {
    case (r, rID) =>
      r.resp <> Mux1H(bankToROH(rID), srams.map(_.io.r.resp))
  }

  // ================================================
  // =========== w.req to SRAM Bank =================
  // ================================================
  val wBankID = getBankID(w.req.bits.setIdx)
  srams.zipWithIndex.foreach {
    case (sram, bankID) =>
      sram.io.w.req.valid := wBankID === bankID.U && w.req.valid
      sram.io.w.req.bits  := w.req.bits
  }
  w.req.ready := Mux1H(UIntToOH(wBankID), srams.map(_.io.w.req.ready))
}

class DCacheMetaArray(val set: Int, val way: Int = 1, nRead: Int = 1)(implicit p: Parameters) extends MemDelegate {
  val r = Vec(nRead, Flipped(new SRAMReadBus(new DCacheMeta(), set, way)))
  val w = Flipped(new SRAMWriteBus(new DCacheMeta(), set, way))

  val valids = VecInit.fill(way)(VecInit.fill(set)(RegInit(false.B)))
}

/**
  * 刷新由流水线完成
  * @param p
  */
class DCacheMain(implicit p: Parameters) extends MemDelegate {
  import DCacheHelper._
  val r      = Vec(loadPipeNum, Flipped(new DCacheRIO()))
  val refill = Flipped(new RefillIO())

  val datas = new MulPortSRAM(Vec(nBytes, UInt8()).asUInt, nSets, nWays, loadPipeNum)
  val metas = new DCacheMetaArray(nSets, nWays, loadPipeNum)

  // ====================================================
  // ============ Read Port Connect =====================
  // ====================================================
  def connectR(rio: DCacheRIO, rID: Int) = {
    datas.r(rID).req.valid       := rio.req.valid
    datas.r(rID).req.bits.setIdx := rio.req.bits
    rio.req.ready                := datas.r(rID).req.ready

    rio.resp.zipWithIndex.map {
      case (resp, wayID) =>
        resp.data := datas.r(rID).resp.data(wayID)
    }
  }

  val rio = Vec(loadPipeNum, new DCacheRIO())
  r.zipWithIndex.foreach { case (r, rID) => connectR(r, rID) }

  when(refill.busy) {
    r.map(_.req.ready := false.B)
    connectR(refill.r, 0)
  }

  // ====================================================
  // =========== Write Port Connect =====================
  // ====================================================
  val wReq  = refill.w.bits
  val wData = VecInit.fill(nWays)(wReq.wdata.data)
  val wMeta = VecInit.fill(nWays)(wReq.wdata.meta)
  datas.w.apply(refill.w.valid, wData, wReq.index, wReq.wWay)
  metas.w.apply(refill.w.valid, wMeta, wReq.index, wReq.wWay)
}
