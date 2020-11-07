package org.tupol.bankka.data.model

import java.time.Instant
import java.util.UUID

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

case class TransactionId(value: String = UUID.randomUUID().toString) {
  override def toString: String = value
}

case class RejectionInfo(reason: String, rejectedAt: Instant = Instant.now())

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[StartTransaction], name = "StartTransaction"),
    new JsonSubTypes.Type(value = classOf[CompletedTransaction], name = "CompletedTransaction"),
    new JsonSubTypes.Type(value = classOf[RefundedTransaction], name = "RefundedTransaction"),
    new JsonSubTypes.Type(value = classOf[RejectedTransaction], name = "RejectedTransaction")))
sealed trait Transaction {
  def id: TransactionId
  def from: AccountId
  def to: AccountId
  def amount: Long
  def createdAt: Instant
  def completedAt: Option[Instant]
  def rejectionInfo: Option[RejectionInfo]
  def refundedAt: Option[Instant]
  final def completed: Boolean = completedAt.isDefined
  final def rejected: Boolean  = rejectionInfo.isDefined
  final def refunded: Boolean  = refundedAt.isDefined
}

case class StartTransaction(id: TransactionId, from: AccountId, to: AccountId, amount: Long) extends Transaction {
  require(amount > 0, "The transaction amount must be greater than 0")
  override val createdAt: Instant                   = Instant.now()
  override val completedAt: Option[Instant]         = None
  override val rejectionInfo: Option[RejectionInfo] = None
  override val refundedAt: Option[Instant]          = None
}
case class CompletedTransaction private (
  id: TransactionId,
  from: AccountId,
  to: AccountId,
  amount: Long,
  createdAt: Instant,
  completedAt: Option[Instant]
) extends Transaction {
  require(amount > 0, "The transaction amount must be greater than 0")
  override val rejectionInfo: Option[RejectionInfo] = None
  override val refundedAt: Option[Instant]          = None
}
object CompletedTransaction {
  def apply(startTransaction: StartTransaction): CompletedTransaction =
    CompletedTransaction(
      id = startTransaction.id,
      from = startTransaction.from,
      to = startTransaction.to,
      amount = startTransaction.amount,
      createdAt = startTransaction.createdAt,
      completedAt = Some(Instant.now())
    )
}
case class RejectedTransaction private (
  id: TransactionId,
  from: AccountId,
  to: AccountId,
  amount: Long,
  createdAt: Instant,
  completedAt: Option[Instant],
  rejectionInfo: Option[RejectionInfo]
) extends Transaction {
  override val refundedAt: Option[Instant] = None
}
object RejectedTransaction {
  def apply(transaction: StartTransaction, rejectionInfo: RejectionInfo): RejectedTransaction =
    RejectedTransaction(
      id = transaction.id,
      from = transaction.from,
      to = transaction.to,
      amount = transaction.amount,
      createdAt = transaction.createdAt,
      completedAt = Some(Instant.now()),
      rejectionInfo = Some(rejectionInfo)
    )
}
case class RefundedTransaction private (
  id: TransactionId,
  from: AccountId,
  to: AccountId,
  amount: Long,
  createdAt: Instant,
  completedAt: Option[Instant],
  rejectionInfo: Option[RejectionInfo],
  refundedAt: Option[Instant]
) extends Transaction
object RefundedTransaction {
  def apply(transaction: RejectedTransaction): RefundedTransaction =
    RefundedTransaction(
      id = transaction.id,
      from = transaction.from,
      to = transaction.to,
      amount = transaction.amount,
      createdAt = transaction.createdAt,
      completedAt = transaction.completedAt,
      rejectionInfo = transaction.rejectionInfo,
      refundedAt = Some(Instant.now())
    )
}

case class TransactionIllegalStateException(id: TransactionId) extends Exception
