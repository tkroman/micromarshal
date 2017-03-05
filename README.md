# upickle-akka
Autoderivation of Akka-HTTP marshallers/unmarshallers with uPickle

# Usage 

Full example (also, currently the only test. Hopefully, not for long): com.tkroman.akka.upickle.Main

In short, just slap `@com.tkroman.akka.upickle.akkaBiMarshalling` on your case class or sealed trait to get an automatic upickle-powered JSON encoding/decoding + Akka HTTP (un)marshallers.
