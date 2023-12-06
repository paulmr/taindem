package taindem.io

import io.circe.Json
import io.circe.syntax._
import scala.concurrent.{ExecutionContext, Future}
import taindem.model._
import io.circe.{Encoder, Decoder}

import GPTClient._

trait GPTClient {

  implicit val ec: ExecutionContext

  val apiKey: String
  val apiRoot: String

  protected val baseHeaders: Map[String, String] = Map(
    "Content-type" -> "application/json",
    "Authorization" -> s"Bearer $apiKey"
  )

  protected def sendRequestBase(url: String, body: String): Future[GPTResponse[String]]
  // impl can use baseHeaders

  protected def sendRequest(url: String, body: Json): Future[GPTResponse[Json]] =
    sendRequestBase(url, body.toString).map { res =>
      res.map(_.asJson)
    }

  private def decodeAs[T : Decoder](json: Json): GPTResponse[T] =
    json.as[T].left.map(_.message)

  private def endpoint[In : Encoder, Out : Decoder](path: String): GPTEndpoint[In, Out]  = { in =>
    sendRequest(apiRoot + "/" + path, in.asJson).map { res =>
      res.flatMap { json => decodeAs[Out](json) }
    }
  }

  def completion = endpoint[CompletionsRequest, CompletionsResponse]("v1/chat/completions")

}

class GTPClientRequests(val apiKey: String, val apiRoot: String = "https://api.openai.com/v1")
  (implicit val ec: ExecutionContext) extends GPTClient {

  def sendRequestBase(url: String, body: String): Future[GPTResponse[String]] = Future {
    val res = requests.get(url, headers = baseHeaders, data = body.toString)
    if(res.statusCode == 200) Right(res.text) else Left(res.statusMessage)
  }

}

object GPTClient {
  type GPTResponse[T] = Either[String, T]
  type GPTEndpoint[In, Out] = In => Future[GPTResponse[Out]]
}
