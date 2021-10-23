package test3_testing

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.DurationInt
import scala.util.Success

/*
Tests create an instance of ActorTestKit. This provides access to:

- An ActorSystem
- Methods for spawning Actors. These are created under the special testkit user guardian
- A method to shut down the ActorSystem from the test suite
 */

class AsyncTestingExampleSpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers {
  val testKit = ActorTestKit()
  import part3_testing.AsyncTest._

  "A testkit" must {
    "support verifying a response" in {
      //#test-spawn
      val pinger = testKit.spawn(Echo(), "ping")
      val probe = testKit.createTestProbe[Echo.Pong]()
      pinger ! Echo.Ping("hello", probe.ref)
      probe.expectMessage(Echo.Pong("hello"))
      //#test-spawn
    }

    "support verifying a response - anonymous" in {
      //#test-spawn-anonymous
      val pinger = testKit.spawn(Echo())
      //#test-spawn-anonymous
      val probe = testKit.createTestProbe[Echo.Pong]()
      pinger ! Echo.Ping("hello", probe.ref)
      probe.expectMessage(Echo.Pong("hello"))
    }

    "be able to stop actors under test" in {
      // Will fail with 'name not unique' exception if the first actor is not fully stopped
      val probe = testKit.createTestProbe[Echo.Pong]()
      //#test-stop-actors
      val pinger1 = testKit.spawn(Echo(), "pinger")
      pinger1 ! Echo.Ping("hello", probe.ref)
      probe.expectMessage(Echo.Pong("hello"))
      testKit.stop(pinger1) // Uses default timeout

      // Immediately creating an actor with the same name
      val pinger2 = testKit.spawn(Echo(), "pinger")
      pinger2 ! Echo.Ping("hello", probe.ref)
      probe.expectMessage(Echo.Pong("hello"))
      testKit.stop(pinger2, 10.seconds) // Custom timeout
      //#test-stop-actors
    }

    "support observing mocked behavior" in {

      //#test-observe-mocked-behavior
      import testKit._

      // simulate the happy path
      val mockedBehavior = Behaviors.receiveMessage[Message] { msg =>
        msg.replyTo ! Success(msg.i)
        Behaviors.same
      }
      val probe = testKit.createTestProbe[Message]()
      val mockedPublisher = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))

      // test our component
      val producer = new Producer(mockedPublisher)
      val messages = 3
      producer.produce(messages)

      // verify expected behavior
      for (i <- 0 until messages) {
        val msg = probe.expectMessageType[Message]
        msg.i shouldBe i
      }
      //#test-observe-mocked-behavior
    }

  }
  //#test-shutdown
  override def afterAll(): Unit = testKit.shutdownTestKit()
  //#test-shutdown
  }
