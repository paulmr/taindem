package taindem.bot

import taindem.Taindem
import sttp.client3.HttpClientSyncBackend
import taindem.client.GPTClient
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import sttp.client3.HttpClientFutureBackend

import scalaj.http._
import scala.concurrent.Await
import com.typesafe.scalalogging.StrictLogging
import mainargs.Flag
import mainargs.arg

// class GPTClientScalaJ(val apiKey: String)(implicit val ec: ExecutionContext) extends GPTClient {
//   protected def sendRequestBase(url: String, headers: Map[String,String], body: String):
//       Future[GPTClient.GPTResponse[Array[Byte]]] = Future {

//     val response = Http(url)
//       .method("POST")
//       .headers(headers)
//       .postData(body)
//       .asBytes

//     if(response.isSuccess) Right(response.body)
//     else Left(response.statusLine)
//   }
// }

object TaindemBotMain extends StrictLogging {

  @mainargs.main
  def run(audioDefault: Boolean = false, @arg(short='i') interactive: Flag): Unit = {
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

    val botToken = Option(System.getenv("TGRAM_BOT_KEY")).get
    val gptApiKey = Option(System.getenv("GPT_API_KEY")).get
    implicit val httpBackend = HttpClientFutureBackend()
    val gpt = new GPTClient(gptApiKey, httpBackend)
    val bot = new TaindemBot(botToken, gpt, audioEnabledByDefault = audioDefault)

    logger.info("Starting bot")
    val res = bot.run()
    if(interactive.value) {
      println("Interactive: press [ENTER] to shutdown the bot, it may take a few seconds...")
      scala.io.StdIn.readLine()
      logger.info("Shutting down...")
      bot.shutdown()
    }
    Await.ready(res, Duration.Inf)
    logger.info("Complete")
  }

  def main(args: Array[String]): Unit =
    mainargs.ParserForMethods(this).runOrExit(args.toIndexedSeq)
}
