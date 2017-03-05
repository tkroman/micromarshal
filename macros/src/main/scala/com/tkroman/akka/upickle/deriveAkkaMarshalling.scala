package com.tkroman.akka.upickle

import scala.meta._
import scala.collection.immutable.Seq
import upickle.AttributeTagged

// TODO dedup
class deriveAkkaMarshalling(pickler: String = "upickle.default") extends scala.annotation.StaticAnnotation {

  inline def apply(defn: Any): Any = meta {
    val unmarshallerTypeName  = "_root_.akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller"
    val marshallerTypeName    = "_root_.akka.http.scaladsl.marshalling.ToEntityMarshaller"
    val rwTypeName            = "_root_.upickle.default.ReadWriter"
    val marshallerMediaType   = {
      Term.Select(
        Term.Name("akka.http.scaladsl.model.MediaTypes"),
        Term.Name("`application/json`")
      )
    }

    val picklerName = this match {
      case q"new $_(${Lit(p: String)})" =>
        s"_root_.$p"
      case q"new $_()" =>
        // TODO read this from default arg?
        "_root_.upickle.default"
    }
    val usePickler = Term.Name(picklerName)
    val rwType = Type.Name(s"$usePickler.ReadWriter")

    def mkImplicitDef(name: Type.Name, typarams: Seq[Type.Param]) = {
      val apType  = Type.Apply(name, typarams.map(n => Type.Name(n.name.value)))
      val umType  = Type.Apply(Type.Name(unmarshallerTypeName), Seq(apType))
      val mType   = Type.Apply(Type.Name(marshallerTypeName), Seq(apType))
      val typaramsWithRWCtxBounds = typarams.map(tp => tp.copy(cbounds = tp.cbounds :+ Type.Name(rwTypeName)))

      Seq(
        // implicit rw def
        q"""
        implicit def upickleRw[..$typarams]: $rwType[$apType] = {
          $usePickler.macroRW[$apType]
        }
        """,
        // implicit marshaller def
        q"""
        implicit def akkaMarshaller[..$typaramsWithRWCtxBounds]: $mType = {
          _root_.akka.http.scaladsl.marshalling.Marshaller
             .stringMarshaller($marshallerMediaType)
             .compose[$apType](t => $usePickler.write(t))
        }
        """,
        // implicit unmarshaller def
        q"""
        implicit def akkaUnmarshaller[..$typaramsWithRWCtxBounds]: $umType = {
          _root_.akka.http.scaladsl.unmarshalling.Unmarshaller
            .byteStringUnmarshaller.mapWithCharset { (str, charset) =>
              $usePickler.read[$apType](str.decodeString(charset.nioCharset()))
            }
        }
        """
      )
    }

    def mkImplicitVal(typeName: Type.Name) = {
      val umApType  = Type.Apply(Type.Name(unmarshallerTypeName), Seq(typeName))
      val mApType   = Type.Apply(Type.Name(marshallerTypeName), Seq(typeName))

      Seq(
        // implicit rw val
        q"""
        implicit val upickleRw: $rwType[$typeName] = {
          $usePickler.macroRW[$typeName]
        }
        """,
        // implicit marshaller val
        q"""
        implicit val akkaMarshaller: $mApType = {
          _root_.akka.http.scaladsl.marshalling.Marshaller
            .stringMarshaller($marshallerMediaType)
            .compose[$typeName](t => $usePickler.write(t))
        }
        """,
        // implicit unmarshaller val
        q"""
        implicit val akkaUnmarshaller: $umApType = {
          _root_.akka.http.scaladsl.unmarshalling.Unmarshaller
            .byteStringUnmarshaller.mapWithCharset { (str, charset) =>
              $usePickler.read[$typeName](str.decodeString(charset.nioCharset()))
            }
        }
        """
      )
    }

    def mkImplicit(name: Type.Name, typarams: Seq[Type.Param]): Seq[Stat] = {
      if (typarams.isEmpty) {
        mkImplicitVal(name)
      } else {
        mkImplicitDef(name, typarams)
      }
    }

    val r = defn match {
      case Term.Block(Seq(trt @ Defn.Trait(_, name, typarams, _, _), companion: Defn.Object)) if trt.mods.exists(_.syntax == "sealed")=>
        val implUm = mkImplicit(name, typarams)
        val templateStats = implUm ++ companion.templ.stats.getOrElse(Nil)
        val newCompanion = companion.copy(templ = companion.templ.copy(stats = Some(templateStats)))
        Term.Block(Seq(trt, newCompanion))

      case trt @ Defn.Trait(_, name, typarams, _, _) if trt.mods.exists(_.syntax == "sealed") =>
        val implUm = mkImplicit(name, typarams)
        val companion   = q"object ${Term.Name(name.value)} { ..$implUm }"
        Term.Block(Seq(trt, companion))

      // companion object exists
      case Term.Block(Seq(cls @ Defn.Class(_, name, typarams, _, _), companion: Defn.Object)) =>
        val implUm = mkImplicit(name, typarams)
        val templateStats = implUm ++ companion.templ.stats.getOrElse(Nil)
        val newCompanion = companion.copy(templ = companion.templ.copy(stats = Some(templateStats)))
        Term.Block(Seq(cls, newCompanion))

      // companion object does not exists
      case cls @ Defn.Class(_, name, typarams, _, _) =>
        val implUm = mkImplicit(name, typarams)
        val companion   = q"object ${Term.Name(name.value)} { ..$implUm }"
        Term.Block(Seq(cls, companion))

      case _ =>
        println(defn.structure)
        abort("@unmarshal must annotate a class or a sealed trait.")
    }

    r
  }
}