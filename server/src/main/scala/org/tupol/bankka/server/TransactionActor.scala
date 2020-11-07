package org.tupol.bankka.server

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.event.Logging.Error.NoCause
import org.tupol.bankka.commons.SerializableMessage
import org.tupol.bankka.data.dao.BankDao
import org.tupol.bankka.data.model._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import TransactionActor._
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout

object TransactionActor {
  def apply(transactionId: TransactionId, bankDao: BankDao)(implicit sharding: ClusterSharding): Behavior[Message] =
    Behaviors.setup[Message] { context =>
      new TransactionActor(context, transactionId, bankDao).execute()
    }

  sealed trait Message  extends SerializableMessage
  sealed trait Request  extends Message
  sealed trait Response extends Message


  case class Execute(replyTo: Option[ActorRef[Response]] = None) extends Request

  case class TransactionResult(transaction: Transaction) extends Response
  case class TransactionException(private val message: String, cause: Throwable = NoCause)
    extends Exception(message, cause)
      with Response
  case class TransactionTargetNotFound(transactionId: TransactionId) extends Response
  case class TransactionNotFound(transactionId: TransactionId) extends Response
}

class TransactionActor(context: ActorContext[Message], transactionId: TransactionId, bankDao: BankDao)(implicit sharding: ClusterSharding) {
  implicit val ec: ExecutionContextExecutor = context.executionContext
  implicit val timeout: Timeout     = 3.seconds


  private def execute(): Behavior[Message] =
    Behaviors.receiveMessagePartial[Message] {
      case Execute(replyTo) =>
        context.log.info(s"Executing transaction $transactionId.")

        def process: Future[Transaction] = for {
          inputTransaction <- bankDao.transactionDao
            .findById(transactionId)
            .flatMap {
              case Some(tx) => Future.successful(tx)
              case None => Future.failed(TransactionException("Transaction not found"))
            }
          response <- bankDao.accountDao.findById(inputTransaction.to).flatMap {
            case Some(account) =>
              ClientSharding
                .bringClient(account.clientId.toString)
                .ask[ClientActor.TransactionResponse](ref => ClientActor.ReceivePayment(inputTransaction, ref))
                .flatMap {
                  case r: ClientActor.TransactionResult => Future.successful(r.transaction)
                  case e: ClientActor.TransactionException => Future.failed(TransactionException("oops", e))
                  case _  => Future.failed(TransactionException("oops"))
                }
            case None => Future.failed(TransactionException("Target account not found"))
          }
        } yield response

        context.pipeToSelf(process) {
          case Success(transaction) => TransactionResult(transaction)
          case Failure(exception) => TransactionException("Could not execute transaction", exception)
        }
        busyWithTransaction(replyTo)
    }

  private def busyWithTransaction(replyTo: Option[ActorRef[Response]]): Behavior[Message] =
    Behaviors.receiveMessage[Message] {
      case response: TransactionResult =>
        replyTo.map(_ ! response)
        Behaviors.stopped
      case error: TransactionException =>
        throw error
      case other =>
        Behaviors.ignore
    }

}
