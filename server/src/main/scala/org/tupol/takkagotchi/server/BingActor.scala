package org.tupol.takkagotchi.server

import akka.actor.typed.scaladsl.{ Behaviors, TimerScheduler }
import akka.actor.typed.{ ActorRef, Behavior }
import org.tupol.takkagotchi.being.{ Bing, BingCharacteristics, BingName, BingStatus }
import org.tupol.takkagotchi.commons.SerializableMessage

object BingActor {

  def apply(name: String, config: BingCharacteristics): Behavior[Message] =
    Behaviors.withTimers[Message] { timerScheduler =>
      new BingActor(timerScheduler, config).egg(BingName(name))
    }

  sealed trait Message  extends SerializableMessage
  sealed trait Request  extends Message
  sealed trait Response extends Message

  case class Feed(food: String)   extends Request
  case class Teach(value: String) extends Request
  case object Hatch               extends Message

  case class GreetingsFrom(name: String, replyTo: ActorRef[String]) extends Request
  case class WhoAreYou(replyTo: ActorRef[Bing])                     extends Request
  case class HowAreYou(replyTo: ActorRef[BingStatus])               extends Request

  private case object Hatched extends Response
  private case object Starved extends Response
  private case object Bored   extends Response
  private case object Dusted  extends Response

  private case object HatchTimerKey
  private case object StarveTimerKey
  private case object BoredomTimerKey
  private case object FinalTimerKey
}

class BingActor(
  timerScheduler: TimerScheduler[BingActor.Message],
  characteristics: BingCharacteristics
) {

  import BingActor._

  private def startHatchingTimer() =
    timerScheduler.startSingleTimer(HatchTimerKey, Hatched, characteristics.hatchAfter)
  private def startStarvationTimer() =
    timerScheduler.startSingleTimer(StarveTimerKey, Starved, characteristics.starveToDustAfter)
  private def startBoredomTimer() =
    timerScheduler.startSingleTimer(BoredomTimerKey, Bored, characteristics.boredToDustAfter)
  private def startLifetimeTimer() =
    timerScheduler.startSingleTimer(FinalTimerKey, Bored, characteristics.ashesToAshesAfter)

  private def egg(name: BingName): Behavior[Message] =
    Behaviors.receivePartial[Message] {
      case (context, Hatch) =>
        context.log.info(s"Starting to hatch $name")
        startHatchingTimer()
        hatching(name)
    }

  private def hatching(name: BingName): Behavior[Message] =
    Behaviors.receivePartial[Message] {
      case (context, Hatched) =>
        context.log.debug(s"$name has hatched and will start the adventure!")
        startLifetimeTimer()
        startStarvationTimer()
        startBoredomTimer()
        alive(Bing(name, characteristics))
    }

  private def alive(bing: Bing): Behavior[Message] =
    Behaviors.receive[Message] { (context, message) =>
      message match {
        case Starved =>
          context.log.info(s"${bing.name} is starving :(")
          bing.status() match {
            case BingStatus.HungryAndBored =>
              context.log.info(s"${bing.name} turned to ashes... It was both hungry and bored :(")
              Behaviors.stopped
            case _ =>
              Behaviors.same
          }
        case Bored =>
          context.log.info(s"${bing.name} is getting bored to death :(")
          bing.status() match {
            case BingStatus.HungryAndBored =>
              context.log.info(s"${bing.name} turned to ashes... It was both hungry and bored :(")
              Behaviors.stopped
            case _ =>
              Behaviors.same
          }
        case Dusted =>
          context.log.info(s"${bing.name} turned to ashes... It was it's time")
          Behaviors.stopped
        case WhoAreYou(replyTo) =>
          context.log.debug(s"Who are you really, ${bing.name}?")
          replyTo ! bing
          Behaviors.same
        case HowAreYou(replyTo) =>
          context.log.debug(s"How are you, ${bing.name}?")
          replyTo ! bing.status()
          Behaviors.same
        case Feed(food) =>
          context.log.debug(s"Feeding ${bing.name} with $food")
          startStarvationTimer()
          alive(bing.withFood(food))
        case Teach(lesson) =>
          context.log.debug(s"Remember $lesson, ${bing.name}!")
          startBoredomTimer()
          alive(bing.withMemory(lesson))
        case GreetingsFrom(name, replyTo) =>
          context.log.debug(s"Hello, ${bing.name}!")
          replyTo ! s"Hi $name! My name is ${bing.name.toString}."
          startBoredomTimer()
          alive(bing.withMemory(s"Greetings from $name"))
        case _ =>
          Behaviors.empty
      }
    }

}
