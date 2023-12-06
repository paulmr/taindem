import mill._, scalalib._

trait BasicModule extends ScalaModule {
  def scalaVersion = "2.13.12"

  def scalacOptions = Seq("-deprecation")

}

object lib extends BasicModule {
  val circeVersion = "0.14.1"

  def scalacOptions = super.scalacOptions.map(_ ++ Seq("-Ymacro-annotations"))

  def ivyDeps = Agg(
    ivy"io.circe::circe-core:${circeVersion}",
    ivy"io.circe::circe-parser:${circeVersion}",
    ivy"io.circe::circe-generic:${circeVersion}",
    ivy"com.lihaoyi::requests:0.8.0" // move to a different lib prob
  )

  object test extends ScalaTests with TestModule.Utest {
    def ivyDeps = Agg(ivy"com.lihaoyi::utest:0.8.2")
  }

}

object cli extends BasicModule {
  override def moduleDeps = Seq(lib)

  def ivyDeps = Agg(ivy"com.lihaoyi::mainargs:0.5.4")
}
