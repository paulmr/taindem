package taindem.cli

import taindem.Taindem
import taindem.model._
import scala.concurrent.Await
import scala.concurrent.duration._
import scalalibdiff.Diff
import java.io.FileOutputStream
import taindem.client.GPTClient
import sttp.client3.HttpClientFutureBackend
import taindem.model.GPTModel

object Cli {

  val logger = org.slf4j.LoggerFactory.getLogger(getClass().getName())

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  def printHistory(t: Taindem) =
    println(t.getHistory().map(msg => s"[${msg.role}]> ${msg.content}").mkString("\n"))

  def diffToString(ds: List[Diff.Difference], _l: String, _r: String): String = {
    val l = _l.split("\\s+").toSeq
    val r = _r.split("\\s+").toSeq
    val left = ds.collect {
      case Diff.Removed(from, to) => fansi.Color.Red(s"-${l.slice(from, to).mkString(" ")}-")
      case Diff.Same(from, to, _, _) => s"${l.slice(from, to).mkString(" ")}"
    }.mkString(" ")
    val right = ds.collect {
      case Diff.Added(from, to) => fansi.Color.Green(s"[${r.slice(from, to).mkString(" ")}]")
      case Diff.Same(from, to, _, _) => s"${l.slice(from, to).mkString(" ")}"
    }.mkString(" ")
    s"\t${left}\n\t${right}"
  }

  def mainLoop(t: Taindem, timeout: Duration): Unit = {
    while(true) {
      val input = scala.io.StdIn.readLine("? ")
      input match {
        case ":quit" =>
          return
        case ":history" =>
          printHistory(t)
        case ":reset" =>
          t.reset()
          println("Cleared")
        case any if any.startsWith(":") =>
          println(s"didn't understand command: ${any.drop(1)}")
        case _ =>
          Await.result(t.submitMessage(input, useAudio = false), timeout) match {
            case Left(err) =>
              println(s"Error: $err")
              return
            case Right(answer) =>
              for(correction <- answer.correction; diff <- answer.diff) {
                println(s"${fansi.Color.Red("Correction")}:\n${diffToString(diff, answer.question, correction)}")
              }
              println(s"${fansi.Color.Green("Answer")}: ${answer.answer}")
              answer.audio.foreach { bytes =>
                val fname = "answer.mp3"
                val out = new FileOutputStream(fname)
                try {
                  out.write(bytes)
                  println(s"Wrote data to: $fname")
                } finally {
                  out.close()
                }
              }
          }
      }
    }
  }

  @mainargs.main def run(timeout: Int = 30,
    language: String = "French",
    model: String = "gpt-3.5-turbo-1106", // https://platform.openai.com/docs/models
    temperature: Option[Double] = None,
  ) = {
    val apiKey = Option(System.getenv("GPT_API_KEY")).get
    val gpt = new GPTClient(apiKey, HttpClientFutureBackend())
    val chosenModel = GPTModel.nicknames.get(model).orElse(GPTModel.models.find(_.name == model)).get
    logger.info(s"model: ${chosenModel.name} ; language: ${language}")
    val taindem = Taindem(gpt, temperature = temperature, language = language, model = chosenModel)
    mainLoop(taindem, timeout.seconds)
  }

  def main(args: Array[String]): Unit =
    mainargs.ParserForMethods(this).runOrExit(args.toIndexedSeq)
}
