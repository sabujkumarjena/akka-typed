package part1

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, PreRestart, SupervisorStrategy}


/*
The default supervision strategy is to stop the actor if an exception is thrown.
In many cases you will want to further customize this behavior.
To use supervision the actual Actor behavior is wrapped using *Behaviors.supervise*.
Typically you would wrap the actor with supervision in the parent when spawning it as a child.
 */

//This example restarts the actor when it fails with an IllegalStateException:
//Behaviors.supervise(behavior).onFailure[IllegalStateException](SupervisorStrategy.restart)

//Or to resume, ignore the failure and process the next message, instead:
//Behaviors.supervise(behavior).onFailure[IllegalStateException](SupervisorStrategy.resume)

//More complicated restart strategies can be used e.g. to restart no more than 10 times in a 10 second period:

/*
Behaviors
  .supervise(behavior)
  .onFailure[IllegalStateException](
    SupervisorStrategy.restart.withLimit(maxNrOfRetries = 10, withinTimeRange = 10.seconds))
 */

//To handle different exceptions with different strategies calls to supervise can be nested:
/*
Behaviors
  .supervise(Behaviors.supervise(behavior).onFailure[IllegalStateException](SupervisorStrategy.restart))
  .onFailure[IllegalArgumentException](SupervisorStrategy.stop)
 */

object FaultTolerance extends App{

  def apply(): Behavior[NotUsed] = Behaviors.setup { context =>
    val counter = context.spawnAnonymous(Counter())
    counter ! Counter.Increment(5)
    Behaviors.same
  }
  ActorSystem(FaultTolerance(),"fault-tolerance")

  object Counter {
    sealed trait Command
    case class Increment(nr: Int) extends Command
    case class GetCount(replyTo: ActorRef[Int]) extends Command

    def apply(): Behavior[Command] =
      Behaviors.supervise(counter(1)).onFailure(SupervisorStrategy.restart)

    private def counter(count: Int): Behavior[Command] =
      Behaviors.receive[Command] { (context, message) =>
        message match {
          case Increment(nr: Int) =>
            context.log.info(s"${count + nr}")
            counter(count + nr)
          case GetCount(replyTo) =>
            replyTo ! count
            Behaviors.same
        }
      }
        .receiveSignal {
          case(_, signal) if signal == PreRestart || signal == PostStop =>
            //release resource
            //resource.close()
            Behaviors.same
        }
  }

}
