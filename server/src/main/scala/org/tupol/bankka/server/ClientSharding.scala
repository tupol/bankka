package org.tupol.bankka.server

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import org.tupol.bankka.data.dao.BankDao
import org.tupol.bankka.data.model.ClientId

object ClientSharding {

  val ClientTypeKey = EntityTypeKey[ClientActor.Message]("Clients")

  def initializeSharding(
    clusterSharding: ClusterSharding,
    bankDao: BankDao,
    stashSize: Int = 100
  ): ActorRef[ShardingEnvelope[ClientActor.Message]] =
    clusterSharding.init(Entity(ClientTypeKey) { entityContext =>
      ClientActor(ClientId(entityContext.entityId), bankDao, stashSize)
    })

}
