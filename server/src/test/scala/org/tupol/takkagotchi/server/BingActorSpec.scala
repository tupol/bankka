package org.tupol.takkagotchi.server

import akka.actor.testkit.typed.scaladsl.{ ManualTime, ScalaTestWithActorTestKit }
import org.scalatest.Inside
import org.scalatest.wordspec.AnyWordSpecLike
import org.tupol.takkagotchi.being.{ Bing, BingCharacteristics, BingStatus }

import scala.concurrent.duration._

class BingActorSpec extends ScalaTestWithActorTestKit(ManualTime.config) with AnyWordSpecLike with Inside {

  import BingActor._

  val manualTime = ManualTime()
  val bingName   = "Julius"

  "BingActor" should {
    val characteristics = BingCharacteristics(
      hatchAfter = 1.second,
      hungryAfter = 2.seconds,
      boredAfter = 3.seconds,
      dustAfter = 10.seconds
    )
    "hatch and live" in {
      val helloProbe     = createTestProbe[String]()
      val bingProbe      = createTestProbe[Bing]()
      val entStatusProbe = createTestProbe[BingStatus]()
      val testedBing     = spawn(BingActor(bingName, characteristics))
      val messages =
        Seq(
          GreetingsFrom("me", helloProbe.ref),
          Feed("some-food"),
          Teach("some-memory"),
          WhoAreYou(bingProbe.ref),
          HowAreYou(entStatusProbe.ref)
        )
      testedBing ! Hatch
      manualTime.timePasses(1200.millis)
      messages.foreach { message =>
        manualTime.timePasses(10.millis)
        testedBing ! message
      }
      val resultBing: Bing = bingProbe.receiveMessage()
      inside(resultBing) {
        case Bing(name, characteristics, hatchedAt, foodLog, memoryLog) =>
          name.value shouldBe bingName
          foodLog.head.entry shouldBe "some-food"
          memoryLog.map(_.entry) should contain theSameElementsAs Seq("Greetings from me", "some-memory")
      }
    }
    "forget hatching and reply when alive" in {
      val helloProbe     = createTestProbe[String]()
      val bingProbe      = createTestProbe[Bing]()
      val entStatusProbe = createTestProbe[BingStatus]()
      val testedBing     = spawn(BingActor(bingName, characteristics))
      val messages =
        Seq(
          GreetingsFrom("me", helloProbe.ref),
          Feed("some-food"),
          Teach("some-memory"),
          WhoAreYou(bingProbe.ref),
          HowAreYou(entStatusProbe.ref)
        )
      testedBing ! Hatch
      manualTime.timePasses(800.millis)
      messages.foreach { message =>
        manualTime.timePasses(10.millis)
        testedBing ! message
      }
      bingProbe.expectNoMessage()
      entStatusProbe.expectNoMessage()

      manualTime.timePasses(300.millis)
      messages.foreach { message =>
        manualTime.timePasses(10.millis)
        testedBing ! message
      }
      val resultBing: Bing = bingProbe.receiveMessage()
      inside(resultBing) {
        case Bing(name, characteristics, hatchedAt, foodLog, memoryLog) =>
          name.value shouldBe bingName
          foodLog.head.entry shouldBe "some-food"
          memoryLog.map(_.entry) should contain theSameElementsAs Seq("Greetings from me", "some-memory")
      }
    }
  }
}
