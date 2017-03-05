package com.tkroman.akka.upickle

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshalling, ToEntityMarshaller}
import akka.http.scaladsl.model.MessageEntity
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.ActorMaterializer

object OptionPickler extends upickle.AttributeTagged {
  import upickle.Js
  override implicit def OptionW[T: Writer]: Writer[Option[T]] = Writer {
    case None    => Js.Null
    case Some(s) => implicitly[Writer[T]].write(s)
  }

  override implicit def OptionR[T: Reader]: Reader[Option[T]] = Reader {
    case Js.Null     => None
    case v: Js.Value => Some(implicitly[Reader[T]].read.apply(v))
  }
}

@deriveAkkaMarshalling("com.tkroman.akka.upickle.OptionPickler")
case class C(x: Int, y: Int)

@deriveAkkaMarshalling
sealed trait T
case class TInt(x: Int) extends T
case class TBool(b: Boolean) extends T

@deriveAkkaMarshalling
case class X(a: String)
object X {
  def f = 1
}

object Main {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  def main(args: Array[String]): Unit = {

    // marshallable entities
    val c = C(1,2)
    val x = X("a")
    val ta: T = TInt(1)
    val tb: T = TBool(true)

    try {
      val mc = marshal(c, C.akkaMarshaller)
      val umc = unmarshal(mc, C.akkaUnmarshaller)
      assert(umc == c)

      val mx = marshal(x, X.akkaMarshaller)
      val umx = unmarshal(mx, X.akkaUnmarshaller)
      assert(umx == x)
      assert(X.f == 1)

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
