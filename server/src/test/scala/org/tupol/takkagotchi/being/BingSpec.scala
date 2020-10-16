package org.tupol.takkagotchi.being

import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class BingSpec extends AnyWordSpecLike with Matchers with Inside {

  import scala.concurrent.duration._
  import org.tupol.takkagotchi.commons.time._
  import BingStatus._

  val defaultCharacteristics =
    BingCharacteristics(hatchAfter = 1.second, hungryAfter = 2.seconds, boredAfter = 3.seconds, dustAfter = 10.seconds)

  def hatchJulius(characteristics: BingCharacteristics = defaultCharacteristics) =
    Bing(BingName("Julius"), characteristics)

  "Julius" should {
    "start fresh" in {
      val Julius = hatchJulius()
      Julius.foodLog.isEmpty shouldBe true
      Julius.memoryLog.isEmpty shouldBe true
      Julius.status() shouldBe HungryAndBored
    }
    "get some food" in {
      val Julius = hatchJulius().withFood("apple").withFood("pear")
      Julius.status() shouldBe Bored
      Julius.foodLog.map(_.entry) should contain theSameElementsAs Seq("apple", "pear")
      Julius.memoryLog.isEmpty shouldBe true
    }
    "learn some stuff" in {
      val Julius = hatchJulius().withMemory("apple").withMemory("pear")
      Julius.status() shouldBe Hungry
      Julius.foodLog.isEmpty shouldBe true
      Julius.memoryLog.map(_.entry) should contain theSameElementsAs Seq("apple", "pear")
    }
    "learn some stuff and get some food, all reasons for happiness :)" in {
      val Julius = hatchJulius()
        .withFood("apple")
        .withFood("pear")
        .withMemory("apple")
        .withMemory("pear")
      Julius.status() shouldBe Happy
      Julius.foodLog.map(_.entry) should contain theSameElementsAs Seq("apple", "pear")
      Julius.memoryLog.map(_.entry) should contain theSameElementsAs Seq("apple", "pear")
    }
    "learn some stuff and get some food, but later it becomes hungry" in {
      val characteristics = defaultCharacteristics.copy(boredAfter = 3.seconds, hungryAfter = 2.seconds)
      val Julius = hatchJulius(characteristics)
        .withFood("apple")
        .withFood("pear")
        .withMemory("apple")
        .withMemory("pear")
      val when = (now().plusMillis(defaultCharacteristics.hungryAfter.toMillis))
      Julius.status(when) shouldBe Hungry
      Julius.foodLog.map(_.entry) should contain theSameElementsAs Seq("apple", "pear")
      Julius.memoryLog.map(_.entry) should contain theSameElementsAs Seq("apple", "pear")
    }
    "learn some stuff and get some food, but later it becomes bored" in {
      val characteristics = defaultCharacteristics.copy(boredAfter = 2.seconds, hungryAfter = 3.seconds)
      val Julius = hatchJulius(characteristics)
        .withFood("apple")
        .withFood("pear")
        .withMemory("apple")
        .withMemory("pear")
      val when = (now().plusMillis(defaultCharacteristics.hungryAfter.toMillis))
      Julius.status(when) shouldBe Bored
      Julius.foodLog.map(_.entry) should contain theSameElementsAs Seq("apple", "pear")
      Julius.memoryLog.map(_.entry) should contain theSameElementsAs Seq("apple", "pear")
    }
    "learn some stuff and get some food, but later it becomes bored and hungry" in {
      val characteristics = defaultCharacteristics.copy(boredAfter = 2.seconds, hungryAfter = 3.seconds)
      val Julius = hatchJulius(characteristics)
        .withFood("apple")
        .withFood("pear")
        .withMemory("apple")
        .withMemory("pear")
      val when = (now().plusSeconds(3))
      Julius.status(when) shouldBe HungryAndBored
      Julius.foodLog.map(_.entry) should contain theSameElementsAs Seq("apple", "pear")
      Julius.memoryLog.map(_.entry) should contain theSameElementsAs Seq("apple", "pear")
    }
  }
}
