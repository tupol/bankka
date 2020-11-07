package org.tupol.bankka.data.model

import java.util.UUID

import org.tupol.bankka.server.ClientActor.ClientException

import scala.util.{ Failure, Success, Try }

case class ClientId(value: String = UUID.randomUUID().toString) {
  override def toString: String = value
}

case class Client(id: ClientId, name: String, accounts: Map[String, Account] = Map(), active: Boolean = true) {
  def label: String = s"$id:$name"
  def account(id: AccountId): Try[Account] =
    accounts.get(id.toString) match {
      case None      => Failure(new ClientException("The source account must exist and belong to the client"))
      case Some(acc) => Success(acc)
    }
  def withAccount(account: Account): Client = this.copy(accounts = accounts + (account.id.toString -> account))
}
