package part1

/*
The ActorContext
The ActorContext can be accessed for many purposes such as:

- Spawning child actors and supervision
- Watching other actors to receive a Terminated(otherActor) event should the watched actor stop permanently
- Logging
- Creating message adapters
- Request-response interactions (ask) with another actor
- Access to the self ActorRef

If a behavior needs to use the ActorContext, for example to spawn child actors, or use context.self, it can be obtained by wrapping construction with Behaviors.setup:
 */

/*
ActorContext Thread Safety
Many of the methods in ActorContext are not thread-safe and

Must not be accessed by threads from scala.concurrent.Future callbacks
Must not be shared between several actor instances
Must only be used in the ordinary actor message processing thread
 */

/*
The Guardian Actor
The top level actor, also called the user guardian actor, is created along with the ActorSystem. Messages sent to the actor system are directed to the root actor.
The root actor is defined by the behavior used to create the ActorSystem

For very simple applications the guardian may contain the actual application logic and handle messages. As soon as the application handles more than one concern the guardian should instead just bootstrap the application, spawn the various subsystems as children and monitor their lifecycles.

When the guardian actor stops this will stop the ActorSystem.
 */
object ActorLifeCycle extends App {

}
