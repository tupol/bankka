package org.tupol.bankka.server

import akka.actor.typed.{ActorSystem, Scheduler}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.json4s.ext.{JavaTimeSerializers, JavaTypesSerializers}
import org.tupol.bankka.data.dao.BankDao
import org.tupol.bankka.data.model.{AccountId, ClientId}
import org.tupol.bankka.server.ClientActor.{AccountResponse, ClientResponse, TransactionResponse}
import org.tupol.bankka.server.ClientSharding.bringClient

import scala.concurrent.duration._
import scala.util.{Failure, Success}

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

  private implicit val sharding: ClusterSharding = ClusterSharding(system)
  // This is very important to describe how the instances will be created inside the system
  ClientSharding.initializeSharding(sharding, bankDao, stashSize)

  val routes: Route =
    path("create") {
      post {
        formFields("client-name") { clientName =>
          val clientId = ClientId().toString
          val process = bringClient(clientId)
            .ask[ClientResponse](ref => ClientActor.CreateClient(clientName, ref))
          onComplete(process) {
            case Success(client) => complete(client)
            case Failure(error)  => error.printStackTrace; failWith(error)
          }
        }
      }
    } ~
      path("find") {
        get {
          formFields("client-id") { clientId =>
            val process = bringClient(clientId)
              .ask[ClientResponse](ref => ClientActor.GetClient(ref))
            onComplete(process) {
              case Success(client) => complete(client)
              case Failure(error)  => error.printStackTrace; failWith(error)
            }
          }
        }
      } ~
      path("activate") {
        post {
          formFields("client-id") { clientId =>
            val process = bringClient(clientId)
              .ask[ClientResponse](ref => ClientActor.ActivateClient(ref))
            onComplete(process) {
              case Success(client) => complete(client)
              case Failure(error)  => error.printStackTrace; failWith(error)
            }
          }
        }
      } ~
      path("deactivate") {
        post {
          formFields("client-id") { clientId =>
            val process = bringClient(clientId)
              .ask[ClientResponse](ref => ClientActor.DeactivateClient(ref))
            onComplete(process) {
              case Success(client) => complete(client)
              case Failure(error)  => error.printStackTrace; failWith(error)
            }
          }
        }
      } ~
      path("create-account") {
        post {
          formFields("client-id", "credit-limit", "initial-amount") { (clientId, creditLimit, initialAmount) =>
            val process = bringClient(clientId)
              .ask[AccountResponse](
                ref => ClientActor.CreateAccount(creditLimit.toLong, initialAmount.toLong, ref)
              )
            onComplete(process) {
              case Success(client) => complete(client)
              case Failure(error)  => error.printStackTrace; failWith(error)
            }
          }
        }
      } ~
      path("order-payment") {
        post {
          formFields("client-id", "from", "to", "amount") { (clientId, from, to, amount) =>
            val process = bringClient(clientId)
              .ask[TransactionResponse](
                ref => ClientActor.OrderPayment(AccountId(from), AccountId(to), amount.toLong, ref)
              )
            onComplete(process) {
              case Success(client) => complete(client)
              case Failure(error)  => error.printStackTrace; failWith(error)
            }
          }
        }
      }
}
