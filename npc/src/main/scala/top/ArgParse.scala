package top

import org.chipsalliance.cde.config._
import core._

import scala.annotation.tailrec
import scala.sys.exit

object ArgParser {
  // TODO: add more explainations
  val usage =
    """
      |XiangShan Options
      |--xs-help                  print this help message
      |--config <ConfigClassName>
      |--num-cores <Int>
      |--with-dramsim3
      |--fpga-platform
      |--enable-difftest
      |--enable-log
      |--with-chiseldb
      |--with-rollingdb
      |--disable-perf
      |""".stripMargin

  def getConfigByName(confString: String): Parameters = {
    var prefix = "top." // default package is 'top'
    if (confString.contains('.')) { // already a full name
      prefix = ""
    }
    val c = Class.forName(prefix + confString).getConstructor(Integer.TYPE)
    c.newInstance(1.asInstanceOf[Object]).asInstanceOf[Parameters]
  }
  def parse(args: Array[String]): (Parameters, Array[String], Array[String]) = {
    val default     = new DefaultConfig(1)
    var firrtlOpts  = Array[String]()
    var firtoolOpts = Array[String]()
    @tailrec
    def nextOption(config: Parameters, list: List[String]): Parameters = {
      list match {
        case Nil => config
        case "--xs-help" :: tail =>
          println(usage)
          if (tail == Nil) exit(0)
          nextOption(config, tail)
        case "--config" :: confString :: tail =>
          nextOption(getConfigByName(confString), tail)
        case "--with-dramsim3" :: tail =>
          nextOption(
            config.alter((site, here, up) => {
              case DebugOptionsKey => up(DebugOptionsKey).copy(UseDRAMSim = true)
            }),
            tail
          )
        case "--fpga-platform" :: tail =>
          nextOption(
            config.alter((site, here, up) => {
              case DebugOptionsKey => up(DebugOptionsKey).copy(FPGAPlatform = true)
            }),
            tail
          )
        case "--enable-difftest" :: tail =>
          nextOption(
            config.alter((site, here, up) => {
              case DebugOptionsKey => up(DebugOptionsKey).copy(EnableDifftest = true)
            }),
            tail
          )
        case "--enable-log" :: tail =>
          nextOption(
            config.alter((site, here, up) => {
              case DebugOptionsKey => up(DebugOptionsKey).copy(EnableDebug = true)
            }),
            tail
          )
        case "--disable-perf" :: tail =>
          nextOption(
            config.alter((site, here, up) => {
              case DebugOptionsKey => up(DebugOptionsKey).copy(EnablePerfDebug = false)
            }),
            tail
          )
        case "--firtool-opt" :: option :: tail =>
          firtoolOpts ++= option.split(" ").filter(_.nonEmpty)
          nextOption(config, tail)
        case option :: tail =>
          // unknown option, maybe a firrtl option, skip
          firrtlOpts :+= option
          nextOption(config, tail)
      }
    }
    var config = nextOption(default, args.toList)
    (config, firrtlOpts, firtoolOpts)
  }
}