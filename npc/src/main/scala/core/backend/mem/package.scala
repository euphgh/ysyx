package core.backend

import core._
import core.mmu._
import utils._
import utility._
import chisel3._
import chisel3.util._
import core.backend._
import org.chipsalliance.cde.config._

package object mem {
  trait HasMemHelper extends HasMyParams {

    val loadPipeNum  = 2
    val storePipeNum = 2

    abstract class AddrHelper {
      val nWays       = 4
      val nBytes      = 64
      val offsetWidth = log2Ceil(nBytes)
      val indexWidth  = 12 - offsetWidth
      def getTag(addr:    UInt) = addr(addr.getWidth - 1, offsetWidth + indexWidth)
      def getIndex(addr:  UInt) = addr(indexWidth + offsetWidth - 1, offsetWidth)
      def getOffset(addr: UInt) = addr(offsetWidth - 1, 0)
      def getXLEN(addr:   UInt) = addr(offsetWidth - 1, log2Ceil(XLEN / 8))
    }
    object DCacheHelper extends AddrHelper {
      override val nWays       = 4
      override val nBytes      = 64
      override val offsetWidth = log2Ceil(nBytes)
      override val indexWidth  = 12 - offsetWidth
    }

    object WBufferHelper extends AddrHelper {
      override val nWays       = 4
      override val nBytes      = 32
      override val offsetWidth = log2Ceil(nBytes)
      override val indexWidth  = 12 - offsetWidth
    }

  }

  abstract class MemBundle(implicit p: Parameters) extends CoreBundle with HasMemHelper
  abstract class MemModule(implicit p: Parameters) extends CoreModule with HasMemHelper
  abstract class MemDelegate(implicit p: Parameters) extends CoreDelegate with HasMemHelper

  class DCacheRIO(implicit p: Parameters) extends MemBundle {
    import DCacheHelper._
    val req  = Decoupled(UInt(indexWidth.W))
    val resp = Vec(nWays, new DCacheResp())
  }

  /* Refill data to DCache */
  class RefillIO(implicit p: Parameters) extends MemBundle {
    val r = new DCacheRIO()
    val w = Decoupled(new Bundle {
      import DCacheHelper._
      val index = UInt(indexWidth.W)
      val wdata = new DCacheResp()
      val wmask = UInt(nWays.W) // way mask
    })
  }

  // MMIO的load和Store + Store的不命中均可以使用
  class LoadMissIO(implicit p: Parameters) extends MemBundle {
    val req = Decoupled(new Bundle {
      val paddr = UInt(PAddrBits.W)
      val write = Bool()
      val mask  = Vec((XLEN / 8), Bool())
      val mmio  = Bool()
    })
    val resp = Flipped(Valid(Vec((XLEN / 8), UInt8())))
  }

  class MissUnit(implicit p: Parameters) extends MemModule {
    val io = IO(new Bundle {
      val wbuffer = Flipped(new WBufferWBackIO)
      val load    = new LoadMissIO
      val refill  = new RefillIO()
    })
  }

  object DCacheLineStatu extends ChiselEnum {
    val none, valid, dirty = Value
  }
  class DCacheMeta(implicit p: Parameters) extends MemBundle {
    import DCacheHelper._

    val tag   = UInt((PAddrBits - indexWidth - offsetWidth).W)
    val statu = DCacheLineStatu()
  }
  class DCacheResp(implicit p: Parameters) extends MemBundle {
    import DCacheHelper._

    val meta = new DCacheMeta
    val data = Vec(nBytes, UInt8()).asUInt

    val debugIndex = UInt(indexWidth.W)
  }

  /**
    * 需要自动机，确定是否在堵住期间有来自missUnit的写，如果存在，需要重读
    * @param p
    */
  class DCacheMain(implicit p: Parameters) extends CoreDelegate with HasMemHelper {
    val r      = new DCacheRIO
    val refill = Flipped(new RefillIO)
  }

}
