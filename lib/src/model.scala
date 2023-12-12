package taindem.model

import scalalibdiff.Diff
import io.circe.generic.JsonCodec
import io.circe.Json

// ==== CHATGPT models

@JsonCodec case class Message(role: String, content: String)
@JsonCodec case class CompletionsRequest(
  messages: Seq[Message],
  model: String,
  response_format: Option[ResponseFormat] = None,
  temperature: Option[Double] = None,
  top_p: Option[Double] = None
)

@JsonCodec case class ResponseFormat(`type`: String)

@JsonCodec case class Choice(
  finish_reason: String,
  index: Int,
  message: Message
)

@JsonCodec case class Usage(
  completion_tokens: Int,
  prompt_tokens: Int,
  total_tokens: Int
)

@JsonCodec case class CompletionsResponse(
  choices: Seq[Choice],
  created: Long,
  id: String,
  model: String,
  `object`: String,
  usage: Usage
)

// TAINDEM models

@JsonCodec case class TaindemAnswerJson(
  correction: Option[String],
  answer: String
)

case class TaindemAnswer(
  correction: Option[String],
  answer: String,
  question: String,
  diff: Option[List[Diff.Difference]]
)
