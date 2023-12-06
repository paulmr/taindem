package taindem.model

import utest._
import io.circe.parser._

object JsonTests extends TestSuite {

  val exampleResponse = """
{
  "choices": [
    {
      "finish_reason": "stop",
      "index": 0,
      "message": {
        "content": "The 2020 World Series was played in Texas at Globe Life Field in Arlington.",
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

  def tests = Tests {
    test("example request") {
      assert(parse(exampleResponse).flatMap(_.as[CompletionsResponse]).isRight)
    }

    test("example response") {
      assert(parse(exampleRequest).flatMap(_.as[CompletionsRequest]).isRight)
    }
  }

}
