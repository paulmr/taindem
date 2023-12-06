package taindem.cli

import taindem.model._

import scala.concurrent.Await
import scala.concurrent.duration._

object Cli {
  @mainargs.main def run() = {
    implicit val ec: scala.concurrent.ExecutionContext =
      scala.concurrent.ExecutionContext.global
    val apiKey = Option(System.getenv("GPT_API_KEY")).get
    val gpt = new taindem.client.GPTClientRequests(apiKey)
    val req = CompletionsRequest(Seq(
      Message(role = "system", content = "You are a friendly machine who will help me to learn french.")))
    val res = Await.result(gpt.completion(req), 20.seconds)
    println(res)
  }

  def main(args: Array[String]): Unit =
    mainargs.ParserForMethods(this).runOrExit(args.toIndexedSeq)
}
