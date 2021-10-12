package part1

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.collection.immutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.reflect.ClassTag

object GeneralPurposeResponseAgrregator extends App {

  object Hotel1 {
    final case class RequestQuote(replyTo: ActorRef[Quote])
    final case class Quote(hotel: String, price: BigDecimal)
  }

  object Hotel2 {
    final case class RequestPrice(replyTo: ActorRef[Price])
    final case class Price(hotel: String, price: BigDecimal)
  }

  // Any since no common type between Hotel1 and Hotel2
  type Reply = Any

  object Aggregator {

    sealed trait Command
    private case object ReceiveTimeout extends Command
    private case class WrappedReply[R](reply: R) extends Command

    def apply[Reply: ClassTag, Aggregate](
                                           sendRequests: ActorRef[Reply] => Unit,
                                           expectedReplies: Int,
                                           replyTo: ActorRef[Aggregate],
                                           aggregateReplies: immutable.IndexedSeq[Reply] => Aggregate,
                                           timeout: FiniteDuration): Behavior[Command] = {
      Behaviors.setup { context =>
        context.setReceiveTimeout(timeout, ReceiveTimeout)
        val replyAdapter = context.messageAdapter[Reply](WrappedReply(_))
        sendRequests(replyAdapter)

        def collecting(replies: immutable.IndexedSeq[Reply]): Behavior[Command] = {
          Behaviors.receiveMessage {
            case WrappedReply(reply) =>
              val newReplies = replies :+ reply.asInstanceOf[Reply]
              if (newReplies.size == expectedReplies) {
                val result = aggregateReplies(newReplies)
                replyTo ! result
                Behaviors.stopped
              } else
                collecting(newReplies)

            case ReceiveTimeout =>
              val aggregate = aggregateReplies(replies)
              replyTo ! aggregate
              Behaviors.stopped
          }
        }

        collecting(Vector.empty)
      }
    }

  }

  object HotelCustomer {
    sealed trait Command
    final case class Quote(hotel: String, price: BigDecimal)
    final case class AggregatedQuotes(quotes: List[Quote]) extends Command

    def apply(hotel1: ActorRef[Hotel1.RequestQuote], hotel2: ActorRef[Hotel2.RequestPrice]): Behavior[Command] = {

      Behaviors.setup[Command] { context =>
        context.spawnAnonymous(
          Aggregator[Reply, AggregatedQuotes](
            sendRequests = { replyTo =>
              hotel1 ! Hotel1.RequestQuote(replyTo)
              hotel2 ! Hotel2.RequestPrice(replyTo)
            },
            expectedReplies = 2,
            context.self,
            aggregateReplies = replies =>
              // The hotels have different protocols with different replies,
              // convert them to `HotelCustomer.Quote` that this actor understands.
              AggregatedQuotes(
                replies
                  .map {
                    case Hotel1.Quote(hotel, price) => Quote(hotel, price)
                    case Hotel2.Price(hotel, price) => Quote(hotel, price)
                    case unknown                    => throw new RuntimeException(s"Unknown reply $unknown")
                  }
                  .sortBy(_.price)
                  .toList),
            timeout = 5.seconds))

        Behaviors.receiveMessage {
          case AggregatedQuotes(quotes) =>
            context.log.info("Best {}", quotes.headOption.getOrElse("Quote N/A"))
            Behaviors.same
        }
      }
    }
  }
  //#usage

}



// Any since no common type between Hotel1 and Hotel2
//type Reply = Any

