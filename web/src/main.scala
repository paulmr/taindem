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
  def messageLogElem: Binding[HTMLDivElement] = {
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
    <input type="text" onkeypress= { ev:Event =>
      val target = ev.target.asInstanceOf[HTMLInputElement]
      if(ev.asInstanceOf[KeyboardEvent].key == "Enter") {
        submitUserMessage(target.value)
        target.value = ""
      }
    }/>
    </div>
    </div>
  }

  def showInput() = {
    document.getElementById("user-input").scrollIntoView()
  }

  def submitUserMessage(msg: String) = {
    messageLog.value += UserMessage(msg)
  }

  @JSExport
  def main(): Unit = {
    html.render(document.body, messageLogElem)
  }
}
