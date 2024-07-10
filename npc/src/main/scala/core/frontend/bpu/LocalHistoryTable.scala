package core.frontend.bpu

import core._
import utils._
import utility._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

object LocHisTab {
  val idxWidth: Int = 4
  val clrWidth: Int = 4
  val cntLimit: Int = 14
  val hisWidth: Int = 9
  val writeFirstUpdate = true
  val writeFirstRead   = true

  val lhtTagWidth = 32 - idxWidth - 4
  val lhtTagLsb   = 32 - lhtTagWidth
  val entriesNum  = math.pow(2, idxWidth).toInt
  val takeCntNum  = math.pow(2, hisWidth).toInt

  class LhtOutIO(implicit p: Parameters) extends CoreBundle {
    val take = Bool()
    val cnt  = UInt(clrWidth.W)
  }

  def calNextCnt(cnt: UInt, take: Bool): UInt = {
    val ioWidth = cnt.getWidth
    val res = MuxCase(
      Mux(take, cnt + 1.U, cnt - 1.U),
      Seq(
        cnt.andR -> Mux(take, cnt, cnt - 1.U),
        !cnt.orR -> Mux(take, 1.U, 0.U)
      )
    )
    require(res.getWidth == ioWidth)
    res
  }
  def getTag(address: UInt) = {
    require(address.getWidth == 32)
    val res = address(31, lhtTagLsb)
    require(res.getWidth == lhtTagWidth)
    res
  }
  def getIdx(address: UInt) = {
    require(address.getWidth == 32)
    val res = address(lhtTagLsb - 1, 4)
    require(res.getWidth == idxWidth)
    res
  }
  class MemFirstW[T <: Data](
    size:      BigInt,
    t:         T,
    readPorts: Seq[Boolean] = Seq(false)
  )(
    implicit p: Parameters)
      extends CoreModule {
    val readPortsNum = readPorts.length
    val idxWidth     = log2Ceil(size)
    val readReq      = List.fill(readPortsNum)(IO(Input(UInt(idxWidth.W))))
    val readRes      = List.fill(readPortsNum)(IO(Output(t)))
    val wen          = IO(Input(Bool()))
    val wData        = IO(Input(t))
    val wAddr        = IO(Input(UInt(idxWidth.W)))
    val ram          = Mem(size, t)
    (0 until readPortsNum).foreach(i => {
      readRes(i) := ram.read(readReq(i))
    })
    when(wen) {
      ram.write(wAddr, wData)
      (0 until readPortsNum).foreach(i => {
        if (readPorts(i)) {
          when(readReq(i) === wAddr) { readRes(i) := wData }
        }
      })
    }
    def access(num: Int, idx: UInt): T = {
      require(num >= 0)
      require(num < readPortsNum)
      readReq(num) := idx
      readRes(num)
    }
    def write(enable: Bool, index: UInt, data: T) = {
      wen   := enable
      wAddr := index
      wData := data
    }
  }
}

class LocHisTab(implicit p: Parameters) extends CoreDelegate {
  import LocHisTab._
  val update = Wire(new BaseBPUWrite(Bool()))

  var readPortNum: Int = 0

  var resps = Seq.fill(fetchNum)(Valid(new LhtOutIO()))

  def req(valid: Bool, addr: UInt, instrIdx: Int): Bool = {
    readPortNum = readPortNum + 1
    require(readPortNum <= fetchNum)
    val readPC  = RegEnable(addr, valid)
    val rPCIdx  = getIdx(readPC)
    val wPCIdx  = WireInit(getIdx(writePC))
    val wPCIdxR = WireInit(getIdx(writePCr))
    // write when valid
    val clrMem   = Module(new MemFirstW(entriesNum, UInt(clrWidth.W), Seq(writeFirstRead, writeFirstUpdate)))
    val clrROut  = clrMem.access(0, rPCIdx)
    val clrWOut  = clrMem.access(1, wPCIdx)
    val clrWen   = WireInit(false.B)
    val clrWData = WireInit(0.U(clrWidth.W))
    clrMem.write(clrWen, wPCIdxR, clrWData)

    val tagsMem   = Module(new MemFirstW(entriesNum, UInt(lhtTagWidth.W), Seq(writeFirstRead, writeFirstUpdate)))
    val tagsROut  = tagsMem.access(0, rPCIdx)
    val tagsWOut  = tagsMem.access(1, wPCIdx)
    val tagsWen   = WireInit(false.B)
    val tagsWData = WireInit(0.U(lhtTagWidth.W))
    tagsMem.write(tagsWen, wPCIdxR, tagsWData)

    val fastCntMem   = Module(new MemFirstW(entriesNum, UInt(2.W), Seq(writeFirstRead, writeFirstUpdate)))
    val fastCntROut  = fastCntMem.access(0, rPCIdx)
    val fastCntWOut  = fastCntMem.access(1, wPCIdx)
    val fastCntWen   = WireInit(false.B)
    val fastCntWData = WireInit(1.U(2.W))
    fastCntMem.write(fastCntWen, wPCIdxR, fastCntWData)

    val hisAndCnts = Module(new MemFirstW(entriesNum, UInt((hisWidth + takeCntNum * 2).W), Seq(writeFirstUpdate)))
    val hAcWOut    = hisAndCnts.access(0, wPCIdx)
    val hisWOut    = hAcWOut(hAcWOut.getWidth - 1, hAcWOut.getWidth - hisWidth)
    val cntWOut    = Wire(Vec(takeCntNum, UInt(2.W)))
    (0 until takeCntNum).foreach(i => { cntWOut(i) := hAcWOut(i * 2 + 1, i * 2) })
    val hisWData = WireInit(0.U(hisWidth.W))
    val cntWData = WireInit(VecInit.fill(takeCntNum)(1.U(2.W)))
    val hAcWen   = WireInit(false.B)
    val hAcWDate = Cat(hisWData, cntWData.asUInt)
    hisAndCnts.write(hAcWen, wPCIdxR, hAcWDate)

    val (resetState, resetSet)   = (WireInit(false.B), WireInit(0.U))
    val _resetState              = RegInit(true.B)
    val (_resetSet, resetFinish) = Counter(_resetState, entriesNum)
    when(resetFinish) { _resetState := false.B }
    when(resetState) {
      hAcWen     := true.B
      fastCntWen := true.B
      tagsWen    := true.B
      clrWen     := true.B
      wPCIdxR    := resetSet
    }
    resetState := _resetState
    resetSet   := _resetSet

    // Write =============================================================
    val realTake  = update.data(instrIdx).bits
    val realTakeR = RegNext(realTake)

    val write2 = RegNext(update.data(instrIdx).valid && update.instrOff(instrIdx)(1, 0) === instrIdx.U)

    val nextHisW = Cat(hisWOut(hisWidth - 2, 0), realTake)
    val wCntW    = calNextCnt(fastCntWOut, realTake)

    dontTouch(writePC)
    val tagMatchR       = RegNext(tagsWOut === getTag(writePC))
    val cntZeroR        = RegNext(clrWOut === 0.U)
    val clrWDataPlus1R  = RegNext(calNextCnt(clrWOut, true.B))
    val clrWDataMinus1R = RegNext(calNextCnt(clrWOut, false.B))
    val nextHisR        = RegNext(nextHisW)
    val wCntR           = RegNext(wCntW)
    val nextTakeCntsR = RegNext {
      val newCntData = WireInit(cntWOut)
      newCntData(hisWOut) := wCntW
      newCntData
    }
    val nextFastCntR = RegNext(Mux(nextHisW === hisWOut, wCntW, cntWOut(nextHisW)))
    when(write2) {
      when(tagMatchR) {
        clrWData := clrWDataPlus1R
        clrWen   := true.B

        asg(tagsWData, getTag(writePCr))
        tagsWen := true.B

        hisWData := nextHisR
        cntWData := nextTakeCntsR
        hAcWen   := true.B

        fastCntWData := nextFastCntR
        fastCntWen   := true.B
      }.elsewhen(cntZeroR) {
        tagsWData := getTag(writePCr)
        tagsWen   := true.B

        asg(fastCntWData, Mux(realTakeR, 1.U(2.W), 0.U(2.W)))
        fastCntWen := true.B

        asg(cntWData, VecInit.fill(takeCntNum)(1.U(2.W)))
        asg(cntWData(0), Mux(realTakeR, 2.U(2.W), 0.U(2.W)))
        asg(hisWData, 0.U(hisWidth.W) | realTakeR)
        hAcWen := true.B
      }.otherwise {
        clrWData := clrWDataMinus1R
        clrWen   := true.B
      }
    }
    // Read ==============================================================
    val resp = resps(instrIdx)
    resp.valid     := RegNext(valid)
    resp.bits.take := false.B
    resp.bits.cnt  := 0.U(clrWidth.W)
    when(tagsROut === getTag(readPC)) {
      resp.bits.take := fastCntROut > 1.U
      resp.bits.cnt  := clrROut
    }
    true.B
  }

  val readAddr = List.fill(fetchNum)(IO(Flipped(Valid(VAddr()))))
  val readRes  = List.fill(fetchNum)(IO(Output(new LhtOutIO)))

  val writePC = Wire(VAddr())
  asg(writePC, Cat(update.tagIdx, update.instrOff(0), 0.U(2.W)))
  val writePCr = Reg(VAddr()); writePCr := writePC

  if (verilator) {
    // TODO: fix Difftest
    // val diffLht = Module(new DifftestLHTRead)
    // diffLht.io.clock := clock
    // diffLht.io.en    := readAddr(0).valid
    // asg(diffLht.io.outOK, RegNext(readAddr(0).valid))
    // (0 until fetchNum).foreach { i =>
    //   asg(diffLht.io.readAddr(i), readAddr(i).bits)
    //   asg(diffLht.io.readCnt(i), readRes(i).cnt)
    //   asg(diffLht.io.readTake(i), readRes(i).take)
    // }
  }
}
