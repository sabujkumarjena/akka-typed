package part1

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ChatRoomApp extends App{
  //guardian actor

  def apply(): Behavior[NotUsed] = Behaviors.setup { context =>
    val chatRoom = context.spawn(ChatRoom(), "chatroom")

    val gabblerRef1 = context.spawn(Gabbler(), "gabbler1")
    context.watch(gabblerRef1)
    chatRoom ! ChatRoom.GetSession("Sabuj", gabblerRef1)

    val gabblerRef2 = context.spawn(Gabbler(), "gabbler2")
    context.watch(gabblerRef1)
    chatRoom ! ChatRoom.GetSession("Akash", gabblerRef2)
    Behaviors.receiveSignal {
      case (_, Terminated(_)) => Behaviors.stopped
    }
  }

  ActorSystem(ChatRoomApp(), "ChatRoomDemo")
}

object  ChatRoom {

  sealed trait RoomCommand
  final case class GetSession(screenName: String, replyTo: ActorRef[SessionEvent]) extends RoomCommand
  private final case class PublishSessionMessage(screenName: String, message: String) extends RoomCommand

  sealed trait SessionEvent
  final case class SessionGranted(handle: ActorRef[PostMessage]) extends SessionEvent
  final case class SessionDenied(reason: String) extends SessionEvent
  final case class MessagePosted(screenName: String, message: String) extends SessionEvent

  sealed trait SessionCommand
  final case class PostMessage(message: String) extends SessionCommand
  private final case class NotifyClient(message: MessagePosted) extends SessionCommand

  def apply(): Behavior[RoomCommand] = chatRoom(List.empty)

  //chatroom actor
  private def chatRoom(sessions: List[ActorRef[SessionCommand]]) : Behavior[RoomCommand] =
    Behaviors.receive { (contenxt, message) =>
      message match {
        case GetSession(screenName, client) =>
          //create a child actor for further interaction with client
          val sess = contenxt.spawn(session(contenxt.self, screenName, client), name = URLEncoder.encode(screenName, StandardCharsets.UTF_8.name()))
          client ! SessionGranted(sess)
          chatRoom(sess :: sessions)
        case PublishSessionMessage(screenName, message) =>
          val notification = NotifyClient(MessagePosted(screenName, message))
          sessions.foreach(_ ! notification)
          Behaviors.same
      }
    }

  //session actor
  private def session(
                     room: ActorRef[PublishSessionMessage],
                     screenName: String,
                     client: ActorRef[SessionEvent]
                     ): Behavior[SessionCommand] =
    Behaviors.receiveMessage {
      case PostMessage(message) =>
        //from client, publish to others via the room
        room ! PublishSessionMessage(screenName, message)
        Behaviors.same
      case NotifyClient(message) =>
        //publish from the room
        client ! message
        Behaviors.same
    }
}
object Gabbler {
  // client actor
  import ChatRoom._

  def apply(): Behavior[SessionEvent] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case SessionGranted(handle) =>
          handle ! PostMessage("Hello World!")
          Behaviors.same
        case MessagePosted(screenName, message) =>
          context.log.info2("message has been posted by '{}': {}", screenName, message)
          Behaviors.same
      }
    }
}
