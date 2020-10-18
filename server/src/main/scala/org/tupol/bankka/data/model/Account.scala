package org.tupol.bankka.data.model

import java.util.UUID

case class AccountId(value: UUID = UUID.randomUUID()) {
  override def toString: String = value.toString
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
