package taindem

import model._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import taindem.client.GPTClient.GPTResponse
import _root_.io.circe.Json
import _root_.io.circe.parser.parse

case class Taindem(
  gpt: client.GPTClient,
  model: String = "gpt-3.5-turbo-1106",
  history: Seq[Message] = Taindem.startPrompt("French")) {
  def submitMessage(msgText: String)(implicit ec: ExecutionContext): Future[GPTResponse[(TaindemAnswer, Taindem)]] = {
    val userMsg = Message("user", msgText)
    val req = CompletionsRequest(model = model, messages = history :+ userMsg,
      response_format = Some(ResponseFormat("json_object")))
    gpt.completion(req).map { res =>
      res.flatMap { completions =>
        val baseMessage = completions.choices.head.message
        parse(baseMessage.content)
          .flatMap(_.as[TaindemAnswer])
          .left.map(_.getMessage())
          .orElse[String, TaindemAnswer](
            Right(TaindemAnswer(correction = "<no json>", answer = baseMessage.content)))
          .map { responseMsg =>
            responseMsg -> copy(history = history :+ userMsg :+ baseMessage)
          }
      }
    }
  }
}

object Taindem {
  def startPrompt(language: String) = Seq(
    Message("system",
      s"""You are going to speak to me entirely in ${language}. We are going
         |to have a converstation in order to practice my language
         |skills. Before you answer, please correct each thing that I
         |say so that it sounds natural and like the sort of thing a
         |native speaker of ${language} would say, however,
         |maintaining the level of formality or informality that I
         |have provided in my initial message. Answer in the form of a
         |Json object, which contains two fields. The field
         |"correction" should contain the corrected the version of my
         |sentence. The field "answer" should contain your normal
         |response.
         """.stripMargin
    )
  )
}
