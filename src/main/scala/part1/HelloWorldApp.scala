package part1

import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

object HelloWorldApp extends App {
  // refer docs/hello-world.png

  object HelloWorld {
    //HelloWorld actor

    //all messages types
    final case class Greet(whom: String, replyTo: ActorRef[Greeted])
    final case class Greeted(whom: String, from: ActorRef[Greet])

    def apply(): Behavior[Greet] = Behaviors.receive { (context, message) =>
      context.log.info("Hello {}!", message.whom)
      message.replyTo ! Greeted(message.whom, context.self)
      Behaviors.same
    }
  }

  object HelloWorldBot {
    //HelloWorldBot Actor
    //This bot (actor) send greet message max time to Helloworld actor.
    def apply(max: Int): Behavior[HelloWorld.Greeted] = {
      bot(0, max)
    }
    private def bot(greetingCounter: Int, max: Int): Behavior[HelloWorld.Greeted] =
      Behaviors.receive { (context, message) =>
        val n = greetingCounter + 1
        context.log.infoN("Greeting {} for {}", n, message.whom) //require loggerops
        if (n == max) {
          Behaviors.stopped
        } else {
          message.from ! HelloWorld.Greet(message.whom, context.self)
          bot(n, max)
        }
      }
  }

  object HelloWorldMain {
    // the guardian actor
    final case class SayHello(name: String)

    def apply(): Behavior[SayHello] = Behaviors.setup { context =>

      val greeter = context.spawn(HelloWorld(), "greeter")
      Behaviors.receiveMessage { message =>  //Recieve[T] extends Behavior[T]
        val replyTo = context.spawn(HelloWorldBot(3), message.name)
        greeter ! HelloWorld.Greet(message.name, replyTo)
        Behaviors.same
      }
    }
  }
  val system: ActorSystem[HelloWorldMain.SayHello] =
    ActorSystem(HelloWorldMain(), "hello")

  system ! HelloWorldMain.SayHello("World")
  system ! HelloWorldMain.SayHello("Akka")
}
