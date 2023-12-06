package taindem.model

import io.circe.generic.JsonCodec

@JsonCodec case class Message(role: String, content: String)
@JsonCodec case class CompletionsRequest(messages: Seq[Message], model: String = "gpt-3.5-turbo")

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
