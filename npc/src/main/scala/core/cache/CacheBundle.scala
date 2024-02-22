package core.cache

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class CacheInstBundle(implicit p: Parameters) extends CoreBundle {
  val op    = Output(CacheOp())
  val taglo = Output(UWord)
}

class CacheLowAddr(lineBytes: Int)(implicit p: Parameters) extends CoreBundle {
  require(lineBytes == 32 || lineBytes == 64)
  val cIdxWid = 12 - log2Ceil(lineBytes)
  val cOffWid = log2Ceil(lineBytes)
  val index   = Output(UInt(cIdxWid.W))
  val offset  = Output(UInt(cOffWid.W))
}

class CacheRWReq(lineBytes: Int)(implicit p: Parameters) extends CoreBundle {
  val lowAddr = new CacheLowAddr(lineBytes)
  val isWrite = Bool()
  val size    = UInt(3.W)
  val wWord   = UWord
  val wStrb   = UInt(4.W)
}

class CacheMeta(hasDirty: Boolean = false)(implicit p: Parameters) extends CoreBundle {
  val tag   = UInt(tagWidth.W)
  val dirty = if (hasDirty) Some(Bool()) else None
  val valid = Bool()
}

class CacheStage1OutIO(roads: Int, wordNum: Int, isDcache: Boolean)(implicit p: Parameters) extends CoreBundle {
  val lineBytes = wordNum * 4

  val meta       = Vec(roads, new CacheMeta(isDcache))
  val wMetasHit  = Vec(roads, Bool())
  val wDatasHit  = Vec(roads, Bool())
  val lowAddrHit = Vec(roads, Bool())
  val rawMask    = UInt(4.W)
  // ICache
  val idata     = if (!isDcache) Some(Vec(roads, Output(Vec(fetchNum, UWord)))) else None
  val iCacheReq = if (!isDcache) Some(new CacheLowAddr(lineBytes)) else None
  // DCache
  val ddata     = if (isDcache) Some(Vec(roads, Output(UWord))) else None
  val dCacheReq = if (isDcache) Some(new CacheRWReq(lineBytes)) else None
  val dataline  = if (isDcache) Some(Vec(roads, Output(Vec(wordNum, UWord)))) else None
}

class CacheStage1In(isDcache: Boolean, lineBytes: Int)(implicit p: Parameters) extends CoreBundle {
  val rwReq = if (isDcache) Some(new CacheRWReq(lineBytes)) else None
  val ifReq = if (!isDcache) Some(new CacheLowAddr(lineBytes)) else None
}

object CacheUtils {
  def selectWord(offset: UInt, datas: Vec[UInt]) = {
    val wordNum = math.pow(2, offset.getWidth).toInt / 4
    require(datas.length == wordNum)
    LookupUInt(
      offset >> 2,
      (0 until wordNum).map(j => {
        j.U -> datas(j)
      })
    )
  }
  def selectInstrGroup(offset: UInt, datas: Vec[UInt]) = {
    val wordNum = math.pow(2, offset.getWidth).toInt / 4
    LookupUInt(
      offset >> 2,
      (0 until wordNum).map(j => {
        val dataLine = datas
        j.U -> VecInit(
          dataLine((j + 0) % wordNum),
          dataLine((j + 1) % wordNum),
          dataLine((j + 2) % wordNum),
          dataLine((j + 3) % wordNum)
        )
      })
    )
  }
}
