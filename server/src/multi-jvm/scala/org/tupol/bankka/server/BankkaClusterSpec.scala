package org.tupol.bankka.server

import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpecLike

// RUN WITH
//              sbt multi-jvm:test
// Please note that by default MultiJvm test sources are located in src/multi-jvm/..., and not in src/test/....
// Naming convention: {TestName}MultiJvm{NodeName}.

object BankkaClusterSpecConfig extends MultiNodeConfig {
  // register the named roles (nodes) of the test
  // note that this is not the same thing as cluster node roles
  val first = role("first")
  val second = role("second")
  val third = role("third")

  // this configuration will be used for all nodes
  // note that no fixed host names and ports are used
  commonConfig(ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.actor.provider = cluster
    akka.cluster.roles = [compute]
    """).withFallback(ConfigFactory.load()))

}


class BankkaClusterSpecMultiJvmNode1 extends BankkaClusterSpec
class BankkaClusterSpecMultiJvmNode2 extends BankkaClusterSpec
class BankkaClusterSpecMultiJvmNode3 extends BankkaClusterSpec

abstract class BankkaClusterSpec extends MultiNodeSpec(BankkaClusterSpecConfig)
  with AnyWordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {


  system.log.info("Starting node...")

  import BankkaClusterSpecConfig._
  import akka.actor.typed.scaladsl.adapter._

  import scala.concurrent.duration._

  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  implicit val typedSystem = system.toTyped

  "The stats sample with single master" must {
    "illustrate how to startup cluster" in within(15.seconds) {
      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      val firstAddress = node(first).address
      val secondAddress = node(second).address
      val thirdAddress = node(third).address

      Cluster(system) join firstAddress

      receiveN(3).collect { case MemberUp(m) => m.address }.toSet should be(
        Set(firstAddress, secondAddress, thirdAddress))

      Cluster(system).unsubscribe(testActor)

      testConductor.enter("all-up")
    }

    "show usage of the statsServiceProxy" in within(20.seconds) {
      // eventually the service should be ok,
      // service and worker nodes might not be up yet
      awaitAssert {
        system.log.info("Trying a request")
//        val probe = TestProbe[StatsService.Response]()
//        singletonProxy ! StatsService.ProcessText("this is the text that will be analyzed", probe.ref)
//        val response = probe.expectMessageType[StatsService.JobResult](3.seconds)
//        response.meanWordLength should be(3.875 +- 0.001)
      }

      testConductor.enter("done")
    }
  }

}
