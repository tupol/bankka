package org.tupol.takkagotchi.server

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import org.tupol.takkagotchi.being.BingCharacteristics

object BingSharding {

  val BingTypeKey = EntityTypeKey[BingActor.Message]("TheBings")

  def initializeSharding(
    clusterSharding: ClusterSharding,
    bingCharacterstics: BingCharacteristics
  ): ActorRef[ShardingEnvelope[BingActor.Message]] =
    clusterSharding.init(Entity(BingTypeKey) { entityContext =>
      BingActor(entityContext.entityId, bingCharacterstics)
    })

}
