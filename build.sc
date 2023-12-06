import mill._, scalalib._

object lib extends ScalaModule {
  def scalaVersion = "2.13.12"

  val circeVersion = "0.14.1"

  def scalacOptions = Seq("-Ymacro-annotations")

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
