package core.backend.funcunit

import core._
import utils._
import chisel3._
import chisel3.util._
import utility._
import core.backend._
import org.chipsalliance.cde.config._

class MulAlu(implicit p: Parameters) extends DecoupledAlu("Mul") {}
class DivAlu(implicit p: Parameters) extends DecoupledAlu("Div") {}
