package part2

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

object AkkaDiscovery extends App {
 /*
 Finally in the guardian actor we spawn the service as well as subscribing to any actors registering against the ServiceKey.
 Subscribing means that the guardian actor will be informed of any new registrations via a Listing message:

 Each time a new (which is just a single time in this example) *PingService* is registered the guardian actor
 spawns a *Pinger* for each currently known PingService. The Pinger sends a Ping message and
 when receiving the Pong reply it stops.


  */
  def apply():Behavior[Nothing] = Behaviors.setup[Receptionist.Listing] { context =>
    context.spawnAnonymous(PingService())

    context.system.receptionist ! Receptionist.Subscribe(PingService.PingServiceKey, context.self)

    context.spawnAnonymous(PingService.ps2())

    Behaviors.receiveMessagePartial[Receptionist.Listing] {
      case PingService.PingServiceKey.Listing(listings) =>
        context.log.info(s"list of PingService providers:: ${listings}")
        listings.foreach(ps => context.spawnAnonymous(Pinger(ps)))
        Behaviors.same
    }
  }
    .narrow

  ActorSystem[Nothing](AkkaDiscovery(),"akka-discovery")

  object Pinger {
    def apply(pingService: ActorRef[PingService.Ping]): Behavior[PingService.Pong.type] = {
      Behaviors.setup { context =>
        pingService ! PingService.Ping(context.self)

        Behaviors.receiveMessage { _ =>
          context.log.info("{} was ponged!!", context.self)
          Behaviors.stopped
        }
      }
    }
  }

  object PingService {

    final case class Ping(replyTo: ActorRef[Pong.type])

    case object Pong

    val PingServiceKey = ServiceKey[Ping]("ping-service")

    def apply(): Behavior[Ping] = Behaviors.setup { context =>
      context.system.receptionist ! Receptionist.Register(PingServiceKey, context.self)

      Behaviors.receiveMessage {
        case Ping(replyTo) =>
          context.log.info("Pinged by {}", replyTo)
          replyTo ! Pong
          Behaviors.same
      }
    }

    def ps2(): Behavior[Ping] = Behaviors.setup { context =>
      context.system.receptionist ! Receptionist.Register(PingServiceKey, context.self)

      Behaviors.receiveMessage {
        case Ping(replyTo) =>
          context.log.info("Pinged by {} to ps2", replyTo)
          replyTo ! Pong
          Behaviors.same
      }
    }
  }
}

/*
Obtaining Actor references
There are two general ways to obtain Actor references: by creating actors and by discovery using the Receptionist.

 Receptionist
 ************
You register the specific actors that should be discoverable from each node in the local Receptionist instance.
The API of the receptionist is also based on actor messages. This registry of actor references is then automatically distributed to all other nodes in the case of a cluster.
You can lookup such actors with the key that was used when they were registered.
The reply to such a Find request is a Listing, which contains a Set of actor references that are registered for the key.
Note that several actors can be registered to the same key.

The registry is dynamic. New actors can be registered during the lifecycle of the system. Entries are removed when registered actors are stopped,
manually deregistered or the node they live on is removed from the Cluster
 */