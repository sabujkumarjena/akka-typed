package part1

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{Behaviors}
/*
An **ActorSystem** is home to a hierarchy of Actors. It is created using ActorSystem#apply from a Behavior object
that describes the root Actor of this hierarchy and which will create all other Actors beneath it.
A system also implements the ActorRef type, and sending a message to the system directs that message to the root Actor.
*/

/*
Behaviour[T]
The behavior of an actor defines how it reacts to the messages that it receives.

Behaviors can be formulated in a number of different ways,
one is by using the DSLs in akka.actor.typed.scaladsl.Behaviors

def receive[T](onMessage: (ActorContext[T], T) => Behavior[T]): Receive[T]
Construct an actor behavior that can react to both incoming messages and lifecycle signals.

def empty[T]: Behavior[T]
A behavior that treats every incoming message as unhandled.

def ignore[T]: Behavior[T]
A behavior that ignores every incoming message and returns “same”.

def same[T]: Behavior[T]
Return this behavior from message processing in order to advise the system to reuse the previous behavior.

def setup[T](factory: (ActorContext[T]) => Behavior[T]): Behavior[T]
setup is a factory for a behavior.

def stopped[T](postStop: () => Unit): Behavior[T]
Return this behavior from message processing to signal that this actor shall terminate voluntarily.

 */

object ActorsIntro extends App {

/*
An actor encapsulate
  - state
  - behavior


 */
object WordCountActor {
  /*
  The actor is defined by its behavior which changes on receipt of message.
  This actor maintains the state (total count), the number of
   */
  def apply(): Behavior[Int] = wordCount(0)

  def wordCount(n: Int): Behavior[Int] = Behaviors.receive {
    (context, message) =>
      println(n+ message)
      wordCount(n + message)
  }
}

val system = ActorSystem(WordCountActor(), "wcActor1") //create guardian actor from a behaviour
  system ! 5
  system ! 7

}
/* content of build.sbt
name := "akka-typed"

version := "0.1"

scalaVersion := "2.13.6"

val AkkaVersion = "2.6.16"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
)
 */