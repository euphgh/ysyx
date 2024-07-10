package core.frontend.bpu

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

trait BCacheHelp { this: CoreModule =>
  // configurable:
  val bCacheidxWidth   = 7
  val bCacheMemUseSRAM = false
  class BCacheWIO(implicit p: Parameters) extends CoreBundle {
    val pc  = VAddr()
    val dst = VAddr()
  }
  def lowWidth       = log2Ceil(fetchNum)
  val bCacheTagWidth = VAddrBits - bCacheidxWidth - lowWidth
  val entriesyNum    = math.pow(2, bCacheidxWidth).toInt
  def cleanMask      = Cat(Fill(bCacheTagWidth, false.B), Fill(lowWidth + bCacheidxWidth, true.B))
  // can be change for better design
  def hash(address: UInt) = address(bCacheidxWidth + lowWidth - 1, lowWidth)
  def getTag(address: UInt) = {
    require(address.getWidth == VAddrBits)
    val res = address(31, bCacheidxWidth + lowWidth)
    require(res.getWidth == bCacheTagWidth)
    res
  }
}

class BCache(implicit p: Parameters) extends CoreModule with BCacheHelp {
  val io = IO(new Bundle {
    val readAddr = Flipped(Valid(VAddr()))
    val readRes  = Valid(VAddr())
    val write    = Flipped(Valid(new BCacheWIO))
  })
  val ramWidth = bCacheTagWidth + VAddrBits
  val ram = Module(
    DualPortsSRAM(
      gen         = UInt(ramWidth.W),
      set         = entriesyNum,
      useSRAM     = bCacheMemUseSRAM,
      shouldReset = true,
      holdRead    = false,
      singlePort  = false,
      writefirst  = true
    )
  )
  val res  = ram.io.r(io.readAddr.valid, hash(io.readAddr.bits))
  val rTag = res.resp.data(VAddrBits + bCacheTagWidth - 1, VAddrBits)
  val rDst = res.resp.data(31, 0)
  if (enableBCache) {
    io.readRes.valid := rTag === getTag(RegEnable(io.readAddr.bits, io.readAddr.valid))
    io.readRes.bits  := rDst
  } else {
    io.readRes.valid := false.B
    io.readRes.bits  := DontCare
  }

  val wPC   = io.write.bits.pc
  val wDst  = io.write.bits.dst
  val wData = Cat(getTag(wPC), wDst)
  ram.io.w(io.write.valid, wData, hash(wPC))
}
