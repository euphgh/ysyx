import chisel3._
import chisel3.util._
package object utils {
  def AssertWhen(timeCond: => Bool, assertCond: => Bool) = {
    assert((timeCond && assertCond) || !timeCond)
  }
}
