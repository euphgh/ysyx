package utils
import chisel3._
import chisel3.util._

abstract class VecMem[T <: Data](val t: T, val length: Int) {
  def read(idx:  UInt): T
  def write(idx: UInt, data: T): Unit
  def write(idx: UInt, data: UInt): Unit = write(idx, data.asTypeOf(t))
}

object VecMem {
  class MemImpl[T <: Data](t: T, length: Int) extends VecMem(t, length) {
    val mem = Mem(length, t)
    override def read(idx:  UInt) = mem.read(idx)
    override def write(idx: UInt, data: T) = mem.write(idx, data)
  }

  class RegImpl[T <: Data](t: T, length: Int) extends VecMem(t, length) {
    val reg = Reg(Vec(length, t))
    override def read(idx:  UInt) = reg(idx)
    override def write(idx: UInt, data: T) = reg(idx) := data

    val foo = Cat(Seq(12.U))
  }

  def apply[T <: Data](length: Int, t: T, regVec: Boolean = false) = {
    if (regVec) new RegImpl(t, length)
    else new MemImpl(t, length)
  }
}
