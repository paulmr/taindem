package taindem.bot

import taindem.Taindem
import sttp.client3.HttpClientSyncBackend
import taindem.client.GPTClient
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import scalaj.http._
import scala.concurrent.Await

class GPTClientScalaJ(val apiKey: String)(implicit val ec: ExecutionContext) extends GPTClient {
  protected def sendRequestBase(url: String, headers: Map[String,String], body: String):
      Future[GPTClient.GPTResponse[Array[Byte]]] = Future {

    val response = Http(url)
      .method("POST")
      .headers(headers)
      .postData(body)
      .asBytes

    if(response.isSuccess) Right(response.body)
    else Left(response.statusLine)
  }
}

object TaindemBotMain {

  def main(args: Array[String]): Unit = {
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

    val botToken = Option(System.getenv("TGRAM_BOT_KEY")).get
    val gptApiKey = Option(System.getenv("GPT_API_KEY")).get
    val gpt = new GPTClientScalaJ(gptApiKey)
    val bot = new TaindemBot(botToken, gpt)

    println("Starting bot")
    val res = bot.run()
    println("Press [ENTER] to shutdown the bot, it may take a few seconds...")
    scala.io.StdIn.readLine()
    println("Shutting down...")
    bot.shutdown()
    Await.ready(res, Duration.Inf)
    println("Complete")
  }
}
