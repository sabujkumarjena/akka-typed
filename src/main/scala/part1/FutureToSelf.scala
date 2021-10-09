package part1

import akka.{Done, NotUsed}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.concurrent.Future
import scala.util.{Failure, Success}

//see futuretoself.png of doc
object FutureToSelf extends App {
  trait CustomerDataAccess {
    def update(value: Customer): Future[Done]
  }
  final case class Customer(id: String, version: Long, name: String, address: String)

  val dataAccess = new CustomerDataAccess {
    override def update(value: Customer): Future[Done] = Future.successful(Done)
  }

  def apply():Behavior[NotUsed] = Behaviors.setup{ context =>
    val repository = context.spawn(CustomerRepository(dataAccess), "customer-repository")
    val probe = context.spawn(CustomerRepository.probe(), "probe")

    repository ! CustomerRepository.Update(Customer("123", 1L, "Alice", "Fairy tail road 7"), probe)
    Behaviors.same
  }
  ActorSystem(FutureToSelf(),"skj")

  object CustomerRepository {
    sealed trait Command
    final case class Update(value: Customer, replyTo: ActorRef[UpdateResult]) extends Command
    private final case class WrappedUpdateResult(result: UpdateResult, replyTo: ActorRef[UpdateResult])
      extends Command

    sealed trait UpdateResult
    final case class UpdateSuccess(id: String) extends UpdateResult
    final case class UpdateFailure(id: String, reason: String) extends UpdateResult


    private val MaxOperationsInProgress = 10

    def apply(dataAccess: CustomerDataAccess): Behavior[Command] = {
      next(dataAccess, operationsInProgress = 0)
    }

    private def next(dataAccess: CustomerDataAccess, operationsInProgress: Int): Behavior[Command] = {
      Behaviors.receive { (context, command) =>
        command match {
          case Update(value, replyTo) =>
            if (operationsInProgress == MaxOperationsInProgress) {
              replyTo ! UpdateFailure(value.id, s"Max $MaxOperationsInProgress concurrent operations supported")
              Behaviors.same
            } else {
              val futureResult = dataAccess.update(value)
              context.pipeToSelf(futureResult) {
                // map the Future value to a message, handled by this actor
                case Success(_) => context.log.info("received")
                  WrappedUpdateResult(UpdateSuccess(value.id), replyTo)
                case Failure(e) => WrappedUpdateResult(UpdateFailure(value.id, e.getMessage), replyTo)
              }
              // increase operationsInProgress counter
              next(dataAccess, operationsInProgress + 1)
            }

          case WrappedUpdateResult(result, replyTo) =>
            // send result to original requestor
            replyTo ! result
            // decrease operationsInProgress counter
            next(dataAccess, operationsInProgress - 1)
        }
      }
    }
    def probe(): Behavior[UpdateResult]= Behaviors.ignore
  }
}
