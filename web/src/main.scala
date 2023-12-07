package taindem.web

import taindem.model._
import com.thoughtworks.binding.Binding, Binding._
import org.scalajs.dom._
import org.lrng.binding.html
import scala.scalajs.js.annotation._

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
  def messageLogElem(submitCB: String => Unit): Binding[HTMLDivElement] = {
    <div id="app">
    <div id="message-log">
    {
      for(msg <- messageLog) yield {
        msg match {
          case r: RobotResponse =>
            <div class="message message-robot">
            <div class="message-correction">{r.message.correction}</div>
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

  @JSExport
  def main(): Unit = {
    implicit val ec: scala.concurrent.ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

    val apiKey = Option(window.localStorage.getItem("chatgpt-key")).getOrElse("test")

    val gpt = new GPTClientFetch(apiKey)
    var t = new taindem.Taindem(gpt)

    def inputElement = document.getElementById("user-input-txt").asInstanceOf[HTMLInputElement]

    def submit(msg: String) = {
      inputElement.disabled = true
      messageLog.value += UserMessage(msg)
      t.submitMessage(msg).foreach { res =>
        res match {
          case Left(err) => window.alert(err)
          case Right((answer, nextT)) =>
            messageLog.value += RobotResponse(answer)
            t = nextT
        }
        inputElement.scrollIntoView()
        inputElement.disabled = false
        inputElement.focus()
      }
    }

    html.render(document.body, messageLogElem(submit))
  }
}
