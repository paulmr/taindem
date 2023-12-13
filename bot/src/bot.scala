package taindem.bot

import taindem.Taindem
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.api.RequestHandler
import scala.concurrent.Future
import com.bot4s.telegram.clients.FutureSttpClient
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
import com.bot4s.telegram.api.ChatActions
import com.bot4s.telegram.models.InputFile
import com.bot4s.telegram.methods.SendVoice
import sttp.client3._
import com.bot4s.telegram.methods.GetFile
import taindem.model.TranscriptionRequest

case class UserState(t: Taindem, useAudio: Boolean = false)

class TaindemBot(token: String, gpt: GPTClient)(implicit backend: SttpBackend[Future, Any]) extends TelegramBot
    with Polling
    with Messages[Future]
    with Commands[Future]
    with ChatActions[Future] {

  private val users = collection.mutable.Map.empty[ChatId, UserState]

  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  private def getOrSetUserState(implicit msg: Message): UserState = users.get(msg.chat.chatId) match {
    case Some(st) => st
    case None =>
      val newSt = UserState(new Taindem(gpt))
      users(msg.chat.chatId) = newSt
      newSt
  }

  private def withUserState(f: UserState => UserState)(implicit msg: Message) =
    users.update(msg.chat.chatId, f(getOrSetUserState))

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
    withUserState(_.copy(t = new Taindem(gpt)))
    reply("Chat history reset").map(_ => ())
  }

  onCommand("lang") { implicit msg =>
    withArgs { args =>
      (for(lang <- args.headOption) yield {
        users(msg.chat.chatId) = UserState(new Taindem(gpt, language = lang))
        request(SendMessage(chatId = msg.chat.chatId, text = s"Language changed to $lang"))
          .map(_ => ())
      }).getOrElse(reply("Please give me a language to change to as an argument.").map(_ => ()))
    }
  }

  onCommand("start") { implicit msg =>
    reply("Hi there! I will chat to in a language of your choice, " +
      "and correct you. Use /lang to change language, or /reset " +
      "to tell me to forget the history of our conversation.\n\n" +
      "Use /audio to toggle creation of audio responses.").map(_ => ())
  }

  onCommand("audio") { implicit msg => withArgs { args =>
    withUserState { u =>
      val cmd = args.headOption.getOrElse("toggle")
      val update = cmd match {
        case "on" => true
        case "off" => false
        case _ => !u.useAudio
      }
      u.copy(useAudio = update)
    }
    reply(
      (if(getOrSetUserState.useAudio)
        "Enabling audio messages. Remember (as per ChatGPT policy I have to remind you!) this is not a real person's voice ! It's AI."
      else "Disabling audio")
    ).map(_ => ())
  } }


  onMessage { implicit msg =>
    if(msg.text.isDefined) handleTextMessage(msg)
    else if(msg.voice.isDefined) handleVoiceMessage(msg)
    else Future.successful(())
  }

  def sendTaindemAnswer(answer: TaindemAnswer)(implicit msg: Message): Future[Unit] =
    for {
      _ <- getCorrection(answer).map(c => request(SendMessage(chatId = msg.chat.chatId, replyToMessageId = Some(msg.messageId), text = c, parseMode = Some(ParseMode.HTML)))).getOrElse(Future.successful(()))
      _ <- answer.audio match {
        case Some(audio) =>
          request(SendVoice(msg.source, InputFile("answer.mp3", audio)))
        case None =>
          request(SendMessage(chatId = msg.chat.chatId, text = answer.answer, parseMode = Some(ParseMode.HTML)))
      }
    } yield ()

  def handleVoiceMessage(implicit msg: Message): Future[Unit] = // reply("voice not supported yet").map(_ => ())
    msg.voice match {
      case None => Future.successful(())
      case Some(v) =>
        for {
          file <- request(GetFile(v.fileId))
          _ <- uploadingAudio
          data <- basicRequest
          .get(uri"https://api.telegram.org/file/bot${token}/${file.filePath.get}")
          .response(asFileAlways(new java.io.File(s"voice-${v.fileId}.ogg")))
          .send(backend)
          body = data.body
          _ = println(s"downloaded: file($body)")
          tr <- gpt.transcription(TranscriptionRequest(file = body, language = Some(getOrSetUserState.t.language)))
          _ = body.delete()
          _ <- tr match {
            case Left(err) => reply(s"error: $err")
            case Right(res) =>
              getOrSetUserState.t.submitMessage(res.text, getOrSetUserState.useAudio).flatMap {
                case Left(err) => reply(s"Error: $err")
                case Right(answer) =>
                  sendTaindemAnswer(answer)
              }
          }
        } yield ()
    }

  def handleTextMessage(implicit msg: Message): Future[Unit] =
    msg.text match {
      case None => Future(()) // ignore non-text messages
      case Some(text) if text.startsWith("/") => Future(()) // ignore commands
      case Some(text) =>
        val u = users.getOrElseUpdate(msg.chat.chatId, UserState(Taindem(gpt)))
        (if(u.useAudio) uploadingAudio else typing).flatMap { _ =>
          val req = u.t.submitMessage(text, u.useAudio)
          req.map {
            case Left(errMsg) =>
              println(s"Error: $errMsg")
              request(
                SendMessage(
                  chatId = msg.chat.chatId,
                  text = "Sorry, unable to send message. Try again."
                )
              )
            case Right(answer) =>
              sendTaindemAnswer(answer)
          }
        }
    }
}
