package macros.decode

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chisel3._
import chisel3.util._
import scala.util.Random

@DecodeMacro
class TestBundle extends DCBundle {
  val srcType = SRCType()
  val brType  = BranchType.NON
  val dstType = DSTType()
  val fuType  = ChiselFuType()
}

object PatRand {
  def apply(pat: BitPat): UInt = {
    val bitString = pat.toString().substring(7).init.reverse
    val uintVal = bitString.foldRight(BigInt(0)) {
      case (c, acc) => {
        (acc << 1) + (if (c == '?') BigInt(Random.nextInt(2)) else BigInt(c.asDigit))
      }
    }
    uintVal.U(pat.getWidth.W)
  }
}

class DecodeTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("DecodeMacro")
  it should "same with specified result" in {
    test(new Module {
      val io = IO(new Bundle {
        val in  = Input(UInt(32.W))
        val out = Output(new TestBundle)
      })
      Mips32Instr.decode(io.in, io.out)
    }) { c =>
      c.io.in.poke(PatRand(Mips32Instr.ADD))
      c.io.out.srcType.expect(SRCType.RSRT)
      c.io.out.brType.expect(BranchType.NON)
      c.io.out.dstType.expect(DSTType.toRD)
      c.io.out.fuType.expect(ChiselFuType.SubALU)
      c.clock.step()

      c.io.in.poke(PatRand(Mips32Instr.JAL))
      c.io.out.srcType.expect(SRCType.noSRC)
      c.io.out.brType.expect(BranchType.JAL)
      c.io.out.dstType.expect(DSTType.to31)
      c.io.out.fuType.expect(ChiselFuType.MainALU)
    }
  }
}
