package core.backend.mem

import core._
import core.mmu._
import utils._
import utility._
import chisel3._
import chisel3.util._
import core.backend._
import org.chipsalliance.cde.config._

class CacheMetaArray(
  statuGen: UInt,
  tagWidth: Int
)(val set:  Int,
  val way:  Int = 1,
  nRead:    Int = 1
)(
  implicit p: Parameters)
    extends MemDelegate {

  val r = Vec(nRead, Flipped(new SRAMReadBus(new DCacheMeta(), set, way)))
  val w = Flipped(new SRAMWriteBus(new DCacheMeta(), set, way))

  val status = VecInit.fill(way)(VecInit.fill(set)(RegInit(0.U.asTypeOf(statuGen))))
  val metas  = new MulPortSRAM(new CacheMetaStripStatu(), set, way, nRead)

  r.zip(metas.r).zipWithIndex.map {
    case ((ior, metar), rID) =>
      metar.req <> ior.req
      val statuLocked = VecInit((0 until way).map { wayID =>
        status(wayID)(ior.req.bits.setIdx)
      })
      ior.resp.data := metar.resp.data.zip(statuLocked).map {
        case (metaStripStatu, statu) =>
          metaStripStatu.toMeta(statu).asTypeOf(new DCacheMeta)
      }
  }
  val waymask = w.req.bits.waymask.getOrElse(Fill(way, true.B))
  (0 until way).map { wayID =>
    when(w.req.fire && waymask(wayID)) {
      status(wayID)(w.req.bits.setIdx) := w.req.bits.data(wayID).statu
    }
  }
  metas.w(w.req.valid, VecInit(w.req.bits.data.map(_.stripStatu())), w.req.bits.setIdx, waymask)
}

class CacheMetaStripStatu(implicit p: Parameters) extends MemBundle {
  val tag = UInt(pTagWidth.W)

  def toMeta(statu: UInt): CacheMeta = {
    val ret = Wire(new CacheMeta(statu))
    Connect.byName(ret, this)
    ret.statu := statu
    ret
  }
}

class CacheMeta(statuGen: UInt)(implicit p: Parameters) extends CacheMetaStripStatu {

  val statu = statuGen.cloneType

  def stripStatu(): CacheMetaStripStatu = {
    val ret = new CacheMetaStripStatu()
    Connect.byName(ret, this)
    ret
  }
}

class DCacheMeta(implicit p: Parameters) extends CacheMeta(DCacheLineStatu().asUInt) {
  def dirty(that: DCacheMeta) = {
    this       := that
    this.statu := DCacheLineStatu.dirty.asUInt
    this
  }
}

// trait HasMemHelper extends HasMyParams {

//   abstract class AddrHelper {
//     val nWays       = 4
//     val nBytes      = 64
//     val nWords      = nBytes / XBYTE
//     val offsetWidth = log2Ceil(nBytes)
//     val indexWidth  = 12 - offsetWidth
//     val nSets       = math.pow(2, indexWidth).toInt
//     val pTagWidth   = PAddrBits - offsetWidth - indexWidth
//     val vTagWidth   = VAddrBits - offsetWidth - indexWidth
//     def getTag(addr:    UInt) = addr(addr.getWidth - 1, offsetWidth + indexWidth)
//     def getIndex(addr:  UInt) = addr(indexWidth + offsetWidth - 1, offsetWidth)
//     def getOffset(addr: UInt) = addr(offsetWidth - 1, 0)
//     def getXLEN(addr:   UInt) = addr(offsetWidth - 1, log2Ceil(XBYTE))
//   }
//   object DCacheHelper extends AddrHelper {
//     override val nWays       = 4
//     override val nBytes      = 64
//     override val offsetWidth = log2Ceil(nBytes)
//     override val indexWidth  = 12 - offsetWidth

//   }

//   object WBufferHelper extends AddrHelper {
//     override val nWays       = 4
//     override val nBytes      = 32
//     override val offsetWidth = log2Ceil(nBytes)
//     override val indexWidth  = 0
//   }

//   object ICacheHelper extends AddrHelper {
//     override val nWays       = 2
//     override val nBytes      = 64
//     override val offsetWidth = log2Ceil(nBytes)
//     override val indexWidth  = 12 - offsetWidth
//   }

// }

abstract class MemBundle(implicit p: Parameters) extends CoreBundle with CacheHelper
abstract class MemModule(implicit p: Parameters) extends CoreModule with CacheHelper
abstract class MemDelegate(implicit p: Parameters) extends CoreDelegate with CacheHelper

abstract class ICacheBundle(implicit p: Parameters) extends CoreBundle with CacheHelper {
  override val cacheParam = cores.iCacheParams
}
abstract class ICacheModule(implicit p: Parameters) extends CoreModule with CacheHelper {
  override val cacheParam = cores.iCacheParams
}
abstract class ICacheDelegate(implicit p: Parameters) extends CoreDelegate with CacheHelper {
  override val cacheParam = cores.iCacheParams
}

case class WBufferHelper()(implicit p: Parameters) extends CoreDelegate with CacheHelper {
  override val cacheParam = cores.wBufferParams
}

class DCacheRIO(implicit p: Parameters) extends MemBundle {
  val req  = Decoupled(UInt(indexWidth.W))
  val resp = Vec(nWays, new DCacheResp()) // valid is for write update
}

/* Refill data to DCache */
class RefillIO(implicit p: Parameters) extends MemBundle {
  val r = new DCacheRIO()
  val w = Decoupled(new Bundle {
    val index = UInt(indexWidth.W)
    val wdata = new DCacheResp()
    val wWay  = UInt(nWays.W) // way mask
  })
  val busy = Bool()
}

class DCacheHitIO(implicit p: Parameters) extends MemBundle {
  val setIdx = Output(UInt(indexWidth.W))
  val way    = Valid(UInt(nWays.W))
}

class RefillByPass(implicit p: Parameters) extends MemBundle {
  val paddr = UInt(PAddrBits.W) // align for cache line
  val datas = Vec(nBytes / XBYTE, UWord())
}

// MMIO的load和Store + Store的不命中均可以使用
class MemMissIO(implicit p: Parameters) extends MemBundle {
  val req = Decoupled(new Bundle {
    val paddr = UInt(PAddrBits.W)
    val write = Bool()
    val mask  = Vec(XBYTE, Bool())
    val mmio  = Bool()
    val wdata = UWord()
  })
  val resp = Flipped(Valid(UWord()))
}

object DCacheLineStatu extends ChiselEnum {
  val none, clean, dirty = Value
  def hit(statu:     UInt) = statu =/= none.asUInt
  def isDirty(statu: UInt) = statu =/= dirty.asUInt
}

class DCacheResp(implicit p: Parameters) extends MemBundle {
  val meta = new DCacheMeta()
  val data = Vec(nBytes, UInt8()).asUInt

  val debugIndex = UInt(indexWidth.W)
}
