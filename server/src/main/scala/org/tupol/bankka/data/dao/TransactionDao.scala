package org.tupol.bankka.data.dao

import org.tupol.bankka.data.model.{
  Account,
  AccountId,
  CompletedTransaction,
  RefundedTransaction,
  RejectedTransaction,
  RejectionInfo,
  StartTransaction,
  Transaction,
  TransactionId,
  TransactionIllegalStateException
}

import scala.concurrent.{ ExecutionContext, Future }

trait TransactionDao {
  def create(from: AccountId, to: AccountId, amount: Long): Future[Transaction]
  def createSameClient(from: Account, to: Account, amount: Long): Future[Transaction]
  def findById(id: TransactionId): Future[Option[Transaction]]
  def findAll: Future[Iterable[Transaction]]
  def findAllStarted: Future[Iterable[StartTransaction]]
  def findAllCompleted: Future[Iterable[CompletedTransaction]]
  def findAllRejected: Future[Iterable[RejectedTransaction]]
  def findAllRefunded: Future[Iterable[RefundedTransaction]]
  def complete(transaction: Transaction): Future[Transaction]
  def reject(transaction: Transaction, rejectionInfo: RejectionInfo): Future[Transaction]
  def refund(transaction: Transaction): Future[Transaction]
}
class InMemoryTransactionDao(implicit ec: ExecutionContext) extends TransactionDao {

  private var transactions = Map.empty[TransactionId, Transaction]

  override def create(from: AccountId, to: AccountId, amount: Long): Future[StartTransaction] =
    Future {
      synchronized {
        val transaction = StartTransaction(TransactionId(), from, to, amount)
        transactions = transactions + (transaction.id -> transaction)
        transaction
      }
    }

  override def createSameClient(from: Account, to: Account, amount: Long): Future[CompletedTransaction] =
    Future {
      require(from.clientId == to.clientId, "both accounts need to belong to the same client")
      synchronized {
        val transaction = CompletedTransaction(StartTransaction(TransactionId(), from.id, to.id, amount))
        transactions = transactions + (transaction.id -> transaction)
        transaction
      }
    }

  override def findById(id: TransactionId): Future[Option[Transaction]] =
    Future {
      synchronized {
        transactions.get(id)
      }
    }
  override def complete(transaction: Transaction): Future[Transaction] =
    transaction match {
      case tx: StartTransaction =>
        Future {
          synchronized {
            val completedTransaction = CompletedTransaction(tx)
            transactions.updated(transaction.id, completedTransaction)
            completedTransaction
          }
        }
      case _ =>
        Future.failed(TransactionIllegalStateException(transaction.id))
    }
  override def reject(transaction: Transaction, rejectionInfo: RejectionInfo): Future[Transaction] =
    transaction match {
      case tx: StartTransaction =>
        Future {
          synchronized {
            val rejectedTransaction = RejectedTransaction(tx, rejectionInfo)
            transactions.updated(transaction.id, rejectedTransaction)
            rejectedTransaction
          }
        }
      case _ =>
        Future.failed(TransactionIllegalStateException(transaction.id))
    }

  override def refund(transaction: Transaction): Future[Transaction] =
    transaction match {
      case tx: RejectedTransaction =>
        Future {
          synchronized {
            val refundedTransaction = RefundedTransaction(tx)
            transactions.updated(transaction.id, refundedTransaction)
            refundedTransaction
          }
        }
      case _ =>
        Future.failed(TransactionIllegalStateException(transaction.id))
    }

  override def findAllStarted: Future[Iterable[StartTransaction]] =
    Future.successful(transactions.collect { case (_, tx: StartTransaction) => tx })

  override def findAllCompleted: Future[Iterable[CompletedTransaction]] =
    Future.successful(transactions.collect { case (_, tx: CompletedTransaction) => tx })

  override def findAllRejected: Future[Iterable[RejectedTransaction]] =
    Future.successful(transactions.collect { case (_, tx: RejectedTransaction) => tx })

  override def findAllRefunded: Future[Iterable[RefundedTransaction]] =
    Future.successful(transactions.collect { case (_, tx: RefundedTransaction) => tx })

  override def findAll: Future[Iterable[Transaction]] =
    Future.successful(transactions.values)
}
