package core

import org.chipsalliance.cde.config._
import utility._
import chisel3._
import chisel3.util._
import core.cache._

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
  IcachLineBytes:   Int          = 64,
  DcachLineBytes:   Int          = 64,
  basicBpuIdxWidth: Int          = 6,
  IcachRoads:       Int          = 2,
  DcachRoads:       Int          = 2,
  retAddrStackSize: Int          = 8,
  storeQSize:       Int          = 8,
  tlbEntriesNum:    Int          = 4,
  enableBCache:     Boolean      = true,
  XLEN:             Int          = 64,
  HasMExtension:    Boolean      = false,
  HasCExtension:    Boolean      = false,
  HasHExtension:    Boolean      = false,
  AddrBits:         Boolean      = false,
  icacheParams:     ICacheParams = ICacheParams())

case object DebugOptionsKey extends Field[DebugOptions]

case class DebugOptions(
  FPGAPlatform:     Boolean = false,
  EnableDifftest:   Boolean = false,
  AlwaysBasicDiff:  Boolean = true,
  EnableDebug:      Boolean = false,
  EnablePerfDebug:  Boolean = true,
  UseDRAMSim:       Boolean = false,
  EnableConstantin: Boolean = false,
  EnableChiselDB:   Boolean = false,
  AlwaysBasicDB:    Boolean = true,
  EnableRollingDB:  Boolean = false)

trait HasMyParams {
  implicit val p: Parameters
  val cores = p(CoreParamsKey)
  val XLEN  = cores.XLEN
  val XBYTE = XLEN / 8

  val EnableDebugHW = p(DebugOptionsKey).EnableDebug
  // configurable:
  val IcachLineBytes   = 64
  val basicBpuIdxWidth = 6
  val HasHExtension    = false

  val IcachRoads       = 2
  val retAddrStackSize = 8
  val storeQSize       = 8
  val tlbEntriesNum    = 4
  val enableBCache     = true

  // General Parameter for mycpu
  val excCodeWidth = 5
  val PAddrBits    = 32
  val VAddrBits    = 39
  val tagWidth     = VAddrBits - 12
  require(IcachLineBytes == 64 || IcachLineBytes == 32)
  val IcacheOffsetWidth = log2Ceil(IcachLineBytes)
  val instrWidth        = 32
  val dataWidth         = XLEN
  val immWidth          = 16
  def getOffsetI(word: UInt) = word(IcacheOffsetWidth - 1, 0)
  def isDataId(id:     UInt) = id(0)
  def isInstrId(id:    UInt) = id(0) =/= false.B
  def isDCacheId(id:   UInt) = id === 1.U
  def isUartBufId(id:  UInt) = id === 3.U
  val instrOffLsb   = 2
  val instrOffMsb   = log2Ceil(IcachLineBytes) - 1
  val instrOffWidth = instrOffMsb - instrOffLsb + 1
  def getAlignPC(pc: UInt) = {
    require(pc.getWidth == VAddrBits)
    val ifTag = pc(XLEN - 1, 4)
    val alignPC =
      Mux(
        pc(instrOffMsb, instrOffLsb) > ((IcachLineBytes / 4) - 4).U(instrOffWidth.W),
        Cat(ifTag + 1.U, 0.U(4.W)),
        Cat(ifTag + 1.U, pc(3, 0))
      )
    alignPC
  }

  val predictNum = 4
  val fetchNum   = 4
  val renameNum  = 3
  val wBNum      = 3
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

  def UInt8()  = UInt(8.W)
  def UInt16() = UInt(16.W)
  def UInt32() = UInt(32.W)
  def UInt64() = UInt(64.W)

  object UWord {
    def apply() = UInt(XLEN.W)
    def toUInt8s(uword: UInt) = {
      require(uword.getWidth == XLEN)
      (0 until XLEN / 8).map(i => uword((i + 1) * 8 - 1, i * 8))
    }
    def toVec(inUInt: UInt)(implicit p: Parameters): Vec[UInt] = {
      val XLEN    = p(CoreParamsKey).XLEN
      val inWidth = inUInt.getWidth
      require(inWidth % XLEN == 0)
      require(inWidth > XLEN)
      VecInit((0 until inWidth / XLEN).map(i => inUInt((i + 1) * XLEN - 1, i * XLEN)))
    }
  }
}
