package taindem

import scalalibdiff.Diff
import model._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import taindem.client.GPTClient.GPTResponse
import _root_.io.circe.Json
import _root_.io.circe.parser.parse

case class Taindem(
  gpt: client.GPTClient,
  model: String = "gpt-3.5-turbo-1106",
  language: String = "French",
  temperature: Option[Double] = None) {

  private var history: List[Message] = Taindem.startPrompt(language)

  private def addMessage(m: Message) = history = history :+ m

  def reset() = history = Taindem.startPrompt(language)

  def submitMessage(msgText: String)(implicit ec: ExecutionContext): Future[GPTResponse[TaindemAnswer]] = {
    val userMsg = Message("user", msgText)
    addMessage(userMsg)
    val req = CompletionsRequest(
      model = model,
      messages = history,
      response_format = Some(ResponseFormat("json_object")),
      temperature = temperature
    )
    gpt.completion(req).map { res =>
      res.flatMap { completions =>
        val baseMessage = completions.choices.head.message
        addMessage(baseMessage)
        parse(baseMessage.content)
          .flatMap(_.as[TaindemAnswerJson])
          .map { baseAnswer =>
            val diff = for(correction <- baseAnswer.correction) yield Diff(
              msgText.split("\\s+").toSeq, correction.split("\\s+").toSeq)
            TaindemAnswer(correction = baseAnswer.correction.filter(_ != msgText), // remove if no changes
              answer = baseAnswer.answer, question = msgText, diff = diff)
          }
          .left.map(_.getMessage())
          .orElse[String, TaindemAnswer](
            Right(TaindemAnswer(correction = None, answer = baseMessage.content,
              question = msgText, diff = None)))
      }
    }
  }

  def getHistory = history.toSeq
}

object Taindem {
  def startPrompt(language: String) = List(
    Message("system",
      s"""You are going to speak to me entirely in ${language}. We are going
         |to have a converstation in order to practice my language
         |skills. Before you answer, please correct each thing that I
         |say so that it sounds natural and like the sort of thing a
         |native speaker of ${language} would say, however,
         |maintaining the level of formality or informality that I
         |have provided in my initial message. In terms of the
         |correction itself, only correct things if they are wrong or
         |would seem unatural for a native speaker of ${language}. You
         |don't need to correct slight style changes or things like
         |that. Answer in the form of a Json object, which contains
         |two fields. It should always contain the first field, which
         |is called "correction". This field will contain the
         |corrected version of my sentence. The field "answer" should
         |contain your normal response.
         """.stripMargin
    )
  )
}
