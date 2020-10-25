package org.tupol.bankka.data.model

import java.util.UUID

import scala.util.Try

case class AccountId(value: UUID = UUID.randomUUID()) {
  override def toString: String = value.toString
}
object AccountId {
  def fromString(value: String): Try[AccountId] = Try(AccountId(UUID.fromString(value)))
}

case class Account(id: AccountId, clientId: ClientId, creditLimit: Long, amount: Long, active: Boolean = true) {
  require(amount >= -creditLimit, "The account amount can no be smaller than the credit limit")
  def credit(amount: Long): Account = {
    require(amount > 0, "Amount must be greater than 0")
    this.copy(amount = this.amount + amount)
  }
  def debit(amount: Long): Account = {
    require(amount > 0, "Amount must be greater than 0")
    this.copy(amount = this.amount - amount)
  }
}
