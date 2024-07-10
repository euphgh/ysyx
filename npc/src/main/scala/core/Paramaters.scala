package core

import org.chipsalliance.cde.config._
import utility._
import chisel3._
import chisel3.util._
import core.cache._
import utils._
import freechips.rocketchip.rocket.DCacheParams

case object SoCParamsKey extends Field[SoCParameters]

case class SoCParameters(
  EnableILA: Boolean = false,
  PAddrBits: Int     = 36,
  extIntrs:  Int     = 64)

trait HasSoCParameter {
  implicit val p: Parameters

  val soc       = p(SoCParamsKey)
  val debugOpts = p(DebugOptionsKey)

  val EnableILA = soc.EnableILA

  val NrExtIntr = soc.extIntrs
}

case object CoreParamsKey extends Field[CoreParams]

case class CoreParams(
  basicBpuIdxWidth: Int         = 6,
  retAddrStackSize: Int         = 8,
  storeQSize:       Int         = 8,
  tlbEntriesNum:    Int         = 4,
  enableBCache:     Boolean     = true,
  XLEN:             Int         = 64,
  HasMExtension:    Boolean     = false,
  HasCExtension:    Boolean     = false,
  HasHExtension:    Boolean     = false,
  AddrBits:         Boolean     = false,
  iCacheParams:     CacheParams = CacheParams(userData = BranchType()),
  dCacheParams:     CacheParams = CacheParams(),
  wBufferParams:    CacheParams = CacheParams(nBytes = 32, hasIndex = false))

case class CacheParams(
  val nWays:    Int     = 4,
  val nBytes:   Int     = 64,
  val hasIndex: Boolean = true,
  val userData: Data    = UInt(0.W))

case object CacheParamsKey extends Field[CacheParams]

case class ICacheParams(
  nWays:    Int     = 4,
  nBytes:   Int     = 64,
  hasIndex: Boolean = true,
  userData: Data    = BranchType())

trait CacheHelper extends HasMyParams {
  val cacheParam = cores.dCacheParams

  val nWays      = cacheParam.nWays
  val nBytes     = cacheParam.nBytes
  val indexWidth = if (cacheParam.hasIndex) (12 - log2Ceil(nBytes)) else 0

  val nWords      = nBytes / XBYTE
  val offsetWidth = log2Ceil(nBytes)
  val nSets       = math.pow(2, indexWidth).toInt
  val pTagWidth   = PAddrBits - offsetWidth - indexWidth
  val vTagWidth   = VAddrBits - offsetWidth - indexWidth
  def getTag(addr:    UInt) = addr(addr.getWidth - 1, offsetWidth + indexWidth)
  def getIndex(addr:  UInt) = addr(indexWidth + offsetWidth - 1, offsetWidth)
  def getOffset(addr: UInt) = addr(offsetWidth - 1, 0)
  def getXLEN(addr:   UInt) = addr(offsetWidth - 1, log2Ceil(XBYTE))
}

case object DebugOptionsKey extends Field[DebugOptions]

case class DebugOptions(
  FPGAPlatform:    Boolean = false,
  EnableDifftest:  Boolean = false,
  AlwaysBasicDiff: Boolean = true,
  EnableDebug:     Boolean = false,
  EnablePerfDebug: Boolean = true)

trait HasMyParams {
  implicit val p: Parameters
  val cores = p(CoreParamsKey)
  val XLEN  = cores.XLEN
  val XBYTE = XLEN / 8

  val EnableDebugHW = p(DebugOptionsKey).EnableDebug
  // configurable:
  val iCacheBlkBytes   = cores.iCacheParams.nBytes
  val basicBpuIdxWidth = 6
  val HasHExtension    = false

  val loadPipeNum  = 2
  val storePipeNum = 2

  val frontPtwReqNum = 1
  val storePtwReqNum = storePipeNum
  val loadPtwReqNum  = loadPipeNum
  val ptwReqNum      = loadPtwReqNum + storePtwReqNum + frontPtwReqNum

  val NRPhyRegs = 64

  val IcachRoads       = 2
  val retAddrStackSize = 8
  val storeQSize       = 8
  val tlbEntriesNum    = 4
  val enableBCache     = true

  // General Parameter for mycpu
  val excCodeWidth = 5
  val PAddrBits    = 32
  val VAddrBits    = 39
  def VAddr()      = UInt(VAddrBits.W)
  def PAddr()      = UInt(PAddrBits.W)
  require(iCacheBlkBytes == 64 || iCacheBlkBytes == 32)
  val IcacheOffsetWidth = log2Ceil(iCacheBlkBytes)
  val instrWidth        = 32
  val dataWidth         = XLEN
  val immWidth          = 16
  def getOffsetI(word: UInt) = word(IcacheOffsetWidth - 1, 0)
  def isDataId(id:     UInt) = id(0)
  def isInstrId(id:    UInt) = id(0) =/= false.B
  def isDCacheId(id:   UInt) = id === 1.U
  def isUartBufId(id:  UInt) = id === 3.U
  val maxInstrLen   = 4
  val minInstrLen   = 2
  val instrPt       = log2Ceil(maxInstrLen)
  val fetchPt       = log2Ceil(maxInstrLen * fetchNum)
  val iCacheBlkPt   = log2Ceil(cores.iCacheParams.nBytes)
  val instrOffWidth = iCacheBlkPt - instrPt
  // 在一个ICache块中，取fetchNum条指令之后，下一个取指地址
  // 如果当前的地址位于一个ICache块后部分，不足以取fetchNum条指令，则直接是下一个ICache块的地址
  // VAddrBits             |iCacheBlkPt|    |fetchPt|    |instrPt|    |    |
  // 31 ...                |6          |5   |4      |3   |2      |1   |0   |
  //                                    0    0       0    0 -> 0 1 0 0
  //                                    0    0       0    1 -> 0 1 0 1
  //                                    0    0       1    0 -> 0 1 1 0
  //                                    0    0       1    1 -> 0 1 1 1
  //                                    0    1       0    0 -> 1 0 0 0
  //                                    0    1       0    1 -> 1 0 0 1
  //                                    0    1       1    0 -> 1 0 1 0
  //                                    0    1       1    1 -> 1 0 1 1
  //                                    1    0       0    0 -> 1 1 0 0
  //                                    1    0       0    1 -> 1 1 0 1
  //                                    1    0       1    0 -> 1 1 1 0
  //                                    1    0       1    1 -> 1 1 1 1
  //                                    1    1       0    0 -> overflow
  //                                    1    1       0    1 -> overflow
  //                                    1    1       1    0 -> overflow
  //                                    1    1       1    1 -> overflow

  def getAlignPC(pc: UInt) = {
    require(pc.getWidth == VAddrBits)
    val ifTag = pc(VAddrBits - 1, fetchPt)
    Mux(
      pc(iCacheBlkPt - 1, instrPt) > ((iCacheBlkBytes / maxInstrLen) - fetchNum).U,
      Cat(ifTag + 1.U, 0.U(fetchPt.W)),
      Cat(ifTag + 1.U, pc(fetchPt - 1, 0))
    )
  }

  val predictNum = 4
  val fetchNum   = 4
  val renameNum  = 3
  val wBNum      = aluNum + 1 + loadPipeNum + storePipeNum
  val issueNum   = 4 //should be 4
  val srcRegNum  = 2
  val srcDataNum = 3
  val retireNum  = 3 //should be 4

  val aRegNum       = 32
  val aRegAddrWidth = log2Up(aRegNum)
  val pRegNum       = 64
  val pRegAddrWidth = log2Up(pRegNum)
  val robNum        = 32
  val robIndexWidth = log2Up(robNum)
  val freeListSize  = 32

  val aluNum = 3

  object ARegIdx {
    def apply() = UInt(aRegAddrWidth.W)
  }
  object PRegIdx {
    def apply() = UInt(pRegAddrWidth.W)
    def apply(data: Int) = data.U(pRegAddrWidth.W)
  }

  val tlbIndexWidth = log2Ceil(tlbEntriesNum)
  def TLBIdx        = UInt(tlbIndexWidth.W)

  val prfReadPortNum = srcRegNum * issueNum

  val aluFuNum     = 2
  val aluRsInPorts = 2

  val aluBypassNum = 2

  val csrAddrWidth = 12
  def CSRIdx       = UInt(csrAddrWidth.W)

  val verilator = true

  object UWord extends UIntLen {
    override val len = XLEN
  }
}
