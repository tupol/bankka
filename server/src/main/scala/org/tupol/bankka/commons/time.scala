package org.tupol.bankka.commons

import java.time.Instant

import scala.concurrent.duration._

object time {

  def now(): Instant = Instant.now()

  implicit class TimeOps(instant: Instant) {
    def -(other: Instant): FiniteDuration = FiniteDuration((instant.toEpochMilli - other.toEpochMilli), MILLISECONDS)
  }

}
