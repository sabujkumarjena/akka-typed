package part2

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

/*
In some cases it is useful to distribute messages of the same type over a set of actors,
so that messages can be processed in parallel - a single actor will only process one message at a time.

The router itself is a behavior that is spawned into a running actor that will then forward any message
sent to it to one final recipient out of the set of routees.

There are two kinds of routers included in Akka Typed - the pool router and the group router.
 */
object Router extends App {

  //Routee
  object Worker {
    sealed trait Command
    case class DoLog(text: String) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { context =>
      context.log.info("Starting worker")

      Behaviors.receiveMessage {
        case DoLog(text) =>
          context.log.info("Got message {}", text)
          Behaviors.same
      }
    }
  }
}
