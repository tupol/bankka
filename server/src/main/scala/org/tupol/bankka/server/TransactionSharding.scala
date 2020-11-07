package org.tupol.bankka.server

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef, EntityTypeKey}
import org.tupol.bankka.data.dao.BankDao
import org.tupol.bankka.data.model.TransactionId

object TransactionSharding {

  val TransactionTypeKey = EntityTypeKey[TransactionActor.Message]("Transactions")

  def initializeSharding(
    clusterSharding: ClusterSharding,
    bankDao: BankDao
  ): ActorRef[ShardingEnvelope[TransactionActor.Message]] =
    clusterSharding.init(Entity(TransactionTypeKey) { entityContext =>
      TransactionActor(TransactionId(entityContext.entityId), bankDao)(clusterSharding)
    })

  def bringTransaction(clientId: String)(implicit sharding: ClusterSharding): EntityRef[TransactionActor.Message] =
    sharding
      .entityRefFor(TransactionSharding.TransactionTypeKey, clientId)
}
