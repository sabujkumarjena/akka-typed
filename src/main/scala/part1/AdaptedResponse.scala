package part1

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}

import java.net.URI

//refer adapted-response diagram in docs
object AdaptedResponse extends App {
  def apply(): Behavior[NotUsed] = Behaviors.setup { context =>
    def uriBehavior():Behavior[URI] = Behaviors.empty
    val uriActor = context.spawn(uriBehavior(), "client1")
    val backendActor = context.spawn(Backend(),"backend")
    val frontendActor = context.spawn(Frontend(backendActor), "frontend")
    frontendActor ! Frontend.Translate(URI.create("sabuj"), uriActor)
    frontendActor ! Frontend.Translate(URI.create("jena"), uriActor)
    Behaviors.same
  }
  ActorSystem(AdaptedResponse(), "adapted-message-demo")
}

object Backend {
  sealed trait Request
  final case class StartTranslationJob(taskId: Int, site: URI, replyTo: ActorRef[Response]) extends Request

  sealed trait Response
  final case class JobStarted(taskId: Int) extends Response
  final case class JobProgress(taskId: Int, progress: Double) extends Response
  final case class JobCompleted(taskId: Int, result: URI) extends Response

  def apply(): Behavior[Request] = Behaviors.receiveMessage { message =>
    message match {
      case StartTranslationJob(taskId, site, replyTo) =>
        replyTo ! JobStarted(taskId)
        Behaviors.same
    }

  }
}


object Frontend {

  sealed trait Command
  //final case class Translate(site: URI) extends Command
  final case class Translate(site: URI, replyTo: ActorRef[URI]) extends Command
  private final case class WrappedBackendResponse(response: Backend.Response) extends Command

  def apply(backend: ActorRef[Backend.Request]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      /*
      A message adapter will be used if the message class matches the given class or is a subclass thereof. The registered adapters are tried in reverse order of their registration order, i.e. the last registered first.
A message adapter (and the returned ActorRef) has the same lifecycle as this actor. It's recommended to register the adapters in a top level Behaviors.setup or constructor of AbstractBehavior but it's possible to register them later also if needed. Message adapters don't have to be stopped since they consume no resources other than an entry in an internal Map and the number of adapters are bounded since it's only possible to have one per message class. * The function is running in this actor and can safely access state of it.
*Warning*: This method is not thread-safe and must not be accessed from threads other than the ordinary actor message processing thread, such as Future callbacks.

       */
      val backendResponseMapper: ActorRef[Backend.Response] =
        context.messageAdapter(rsp => WrappedBackendResponse(rsp))

      def active(inProgress: Map[Int, ActorRef[URI]], count: Int): Behavior[Command] = {
        Behaviors.receiveMessage[Command] {
          //case Translate(site, replyTo) =>
          case Translate(site, replyTo) =>
            val taskId = count + 1
            backend ! Backend.StartTranslationJob(taskId, site, backendResponseMapper)
            active(inProgress.updated(taskId, replyTo), taskId)

          case wrapped: WrappedBackendResponse =>
            wrapped.response match {
              case Backend.JobStarted(taskId) =>
                context.log.info("Started {}", taskId)
                Behaviors.same
              case Backend.JobProgress(taskId, progress) =>
                context.log.info2("Progress {}: {}", taskId, progress)
                Behaviors.same
              case Backend.JobCompleted(taskId, result) =>
                context.log.info2("Completed {}: {}", taskId, result)
                inProgress(taskId) ! result
                active(inProgress - taskId, count)
            }
        }
      }

      active(inProgress = Map.empty, count = 0)
    }
}