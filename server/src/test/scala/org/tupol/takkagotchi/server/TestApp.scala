package org.tupol.takkagotchi.server

import java.time.Instant

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Scheduler }
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.tupol.takkagotchi.being.BingCharacteristics

import scala.concurrent.duration._

object TestApp extends App {

  println(s"### TestApp")

  val log = LoggerFactory.getLogger(this.getClass)

  val config = ConfigFactory.load("application-simple-cluster.conf")

  val defaultBingCharacteristics = BingCharacteristics(
    hatchAfter = 2.second,
    hungryAfter = 120.seconds,
    boredAfter = 60.seconds,
    dustAfter = 600.seconds
  )

  def bringBing(name: String) =
    sharding
      .entityRefFor(BingSharding.BingTypeKey, name)

  println(s"### Initializing the actor system")
  val system = ActorSystem[Nothing](Behaviors.empty, "takkagotchi", config)

  println(s"### Initializing cluster sharding")
  private val sharding: ClusterSharding = ClusterSharding(system)

  implicit val timeout: Timeout     = 3.seconds
  implicit val ec                   = system.executionContext
  implicit val scheduler: Scheduler = system.scheduler

  println(s"### Initializing cluster sharding for the bings")
  BingSharding.initializeSharding(sharding, defaultBingCharacteristics)

  println(s"### Bring JULIUS")
  val juliusRef = bringBing("JULIUS")
  juliusRef.tell(BingActor.Hatch)

  println(s"Sleeping ${Instant.now}")
  Thread.sleep(5000)
  println(s"Sleeping ${Instant.now}")

  println(s"### Hello, $juliusRef")

  println(s"### Who are you, $juliusRef?")
  juliusRef.ask(ref => BingActor.WhoAreYou(ref)).map(r => println(s"### $r"))

  println(s"### Hello, $juliusRef")
  juliusRef.ask(ref => BingActor.GreetingsFrom("Oliver", ref)).map(r => println(s"### $r"))

  println(s"### How are you, $juliusRef?")
  juliusRef.ask(ref => BingActor.HowAreYou(ref)).map(r => println(s"### $r"))

}
