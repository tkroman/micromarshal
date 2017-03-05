package com.tkroman.akka.upickle

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller}
import akka.http.scaladsl.model.{ContentTypes, MessageEntity}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.ActorMaterializer

@akkaBiMarshalling
case class C(x: Int, y: Int)

@akkaBiMarshalling
sealed trait T
case class TInt(x: Int) extends T
case class TBool(b: Boolean) extends T

object Main {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  def main(args: Array[String]): Unit = {

    // marshallable entities
    val c = C(1,2)
    val ta: T = TInt(1)
    val tb: T = TBool(true)

    try {
      val mc = marshal(c, C.akkaMarshaller)
      val umc = unmarshal(mc, C.akkaUnmarshaller)
      assert(umc == c)

      val mta = marshal(ta, T.akkaMarshaller)
      val umta = unmarshal(mta, T.akkaUnmarshaller)
      assert(umta == ta)

      val mtb = marshal(tb, T.akkaMarshaller)
      val umtb = unmarshal(mtb, T.akkaUnmarshaller)
      assert(umtb == tb)
    } finally {
      Await.result(system.terminate(), 5.seconds)
    }
  }

  private def marshal[X](x: X, m: ToEntityMarshaller[X]): Marshalling.WithFixedContentType[MessageEntity] = {
    Await.result(m(x), 1.second)
      .head
      .asInstanceOf[Marshalling.WithFixedContentType[MessageEntity]]
  }

  private def unmarshal[X](x: Marshalling.WithFixedContentType[MessageEntity], um: FromEntityUnmarshaller[X]) = {
    x.map(e => Await.result(um(e), 1.second)).marshal()
  }
}
