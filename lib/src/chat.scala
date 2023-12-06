package taindem

import model._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import taindem.client.GPTClient.GPTResponse
import _root_.io.circe.Json
import _root_.io.circe.parser.parse

case class Taindem(
  gpt: client.GPTClient,
  model: String = "gpt-3.5-turbo",
  history: Seq[Message] = Taindem.startPrompt("French")) {
  def submitMessage(msgText: String)(implicit ec: ExecutionContext): Future[GPTResponse[(Message, Taindem)]] = {
    val userMsg = Message("user", msgText)
    val req = CompletionsRequest(model = model, messages = history :+ userMsg)
    gpt.completion(req).map { res =>
      res.map { completions =>
        val responseMsg = completions.choices.head.message
        responseMsg -> copy(history = history :+ userMsg :+ responseMsg)
      }
    }
  }
}

object Taindem {
  def startPrompt(language: String) = Seq(
    Message("system",
      s"""You are going to speak to me entirely in ${language}. We are going
         |to have a fun converstation in order to practice my language
         |skills. Before you answer, please correct each thing that I
         |say so that it sounds natural and like the sort of thing a
         |native speaker of ${language} would say. Insert the word
         |"Correction" and the provide the correction. After that,
         |give your normal response, prefixed with the word "Answer".
         """.stripMargin
    )
  )
}
