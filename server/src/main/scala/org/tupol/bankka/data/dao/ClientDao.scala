package org.tupol.bankka.data.dao

import org.tupol.bankka.data.model.{ Account, AccountId, Client, ClientId }

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }

trait ClientDao {
  def create(id: ClientId, name: String, accounts: Map[String, Account] = Map()): Future[Client]
  def findById(id: ClientId): Future[Option[Client]]
  def findByName(name: String): Future[Iterable[Client]]
  def update(client: Client): Future[Client]
}

class InMemoryClientDao(implicit ec: ExecutionContext) extends ClientDao {
  private val clients = mutable.Map.empty[ClientId, Client]

  override def create(id: ClientId, name: String, accounts: Map[String, Account] = Map()): Future[Client] =
    Future {
      synchronized {
        val client = Client(id, name, accounts)
        clients.addOne(client.id -> client)
        client
      }
    }

  override def findById(id: ClientId): Future[Option[Client]] =
    Future {
      synchronized {
        clients.get(id)
      }
    }

  override def findByName(name: String): Future[Iterable[Client]] =
    Future {
      synchronized {
        clients.values.filter(_.name == name)
      }
    }

  override def update(client: Client): Future[Client] =
    Future {
      synchronized {
        clients.addOne(client.id, client)
      }
      client
    }

}
