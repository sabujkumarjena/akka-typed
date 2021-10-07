package part1

import akka.NotUsed
import akka.actor.typed.SpawnProtocol.Spawn
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Props, SpawnProtocol}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object AskPatternOutside extends App {

  def apply(): Behavior[NotUsed] = Behaviors.setup { context =>
    val cookieFabric: ActorRef[CookieFabric.GiveMeCookies] = context.spawn(CookieFabric(), "cookie-fabric")
    // cookieFabric.ask()
    implicit val timeout: Timeout = 3.seconds
    // the response callback will be executed on this execution context
    implicit val ec = context.executionContext
    /*
    def ask[Res](replyTo: ActorRef[Res] => Req)(implicit timeout: Timeout, scheduler: Scheduler): Future[Res]
The ask-pattern implements the initiator side of a request–reply protocol.
Note that if you are inside of an actor you should prefer ActorContext.ask as that provides better safety.
The party that asks may be within or without an Actor, since the implementation will fabricate a (hidden) ActorRef that is bound to a scala.concurrent.Promise. This ActorRef will need to be injected in the message that is sent to the target Actor in order to function as a reply-to address, therefore the argument to the ask / ? operator is not the message itself but a function that given the reply-to address will create the message.
 case class Request(msg: String, replyTo: ActorRef[Reply])
 case class Reply(msg: String)

 implicit val system = ...
 implicit val timeout = Timeout(3.seconds)
 val target: ActorRef[Request] = ...
 val f: Future[Reply] = target.ask(replyTo => Request("hello", replyTo))
 // alternatively
 val f2: Future[Reply] = target.ask(Request("hello", _))

     */
    implicit val system = context.system
    val result: Future[CookieFabric.Reply] = cookieFabric.ask(ref => CookieFabric.GiveMeCookies(3, ref))
    result.onComplete {
      case Success(CookieFabric.Cookies(count))         => println(s"Yay, $count cookies!")
      case Success(CookieFabric.InvalidRequest(reason)) => println(s"No cookies for me. $reason")
      case Failure(ex)                                  => println(s"Boo! didn't get cookies: ${ex.getMessage}")
    }
    Behaviors.same
  }
  //implicit val timeout: Timeout = 3.seconds
  //implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "")
  //val cookieFabric: ActorRef[CookieFabric.Command] = spawn(CookieFabric(),"cookie-fapric", props = Props.empty,)
   ActorSystem(AskPatternOutside(),"ask-pattern-outside")
}

object CookieFabric {
  sealed trait Command
  case class GiveMeCookies(count: Int, replyTo: ActorRef[Reply]) extends Command

  sealed trait Reply
  case class Cookies(count: Int) extends Reply
  case class InvalidRequest(reason: String) extends Reply

  def apply(): Behaviors.Receive[CookieFabric.GiveMeCookies] =
    Behaviors.receiveMessage { message =>
      if (message.count >= 5)
        message.replyTo ! InvalidRequest("Too many cookies.")
      else
        message.replyTo ! Cookies(message.count)
      Behaviors.same
    }
}