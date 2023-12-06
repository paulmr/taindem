package taindem.io

import io.circe.Json
import scala.concurrent.Future
import taindem.model._

trait GPTClient {

  val apiKey: String

  def sendRequest(body: Json): Future[Either[String, Json]]

  def completion(request: CompletionRequest): Future[CompletionResponse] = ???

}
