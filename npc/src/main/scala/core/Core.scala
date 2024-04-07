package core

import chisel3._
import org.chipsalliance.cde.config._
import macros.decode._
import core.frontend._

abstract class CoreBundle(implicit val p: Parameters) extends Bundle with HasMyParams
abstract class CoreModule(implicit val p: Parameters) extends Module with HasMyParams
class CoreScope(implicit val p: Parameters) extends HasMyParams

class Core()(implicit p: Parameters) extends CoreModule {
    
}