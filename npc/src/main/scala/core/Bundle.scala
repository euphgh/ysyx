package core

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import core.cache._
import macros.decode._
import utility._

/*==================== BASIC BUNDLE ====================*/
class BasicExInfoBundle(implicit p: Parameters) extends CoreBundle {
  val pc   = Output(UInt(VAddrBits.W))
  val isBd = Output(Bool())
}

class ExCommitBundle(implicit p: Parameters) extends CoreBundle {
  val basic    = new BasicExInfoBundle()
  val excptVec = ExceptionVec()
  val badVaddr = Output(UInt(VAddrBits.W))
}

//bpu info for per inst
class PredictResultBundle(implicit p: Parameters) extends CoreBundle {
  val counter = UInt(2.W)
  val btbType = BtbType()
  val target  = UInt(VAddrBits.W)
  val taken   = Bool()
}

class BasicInstInfoBundle(implicit p: Parameters) extends CoreBundle {
  val instr = Output(UInt(instrWidth.W))
  val pcVal = Output(UInt(VAddrBits.W))
}

class WPrfBundle(implicit p: Parameters) extends CoreBundle {
  val pDest  = PRegIdx()
  val result = UWord()
}

class DebugHW(implicit p: Parameters) extends CoreBundle {
  val inner = if (EnableDebugHW) Some(new DebugHW.Inner) else None

  def fromIBuffer(that: InstBufferOutIO) = {
    if (inner.isDefined) {
      inner.get.pc    := that.basicInstInfo.pcVal
      inner.get.instr := that.basicInstInfo.instr
    }
  }

  def set(that: DebugHW): DebugHW = {
    if (inner.isDefined && that.inner.isDefined) {
      inner.get := that.inner.get
    }
    this
  }

  def set(that: DebugHW.Inner): DebugHW = {
    if (inner.isDefined) {
      inner.get := that
    }
    this
  }

  def set(name: String, value: Data): DebugHW = {
    if (inner.isDefined) {
      inner.get.elements(name) := value
    }
    this
  }

  def set(elements: Map[String, Data]): DebugHW = {
    elements.foreach {
      case (name, data) =>
        set(name, data)
    }
    this
  }

  def set(pc: UInt = DontCare.asUInt, instr: UInt = DontCare.asUInt)(implicit p: Parameters): DebugHW = {
    set("pc", pc)
    set("instr", instr)
    this
  }
}

object DebugHW {
  class Inner(implicit p: Parameters) extends CoreBundle {
    val pc    = UInt(VAddrBits.W)
    val instr = UInt(instrWidth.W)
  }

  def apply()(implicit p: Parameters) = new DebugHW

  def apply(pc: UInt = 0.U, instr: UInt = 0.U)(implicit p: Parameters) = Wire(new DebugHW).set(pc, instr)

  def apply(elements: Map[String, Data])(implicit p: Parameters) = Wire(new DebugHW).set(elements)

  def apply(that: Inner)(implicit p: Parameters): DebugHW = Wire(DebugHW()).set(that)

  def dontCare()(implicit p: Parameters) = Wire(DebugHW()).set(DontCare.asTypeOf(new Inner))

}

class WbRobBundle(implicit p: Parameters) extends CoreBundle {
  val robIdx       = Output(UInt(robIndexWidth.W))
  val excptVec     = ExceptionVec()
  val isMispredict = Output(Bool())
  val debugHW      = DebugHW()
}

/*==================== 流水级OUT接口，不带valid-rdy ====================*/
class InstARegsIdxBundle(implicit p: Parameters) extends CoreBundle {
  val srcs = Vec(srcRegNum, ARegIdx())
  val dest = ARegIdx()
}
class InstBufferEntry(implicit p: Parameters) extends CoreBundle {
  val predictResult = new PredictResultBundle
  val realBrType    = BranchType()
  val basicInstInfo = new BasicInstInfoBundle
  val exception     = FrontExcCode()
}
class InstBufferOutIO(implicit p: Parameters) extends InstBufferEntry {
  val whichFu  = FuType()
  val aRegsIdx = new InstARegsIdxBundle
}

class SrcRegMeta(implicit p: Parameters) extends CoreBundle {
  val pIdx  = PRegIdx()
  val inPrf = Bool()
}

//rsBasicEntry < rsOutIO(each rs may has extra)
class RsBasicEntry(implicit p: Parameters) extends CoreBundle {
  val excptVec     = ExceptionVec()
  val destAregAddr = ARegIdx()
  val destPregAddr = PRegIdx()

  val robIdx  = RobPtr()
  val debugHW = DebugHW()

  val pSrcs     = Vec(srcRegNum, Output(PRegIdx()))
  val prevPDest = PRegIdx()
  val sratInPrf = Vec(srcRegNum, Bool())
  val wbInPrf   = Vec(srcRegNum, Bool())
  val grpInPrf  = Vec(srcRegNum, Bool())

  val wbInfo = Vec(wBNum, PRegIdx())
}

class RobPtr(implicit p: Parameters)
    extends CircularQueuePtr[RobPtr](p => (new CoreDelegate()(p) {}).robNum)
    with HasCircularQueuePtrHelper

object RobPtr {
  def apply()(implicit p: Parameters) = new RobPtr
  def apply(f: Bool, v: UInt)(implicit p: Parameters): RobPtr = {
    val ptr = Wire(RobPtr())
    ptr.flag  := f
    ptr.value := v
    ptr
  }
}

@DecodeMacro
class DecodeInstInfoBundle extends DCBundle {
  val src1From = Src1From()
  val src2From = Src2From()
  val aluType  = AluType()
  val memType  = MemType()
  val mduType  = MduType()
  val fuType   = FuType()
}

class CtrlFlow(implicit p: Parameters) extends CoreBundle {
  val src1From = Src1From()
  val src2From = Src2From()
  val src3From = Src3From()
  val fuOp     = FuOpType()
  val fuType   = FuType()
}

/**
  * Lsu/sAlu extra:imm
  *   sAlu:may select as src2
  *   ldst:count addr(take to mem1)
  *
  * mAlu extra:
  *   branch:dsPC,low26,predictRes
  *     predictRes take to EXE
  *     dsPc and low26 count a target,take to EXE
  *     for "AL" branch,we notice that it don't need src2
  *       so we can take the link addr in src2
  *   notice that for I-inst,it should take low16 bit of low26 as its src2
  */
class MicroOp(implicit p: Parameters) extends CtrlFlow {
  val currPDest = PRegIdx()
  val prevPDest = PRegIdx()
  val currADest = ARegIdx()
  val excptVec  = ExceptionVec()

  val robIdx = RobPtr()
  val pc     = UInt(VAddrBits.W)

  val pRegsMeta = Vec(srcRegNum, new SrcRegMeta)
  val imm       = UInt(20.W)
  val debugHW   = DebugHW()
}

class RsRealOutIO()(implicit p: Parameters) extends CoreBundle {
  // val origin = new RsOutIO
  val inPrf = Output(Vec(srcRegNum, Bool()))
}

class RobSavedUop(implicit p: Parameters) extends CoreBundle {
  val prevPDest = PRegIdx() // free when retire
  val currPDest = PRegIdx() // updata A-RAT when retire
  val currADest = ARegIdx() // updata A-RAT when retire
  val isSingle  = Bool()
}

class DestRegMeta(implicit p: Parameters) extends CoreBundle {
  val prevPDest = PRegIdx() // free when retire
  val currPDest = PRegIdx() // updata A-RAT when retire
  val currADest = ARegIdx() // updata A-RAT when retire
}

class DispatchToRobIO(implicit p: Parameters) extends CoreBundle {
  val destRegMeta = new DestRegMeta
}

class RATWriteBackIO(implicit p: Parameters) extends CoreBundle {
  val aDest = ARegIdx()
  val pDest = PRegIdx()
}

class FrontRedirct()(implicit p: Parameters) extends CoreBundle {
  val target = Output(UInt(VAddrBits.W))
}

object FrontRedirct {
  def apply(target: UInt)(implicit p: Parameters): FrontRedirct = {
    val ret = Wire(new FrontRedirct)
    ret.target := target
    ret
  }

  def apply(valid: Bool, target: UInt)(implicit p: Parameters): ValidIO[FrontRedirct] = {
    val ret = Valid(new FrontRedirct)
    ret.valid := valid
    ret.bits  := FrontRedirct(target)
    ret
  }

  // Merge by priority, first parameters has highest priority
  def merge(redirects: Valid[FrontRedirct]*)(implicit p: Parameters) = {
    val valid  = ParallelOR(redirects.map(_.valid))
    val target = ParallelPriorityMux(redirects.map(r => (r.valid, r.bits.target)))
    apply(valid, target)
  }

  def output()(implicit p: Parameters) = Valid(new FrontRedirct)

  def input()(implicit p: Parameters) = Flipped(Valid(new FrontRedirct))
}

class Redirect()(implicit p: Parameters) extends FrontRedirct {
  val robPtr = new RobPtr
}

object Redirect {
  def output()(implicit p: Parameters) = Valid(new Redirect)
  def input()(implicit p:  Parameters) = Flipped(Valid(new Redirect))
}

/**
  * to rob
  *  robIndex
  *  exception
  *  isMispredict
  *  memReqVaddr
  * to prf
  *   dest
  *   wen
  *   data(alu/mdu:cal load:load store:DontCare)
  *   wmask:only load care
  * to srat
  *   destAregAddr
  *   destPregAddr
  */
class FunctionUnitOutIO(implicit p: Parameters) extends CoreBundle {
  val wbRob     = new WbRobBundle
  val wPrf      = new WPrfBundle
  val currADest = Output(ARegIdx())
}

/**
  * exception for wbRob
  * robIndex for wbRob
  * destPregAddr is for Wprf/srat
  * destAregAddr is for srat
  *
  * srcDatas:=mux(pregData,Bypass)
  *
  * the mem is for lsu
  *   index/offset:12 bit cal
  *   memType:take from decode
  *   wWord:load dont care/store read src
  *   size:gen in Rostage
  *   wStrb:load dont care/actually store dont care at this stage
  *
  * the branch is for branch in mAlu
  *
  * special:
  *   link addr->src2
  *   imm->src2
  *   sa->src1
  *   {0.U(24.W),c0Addr}->src1
  */
class ReadOpStageOutIO()(implicit p: Parameters) extends CoreBundle {
  val robIndex     = Output(UInt(robIndexWidth.W))
  val excptVec     = ExceptionVec()
  val destPregAddr = Output(UInt(pRegAddrWidth.W))
  val destAregAddr = Output(ARegIdx())
  val prevPDest    = Output(PRegIdx())
  val prevData     = Output(UInt32)
  val debugHW      = DebugHW()
  val srcData      = Vec(2, Output(UInt(XLEN.W)))
}

//just use to instantiate exeStageIO in alu/mdu
// class ExeStageIO(fuKind: FuType.t)(implicit p: Parameters) extends CoreBundle {
//   val in  = Flipped(Decoupled(new ReadOpStageOutIO(kind = fuKind)))
//   val out = Decoupled(new FunctionUnitOutIO)
// }

class SatpStruct(implicit p: Parameters) extends CoreBundle {
  val mode = UInt(4.W)
  val asid = UInt(16.W)
  val ppn  = UInt(44.W)
}

class TlbSatpBundle(implicit p: Parameters) extends SatpStruct {
  val changed = Bool()

  def apply(satp_value: UInt): Unit = {
    require(satp_value.getWidth == XLEN)
    val sa = satp_value.asTypeOf(new SatpStruct)
    mode    := sa.mode
    asid    := sa.asid
    ppn     := Cat(0.U((44 - PAddrBits).W), sa.ppn(PAddrBits - 1, 0)).asUInt
    changed := DataChanged(sa.asid) // when ppn is changed, software need do the flush
  }
}

class TlbCsrBundle(implicit p: Parameters) extends CoreBundle {
  val satp = new TlbSatpBundle()
  val priv = new Bundle {
    val mxr = Bool()
    val sum = Bool()
  }

  override def toPrintable: Printable = {
    p"Satp mode:0x${Hexadecimal(satp.mode)} asid:0x${Hexadecimal(satp.asid)} ppn:0x${Hexadecimal(satp.ppn)} " +
      p"Priv mxr:${priv.mxr} sum:${priv.sum}"
  }
}
