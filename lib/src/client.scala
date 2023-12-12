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

  implicit protected val ec: ExecutionContext

  val apiKey: String
  val apiRoot: String = "https://api.openai.com"

  private val baseHeaders: Map[String, String] = Map(
    "Content-type" -> "application/json",
    "Authorization" -> s"Bearer $apiKey"
  )

  // the implementation should:
  //    - add the provided headers to the request
  //    - send a `POST` request to the provided URL with the `body`
  //    and return the response as an `Either` (i.e. `GPTResponse`)
  //    with an error string in left if there was a problem
  protected def sendRequestBase(url: String, headers: Map[String, String], body: String):
      Future[GPTResponse[Array[Byte]]]


  private def decodeAs[T : Decoder](json: Json): GPTResponse[T] =
    json.as[T].left.map(_.message)

  private def endpointBytes[In : Encoder](path: String): GPTEndpoint[In, Array[Byte]]  = { in =>
    sendRequestBase(apiRoot + "/" + path, baseHeaders, in.asJson.toString)
  }

  private def endpointJson[In : Encoder, Out : Decoder](path: String): GPTEndpoint[In, Out] =
    endpointBytes(path).andThen { resFuture =>
      resFuture.map { gptRes =>
        gptRes.flatMap { bytes =>
          parse(new String(bytes))
            .left
            .map(_.message)
            .flatMap { json =>
              decodeAs[Out](json)
            }
        }
      }
    }

  def completion = endpointJson[CompletionsRequest, CompletionsResponse]("v1/chat/completions")
  def speech = endpointBytes[SpeechRequest]("v1/audio/speech")
}

object GPTClient {
  type GPTResponse[T] = Either[String, T]
  type GPTEndpoint[In, Out] = In => Future[GPTResponse[Out]]
}
