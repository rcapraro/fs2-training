package fr.capraro.fs2.effectful

import cats.effect.*
import fs2.*

import scala.concurrent.duration.*

object Combine extends IOApp.Simple {

  override def run: IO[Unit] = {

    val s = Stream.repeatEval(IO.println("Emitting...") *> IO(42))
    s.take(10).compile.toList.flatMap(IO.println)

    val s2 =
      for {
        x <- Stream.eval(IO.println("Producing 42") *> IO(42))
        y <- Stream.eval(IO.println("Producing 42") *> IO(x + 1))
      } yield y

    s2.compile.toList.flatMap(IO.println)

    val s3 = Stream(1, 2, 3).evalMap(i => IO.println(s"Element: $i").as(i))
    s3.compile.drain
    s3.compile.toList.flatMap(IO.println)

    // val myIO: IO[Int] = IO(42).flatTap(IO.println)

    val s4 = Stream(1, 2, 3).evalTap(IO.println)
    s4.compile.toList.flatMap(IO.println)

    val filterByFlippingCoin =
      Stream
        .range(1, 100)
        .evalFilter(i => IO(i % 2 == 0))

    filterByFlippingCoin.compile.toList.flatMap(IO.println)

    val s5 = Stream.exec(IO.println("Start")) ++ Stream(1, 2, 3) ++ Stream(4, 5, 6) ++ Stream.exec(IO.println("Finish"))
    s5.compile.toList.flatMap(IO.println)

    val delayed = Stream.sleep_[IO](1.second) ++ Stream.eval(IO.println("I am awake!"))
    delayed.compile.drain

    // Exo
    def evalEvery[A](d: FiniteDuration)(fa: IO[A]): Stream[IO, A] =
      (Stream.sleep_[IO](d) ++ Stream.eval(fa)).repeat

    evalEvery(2.seconds)(IO.println("Hi")).as(42).take(5).compile.toList.flatMap(IO.println)

  }
}
