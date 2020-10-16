package org.tupol.takkagotchi.server

import akka.actor.typed.{ ActorSystem, Scheduler }
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.json4s.ext.{ JavaTimeSerializers, JavaTypesSerializers }
import org.tupol.takkagotchi.being.{ Bing, BingCharacteristics, BingStatus }

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

class ServerRoutes(
  system: ActorSystem[_],
  bingCharacterstics: BingCharacteristics
) {

  import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
  import org.json4s.{ DefaultFormats, Formats }

  implicit val formats: Formats = DefaultFormats ++ JavaTypesSerializers.all ++ JavaTimeSerializers.all
  implicit val serialization    = org.json4s.jackson.Serialization

  implicit val timeout: Timeout     = 3.seconds
  implicit val ec                   = system.executionContext
  implicit val scheduler: Scheduler = system.scheduler

  private val sharding: ClusterSharding = ClusterSharding(system)
  // This is very important to describe how the instances will be created inside the system
  BingSharding.initializeSharding(sharding, bingCharacterstics)

  def bringBing(name: String) =
    sharding
      .entityRefFor(BingSharding.BingTypeKey, name)

  val routes: Route =
    path("create" / Segment) { bingName =>
      get {
        bringBing(bingName).tell(BingActor.Hatch)
        complete(bingName)
      }
    } ~
      path("find" / Segment) { bingName =>
        get {
          onComplete(bringBing(bingName).ask[Bing](ref => BingActor.WhoAreYou(ref))) {
            case Success(bing) => complete(bing)
            case Failure(error) =>
              error.printStackTrace()
              failWith(error)
          }
        }
      } ~
      path("status" / Segment) { bingName =>
        get {
          onComplete(bringBing(bingName).ask[BingStatus](ref => BingActor.HowAreYou(ref))) {
            case Success(status) => complete(status.toString)
            case Failure(error)  => failWith(error)
          }
        }
      } ~
      path("hello" / Segment) { bingName =>
        post {
          formFields("from") { name =>
            onComplete(bringBing(bingName).ask[String](ref => BingActor.GreetingsFrom(name, ref))) {
              case Success(reply) => complete(reply)
              case Failure(error) => failWith(error)
            }
          }
        }

      } ~
      path("feed" / Segment) { bingName =>
        post {
          formFields("food") { food =>
            bringBing(bingName).tell(BingActor.Feed(food))
            complete(s"Bing $bingName was fed with $food")
          }
        }
      } ~
      path("teach" / Segment) { bingName =>
        post {
          formFields("lesson") { lesson =>
            bringBing(bingName).tell(BingActor.Teach(lesson))
            complete(s"$bingName learned $lesson")
          }
        }
      }
}
