package com.tkroman.micromarshal

import scala.reflect.ClassTag
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}

class MarshallingSuite  extends FunSuite
  with Matchers
  with ScalatestRouteTest
  with ScalaFutures {

  def routes[A: ClassTag: ToEntityMarshaller: FromEntityUnmarshaller]: Route = (pathSingleSlash & post) {
    entity(as[A]) { a => complete(a) }
  }

  test("simple case class w/o companion") {
    bidiCheck(SimpleNoCompanion(41, 1))
  }

  test("simple case class w/ companion & implicit ReadWriter defined") {
    bidiCheck(WithCompanionAndImplicitRw("str"))
  }

  test("sealed hierarchy") {
    bidiCheck[SimpleHierNoCompanion](TInt(1))
    bidiCheck[SimpleHierNoCompanion](TBool(true))
  }

  test("parametrized class hierarchy") {
    bidiCheck(Parametrized(1))
  }

  private def bidiCheck[A: ClassTag: ToEntityMarshaller: FromEntityUnmarshaller](a: A) = {
    Post("/", a) ~> routes[A] ~> check { responseAs[A] shouldBe a }
  }
}
