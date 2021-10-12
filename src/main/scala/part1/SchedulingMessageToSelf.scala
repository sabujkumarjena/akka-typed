package part1

import akka.NotUsed
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, scaladsl}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/*
The Buncher actor buffers a burst of incoming messages and delivers them as a batch after a timeout or
when the number of batched messages exceeds a maximum size.
 */
object SchedulingMessageToSelf extends App {

  def apply(): Behavior[NotUsed] = Behaviors.setup{ context =>
    val target = context.spawn(Buncher.targetBehavior(),"target")
    val buncher = context.spawn(Buncher(target,3.seconds, 3), "buncher")
    buncher ! Buncher.ExcitingMessage("m1")
    buncher ! Buncher.ExcitingMessage("m2")
    Thread.sleep(3100)
    buncher ! Buncher.ExcitingMessage("m3")
    buncher ! Buncher.ExcitingMessage("m4")
    Behaviors.same
  }
  ActorSystem(SchedulingMessageToSelf(), "scheduling-message-to-self")
}

object Buncher {

  sealed trait Command
  final case class ExcitingMessage(message: String) extends Command
  final case class Batch(messages: Vector[Command])
  private case object Timeout extends Command
  private case object TimerKey

  def apply(target: ActorRef[Batch], after: FiniteDuration, maxSize: Int): Behavior[Command] = {
    Behaviors.withTimers(timers => new Buncher(timers, target, after, maxSize).idle())
  }

  def targetBehavior(): Behavior[Batch] = Behaviors.receive { (context,message) =>
    message match {
      case Batch(messages) => messages.foreach(x => context.log.info(s"${x}"))
        Behaviors.same
    }
    //Behaviors.same
  }
}

class Buncher(
               timers: TimerScheduler[Buncher.Command],
               target: ActorRef[Buncher.Batch],
               after: FiniteDuration,
               maxSize: Int) {
  import Buncher._

  private def idle(): Behavior[Command] = {
    Behaviors.receiveMessage[Command] { message =>
      timers.startSingleTimer(TimerKey, Timeout, after)
      active(Vector(message))
    }
  }

  def active(buffer: Vector[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (context, message)=> message match {

      case Timeout =>
        context.log.info("Timed out")
        target ! Batch(buffer)
        idle()
      case m =>
        val newBuffer = buffer :+ m
        if (newBuffer.size == maxSize) {
          context.log.info("Reached maximum number of messages")
          timers.cancel(TimerKey)
          target ! Batch(newBuffer)
          idle()
        } else
          active(newBuffer)
    }
    }
  }
}
