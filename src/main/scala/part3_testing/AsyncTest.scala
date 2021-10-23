package part3_testing

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.Future
import scala.util.Try

/*
Asynchronous testing uses a real ActorSystem that allows you to test your Actors in a more realistic environment.

The minimal setup consists of the test procedure, which provides the desired stimuli, the actor under test, and
an actor receiving replies. Bigger systems replace the actor under test with a network of actors, apply stimuli
at varying injection points and arrange results to be sent from different emission points, but the basic principle
stays the same in that a single procedure drives the test.

*/

object AsyncTest {


  //Actor under test
  object Echo {
    case class Ping(message: String, response: ActorRef[Pong])

    case class Pong(message: String)

    def apply(): Behavior[Ping] = Behaviors.receiveMessage {
      case Ping(m, replyTo) =>
        replyTo ! Pong(m)
        Behaviors.same
    }
  }

  //under test 2
  case class Message(i: Int, replyTo: ActorRef[Try[Int]])

  class Producer(publisher: ActorRef[Message])(implicit scheduler: Scheduler) {

    def produce(messages: Int)(implicit timeout: Timeout): Unit = {
      (0 until messages).foreach(publish)
    }

    private def publish(i: Int)(implicit timeout: Timeout): Future[Try[Int]] = {
      publisher.ask(ref => Message(i, ref))
    }

  }

}