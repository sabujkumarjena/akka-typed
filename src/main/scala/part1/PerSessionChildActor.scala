package part1

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

//ref docs/per-session-child-actor.png
/*
Useful when:

A single incoming request should result in multiple interactions with other actors before a result can be built, for example aggregation of several results
You need to handle acknowledgement and retry messages for at-least-once delivery
Problems:

Children have life cycles that must be managed to not create a resource leak, it can be easy to miss a scenario where the session actor is not stopped
It increases complexity, since each such child can execute concurrently with other children and the parent
 */

object PerSessionChildActor extends App{
  def apply():Behavior[NotUsed] = Behaviors.setup{context =>
    val client = context.spawn(Home.dummyClient(), "client")
    val home = context.spawn(Home(), "home")
    home ! Home.LeaveHome("sabuj", client)
    Behaviors.same
  }
 val system = ActorSystem(PerSessionChildActor(),"home")

}

case class Keys()
case class Wallet()

// #per-session-child

object KeyCabinet {
  case class GetKeys(whoseKeys: String, replyTo: ActorRef[Keys])

  def apply(): Behavior[GetKeys] =
    Behaviors.receiveMessage {
      case GetKeys(_, replyTo) =>
        replyTo ! Keys()
        Behaviors.same
    }
}

object Drawer {
  case class GetWallet(whoseWallet: String, replyTo: ActorRef[Wallet])

  def apply(): Behavior[GetWallet] =
    Behaviors.receiveMessage {
      case GetWallet(_, replyTo) =>
        replyTo ! Wallet()
        Behaviors.same
    }
}

object Home {
  sealed trait Command
  case class LeaveHome(who: String, replyTo: ActorRef[ReadyToLeaveHome]) extends Command
  case class ReadyToLeaveHome(who: String, keys: Keys, wallet: Wallet)

  def apply(): Behavior[Command] = {
    Behaviors.setup[Command] { context =>
      val keyCabinet: ActorRef[KeyCabinet.GetKeys] = context.spawn(KeyCabinet(), "key-cabinet")
      val drawer: ActorRef[Drawer.GetWallet] = context.spawn(Drawer(), "drawer")

      Behaviors.receiveMessage[Command] {
        case LeaveHome(who, replyTo) =>
          context.spawn(prepareToLeaveHome(who, replyTo, keyCabinet, drawer), s"leaving-$who")
          Behaviors.same
      }
    }


  }
  def dummyClient(): Behavior[ReadyToLeaveHome] = Behaviors.empty
  // per session actor behavior
  def prepareToLeaveHome(
                          whoIsLeaving: String,
                          replyTo: ActorRef[ReadyToLeaveHome],
                          keyCabinet: ActorRef[KeyCabinet.GetKeys],
                          drawer: ActorRef[Drawer.GetWallet]): Behavior[NotUsed] = {
    // we don't _really_ care about the actor protocol here as nobody will send us
    // messages except for responses to our queries, so we just accept any kind of message
    // but narrow that to more limited types when we interact
    Behaviors
      .setup[AnyRef] { context =>
        var wallet: Option[Wallet] = None
        var keys: Option[Keys] = None

        // we narrow the ActorRef type to any subtype of the actual type we accept
        keyCabinet ! KeyCabinet.GetKeys(whoIsLeaving, context.self.narrow[Keys])
        drawer ! Drawer.GetWallet(whoIsLeaving, context.self.narrow[Wallet])

        def nextBehavior(): Behavior[AnyRef] =
          (keys, wallet) match {
            case (Some(w), Some(k)) =>
              // we got both, "session" is completed!
              replyTo ! ReadyToLeaveHome(whoIsLeaving, w, k)
              context.log.info("Leaving")
              Behaviors.stopped

            case _ =>
              Behaviors.same
          }

        Behaviors.receiveMessage {
          case w: Wallet =>
            wallet = Some(w)
            nextBehavior()
          case k: Keys =>
            keys = Some(k)
            nextBehavior()
          case _ =>
            Behaviors.unhandled
        }
      }
      .narrow[NotUsed] // we don't let anyone else know we accept anything
  }
}
