import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule
import $file.difftest.build
import $file.`rocket-chip`.common
import $file.`rocket-chip`.cde.common
import $file.`rocket-chip`.hardfloat.build

val defaultScalaVersion = "2.13.10"

def defaultVersions(chiselVersion: String) = chiselVersion match {
  case "chisel" =>
    Map(
      "chisel" -> ivy"org.chipsalliance::chisel:6.0.0-RC1",
      "chisel-plugin" -> ivy"org.chipsalliance:::chisel-plugin:6.0.0-RC1",
      "chiseltest" -> ivy"edu.berkeley.cs::chiseltest:5.0.2"
    )
  case "chisel3" =>
    Map(
      "chisel" -> ivy"edu.berkeley.cs::chisel3:3.6.0",
      "chisel-plugin" -> ivy"edu.berkeley.cs:::chisel3-plugin:3.6.0",
      "chiseltest" -> ivy"edu.berkeley.cs::chiseltest:0.6.2"
    )
}

trait HasChisel extends SbtModule with Cross.Module[String] {
  def chiselModule: Option[ScalaModule] = None

  def chiselPluginJar: T[Option[PathRef]] = None

  def chiselIvy: Option[Dep] = Some(defaultVersions(crossValue)("chisel"))

  def chiselPluginIvy: Option[Dep] = Some(defaultVersions(crossValue)("chisel-plugin"))

  override def scalaVersion = defaultScalaVersion

  override def scalacOptions = super.scalacOptions() ++
    Agg("-language:reflectiveCalls", "-Ymacro-annotations", "-Ytasty-reader")

  override def ivyDeps = super.ivyDeps() ++ Agg(chiselIvy.get)

  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(chiselPluginIvy.get)
}

object difftest extends Cross[Difftest]("chisel", "chisel3")
trait Difftest extends HasChisel {
  override def millSourcePath = os.pwd / "difftest"
}

object rocketchip extends Cross[RocketChip]("chisel", "chisel3")

trait RocketChip extends millbuild.`rocket-chip`.common.RocketChipModule with HasChisel {
  def scalaVersion: T[String] = T(defaultScalaVersion)

  override def millSourcePath = os.pwd / "rocket-chip"

  def macrosModule = macros

  def hardfloatModule = hardfloat(crossValue)

  def cdeModule = cde

  def mainargsIvy = ivy"com.lihaoyi::mainargs:0.5.4"

  def json4sJacksonIvy = ivy"org.json4s::json4s-jackson:4.0.6"

  object macros extends Macros

  trait Macros extends millbuild.`rocket-chip`.common.MacrosModule with SbtModule {

    def scalaVersion: T[String] = T(defaultScalaVersion)

    def scalaReflectIvy = ivy"org.scala-lang:scala-reflect:${defaultScalaVersion}"
  }

  object hardfloat extends Cross[Hardfloat](crossValue)

  trait Hardfloat extends millbuild.`rocket-chip`.hardfloat.common.HardfloatModule with HasChisel {

    def scalaVersion: T[String] = T(defaultScalaVersion)

    override def millSourcePath = os.pwd / "rocket-chip" / "hardfloat" / "hardfloat"

  }

  object cde extends CDE

  trait CDE extends millbuild.`rocket-chip`.cde.common.CDEModule with ScalaModule {

    def scalaVersion: T[String] = T(defaultScalaVersion)

    override def millSourcePath = os.pwd / "rocket-chip" / "cde" / "cde"
  }
}

object utility extends Cross[Utility]("chisel", "chisel3")
trait Utility extends HasChisel {

  override def millSourcePath = os.pwd / "utility"

  override def moduleDeps = super.moduleDeps ++ Seq(
    rocketchip(crossValue)
  )

}

object macros extends Cross[Macros]("chisel", "chisel3")
trait Macros extends HasChisel {
  override def scalacOptions = super.scalacOptions() ++ Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-Ywarn-dead-code",
    "-Ywarn-unused",
    "-Ymacro-annotations"
  )
  val scalaReflect     = ivy"org.scala-lang:scala-reflect:${scalaVersion}"
  override def ivyDeps = super.ivyDeps() ++ Agg(scalaReflect)
  object test extends SbtModuleTests with TestModule.ScalaTest {
    override def ivyDeps = super.ivyDeps() ++ Agg(
      defaultVersions(crossValue)("chiseltest")
    )
  }
}

object playground extends Cross[PlayGround]("chisel", "chisel3")
trait PlayGround extends HasChisel {

  override def millSourcePath = os.pwd

  def rocketModule = rocketchip(crossValue)

  def difftestModule = difftest(crossValue)

  def utilityModule = utility(crossValue)

  def macrosModule = macros(crossValue)

  override def forkArgs = Seq("-Xmx20G", "-Xss256m")

  override def sources = T.sources {
    super.sources() ++ Seq(PathRef(millSourcePath / "src" / crossValue / "main" / "scala"))
  }

  override def moduleDeps = super.moduleDeps ++ Seq(
    macrosModule,
    rocketModule,
    utilityModule,
    difftestModule
  )

  object test extends SbtModuleTests with TestModule.ScalaTest {
    override def forkArgs = Seq("-Xmx20G", "-Xss256m")

    override def sources = T.sources {
      super.sources() ++ Seq(PathRef(this.millSourcePath / "src" / crossValue / "test" / "scala"))
    }

    override def ivyDeps = super.ivyDeps() ++ Agg(
      defaultVersions(crossValue)("chiseltest")
    )
  }
}
