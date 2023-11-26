import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule
import $file.difftest.build

trait ChiselModule extends ScalaModule with ScalafmtModule {
  override def scalaVersion = "2.13.10"

  override def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:6.0.0-M3"
  )

  override def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:6.0.0-M3"
  )

  override def scalacOptions = Seq(
    "-Ymacro-annotations",
    "-Xfatal-warnings",
    "-feature",
    "-deprecation",
    "-language:reflectiveCalls",
    "-Xcheckinit"
  )
}

object playground extends ChiselModule {
  override def millSourcePath = os.pwd
  object test extends ScalaTests with TestModule.ScalaTest {
    override def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"edu.berkeley.cs::chiseltest:5.0.2"
    )
  }
}

object difftest extends millbuild.difftest.build.DifftestTrait with ChiselModule {
  object test extends ScalaTests with TestModule.ScalaTest {
    override def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"edu.berkeley.cs::chiseltest:5.0.2"
    )
  }
}
