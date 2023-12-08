package taindem.cli

import taindem.Taindem
import taindem.model._
import scala.concurrent.Await
import scala.concurrent.duration._

object Cli {

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  def printHistory(t: Taindem) =
    println(t.getHistory.map(msg => s"[${msg.role}]> ${msg.content}").mkString("\n"))

  def mainLoop(t: Taindem, timeout: Duration): Unit = {
    while(true) {
      val input = scala.io.StdIn.readLine("? ")
      input match {
        case ":quit" =>
          return
        case ":history" =>
          printHistory(t)
        case _ =>
          Await.result(t.submitMessage(input), timeout) match {
            case Left(err) =>
              println(s"Error: $err")
              return
            case Right(answer) =>
              answer.correction.foreach { correction =>
                println(s"${fansi.Color.Red("Correction")}: ${correction}")
              }
              println(s"${fansi.Color.Green("Answer")}: ${answer.answer}")
          }
      }
    }
  }

  @mainargs.main def run(timeout: Int = 30,
    language: String = "French",
    temperature: Option[Double] = None
  ) = {
    val apiKey = Option(System.getenv("GPT_API_KEY")).get
    val gpt = new GPTClientRequests(apiKey)
    val taindem = Taindem(gpt, temperature = temperature, language = language)
    mainLoop(taindem, timeout.seconds)
  }

  def main(args: Array[String]): Unit =
    mainargs.ParserForMethods(this).runOrExit(args.toIndexedSeq)
}
