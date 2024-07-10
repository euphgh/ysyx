package core.frontend.bpu

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class BtbOutIO(implicit p: Parameters) extends CoreBundle {
  val instType = BtbType()
  val target   = VAddr()
}

class PhtOutIO(implicit p: Parameters) extends CoreBundle {
  val cnt  = UInt(2.W)
  val take = Bool()
}

class BpuUpdateIO(implicit p: Parameters) extends CoreBundle {
  val pc  = Output(VAddr())
  val btb = Valid(new BtbOutIO)
  val pht = Valid(new PhtOutIO)
  val ras = Valid(new RetAddrStack.RecoverIO())
}

class BaseBPUWrite[T <: Data](val gen: T)(implicit p: Parameters) extends CoreBundle {
  val tagIdx   = Input(UInt((VAddrBits - iCacheBlkPt).W))
  val instrOff = Input(Vec(fetchNum, UInt((iCacheBlkPt - instrPt).W)))
  val data     = Flipped(Vec(fetchNum, Valid(gen)))
}

class PhtUpdateIO(implicit p: Parameters) extends BaseBPUWrite(new PhtOutIO())
class BtbUpdateIO(implicit p: Parameters) extends BaseBPUWrite(new BtbOutIO())

class BaseBPU[T <: Data](val gen: T, useRegs: Boolean = true)(implicit p: Parameters) extends CoreDelegate {
  val idxWidth: Int = basicBpuIdxWidth
  val update = Wire(new BaseBPUWrite(gen))

  val lowWidth    = fetchPt
  val bpuTagWidth = VAddrBits - idxWidth - lowWidth
  val entriesyNum = math.pow(2, idxWidth).toInt

  // can be change for better design
  def hash(address: UInt) = address(idxWidth + lowWidth - 1, lowWidth)

  def missFunc(entry: T, addr: UInt): T = entry
  def getTag(address: UInt) = {
    require(address.getWidth == VAddrBits)
    val res = address(VAddrBits - 1, idxWidth + lowWidth)
    require(res.getWidth == bpuTagWidth)
    res
  }

  val ramWidth = gen.getWidth + bpuTagWidth

  var resps = Seq.fill(fetchNum)(Valid(gen))

  def resp(instrIdx: Int): Valid[T] = resps(instrIdx)
  def req(valid: Bool, addr: UInt, instrIdx: Int): Bool = {
    val ram = Module(
      DualPortsSRAM(
        gen         = UInt(ramWidth.W),
        set         = entriesyNum,
        useSRAM     = false,
        shouldReset = true,
        holdRead    = false,
        singlePort  = false,
        writefirst  = true
      )
    )
    // write ========================================
    val updatePC = Cat(update.tagIdx, update.instrOff(instrIdx), 0.U(2.W))
    val wen      = update.data(instrIdx).valid
    when(wen) { assert(updatePC(lowWidth - 1, 2) === instrIdx.U) }
    ram.io.w(wen, Cat(update.data(instrIdx).bits.asUInt, getTag(updatePC)), hash(updatePC))
    // read ========================================
    val readOut = ram.io.r(valid, hash(addr)).resp.data
    val entry   = readOut(ramWidth - 1, bpuTagWidth).asTypeOf(gen)

    val tagHit = Wire(Bool())
    val tag    = Wire(UInt(bpuTagWidth.W))

    require(entry.getWidth == gen.getWidth)
    require(tag.getWidth == bpuTagWidth)

    val lastAddr = RegEnable(addr, valid)

    tag := readOut(bpuTagWidth - 1, 0)
    asg(tagHit, tag === getTag(lastAddr))
    val resp = resps(instrIdx)
    resp.valid := RegNext(valid)
    when(tagHit) {
      resp.bits := entry
    }.otherwise {
      resp.bits := missFunc(entry, lastAddr)
    }
    true.B
  }
}

/**
  * single write port and single read port
  * when write and read to same addr in a cycle
  * read data := write data
  * front update: Branch, J(jmp), JAL(jcall)
  * back  update: JR(jret and jr), JALR(jcall)
  * out should keep out until posedge that in.search.valid is true
  */
class BranchTargetBuffer(implicit p: Parameters) extends BaseBPU(new BtbOutIO()) {
  override val idxWidth: Int = basicBpuIdxWidth
  override def missFunc(entry: BtbOutIO, addr: UInt): BtbOutIO = {
    val out = Wire(new BtbOutIO)
    out.target   := addr + 4.U // calculate next instr address
    out.instType := BtbType.none
    out
  }
}

/**
  * only update branch instr at backend
  */
class PatternHistoryTable(implicit p: Parameters) extends BaseBPU(new PhtOutIO()) {
  override val idxWidth: Int = basicBpuIdxWidth
  override def missFunc(entry: PhtOutIO, addr: UInt): PhtOutIO = {
    val out = Wire(new PhtOutIO())
    out.cnt  := 1.U
    out.take := false.B
    out
  }
}

object PatternHistoryTable {
  def calNextCnt(cnt: UInt, take: Bool): UInt = {
    require(cnt.getWidth == 2)
    LookupUInt(
      cnt,
      Seq(
        0.U -> Mux(take, 1.U, 0.U),
        1.U -> Mux(take, 2.U, 0.U),
        2.U -> Mux(take, 3.U, 1.U),
        3.U -> Mux(take, 3.U, 2.U)
      )
    )
  }
}
