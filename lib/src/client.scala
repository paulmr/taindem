package taindem.client

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import taindem.model._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import GPTClient._

trait GPTClient {

  implicit val ec: ExecutionContext

  val apiKey: String
  val apiRoot: String

  private val baseHeaders: Map[String, String] = Map(
    "Content-type" -> "application/json",
    "Authorization" -> s"Bearer $apiKey"
  )

  // the implementation should:
  //    - add the provided headers to the request
  //    - sent a `POST` request to the provided URL with the `body`
  //    and return the response as an `Either` (i.e. `GPTResponse`)
  //    with an error string in left if there was a problem
  protected def sendRequestBase(url: String, headers: Map[String, String], body: String):
      Future[GPTResponse[String]]

  protected def sendRequest(url: String, body: Json): Future[GPTResponse[Json]] =
    sendRequestBase(url, baseHeaders, body.toString).map { res =>
      res.flatMap(src => parse(src).left.map(_.message))
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

object GPTClient {
  type GPTResponse[T] = Either[String, T]
  type GPTEndpoint[In, Out] = In => Future[GPTResponse[Out]]
}
