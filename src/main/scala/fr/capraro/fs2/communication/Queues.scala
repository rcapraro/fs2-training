package fr.capraro.fs2.communication

import cats.effect.*
import cats.effect.std.Queue
import fs2.*

import java.time.LocalDateTime
import scala.concurrent.duration.*
import scala.util.Random

object Queues extends IOApp.Simple {

  override def run: IO[Unit] = {

    Stream.eval(Queue.unbounded[IO, Int]).flatMap { queue =>
      Stream.eval(Ref.of[IO, Int](0)).flatMap { ref =>
        val producer =
          Stream
            .iterate(0)(_ + 1)
            .covary[IO]
            .evalMap(e => IO.println(s"Offering $e") *> queue.offer(e))
            .drain

        val consumer =
          Stream
            .fromQueueUnterminated(queue)
            .evalMap(e => ref.update(_ + e))
            .metered(300.millis)
            .drain

        producer.merge(consumer).interruptAfter(3.seconds) ++ Stream.eval(ref.get.flatMap(IO.println))
      }
    }.compile.drain

    Stream.eval(Queue.unbounded[IO, Option[Int]]).flatMap { queue =>
      val p = (Stream.range(0, 10).map(Some.apply) ++ Stream(None)).evalMap(queue.offer)
      val c = Stream.fromQueueNoneTerminated(queue).evalMap(i => IO.println(i))
      c.merge(p)
    }.interruptAfter(5.seconds).compile.drain

    trait Controller {
      def postAccount(customerId: Long, accountType: String, creationDate: LocalDateTime): IO[Unit]
    }

    class Server(controller: Controller) {
      def start(): IO[Nothing] = {
        val prog = for {
          randomWait <- IO(Math.abs(Random.nextInt()) % 500)
          _          <- IO.sleep(randomWait.millis)
          _ <- controller.postAccount(
            customerId = Random.between(1L, 1000L),
            accountType = if (Random.nextBoolean()) "ira" else "brokerage",
            creationDate = LocalDateTime.now()
          )
        } yield ()
        prog.foreverM
      }
    }

    object PrintController extends Controller {
      override def postAccount(customerId: Long, accountType: String, creationDate: LocalDateTime): IO[Unit] =
        IO.println(s"Initiating account creation. Customer: $customerId, Account type: $accountType, Created: $creationDate")
    }

    case class CreateAccountData(customerId: Long, accountType: String, creationDate: LocalDateTime)
    class QueueController(queue: Queue[IO, CreateAccountData]) extends Controller {
      override def postAccount(customerId: Long, accountType: String, creationDate: LocalDateTime): IO[Unit] =
        queue.offer(CreateAccountData(customerId, accountType, creationDate))
    }

    Stream.eval(Queue.unbounded[IO, CreateAccountData]).flatMap { queue =>
      val serverStream: Stream[IO, Nothing] = Stream.eval(new Server(new QueueController(queue)).start())
      val consumerStream                    = Stream.fromQueueUnterminated(queue).printlns
      consumerStream.merge(serverStream)
    }.interruptAfter(30.seconds).compile.drain

  }

}
