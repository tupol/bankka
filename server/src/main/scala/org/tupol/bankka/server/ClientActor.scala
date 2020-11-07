package org.tupol.bankka.server

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.event.Logging.Error.NoCause
import org.tupol.bankka.commons.SerializableMessage
import org.tupol.bankka.data.dao.BankDao
import org.tupol.bankka.data.model._
import org.tupol.bankka.server.ClientActor.{ ClientResponse, _ }

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success, Try }

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

  sealed trait ClientResponse                                     extends Response

  sealed trait AccountResponse                                   extends Response

  sealed trait TransactionResponse                                       extends Response

  case class CreateClient(name: String, replyTo: ActorRef[ClientResponse]) extends Request

  case class GetClient(replyTo: ActorRef[ClientResponse])                  extends Request

  case class DeactivateClient(replyTo: ActorRef[ClientResponse])           extends Request

  case class ActivateClient(replyTo: ActorRef[ClientResponse])             extends Request

  case class ClientResult(client: Client)                         extends ClientResponse

  case class ClientResponseException(private val message: String) extends ClientResponse

  case class ClientNotFound(clientId: ClientId)                   extends ClientResponse

  case class ClientAlreadyExists(clientId: ClientId)              extends ClientResponse

  case class ClientException(private val message: String, cause: Throwable = NoCause)
      extends Exception(message, cause)
      with ClientResponse

  case class CreateAccount(creditLimit: Long = 0, amount: Long = 0, replyTo: ActorRef[AccountResponse]) extends Request

  case class AccountResult(accountId: AccountId, client: Client) extends AccountResponse

  case class AccountException(private val message: String, cause: Throwable = NoCause)
      extends Exception(message, cause)
      with AccountResponse

  case class OrderPayment(from: AccountId, to: AccountId, amount: Long, replyTo: ActorRef[TransactionResponse])
      extends Request

  case class ReceivePayment(transaction: Transaction, replyTo: ActorRef[TransactionResponse]) extends Request

  case class TransactionResult(transaction: Transaction, client: Client) extends TransactionResponse
  case class TransactionException(private val message: String, cause: Throwable = NoCause)
      extends Exception(message, cause)
      with TransactionResponse

}

class ClientActor(context: ActorContext[Message], buffer: StashBuffer[Message], clientId: ClientId, bankDao: BankDao) {

  implicit val ec: ExecutionContextExecutor = context.executionContext

  private def start(): Behavior[Message] =
    Behaviors.receiveMessagePartial[Message] {
      case CreateClient(name, replyTo) =>
        context.log.info(s"Creating client $name with id $clientId.")
        context.pipeToSelf(bankDao.clientDao.create(clientId, name)) {
          case Success(client) => ClientResult(client)
          case Failure(cause)  => ClientException(s"Error while creating client $name.", cause)
        }
        busyWithClient(replyTo)
      case GetClient(replyTo) =>
        context.log.info(s"Retrieving client $clientId.")
        context.pipeToSelf(bankDao.clientDao.findById(clientId)) {
          case Success(Some(client)) => ClientResult(client)
          case Success(None)         => ClientNotFound(clientId)
          case Failure(cause)        => ClientException("Error while searching for the client", cause)
        }
        busyWithClient(replyTo)
    }

  private def busyWithTransaction(replyTo: ActorRef[TransactionResponse]): Behavior[Message] =
    Behaviors.receiveMessage[Message] {
      case response: TransactionResult =>
        replyTo ! response
        buffer.unstashAll(active(response.client))
      case error: TransactionException =>
        replyTo ! error
        throw error
      case other =>
        context.log.debug(s"Busy with transaction; Stashing $other")
        buffer.stash(other)
        Behaviors.same
    }

  private def busyWithAccount(replyTo: ActorRef[AccountResponse]): Behavior[Message] =
    Behaviors.receiveMessage[Message] {
      case response: AccountResult =>
        replyTo ! response
        buffer.unstashAll(active(response.client))
      case error: AccountException =>
        replyTo ! error
        throw error
      case other =>
        context.log.debug(s"Busy with account; Stashing $other")
        buffer.stash(other)
        Behaviors.same
    }

  private def busyWithClient(replyTo: ActorRef[ClientResponse]): Behavior[Message] =
    Behaviors.receiveMessage[Message] {
      case response: ClientResult =>
        replyTo ! response
        buffer.unstashAll(active(response.client))
      case exception: ClientException =>
        exception.printStackTrace()
        throw exception
      case other =>
        context.log.debug(s"Stashing $other")
        buffer.stash(other)
        Behaviors.same
    }

  private def active(client: Client): Behavior[Message] =
    Behaviors.receiveMessage[Message] {
      case GetClient(replyTo) =>
        replyTo ! ClientResult(client)
        buffer.unstashAll(active(client))
      case ActivateClient(replyTo) =>
        context.log.info(s"Activating client $clientId.")
        if (client.active) {
          context.log.debug(s"The client ${client.id} is already active")
          replyTo ! ClientException(s"The client ${client.id} is already active")
          Behaviors.same
        } else {
          context.pipeToSelf(bankDao.clientDao.update(client.copy(active = true))) {
            case Success(client) => ClientResult(client)
            case Failure(cause)  => ClientException(s"Error while activating the client ${client.id} ", cause)
          }
          busyWithClient(replyTo)
        }
      case DeactivateClient(replyTo) =>
        context.log.info(s"Deactivating client $clientId.")
        if (!client.active) {
          replyTo ! ClientException(s"The client  ${client.id} is already deactivated")
          Behaviors.same
        } else {
          context.pipeToSelf(bankDao.clientDao.update(client.copy(active = false))) {
            case Success(client) => ClientResult(client)
            case Failure(cause)  => ClientException(s"Error while deactivating the client ${client.id}", cause)
          }
          busyWithClient(replyTo)
        }
      case OrderPayment(from, to, amount, replyTo) =>
        context.log.info(s"Ordering payment for client $clientId.")
        context.pipeToSelf(orderPayment(client, from, to, amount)) {
          case Success((t, c)) => TransactionResult(t, c)
          case Failure(cause)  => TransactionException(s"Order payment failed for client ${client.id}", cause)
        }
        busyWithTransaction(replyTo)
      case ReceivePayment(transaction: Transaction, replyTo) =>
        context.log.info(s"Receiving payment for client $clientId.")
        context.pipeToSelf(receivePayment(client, transaction)) {
          case Success((t, c)) => TransactionResult(t, c)
          case Failure(cause)  => TransactionException(s"Receive payment failed for client ${client.id}", cause)
        }
        busyWithTransaction(replyTo)
      case CreateAccount(creditLimit, amount, replyTo) =>
        context.log.info(s"Creating account for client $clientId with creditLimit $creditLimit and amount $amount")
        context.pipeToSelf(bankDao.accountDao.create(clientId, creditLimit, amount)) {
          case Success(account) => AccountResult(account.id, client.withAccount(account))
          case Failure(cause)   => AccountException(s"Error creating account for client ${client.id}", cause)
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
