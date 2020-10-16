package org.tupol.takkagotchi.being

import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class BingCharacteristicsSpec extends AnyWordSpecLike with Matchers with Inside {

  import scala.concurrent.duration._

  val referenceCharcteristics = BingCharacteristics(
    hatchAfter = 10.second,
    hungryAfter = 20.seconds,
    boredAfter = 30.seconds,
    dustAfter = 100.seconds
  )

  "Characteristics with no resilience" should {
    "show no resilience" in {
      val characteristics = referenceCharcteristics
      characteristics.hatchAfter shouldBe referenceCharcteristics.hatchAfter
      characteristics.starveToDustAfter shouldBe referenceCharcteristics.hungryAfter
      characteristics.boredToDustAfter shouldBe referenceCharcteristics.boredAfter
      characteristics.ashesToAshesAfter shouldBe referenceCharcteristics.dustAfter
    }
  }

  "Characteristics with positive resilience" should {
    "show positive resilience" in {
      val characteristics = referenceCharcteristics.copy(resilience = 0.1)

      characteristics.hatchAfter shouldBe referenceCharcteristics.hatchAfter
      characteristics.starveToDustAfter shouldBe 22.seconds
      characteristics.boredToDustAfter shouldBe 33.seconds
      characteristics.ashesToAshesAfter shouldBe 110.seconds
    }
  }

  "Characteristics with negative resilience" should {
    "show negative resilience" in {
      val characteristics = referenceCharcteristics.copy(resilience = -0.1)

      characteristics.hatchAfter shouldBe referenceCharcteristics.hatchAfter
      characteristics.starveToDustAfter shouldBe 18.seconds
      characteristics.boredToDustAfter shouldBe 27.seconds
      characteristics.ashesToAshesAfter shouldBe 90.seconds
    }
  }
}
