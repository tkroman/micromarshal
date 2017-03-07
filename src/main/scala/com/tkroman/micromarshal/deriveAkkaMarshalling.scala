package com.tkroman.micromarshal

import scala.collection.immutable.Seq
import scala.meta._

class deriveAkkaMarshalling(pickler: String = "upickle.default") extends scala.annotation.StaticAnnotation {

  inline def apply(defn: Any): Any = meta {
    val unmarshallerTypeName  = "_root_.akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller"
    val marshallerTypeName    = "_root_.akka.http.scaladsl.marshalling.ToEntityMarshaller"
    val marshallerMediaType   = {
      Term.Select(
        Term.Name("_root_.akka.http.scaladsl.model.MediaTypes"), Term.Name("`application/json`")
      )
    }
    val unmarshallerMethod = q"_root_.akka.http.scaladsl.unmarshalling.Unmarshaller.byteStringUnmarshaller"
    val marshallerMethod = q"_root_.akka.http.scaladsl.marshalling.Marshaller.stringMarshaller"

    val picklerName = this match {
      case q"new $_(${Lit(p: String)})" =>
        s"_root_.$p"
      case q"new $_()" =>
        // TODO read this from default arg?
        "_root_.upickle.default"
    }

    val usePickler = Term.Name(picklerName)
    val rwType = Type.Name(s"$usePickler.ReadWriter")
    val rType = Type.Name(s"$usePickler.Reader")
    val wType = Type.Name(s"$usePickler.Writer")

    def mkImplicitDef(name: Type.Name, typarams: Seq[Type.Param], companion: Option[Defn.Object]) = {
      val apType  = Type.Apply(name, typarams.map(n => Type.Name(n.name.value)))
      val umType  = Type.Apply(Type.Name(unmarshallerTypeName), Seq(apType))
      val mType   = Type.Apply(Type.Name(marshallerTypeName), Seq(apType))
      val typaramsWithRWCtxBounds = typarams.map(tp => tp.copy(cbounds = tp.cbounds :+ rwType))
      val typaramsWithRCtxBounds = typarams.map(tp => tp.copy(cbounds = tp.cbounds :+ rType))
      val typaramsWithWCtxBounds = typarams.map(tp => tp.copy(cbounds = tp.cbounds :+ wType))
      val rwTypeAp = Type.Apply(rwType, Seq(apType))
      val rwTypeStx = s"ReadWriter[${apType.syntax}]"

      val rw = MkImplicitRwDefn(companion, rwTypeStx) {
        q"""
        implicit def upickleRw[..$typaramsWithRWCtxBounds]: $rwTypeAp = {
          $usePickler.macroRW[$apType]
        }
        """
      }

      rw ++ Seq(
        // implicit marshaller def
        q"""
        implicit def akkaMarshaller[..$typaramsWithWCtxBounds]: $mType = {
          $marshallerMethod($marshallerMediaType)
             .compose[$apType](t => $usePickler.write(t))
        }
        """,
        // implicit unmarshaller def
        q"""
        implicit def akkaUnmarshaller[..$typaramsWithRCtxBounds]: $umType = {
          $unmarshallerMethod.mapWithCharset { (str, charset) =>
              $usePickler.read[$apType](str.decodeString(charset.nioCharset()))
            }
        }
        """
      )
    }

    def mkImplicitVal(typeName: Type.Name, companion: Option[Defn.Object]) = {
      val umApType  = Type.Apply(Type.Name(unmarshallerTypeName), Seq(typeName))
      val mApType   = Type.Apply(Type.Name(marshallerTypeName), Seq(typeName))
      val rwTypeAp  = Type.Apply(rwType, Seq(typeName))
      val rwTypeStx = s"ReadWriter[${typeName.syntax}]"

      val rw = MkImplicitRwDefn(companion, rwTypeStx) {
        q"""
        implicit val upickleRw: $rwTypeAp = {
          $usePickler.macroRW[$typeName]
        }
        """
      }

      rw ++ Seq(
        // implicit marshaller val
        q"""
        implicit val akkaMarshaller: $mApType = {
          $marshallerMethod($marshallerMediaType).compose[$typeName](t => $usePickler.write(t))
        }
        """,
        // implicit unmarshaller val
        q"""
        implicit val akkaUnmarshaller: $umApType = {
          $unmarshallerMethod.mapWithCharset { (str, charset) =>
            $usePickler.read[$typeName](str.decodeString(charset.nioCharset()))
          }
        }
        """
      )
    }

    def mkImplicit(name: Type.Name, typarams: Seq[Type.Param], companion: Option[Defn.Object]): Seq[Stat] = {
      if (typarams.isEmpty) {
        mkImplicitVal(name, companion)
      } else {
        mkImplicitDef(name, typarams, companion)
      }
    }

    val withGenerated = defn match {
      // companion object exists
      case ClassOrSealedTraitWithObject(cls, name, typarams, companion) =>
        val implUm = mkImplicit(name, typarams, Some(companion))
        val templateStats = implUm ++ companion.templ.stats.getOrElse(Nil)
        val newCompanion = companion.copy(templ = companion.templ.copy(stats = Some(templateStats)))
        Term.Block(Seq(cls, newCompanion))

      case ClassOrSealedTraitWithoutObject(cls, name, typarams) =>
        val implUm = mkImplicit(name, typarams, None)
        val companion   = q"object ${Term.Name(name.value)} { ..$implUm }"
        Term.Block(Seq(cls, companion))

      case _ =>
        println(defn.structure)
        abort("@unmarshal must annotate a class or a sealed trait.")
    }

    withGenerated
  }
}

private[micromarshal] object ClassOrSealedTraitWithObject {
  def unapply(arg: Term.Block): Option[(Defn, Type.Name, Seq[Type.Param], Defn.Object)] = {
    arg match {
      case Term.Block(Seq(t: Defn.Trait, comp: Defn.Object)) if t.mods.exists(_.syntax == "sealed") =>
        Some((t, t.name, t.tparams, comp))

      case Term.Block(Seq(c: Defn.Class, comp: Defn.Object)) =>
        Some((c, c.name, c.tparams, comp))

      case _ => None
    }
  }
}

private[micromarshal] object ClassOrSealedTraitWithoutObject {
  def unapply(arg: Defn): Option[(Defn, Type.Name, Seq[Type.Param])] = {
    arg match {
      case t: Defn.Trait if t.mods.exists(_.syntax == "sealed") =>
        Some((t, t.name, t.tparams))
      case c: Defn.Class =>
        Some((c, c.name, c.tparams))
      case _ => None
    }
  }
}

private[micromarshal] object MkImplicitRwDefn {
  // TODO semantic API would be really nice here
  def apply(companion: Option[Defn.Object], expectedRwTypeStx: String)(mkImplicit: => Stat): Seq[Stat] = {
    def isImplicitRwDefinition(mods: Seq[Mod], tpe: Type) = {
      mods.exists(_.syntax == "implicit") && expectedRwTypeStx == tpe.syntax
    }

    companion match {
      case None =>
        Seq(mkImplicit)
      case Some(Defn.Object(_, _, Template(_, _, _, Some(stats)))) =>
        val rwIsDefined = stats.exists {
          case Defn.Val(mods, _, Some(tpe), _) => isImplicitRwDefinition(mods, tpe)
          case Defn.Var(mods, _, Some(tpe), _) => isImplicitRwDefinition(mods, tpe)
          case Defn.Def(mods, _, _, _, Some(tpe), _) => isImplicitRwDefinition(mods, tpe)
          case _ => false
        }
        if (rwIsDefined) {
          Seq.empty
        } else {
          Seq(mkImplicit)
        }
    }
  }
}
