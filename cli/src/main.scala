package taindem.cli

import taindem.Taindem
import taindem.model._
import scala.concurrent.Await
import scala.concurrent.duration._
import taindem.client.GPTClientRequests

object Cli {

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  def printHistory(state: Taindem) =
    println(state.history.map(msg => s"[${msg.role}]> ${msg.content}").mkString("\n"))

  @annotation.tailrec
  def mainLoop(state: Taindem, timeout: Duration): Unit = {
    val input = scala.io.StdIn.readLine("? ")
    input match {
      case ":quit" => ()
      case ":history" =>
        printHistory(state)
        mainLoop(state, timeout)
      case _ =>
        Await.result(state.submitMessage(input), timeout) match {
          case Left(err) =>
            println(s"Error: $err")
          case Right((msg, nextState)) =>
            println(msg.content)
            mainLoop(nextState, timeout)
        }
    }
  }

  @mainargs.main def run(timeout: Int = 30) = {
    val apiKey = Option(System.getenv("GPT_API_KEY")).get
    val gpt = new GPTClientRequests(apiKey)
    val taindem = Taindem(gpt)
    mainLoop(taindem, timeout.seconds)
  }

  def main(args: Array[String]): Unit =
    mainargs.ParserForMethods(this).runOrExit(args.toIndexedSeq)
}
