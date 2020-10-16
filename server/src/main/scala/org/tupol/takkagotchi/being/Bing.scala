package org.tupol.takkagotchi.being

import java.time.Instant

import org.tupol.takkagotchi.commons.SerializableMessage
import org.tupol.takkagotchi.commons.time._

import scala.concurrent.duration._

case class BingName(value: String) {
  override def toString: String = value
}
case class Bing(
  name: BingName,
  characteristics: BingCharacteristics,
  hatchedAt: Instant = Instant.now(),
  foodLog: Seq[Record] = Seq(),
  memoryLog: Seq[Record] = Seq()
) extends SerializableMessage {
  def status(when: Instant = now()) = {
    val hungry = lastFedAt().map(t => (when - t) >= characteristics.hungryAfter).getOrElse(true)
    val bored  = lastMemoryAt().map(t => (when - t) >= characteristics.boredAfter).getOrElse(true)
    BingStatus(hungry, bored)
  }

  private def lastFedAt(): Option[Instant] = foodLog.headOption.map(_.timestamp)

  private def lastMemoryAt(): Option[Instant] = memoryLog.headOption.map(_.timestamp)

  def withFood(food: String): Bing = {
    val entry = Record(food)
    this.copy(foodLog = entry +: this.foodLog)
  }
  def withMemory(memory: String): Bing = {
    val entry = Record(memory)
    this.copy(memoryLog = entry +: this.memoryLog)
  }
}

case class BingStatus(hungry: Boolean, bored: Boolean) extends SerializableMessage {
  override def toString: String =
    (hungry, bored) match {
      case (true, true)   => "I am hungry and bored :("
      case (true, false)  => "I am hungry :("
      case (false, true)  => "I am bored :("
      case (false, false) => "I am happy :)"
    }
}

object BingStatus {
  val HungryAndBored = BingStatus(true, true)
  val Hungry         = BingStatus(true, false)
  val Bored          = BingStatus(false, true)
  val Happy          = BingStatus(false, false)
}

/**
 * @param hatchAfter
 * @param hungryAfter
 * @param boredAfter
 * @param dustAfter
 * @param resilience a percentage applied to all durations that extends or reduces the duration to the dust limit
 */
case class BingCharacteristics(
  hatchAfter: FiniteDuration,
  hungryAfter: FiniteDuration,
  boredAfter: FiniteDuration,
  dustAfter: FiniteDuration,
  resilience: Double = 0
) {
  import math._
  require(abs(resilience) < 1, "The absolute value of resilience must be smaller than 1")
  val starveToDustAfter: FiniteDuration = FiniteDuration((hungryAfter * (1 + resilience)).toMillis, MILLISECONDS)
  val boredToDustAfter: FiniteDuration  = FiniteDuration((boredAfter * (1 + resilience)).toMillis, MILLISECONDS)
  val ashesToAshesAfter: FiniteDuration = FiniteDuration((dustAfter * (1 + resilience)).toMillis, MILLISECONDS)
}
