# [WIP]upickle-akka
Autoderivation of Akka-HTTP marshallers/unmarshallers with [uPickle](http://www.lihaoyi.com/upickle-pprint/upickle)

# Usage

Currently unpublished and very much beta.

Full example (also, currently the only test. Hopefully, not for long): com.tkroman.akka.upickle.Main

In short, just slap `@com.tkroman.akka.upickle.deriveAkkaMarshalling` on your case class or sealed trait to get an automatic upickle-powered JSON encoding/decoding + Akka HTTP (un)marshallers.

Consistently with uPickle, `deriveAkkaMarshalling` accepts a custom pickler. For example:

```scala
package a.b.c

// borrowed from http://www.lihaoyi.com/upickle-pprint/upickle/#CustomConfiguration
object OptionPickler extends upickle.AttributeTagged {
  override implicit def OptionW[T: Writer]: Writer[Option[T]] = Writer {
    case None    => Js.Null
    case Some(s) => implicitly[Writer[T]].write(s)
  }

  override implicit def OptionR[T: Reader]: Reader[Option[T]] = Reader {
    case Js.Null     => None
    case v: Js.Value => Some(implicitly[Reader[T]].read.apply(v))
  }
}

@deriveAkkaMarshalling("a.b.c.OptionPickler")
case class Foo(x: Int)
```
