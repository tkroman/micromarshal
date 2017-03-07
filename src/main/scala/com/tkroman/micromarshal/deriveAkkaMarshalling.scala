package com.tkroman.micromarshal

import scala.collection.immutable.Seq
import scala.meta._

class deriveAkkaMarshalling(picklerObject: String = "upickle.default")
  extends scala.annotation.StaticAnnotation {

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

    val picklerName = this.collect {
      case q"new $_(${Lit(p: String)})" => s"_root_.$p"
    }.headOption.getOrElse {
      "_root_.upickle.default"    // TODO read this from default arg?
    }

    val pickler = Term.Name(picklerName)
    val rwType = Type.Name(s"$pickler.ReadWriter")
    val rType = Type.Name(s"$pickler.Reader")
    val wType = Type.Name(s"$pickler.Writer")

    def mkImplicitDef(name: Type.Name, typarams: Seq[Type.Param], companion: Option[Defn.Object]) = {
      val apType  = Type.Apply(name, typarams.map(n => Type.Name(n.name.value)))
      val umType  = Type.Apply(Type.Name(unmarshallerTypeName), Seq(apType))
      val mType   = Type.Apply(Type.Name(marshallerTypeName), Seq(apType))

      def withCtxBound(b: Type.Name) = typarams.map(tp => tp.copy(cbounds = tp.cbounds :+ b))

      val typaramsWithRWCtxBounds = withCtxBound(rwType)
      val typaramsWithRCtxBounds = withCtxBound(rType)
      val typaramsWithWCtxBounds = withCtxBound(wType)

      val rwTypeAp = Type.Apply(rwType, Seq(apType))
      val rwTypeStx = s"ReadWriter[${apType.syntax}]"

      val rw = MkImplicitRwDefn(companion, rwTypeStx) {
        q"""
        implicit def upickleRw[..$typaramsWithRWCtxBounds]: $rwTypeAp = {
          $pickler.macroRW[$apType]
        }
        """
      }

      rw ++ Seq(
        // implicit marshaller def
        q"""
        implicit def akkaMarshaller[..$typaramsWithWCtxBounds]: $mType = {
          $marshallerMethod($marshallerMediaType)
             .compose[$apType](t => $pickler.write(t))
        }
        """,
        // implicit unmarshaller def
        q"""
        implicit def akkaUnmarshaller[..$typaramsWithRCtxBounds]: $umType = {
          $unmarshallerMethod.mapWithCharset { (str, charset) =>
            $pickler.read[$apType](str.decodeString(charset.nioCharset()))
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
          $pickler.macroRW[$typeName]
        }
        """
      }

      rw ++ Seq(
        // implicit marshaller val
        q"""
        implicit val akkaMarshaller: $mApType = {
          $marshallerMethod($marshallerMediaType).compose[$typeName](t => $pickler.write(t))
        }
        """,
        // implicit unmarshaller val
        q"""
        implicit val akkaUnmarshaller: $umApType = {
          $unmarshallerMethod.mapWithCharset { (str, charset) =>
            $pickler.read[$typeName](str.decodeString(charset.nioCharset()))
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
        abort("@deriveAkkaMarshalling must annotate a class or a sealed trait.")
    }

    withGenerated
  }
}

object Mods {
  def isSealed(mods: Seq[Mod]): Boolean = mods.exists("sealed" == _.syntax)
  def isImplicit(mods: Seq[Mod]): Boolean = mods.exists("implicit" == _.syntax)
}

private[micromarshal] object ClassOrSealedTraitWithObject {
  def unapply(arg: Term.Block): Option[(Defn, Type.Name, Seq[Type.Param], Defn.Object)] = {
    arg.collect {
      case Term.Block(Seq(t: Defn.Trait, comp: Defn.Object)) if Mods.isSealed(t.mods) =>
        (t, t.name, t.tparams, comp)

      case Term.Block(Seq(c: Defn.Class, comp: Defn.Object)) =>
        (c, c.name, c.tparams, comp)
    }.headOption
  }
}

private[micromarshal] object ClassOrSealedTraitWithoutObject {
  def unapply(arg: Defn): Option[(Defn, Type.Name, Seq[Type.Param])] = {
    arg.collect {
      case t: Defn.Trait if Mods.isSealed(t.mods) =>
        (t, t.name, t.tparams)
      case c: Defn.Class =>
        (c, c.name, c.tparams)
    }.headOption
  }
}

private[micromarshal] object MkImplicitRwDefn {
  // TODO semantic API would be really nice here
  def apply(companion: Option[Defn.Object], expectedRwTypeStx: String)(mkImplicit: => Stat): Seq[Stat] = {
    def isImplicitRwDefinition(mods: Seq[Mod], tpe: Type) = {
      Mods.isImplicit(mods) && expectedRwTypeStx == tpe.syntax
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
