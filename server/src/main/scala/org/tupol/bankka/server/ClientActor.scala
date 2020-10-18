package org.tupol.bankka.server

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import org.tupol.bankka.commons.SerializableMessage
import org.tupol.bankka.data.dao.BankDao
import org.tupol.bankka.data.model._
import org.tupol.bankka.server.ClientActor.{ClientResponse, _}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

object ClientActor {

  def apply(clientId: ClientId, bankDao: BankDao, stashSize: Int): Behavior[Message] =
    Behaviors.setup[Message] { context =>
      apply(context, clientId, bankDao, stashSize)
    }

  def apply(context: ActorContext[Message], clientId: ClientId, bankDao: BankDao, stashSize: Int): Behavior[Message] =
    Behaviors.withStash(stashSize) { buffer =>
      new ClientActor(context, buffer, clientId, bankDao).start()
    }

  sealed trait Message  extends SerializableMessage
  sealed trait Request  extends Message
  sealed trait Response extends Message

  case class CreateClient(name: String, replyTo: ActorRef[ClientResponse]) extends Request

  case class Get(replyTo: ActorRef[ClientResponse]) extends Request

  case class Deactivate(replyTo: ActorRef[ClientResponse]) extends Request

  case class Activate(replyTo: ActorRef[ClientResponse]) extends Request

  case class CreateAccount(creditLimit: Long = 0, amount: Long = 0, replyTo: ActorRef[AccountResponse]) extends Request

  case class OrderPayment(from: AccountId, to: AccountId, amount: Long, replyTo: ActorRef[Try[TransactionResponse]])
      extends Request

  case class ReceivePayment(transaction: Transaction, replyTo: ActorRef[Try[TransactionResponse]]) extends Request

  class ClientException(private val message: String, cause: Throwable = null)
      extends Exception(message, cause)
      with Response

  sealed trait ClientResponse extends Response

  case class ClientResult(client: Client) extends ClientResponse

  case class ClientResponseException(private val message: String) extends ClientException(message)

  case class ClientNotFound(clientId: ClientId) extends ClientException(s"Client not found: $clientId") with ClientResponse

  case class ClientAlreadyExists(clientId: ClientId) extends ClientException(s"Client already exists: $clientId") with ClientResponse

  case class AccountResponse(accountId: AccountId, client: Client) extends Response

  case class AccountError(private val message: String, cause: Throwable = null)
      extends Exception(message, cause)
      with Response

  case class TransactionResponse(transaction: Transaction, client: Client) extends Response

  case class TransactionError(private val message: String, cause: Throwable = null)
      extends Exception(message, cause)
      with Response

}

class ClientActor(context: ActorContext[Message], buffer: StashBuffer[Message], clientId: ClientId, bankDao: BankDao) {

  implicit val ec: ExecutionContextExecutor = context.executionContext

  private def start(): Behavior[Message] =
    Behaviors.receivePartial[Message] {
      case (context, CreateClient(name, replyTo)) =>
        context.log.info(s"Creating client $name.")
        context.pipeToSelf(bankDao.clientDao.create(clientId, name)) {
          case Success(client) => ClientResult(client)
          case Failure(cause)  => new ClientException(s"Error while creating client $name.", cause)
        }
        busyWithClient(replyTo)
      case (context, Get(replyTo)) =>
        context.log.info(s"Retrieving client $clientId.")
        context.pipeToSelf(bankDao.clientDao.findById(clientId)) {
          case Success(Some(client)) => ClientResult(client)
          case Success(None)         => ClientNotFound(clientId)
          case Failure(cause)        => new ClientException("Error while searching for the client", cause)
        }
        busyWithClient(replyTo)
    }

  private def busyWithTransaction(replyTo: ActorRef[Try[TransactionResponse]]): Behavior[Message] =
    Behaviors.receiveMessage[Message] {
      case response: TransactionResponse =>
        replyTo ! Success(response)
        buffer.unstashAll(active(response.client))
      case exception: TransactionError =>
        replyTo ! Failure(exception)
        throw exception
      case other =>
        println(s"Busy with transaction; Stashing $other")
        buffer.stash(other)
        Behaviors.same
    }

  private def busyWithAccount(replyTo: ActorRef[AccountResponse]): Behavior[Message] =
    Behaviors.receiveMessage[Message] {
      case response: AccountResponse =>
        replyTo ! response
        buffer.unstashAll(active(response.client))
      case exception: AccountError =>
        throw exception
      case other =>
        println(s"Busy with account; Stashing $other")
        buffer.stash(other)
        Behaviors.same
    }

  private def busyWithClient(replyTo: ActorRef[ClientResponse]): Behavior[Message] =
    Behaviors.receiveMessage[Message] {
      case response: ClientResult =>
        replyTo ! response
        buffer.unstashAll(active(response.client))
      case exception: ClientException =>
        throw exception
      case other =>
        println(s"Stashing $other")
        buffer.stash(other)
        Behaviors.same
    }

  private def active(client: Client): Behavior[Message] = Behaviors.receiveMessage[Message] {
    case Get(replyTo) =>
      replyTo ! ClientResult(client)
      Behaviors.same
    case OrderPayment(from, to, amount, replyTo) =>
      context.pipeToSelf(orderPayment(client, from, to, amount)) {
        case Success((t, c)) => TransactionResponse(t, c)
        case Failure(cause)  => TransactionError("Order payment failed", cause)
      }
      busyWithTransaction(replyTo)
    case ReceivePayment(transaction: Transaction, replyTo) =>
      context.pipeToSelf(receivePayment(client, transaction)) {
        case Success((t, c)) => TransactionResponse(t, c)
        case Failure(cause)  => TransactionError("Receive payment failed", cause)
      }
      busyWithTransaction(replyTo)
    case CreateAccount(creditLimit, amount, replyTo) =>
      context.pipeToSelf(bankDao.accountDao.create(clientId, creditLimit, amount)) {
        case Success(account) => AccountResponse(account.id, client.withAccount(account))
        case Failure(cause)   => AccountError("Error creating account", cause)
      }
      busyWithAccount(replyTo)
  }

  private def orderPayment(client: Client, from: AccountId, to: AccountId, amount: Long)(
    implicit ec: ExecutionContext
  ): Future[(Transaction, Client)] =
    for {
      _    <- Future.fromTry(Try(require(amount > 0, "The amount must be greater than 0")))
      _    <- Future.fromTry(Try(require(from != to, "A payment must be done between different accounts")))
      from <- Future.fromTry(client.account(from))
      result <- client.account(to) match {
                 case Success(clientTo) =>
                   bankDao.sameClientTransfer(from, clientTo, amount).map {
                     case (from, to, transaction) =>
                       (transaction, client.withAccount(from).withAccount(to))
                   }
                 case Failure(_) =>
                   bankDao.otherClientTransfer(from, to, amount).map {
                     case (from, transaction) =>
                       (transaction, client.withAccount(from))
                   }
               }
    } yield result

  private def receivePayment(client: Client, transaction: Transaction)(
    implicit ec: ExecutionContext
  ): Future[(Transaction, Client)] =
    for {
      _  <- Future.fromTry(Try(require(transaction.amount > 0, "The amount must be greater than 0")))
      to <- Future.fromTry(client.account(transaction.to))
      _  <- Future.fromTry(Try(require(to.active, "The target account is not active")))
      result <- bankDao.execute(transaction).map {
                 case (transaction, Some(to)) => (transaction, client.withAccount(to))
                 case (transaction, None)     => (transaction, client)
               }
    } yield result

}
