package taindem.cli

import taindem.client._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class GPTClientRequests(val apiKey: String, val apiRoot: String = "https://api.openai.com")
  (implicit val ec: ExecutionContext) extends GPTClient {

  def sendRequestBase(url: String, headers: Map[String, String], body: String):
      Future[GPTClient.GPTResponse[String]] = Future {
    val res = requests.post(url, headers = headers, data = body.toString)
    if(res.statusCode == 200) Right(res.text()) else Left(res.statusMessage)
  }

}
