package com.example.api

import akka.http.interop.HttpServer
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{ Framing, Sink, Source }
import akka.util.ByteString
import com.example.api.JsonSupport._
import com.example.application.ApplicationService
import com.example.domain._
import com.example.interop.akka.ZioRouteTest
import play.api.libs.json.JsObject
import zio._
import zio.blocking._
import zio.clock.Clock
import zio.duration.Duration
import zio.test.Assertion._
import zio.test._

import scala.concurrent.duration._

object ApiSpec extends ZioRouteTest {

  private val env =
    (ZLayer.succeed(HttpServer.Config("localhost", 8080)) ++
      InMemoryItemRepository.test) >>>
      Api.live.passthrough ++ Blocking.live ++ Clock.live

  private def allItems: ZIO[ItemRepository, Throwable, List[Item]] = ApplicationService.getItems.mapError(_.asThrowable)

  private val specs: Spec[ItemRepository with Blocking with Api with Clock, TestFailure[Throwable], TestSuccess] =
    suite("Api")(
      testM("Add item on POST to '/items'") {
        val item = CreateItemRequest("name", 100.0)

        for {
          routes  <- Api.routes
          entity  <- ZIO.fromFuture(_ => Marshal(item).to[MessageEntity])
          request = Post("/items").withEntity(entity)
          resultCheck <- effectBlocking(request ~> routes ~> check {
                          // Here and in other tests we have to evaluate response on the spot before passing anything to `assert`.
                          // This is due to really tricky nature of how `check` works with the result (no simple workaround found so far)
                          val theStatus = status
                          val theCT     = contentType
                          val theBody   = entityAs[Item]
                          assert(theStatus)(equalTo(StatusCodes.OK)) &&
                          assert(theCT)(equalTo(ContentTypes.`application/json`)) &&
                          assert(theBody)(equalTo(Item(ItemId(0), "name", 100.0)))
                        })
          contentsCheck <- assertM(allItems)(equalTo(List(Item(ItemId(0), "name", 100.0))))
        } yield resultCheck && contentsCheck
      },
      testM("Not allow malformed json on POST to '/items'") {
        val item = JsObject.empty
        for {
          routes  <- Api.routes
          entity  <- ZIO.fromFuture(_ => Marshal(item).to[MessageEntity])
          request = Post("/items").withEntity(entity)
          resultCheck <- effectBlocking(request ~> routes ~> check {
                          val r = response
                          assert(r.status)(equalTo(StatusCodes.BadRequest))
                        })
          contentsCheck <- assertM(allItems)(isEmpty)
        } yield resultCheck && contentsCheck
      },
      testM("Return all items on GET to '/items'") {
        val items = List(Item(ItemId(0), "name", 100.0), Item(ItemId(1), "name2", 200.0))

        for {
          _      <- ZIO.foreach(items)(i => ApplicationService.addItem(i.name, i.price)).mapError(_.asThrowable)
          routes <- Api.routes
          resultCheck <- effectBlocking(Get("/items") ~> routes ~> check {
                          val theStatus = status
                          val theCT     = contentType
                          val theBody   = entityAs[List[Item]]
                          assert(theStatus)(equalTo(StatusCodes.OK)) &&
                          assert(theCT)(equalTo(ContentTypes.`application/json`)) &&
                          assert(theBody)(hasSameElements(items))
                        })
          contentsCheck <- assertM(allItems)(hasSameElements(items))
        } yield resultCheck && contentsCheck
      },
      testM("Delete item on DELETE to '/items/:id'") {
        val items = List(Item(ItemId(0), "name", 100.0), Item(ItemId(1), "name2", 200.0))

        for {
          _      <- ZIO.foreach(items)(i => ApplicationService.addItem(i.name, i.price)).mapError(_.asThrowable)
          routes <- Api.routes
          resultCheck <- effectBlocking(Delete("/items/1") ~> routes ~> check {
                          val s = status
                          assert(s)(equalTo(StatusCodes.OK))
                        })
          contentsCheck <- assertM(allItems)(hasSameElements(items.take(1)))
        } yield resultCheck && contentsCheck
      }  ,
      testM("Notify about deleted items via SSE endpoint") {
        val items = List(Item(ItemId(0), "name", 100.0), Item(ItemId(1), "name2", 200.0))

        for {
          _      <- ZIO.foreach(items)(i => ApplicationService.addItem(i.name, i.price)).mapError(_.asThrowable)
          routes <- Api.routes
          fiber    <- firstNElements(Get("/sse/items/deleted"), routes)(3).fork
          _        <- ZIO.sleep(Duration.fromScala(1.second))
          _        <- ApplicationService.deleteItem(ItemId(1)).mapError(_.asThrowable)
          _        <- ApplicationService.deleteItem(ItemId(2)).mapError(_.asThrowable)
          messages <- fiber.join
        } yield assert(messages.filterNot(_ == "data:"))(hasSameElements(List("data:1", "data:2")))
      }   ) @@ TestAspect.sequential

  def firstNElements(request: HttpRequest, route: Route)(n: Long): Task[Seq[String]] =
    ZIO.fromFuture(_ =>
      Source
        .single(request)
        .via(Route.handlerFlow(route))
        .flatMapConcat(
          _.entity.dataBytes
            .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 100, allowTruncation = true))
            .map(_.utf8String)
            .filter(_.nonEmpty)
        )
        .take(n)
        .runWith(Sink.seq)
    )

  def spec = specs.provideLayer(env)
}
