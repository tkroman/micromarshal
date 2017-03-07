package com.tkroman.micromarshal

import scala.reflect.ClassTag
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}
import upickle.AttributeTagged

class MarshallingSuite  extends FunSuite
  with Matchers
  with ScalatestRouteTest
  with ScalaFutures {

  def routes[A: ClassTag: ToEntityMarshaller: FromEntityUnmarshaller]: Route = (pathSingleSlash & post) {
    entity(as[A]) { a => complete(a) }
  }

  test("simple case class w/o companion") {
    bidiCheck(OptionPickler)(SimpleNoCompanionCustomPickler(41, 1))
  }

  test("simple case class w/ companion & implicit ReadWriter defined") {
    bidiCheck(upickle.default)(WithCompanionAndImplicitRw("str"))
  }

  test("sealed hierarchy") {
    bidiCheck(upickle.default)[SimpleHierNoCompanion](TInt(1))
    bidiCheck(upickle.default)[SimpleHierNoCompanion](TBool(true))
  }

  test("parametrized class hierarchy") {
    bidiCheck(upickle.default)(Parametrized(1))
    bidiCheck(upickle.default)(Parametrized(List(1,2,3)))
    bidiCheck(upickle.default)(Parametrized(SimpleNoCompanion(-10, 10)))
  }

  private def bidiCheck[P <: AttributeTagged](p: P) = {
    new {
      def apply[A: ClassTag: ToEntityMarshaller: FromEntityUnmarshaller: p.Reader: p.Writer](a: A) = {
        Post("/", a) ~> routes[A] ~> check { responseAs[A] shouldBe a }
      }
    }
  }
}
