import mill._, scalalib._, scalajslib._

trait BasicModule extends ScalaModule {
  def scalaVersion = "2.13.12"
  def scalacOptions = Seq(
    "-deprecation",
    "-Ymacro-annotations"
  )

}

trait BasicJSModule extends BasicModule with ScalaJSModule {
  def scalaJSVersion = "1.14.0"
}

object lib extends Module {
  val circeVersion = "0.14.1"

  trait LibModule extends BasicModule with PlatformScalaModule {

    def ivyDeps = Agg(
      ivy"io.circe::circe-core::${circeVersion}",
      ivy"io.circe::circe-parser::${circeVersion}",
      ivy"io.circe::circe-generic::${circeVersion}",
    )

    object test extends ScalaTests with TestModule.Utest {
      def ivyDeps = Agg(ivy"com.lihaoyi::utest::0.8.2")
    }

  }
  object jvm extends LibModule
  object js extends LibModule with BasicJSModule
}

object cli extends BasicModule {
  override def moduleDeps = Seq(lib.jvm)

  def ivyDeps = Agg(
    ivy"com.lihaoyi::mainargs:0.5.4",
    ivy"com.lihaoyi::fansi:0.4.0",
    ivy"com.lihaoyi::requests:0.8.0"
  )
}

object web extends BasicJSModule {
  override def moduleDeps = Seq(lib.js)

  def ivyDeps = Agg(
    ivy"com.yang-bo::html::2.0.2"
  )

  def build = T {
    val jsPath = fullLinkJS().dest.path
    os.copy(jsPath / "main.js", T.dest / "main.js")
    os.copy(jsPath / "main.js.map", T.dest / "main.js.map")
    for(dir <- resources(); f <- os.list(dir.path)) {
      os.copy.into(f, T.dest)
    }
    T.dest
  }
}
