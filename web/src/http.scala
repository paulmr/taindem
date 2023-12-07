package taindem.web

import scala.concurrent.{ExecutionContext, Future}
import taindem.client.GPTClient
import org.scalajs.dom._
import Fetch.fetch

import scala.scalajs.js.Thenable.Implicits._

class GPTClientFetch(val apiKey: String)(implicit val ec: ExecutionContext) extends GPTClient {

  def sendRequestBase(url: String, headers: Map[String, String], _body: String):
      Future[GPTClient.GPTResponse[String]] = {

    val fetchHeaders = new Headers()
    for((k, v) <- headers) {
      fetchHeaders.append(k, v)
    }
    val req = new RequestInit { }
    req.body = _body
    req.method = HttpMethod.POST
    req.headers = fetchHeaders

    fetch(url, req).flatMap { res =>
      if(res.status == 200) res.text().map(Right(_))
      else res.text().map(msg => Left(msg + " " + res.statusText))
    }

  }

}
