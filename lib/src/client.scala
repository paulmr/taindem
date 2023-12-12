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
import sttp.client3._
import sttp.model.StatusCode

class GPTClient(val apiKey: String, sttpBackend: SttpBackend[Future, Any])(implicit ec: ExecutionContext) {

  val apiRoot: String = "https://api.openai.com"

  private val baseHeaders: Map[String, String] = Map(
    "Authorization" -> s"Bearer $apiKey"
  )

  // the implementation should:
  //    - add the provided headers to the request
  //    - send a `POST` request to the provided URL with the `body`
  //    and return the response as an `Either` (i.e. `GPTResponse`)
  //    with an error string in left if there was a problem
  protected def sendRequestBase(url: String, headers: Map[String, String], body: Array[Byte]):
      Future[GPTResponse[Array[Byte]]] =
    basicRequest
      .headers(headers)
      .body(body)
      .post(uri"$url")
      .response(asByteArray)
      .send(sttpBackend)
      .map(_.body)

  private def decodeAs[T : Decoder](json: Json): GPTResponse[T] =
    json.as[T].left.map(_.message)

  private def endpoint[In](path: String)(f: In => GPTRequest => GPTRequest): GPTEndpoint[In, Array[Byte]] = { in =>
    val uri = uri"${apiRoot + "/" + path}"
    val req = basicRequest
      .response(asByteArray)
      .headers(baseHeaders)
    val req2 = f(in)(req)
    println(s"uri=${uri} ;; req2 = ${req2}")
    req2
      .post(uri)
      .send(sttpBackend)
      .map(_.body)
  }

  private def endpointJsonI[In : Encoder](path: String): GPTEndpoint[In, Array[Byte]]  =
    endpoint(path) { in => req =>
      req.header("Content-Type", "application/json").body(in.asJson.toString.getBytes)
    }

  private def endpointJson[In : Encoder, Out : Decoder](path: String): GPTEndpoint[In, Out] =
    endpointJsonI(path).andThen { resFuture =>
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
  def speech = endpointJsonI[SpeechRequest]("v1/audio/speech")
  def transcription = endpoint[TranscriptionRequest]("v1/audio/transcriptions") {
    treq => httpReq => {
      val res = httpReq.multipartBody(
        multipart("model", treq.model),
        multipartFile("file", treq.file),
      )
      res
    }
  }.andThen(_.map(_.flatMap { bytes =>
    parse(new String(bytes)).flatMap(_.as[TranscriptionResponse]).left.map(_.getMessage)
  }))
}

object GPTClient {
  type GPTResponse[T] = Either[String, T]
  type GPTEndpoint[In, Out] = In => Future[GPTResponse[Out]]
  type GPTRequest = PartialRequest[GPTResponse[Array[Byte]], Any]
}
