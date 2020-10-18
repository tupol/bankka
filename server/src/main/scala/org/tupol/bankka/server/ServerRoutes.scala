package org.tupol.bankka.server

import akka.actor.typed.{ ActorSystem, Scheduler }
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.json4s.ext.{ JavaTimeSerializers, JavaTypesSerializers }
import org.tupol.bankka.data.dao.BankDao
import org.tupol.bankka.data.model.ClientId
import org.tupol.bankka.server.ClientActor.ClientResponse

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class ServerRoutes(
  system: ActorSystem[_],
  bankDao: BankDao,
  stashSize: Int = 100
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
  ClientSharding.initializeSharding(sharding, bankDao, stashSize)

  def bringClient(name: String) =
    sharding
      .entityRefFor(ClientSharding.ClientTypeKey, name)

  val routes: Route =
    path("create") {
      post {
        formFields("clientName") { clientName =>
          val clientId = ClientId().toString
          val process = bringClient(clientId)
            .ask[ClientResponse](ref => ClientActor.CreateClient(clientName, ref))
          onComplete(process) {
            case Success(client) => complete(client)
            case Failure(error) =>
              error.printStackTrace()
              failWith(error)
          }
        }
      }
    } ~
      path("find") {
        get {
          formFields("clientId") { clientId =>
            val process = bringClient(clientId)
              .ask[ClientResponse](ref => ClientActor.Get(ref))
            onComplete(process) {
              case Success(client) => complete(client)
              case Failure(error) =>
                error.printStackTrace()
                failWith(error)
            }
          }
        }
      }
}
