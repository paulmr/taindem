package taindem
import taindem.model._
import utest._
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import io.circe.parser._

object ChatTests extends TestSuite {
  import taindem.TestUtils._

  implicit val ec: ExecutionContext = utest.framework.ExecutionContext.RunNow

  def chatTest(msg: String, exp: TaindemAnswer) = {
      val gpt = mockResponse(msg)
      val t = Taindem(gpt)
      val res = Await.result(t.submitMessage("foo"), Duration.Inf)
      assert(res.exists {
        case (ans, _) => ans == exp
      })
  }

  def tests = Tests {
    test("json response") {
      chatTest("""{ "correction": "corrected", "answer": "answer" }""",
        TaindemAnswer(correction = "corrected", answer = "answer"))
    }

    test("not json response") {
      // sometimes chat gpt doesn't actually give us json, would be nice to at least handle that gracefully
      chatTest("bad message", TaindemAnswer(correction = "<no json>", answer = "bad message"))
    }
  }

}
