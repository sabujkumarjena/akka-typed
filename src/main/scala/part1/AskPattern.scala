package part1

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object AskPattern extends App {
  def apply(): Behavior[NotUsed] = Behaviors.setup { context =>
    val halActor = context.spawn(Hal(), "hal")
    val devActor = context.spawn(Dave(halActor),"dave")
    Behaviors.same
  }

  ActorSystem(AskPattern(), "ask-pattern")

}
/*
Request-Response with ask from outside an Actor
Sometimes you need to interact with actors from the outside of the actor system, this can be done with fire-and-forget
as described above or through another version of ask that returns a Future[Response] that is either completed with a
successful response or failed with a TimeoutException if there was no response within the specified timeout.

To do this we use ask (or the symbolic ?) implicitly added to ActorRef by akka.actor.typed.scaladsl.
AskPattern._ to send a message to an actor and get a Future[Response] back. ask takes implicit Timeout
and ActorSystem parameters.
 */


object Hal {
  sealed trait Command
  case class OpenThePodBayDoorsPlease(replyTo: ActorRef[Response]) extends Command
  case class Response(message: String)

  def apply(): Behaviors.Receive[Hal.Command] =
    Behaviors.receiveMessage[Command] {
      case OpenThePodBayDoorsPlease(replyTo) =>
        replyTo ! Response("I'm sorry, Dave. I'm afraid I can't do that.")
        Behaviors.same
    }
}

object Dave {
  sealed trait Command

  // this is a part of the protocol that is internal to the actor itself
  private case class AdaptedResponse(message: String) extends Command

  implicit val timeout : Timeout = 3.seconds
  def apply(hal: ActorRef[Hal.Command]): Behavior[Dave.Command] =
    Behaviors.setup[Command] { context =>
      // Note: The second parameter list takes a function `ActorRef[T] => Message`,
      // as OpenThePodBayDoorsPlease is a case class it has a factory apply method
      // that is what we are passing as the second parameter here it could also be written
      // as `ref => OpenThePodBayDoorsPlease(ref)`
      //def ask[Req, Res](target: RecipientRef[Req], createRequest: ActorRef[Res] => Req)(mapResponse: Try[Res] => T)(implicit responseTimeout: Timeout, classTag: ClassTag[Res]): Unit
      context.ask(hal,Hal.OpenThePodBayDoorsPlease.apply) {
        case Success(Hal.Response(message)) => AdaptedResponse(message)
        case Failure(_)                     => AdaptedResponse("Request failed")
      }
      Behaviors.receiveMessage {
        // the adapted message ends up being processed like any other
        // message sent to the actor
        case AdaptedResponse(message) =>
          context.log.info("Got response from hal: {}", message)
          Behaviors.same
      }
    }
}