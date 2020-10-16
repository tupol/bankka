package org.tupol.takkagotchi.server

import akka.actor.AddressFromURIString
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.{ Config, ConfigFactory }
import org.slf4j.LoggerFactory
import org.tupol.takkagotchi.being.BingCharacteristics

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

// PORT=12552 \
// BING="Julius"
// curl -s  http://127.0.0.1:$PORT/create/$BING ; \
// sleep 15 ; \
// curl -s http://127.0.0.1:$PORT/find/$BING ; \
// curl -X POST --form 'food=apple' -k http://127.0.0.1:$PORT/feed/$BING ; \
// curl -X POST --form 'lesson=grapes' -k http://127.0.0.1:$PORT/teach/$BING ; \
// curl -s http://127.0.0.1:$PORT/find/$BING

// curl -s  http://127.0.0.1:12552/create/Julian
// sleep 10
// curl -s http://127.0.0.1:12552/find/Julian
// curl -s -X POST --form 'from=Oliver' http://127.0.0.1:12552/hello/Julian
// curl -s -X POST --form 'food=apple' -k http://127.0.0.1:12552/feed/Julian
// curl -s -X POST --form 'lesson=grapes' -k http://127.0.0.1:12552/teach/Julian
// curl -s http://127.0.0.1:12551/find/Julian
// curl -s http://127.0.0.1:12551/status/Julian

object Takkagotchi {

  val log = LoggerFactory.getLogger(this.getClass)

  val config = ConfigFactory.load("application-cluster.conf")

  val defaultBingCharacteristics = BingCharacteristics(
    hatchAfter = 5.second,
    hungryAfter = 120.seconds,
    boredAfter = 60.seconds,
    dustAfter = 600.seconds
  )

  def configWithPort(port: Int): Config =
    ConfigFactory.parseString(s"""
       akka.remote.artery.canonical.port = $port
        """).withFallback(config)

  def main(args: Array[String]): Unit = {
    val seedNodePorts =
      config.getStringList("akka.cluster.seed-nodes").asScala.flatMap { case AddressFromURIString(s) => s.port }
    val ports = args.headOption match {
      case Some(port) => Seq(port.toInt)
      case None       => seedNodePorts ++ Seq(0)
    }

    ports.foreach { port =>
      println(s"### Starting service on port: $port")
      val httpPort =
        if (port > 0) 10000 + port // offset from akka port
        else 0                     // let OS decide

      val config = configWithPort(port)
      ActorSystem[Nothing](rootBehavior(httpPort), "takkagotchi", config)
    }
  }

  def rootBehavior(httpPort: Int): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    val routes = new ServerRoutes(context.system, defaultBingCharacteristics).routes
    Server.start(routes, httpPort, context.system)
    Behaviors.empty
  }

}
