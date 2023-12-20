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
import com.bot4s.telegram.api.declarative.{Messages, Commands, Args}
import com.bot4s.telegram.api.ChatActions
import com.bot4s.telegram.models.InputFile
import com.bot4s.telegram.methods.SendVoice
import sttp.client3._
import com.bot4s.telegram.methods.GetFile
import taindem.model.TranscriptionRequest
import java.time.Instant
import scala.concurrent.ExecutionContext

case class UserState(t: Taindem, useAudio: Boolean = false, audioVoice: String = "alloy", showCorrections: Boolean = true)

class TaindemBot(
  token: String,
  gpt: GPTClient,
  audioEnabledByDefault: Boolean = false,
)(implicit backend: SttpBackend[Future, Any]) extends TelegramBot
    with Polling
    with Messages[Future]
    with Commands[Future]
    with ChatActions[Future] {

  val startTime = Instant.now()

  def uptime() = java.time.Duration.ofSeconds(Instant.now().getEpochSecond() - startTime.getEpochSecond())

  implicit class futureIgnore[T](f: Future[T]) {
    def discard = f.map(_ => ())
  }

  private val users = collection.mutable.Map.empty[ChatId, UserState]

  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  private def getOrSetUserState(implicit msg: Message): UserState = users.get(msg.chat.chatId) match {
    case Some(st) => st
    case None =>
      val newSt = UserState(new Taindem(gpt), useAudio = audioEnabledByDefault)
      users(msg.chat.chatId) = newSt
      newSt
  }

  private def updateUserState[T](f: UserState => (T, UserState))(implicit msg: Message): T =
    users.synchronized {
      val (res, st) = f(getOrSetUserState)
      users.update(msg.chat.chatId, st)
      res
    }

  private def updateUserState(f: UserState => UserState)(implicit msg: Message): Unit =
    updateUserState(st => ((), f(st)))

  private def withUserState[T](f: UserState => T)(implicit msg: Message): T =
    updateUserState { st => (f(st), st) }

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

  onCommand("ping") { implicit msg =>
    withUserState { u =>
      reply(
        List(
          "pong",
          s"*audio*: ${u.useAudio}",
          s"*corrections*: ${u.showCorrections}",
          s"*voice*: ${u.audioVoice}",
          s"*language*: ${u.t.language}",
          s"*uptime*: ${startTime} (${uptime()})",
          s"*history length*: ${u.t.getHistory().length}",
        ).mkString("\n"),
        Some(ParseMode.Markdown)
      ).discard
    }
  }

  onCommand("reset") { implicit msg =>
    updateUserState(_.copy(t = new Taindem(gpt)))
    reply("Chat history reset").discard
  }

  onCommand("lang") { implicit msg =>
    withArgs { args =>
      (for(lang <- args.headOption) yield {
        updateUserState(u => u.copy(t = new Taindem(gpt, language = lang)))
        request(SendMessage(chatId = msg.chat.chatId, text = s"Language changed to $lang"))
          .map(_ => ())
      }).getOrElse(reply("Please give me a language to change to as an argument.").discard)
    }
  }

  onCommand("start") { implicit msg =>
    reply("Hi there! I will chat to in a language of your choice, " +
      "and correct you. Use /lang to change language, or /reset " +
      "to tell me to forget the history of our conversation.\n\n" +
      "Use /audio to toggle creation of audio responses.").map(_ => ())
  }

  onCommand("voice") { implicit msg =>
    withArgs { args =>
      val newVoice = args.headOption.getOrElse("alloy")
      updateUserState { u => u.copy(audioVoice = newVoice) }
      reply(s"Voice changed to $newVoice").discard
    }
  }

  onCommand("repeat") { implicit msg =>
    withUserState { st =>
      val msgText = st.t.getHistory().findLast(_.role == "assistant").map(_.content).getOrElse("(not found)")
      reply(msgText).discard
    }
  }

  def toggle(
    get: UserState => Boolean,
    set: (UserState, Boolean) => UserState,
    replyText: Option[Boolean => String] = None
  ) = { implicit msg: Message =>
    withArgs { args =>
      updateUserState { u =>
        val cmd = args.headOption.getOrElse("toggle")
        val update = cmd match {
          case "on" => true
          case "off" => false
          case _ => !get(u)
        }
        val newState = set(u, update)
        val newSetting = get(newState)
        val res = reply(
          replyText.map(f => f(newSetting)).getOrElse(if(newSetting) "Enabled" else "Disabled")
        ).discard
        (res, newState)
      }
    }
  }

  onCommand("audio")(
    toggle(_.useAudio, (st, b) => st.copy(useAudio = b),
      Some { b =>
        if(b)
          "Enabling audio messages. Remember (as per ChatGPT policy I have to remind you!) this is not a real person's voice ! It's AI."
        else
          "Disabling audio"
      }
    )
  )

  onCommand("corrections")(toggle(_.showCorrections, (st, b) => st.copy(showCorrections = b)))

  onMessage { implicit msg =>
    if(msg.text.isDefined) handleTextMessage(msg)
    else if(msg.voice.isDefined) handleVoiceMessage(msg)
    else Future.successful(())
  }

  def sendTaindemAnswer(answer: TaindemAnswer)(implicit msg: Message): Future[Unit] =
    for {
      _ <- (
        if(getOrSetUserState.showCorrections)
          getCorrection(answer)
          .map(c => request(SendMessage(chatId = msg.chat.chatId, replyToMessageId = Some(msg.messageId), text = c, parseMode = Some(ParseMode.HTML))))
        else None
      ).getOrElse(Future.successful(()))
      _ <- answer.audio match {
        case Some(audio) =>
          request(SendVoice(msg.source, InputFile("answer.mp3", audio)))
        case None =>
          request(SendMessage(chatId = msg.chat.chatId, text = answer.answer, parseMode = Some(ParseMode.HTML)))
      }
    } yield ()

  def handleVoiceMessage(implicit msg: Message): Future[Unit] =
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
          fname = data.body
          _ = logger.debug(s"downloaded: file($fname)")
          tr <- gpt.transcription(TranscriptionRequest(file = fname, language = Some(getOrSetUserState.t.language)))
          _ = fname.delete()
          _ <- tr match {
            case Left(err) =>
              logger.info(s"error: $err")
              reply(s"error: $err")
            case Right(res) =>
              withUserState { u =>
                u.t.submitMessage(res.text, getOrSetUserState.useAudio).flatMap {
                  case Left(err) =>
                    logger.info(s"error: $err")
                    reply(s"Error: $err")
                  case Right(answer) =>
                    sendTaindemAnswer(answer)
                }
              }
          }
        } yield ()
    }

  def handleTextMessage(implicit msg: Message): Future[Unit] =
    msg.text match {
      case None => Future(()) // ignore non-text messages
      case Some(text) if text.startsWith("/") => Future(()) // ignore commands
      case Some(text) =>
        withUserState { u =>
          (if(u.useAudio) uploadingAudio else typing).flatMap { _ =>
            val req = u.t.submitMessage(text, useAudio = u.useAudio, audioVoice = u.audioVoice)
            req.map {
              case Left(errMsg) =>
                logger.info(s"Error: $errMsg")
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
}
