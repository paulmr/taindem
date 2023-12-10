package taindem.cli

import taindem.Taindem
import taindem.model._
import scala.concurrent.Await
import scala.concurrent.duration._
import scalalibdiff.Diff

object Cli {

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  def printHistory(t: Taindem) =
    println(t.getHistory.map(msg => s"[${msg.role}]> ${msg.content}").mkString("\n"))

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
          Await.result(t.submitMessage(input), timeout) match {
            case Left(err) =>
              println(s"Error: $err")
              return
            case Right(answer) =>
              for(correction <- answer.correction; diff <- answer.diff) {
                println(s"${fansi.Color.Red("Correction")}:\n${diffToString(diff, answer.question, correction)}")
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
