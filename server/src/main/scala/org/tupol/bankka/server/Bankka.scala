package org.tupol.bankka.server

import akka.actor.AddressFromURIString
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, DispatcherSelector}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory
import org.tupol.bankka.data.dao.{BankDao, InMemoryAccountDao, InMemoryClientDao, InMemoryTransactionDao}

import scala.concurrent.ExecutionContextExecutor
import scala.jdk.CollectionConverters._

object Bankka {

  implicit val logger = LoggerFactory.getLogger(this.getClass)

  val config = ConfigFactory.load("application-cluster.conf")

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

    val system = ActorSystem[Nothing](Behaviors.empty, "None", config)
    ports.foreach { port =>
      logger.info(s"### Starting service on port: $port")
      val httpPort =
        if (port > 0) 10000 + port // offset from akka port
        else 0                     // let OS decide

      val config = configWithPort(port)
      val bankDao = initializeBankDao(system)
      ActorSystem[Nothing](rootBehavior(httpPort, bankDao), "bankka", config)
    }
  }

  def initializeBankDao(system: ActorSystem[_]) = {
    implicit val bec: ExecutionContextExecutor =
      system.dispatchers.lookup(DispatcherSelector.fromConfig("blocking-dispatcher"))
    new BankDao(new InMemoryClientDao(), new InMemoryAccountDao(), new InMemoryTransactionDao)
  }

  def rootBehavior(httpPort: Int, bankDao: BankDao): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    val routes = new ServerRoutes(context.system, bankDao, 100).routes
    Server.start(routes, httpPort, context.system)
    Behaviors.empty
  }

}
