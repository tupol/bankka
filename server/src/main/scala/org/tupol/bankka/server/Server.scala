package org.tupol.bankka.server

import scala.util.{ Failure, Success }

object Server {
  import akka.actor.CoordinatedShutdown
  import akka.actor.typed.ActorSystem
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.server.Route
  import akka.{ Done, actor => classic }

  import scala.concurrent.duration._

  /**
   * Logic to bind the given routes to a HTTP port and add some logging around it
   */
  def start(routes: Route, port: Int, system: ActorSystem[_]): Unit = {
    import akka.actor.typed.scaladsl.adapter._
    import system.executionContext
    implicit val classicSystem: classic.ActorSystem = system.toClassic
    val shutdown                                    = CoordinatedShutdown(classicSystem)

    // Akka http API changes, a conversation for another time
    // Http().newServerAt("localhost", port).bind(routes)

    Http().bindAndHandle(routes, "localhost", port).onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}/")

        shutdown.addTask(
          CoordinatedShutdown.PhaseServiceRequestsDone,
          "http-graceful-terminate"
        ) { () =>
          binding.terminate(10.seconds).map { _ =>
            system.log.info(s"Server http://${address.getHostString}:${address.getPort}/ graceful shutdown completed.")
            Done
          }
        }
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
}
