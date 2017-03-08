# micromarshal

[![Join the chat at https://gitter.im/micromarshal/Lobby](https://badges.gitter.im/micromarshal/Lobby.svg)](https://gitter.im/micromarshal/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
Autoderivation of Akka-HTTP marshallers/unmarshallers with [uPickle](http://www.lihaoyi.com/upickle-pprint/upickle) in < 200 LOC.

## Usage

### Dependency

Very much beta. Cross-build for scala 2.11/2.12.

`"com.tkroman" %% "micromarshal" % "0.0.8"`

In order to expand macro annotations client projects should also have these options enabled:

```scala
lazy val expandMacroAnnotations: Seq[Def.Setting[_]] = Seq(
  addCompilerPlugin(
    ("org.scalameta" % "paradise" % "3.0.0-M7").cross(CrossVersion.full)
  ),

  libraryDependencies +=
    "org.scalameta" %% "scalameta" % "1.6.0" % Provided,

  scalacOptions += "-Xplugin-require:macroparadise",

  // macroparadise plugin doesn't work in repl yet.
  scalacOptions in (Compile, console) := Seq(),

  // macroparadise doesn't work with scaladoc yet.
  sources in (Compile, doc) := Nil
)
```

### Code

[Full example in tests](src/test/scala/com/tkroman/micromarshal/MarshallingSuite.scala)

In short: just slap `@com.tkroman.micromarshal.deriveAkkaMarshalling` on your case class
or sealed trait to get an automatic upickle-powered JSON encoding/decoding support + Akka HTTP (un)marshallers.

```scala
import com.tkroman.micromarshal.deriveAkkaMarshalling

@deriveAkkaMarshalling
case class Foo(str: String)
```

so later in your Akka-HTTP router you can do this:

```scala
get {
  path("foo") {
    complete(Foo("content"))
  }
}
```

## Hygiene

Micromarshal does not rely on generation of fresh names and does not employ typechecking of any kind
to ensure that companion objects of classes already contain implicit `ReadWriter` definitions.

Instead, there is a simple convention:
if the companion object contains a definition of type `upickle.default.ReadWriter[A]`
(or `custom.pickler.ReadWriter[A]`),
no `ReadWriter` implicit is generated. This might come in handy when a need arises to
[manually define and instance for a sealed hierarchy](http://www.lihaoyi.com/upickle-pprint/upickle/#ManualSealedTraitPicklers).

## Custom (un)picklers

Consistently with uPickle, `deriveAkkaMarshalling` accepts a custom pickler. For example:

```scala
package a.b.c

object CustomPickler extends upickle.AttributeTagged {
  // custom pickling here
}

@deriveAkkaMarshalling("a.b.c.OptionPickler")
case class Foo(x: Int)
```

## TODO

* Abstraction over JSON library (would like to support circe)
