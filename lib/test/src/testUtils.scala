package taindem

import io.circe.syntax._
import taindem.client.GPTClient
import scala.concurrent.{ExecutionContext, Future}

object TestUtils {

  def mockResponse(msg: => String): GPTClient = new GPTClient {
    implicit val ec: ExecutionContext = utest.framework.ExecutionContext.RunNow

    val apiKey = "test"

    def sendRequestBase(url: String, headers: Map[String,String], body: String):
        Future[GPTClient.GPTResponse[String]] = Future.successful(Right(exampleResponse(msg)))
  }

  def exampleResponse(msg: String) = {
    s"""
{
  "choices": [
    {
      "finish_reason": "stop",
      "index": 0,
      "message": {
        "content": ${msg.asJson},
        "role": "assistant"
      }
    }
  ],
  "created": 1677664795,
  "id": "chatcmpl-7QyqpwdfhqwajicIEznoc6Q47XAyW",
  "model": "gpt-3.5-turbo-0613",
  "object": "chat.completion",
  "usage": {
    "completion_tokens": 17,
    "prompt_tokens": 57,
    "total_tokens": 74
  }
}
"""
  }

  val exampleRequest = """
{
    "model": "gpt-3.5-turbo",
    "messages": [
      {
        "role": "system",
        "content": "You are a helpful assistant."
      },
      {
        "role": "user",
        "content": "Who won the world series in 2020?"
      },
      {
        "role": "assistant",
        "content": "The Los Angeles Dodgers won the World Series in 2020."
      },
      {
        "role": "user",
        "content": "Where was it played?"
      }
    ]
}
"""

}
