package taindem.bot

import taindem.Taindem
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.api.RequestHandler
import scala.concurrent.Future
import com.bot4s.telegram.clients.ScalajHttpClient
import com.bot4s.telegram.models.Message
import taindem.client.GPTClient
import com.bot4s.telegram.models.ChatId
import com.bot4s.telegram.methods.SendMessage
import taindem.model.TaindemAnswer
import com.bot4s.telegram.models.MessageEntity
import scalalibdiff.Diff
import com.bot4s.telegram.methods.ParseMode
import com.bot4s.telegram.methods.SendChatAction
import com.bot4s.telegram.methods.ChatAction
import com.bot4s.telegram.api.declarative.Messages
import com.bot4s.telegram.api.declarative.Commands

class TaindemBot(token: String, gpt: GPTClient) extends TelegramBot
    with Polling
    with Messages[Future]
    with Commands[Future] {

  private val users = collection.mutable.Map.empty[ChatId, Taindem]

  override val client: RequestHandler[Future] = new ScalajHttpClient(token)

  private def getCorrection(answer: TaindemAnswer): Option[String] =
    for(correction <- answer.correction; ds <- answer.diff) yield {
      val l = answer.question.split("\\s+").toSeq
      val r = correction.split("\\s+").toSeq
      val leftResponse = ds.collect {
        case Diff.Removed(from, to) => s"<del>${l.slice(from, to).mkString(" ")}</del>"
        case Diff.Same(from, to, _, _) => l.slice(from, to).mkString(" ")
      }.mkString(" ")
      val rightResponse = ds.collect {
        case Diff.Added(from, to) => s"<u>${r.slice(from, to).mkString(" ")}</u>"
        case Diff.Same(_, _, from, to) => r.slice(from, to).mkString(" ")
      }.mkString(" ")
      s"✗ ${leftResponse}\n✓ ${rightResponse}\n\n\n"
    }

  onCommand("reset") { implicit msg =>
    users(msg.chat.chatId) = new Taindem(gpt)
    request(SendMessage(chatId = msg.chat.chatId, text = "Chat history reset")).map(_ => ())
  }

  onCommand("lang") { implicit msg =>
    withArgs { args =>
      (for(lang <- args.headOption) yield {
        users(msg.chat.chatId) = new Taindem(gpt, language = lang)
        request(SendMessage(chatId = msg.chat.chatId, text = s"Language changed to $lang"))
          .map(_ => ())
      }).getOrElse(Future.successful(()))
    }
  }

  onMessage { msg =>
    msg.text match {
      case None => Future(()) // ignore non-text messages
      case Some(text) if text.startsWith("/") => Future(()) // ignore commands
      case Some(text) =>
        request(SendChatAction(msg.chat.chatId, ChatAction.Typing)).flatMap { _ =>
          val t = users.getOrElseUpdate(msg.chat.chatId, Taindem(gpt))
          t.submitMessage(text).map {
            case Left(errMsg) =>
              println(s"Error: $errMsg")
              request(
                SendMessage(
                  chatId = msg.chat.chatId,
                  text = "Sorry, unable to send message. Try again."
                )
              )
            case Right(answer) =>
              for {
                _ <- getCorrection(answer).map(c => request(SendMessage(chatId = msg.chat.chatId, replyToMessageId = Some(msg.messageId), text = c, parseMode = Some(ParseMode.HTML)))).getOrElse(Future.successful(()))
                _ <- request(SendMessage(chatId = msg.chat.chatId, text = answer.answer, parseMode = Some(ParseMode.HTML)))
              } yield ()
          }
        }
    }
  }
}
