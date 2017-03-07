package com.tkroman

package object micromarshal {
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

  @deriveAkkaMarshalling("com.tkroman.micromarshal.OptionPickler")
  case class SimpleNoCompanion(x: Int, y: Int)

  @deriveAkkaMarshalling
  sealed trait SimpleHierNoCompanion
  case class TInt(x: Int) extends SimpleHierNoCompanion
  case class TBool(b: Boolean) extends SimpleHierNoCompanion

  @deriveAkkaMarshalling
  case class WithCompanionAndImplicitRw(a: String)
  object WithCompanionAndImplicitRw {
    def f = 1

    import upickle.default._
    implicit val rw: ReadWriter[WithCompanionAndImplicitRw] = upickle.default.macroRW[WithCompanionAndImplicitRw]
  }

  @deriveAkkaMarshalling
  case class Parametrized[A](t: A)
}
