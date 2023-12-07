package taindem.model

import utest._
import io.circe.parser._
import io.circe.syntax._
import taindem.client.GPTClient
import scala.concurrent.Future

object JsonTests extends TestSuite {
  import taindem.TestUtils._

  def tests = Tests {
    test("example request") {
      assert(parse(exampleResponse("foo")).flatMap(_.as[CompletionsResponse]).isRight)
    }

    test("example response") {
      assert(parse(exampleRequest).flatMap(_.as[CompletionsRequest]).isRight)
    }

  }

}
