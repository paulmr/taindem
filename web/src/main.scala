package taindem.web

import taindem.model._
import com.thoughtworks.binding.Binding, Binding._
import org.scalajs.dom._
import org.lrng.binding.html
import scala.scalajs.js.annotation._
import scalalibdiff.Diff

@JSExportTopLevel("taindem")
object TaindemWebApp {

  sealed trait ConversationPoint
  case class RobotResponse(message: TaindemAnswer) extends ConversationPoint
  case class UserMessage(message: String) extends ConversationPoint

  val messageLog = Vars.empty[ConversationPoint]
    // Vars[ConversationPoint](
    //   UserMessage("Hi, how is you?"),
    //   RobotResponse(TaindemAnswer(correction = "Hi, how are you?", answer = "I am fine thanks, you?"))
    // )

  @html
  def renderDiffs(ds: List[Diff.Difference], l: String, r: String) = for(d <- ds) yield {
    d match {
      case Diff.Added(from, to) => <span class="diff-added">{r.substring(from, to)}</span>
      case Diff.Removed(from, to) => <span class="diff-removed">{l.substring(from, to)}</span>
      case Diff.Same(from, to, _, _) => <span class="diff-same">{l.substring(from, to)}</span>
    }
  }

  @html
  def messageLogElem(submitCB: String => Unit, lang: String): Binding[HTMLDivElement] = {
    <div id="app">
    <div id="language">{lang}</div>
    <div id="message-log">
    {
      for(msg <- messageLog) yield {
        msg match {
          case r: RobotResponse =>
            <div class="message message-robot">
            {
              for(correction <- r.message.correction.toSeq; diffs <- r.message.diff) yield {
                <div class="message-correction">{renderDiffs(diffs, r.message.question, correction)}</div>
              }
            }
            <div class="message-answer">{r.message.answer}</div>
            </div>
          case u: UserMessage => <div class="message message-user">{u.message}</div>
        }
      }
    }
    </div>
    <div id="user-input">
    <input id="user-input-txt" type="text" onkeypress= { ev:Event =>
      val target = ev.target.asInstanceOf[HTMLInputElement]
      if(ev.asInstanceOf[KeyboardEvent].key == "Enter") {
        submitCB(target.value)
        target.value = ""
      }
    }/>
    </div>
    </div>
  }

  def showInput() = {
    document.getElementById("user-input").scrollIntoView()
  }

  val qs = new URLSearchParams(window.location.search)

  val localStorageKey = "chatgpt-key"

  def findApiKey(): String = {
    // if there is an api key in the url, override it with that, and save it
    if(qs.has("api-key")) {
      console.log("saving api key from url")
      window.localStorage.setItem(localStorageKey, qs.get("api-key"))
    }
    val stored = window.localStorage.getItem(localStorageKey)
    if(stored == null || stored == "") {
      window.alert("no api key found")
      "test"
    } else stored
  }

  def optionalQueryParam(name: String): Option[String] =
    if(qs.has(name)) Some(qs.get(name)) else None

  @JSExport
  def main(): Unit = {
    implicit val ec: scala.concurrent.ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

    val apiKey = findApiKey()
    console.log(s"using api key: ${apiKey}")
    val lang = optionalQueryParam("lang").getOrElse("French")
    val gpt = new GPTClientFetch(apiKey)
    val t = new taindem.Taindem(gpt, language = lang)

    def inputElement = document.getElementById("user-input-txt").asInstanceOf[HTMLInputElement]

    def submit(msg: String) = {
      inputElement.disabled = true
      messageLog.value += UserMessage(msg)
      t.submitMessage(msg).foreach { res =>
        res match {
          case Left(err) => window.alert(err)
          case Right(answer) =>
            messageLog.value += RobotResponse(answer)
        }
        inputElement.disabled = false
        inputElement.focus()
        inputElement.scrollIntoView()
      }
    }

    html.render(document.body, messageLogElem(submit, lang))
  }
}
