package fr.capraro.fs2.effectful

import cats.effect.*
import fs2.*

import scala.concurrent.duration.*
import scala.util.Random

object Retry extends IOApp.Simple {

  override def run: IO[Unit] = {

    def doEffectFailing[A](io: IO[A]): IO[A] = {
      IO(math.random()).flatMap { flag =>
        if (flag < 0.5) IO.println("Failing...") *> IO.raiseError(new Exception("Boom!"))
        else IO.println("Successful!") *> io
      }
    }

    Stream.retry(
      fo = doEffectFailing(IO(42)),
      delay = 1.second,
      nextDelay = _ * 2,
      maxAttempts = 3
    ).compile.drain

    // Exo
    val searches                              = Stream.iterateEval("")(s => IO(Random.nextPrintableChar()).map(s + _))
    def performSearch(text: String): IO[Unit] = doEffectFailing(IO.println(s"Performing search for text $text"))

    // The delays should be 1s, 2s, 3s, 4,s, ...
    // The max number of attempts is 5
    def performSearchRetrying(text: String): Stream[IO, Unit] =
      Stream
        .retry(
          fo = performSearch(text),
          delay = 1.second,
          nextDelay = _ + 1.second,
          maxAttempts = 5
        )

    // Process the searches
    // 1 - Simulates that the user enter a char every 200 millis
    // 2 - Sample the search string every 500 millis
    // 3 - Run the processing for 5 seconds
    searches
      .metered(200.millis)
      .debounce(500.millis)
      .flatMap(performSearchRetrying)
      .interruptAfter(5.seconds)
      .compile
      .drain

  }

}
