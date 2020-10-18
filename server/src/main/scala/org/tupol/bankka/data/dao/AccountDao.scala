package org.tupol.bankka.data.dao

import org.tupol.bankka.data.model
import org.tupol.bankka.data.model.{ Account, AccountId, ClientId }

import scala.concurrent.{ ExecutionContext, Future }

trait AccountDao {
  def create(clientId: ClientId, creditLimit: Long = 0, amount: Long = 0): Future[Account]
  def findById(accountId: AccountId): Future[Option[Account]]
  def findByClientId(clientId: ClientId): Future[Iterable[Account]]
  def update(account: Account): Future[Account]
}

class InMemoryAccountDao(implicit ec: ExecutionContext) extends AccountDao {
  private var accounts = Map.empty[AccountId, Account]

  override def create(clientId: ClientId, creditLimit: Long = 0, amount: Long = 0): Future[Account] =
    Future {
      synchronized {
        val account = model.Account(AccountId(), clientId, creditLimit, amount)
        accounts = accounts + (account.id -> account)
        account
      }
    }

  override def findById(accountId: AccountId): Future[Option[Account]] =
    Future {
      synchronized {
        accounts.get(accountId)
      }
    }

  override def findByClientId(clientId: ClientId): Future[Iterable[Account]] =
    Future {
      synchronized {
        accounts.values.filter(_.clientId == clientId)
      }
    }

  override def update(account: Account): Future[Account] =
    Future {
      synchronized {
        accounts = accounts.updated(account.id, account)
      }
      account
    }

}
