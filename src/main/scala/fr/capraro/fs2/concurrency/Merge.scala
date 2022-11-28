package fr.capraro.fs2.concurrency

import fs2.*
import cats.effect.*

import scala.concurrent.duration.*
import scala.util.Random

object Merge extends IOApp.Simple {

  override def run: IO[Unit] = {

    val s1Inf: Stream[IO, String] = Stream.iterate("0")(_ + "1").covary[IO].metered(100.millis)
    val s2Inf: Stream[IO, String] = Stream.iterate("a")(_ + "a").covary[IO].metered(200.millis)
    val s3Inf: Stream[IO, String] = s1Inf.merge(s2Inf)
    s3Inf.interruptAfter(5.seconds).printlns.compile.drain

    val s1Failing    = Stream("1", "2", "3").covary[IO].metered(100.millis) ++ Stream.raiseError[IO](new Exception("s1 failed"))
    val sLeftFailing = s1Failing.merge(s2Inf)
    sLeftFailing.interruptAfter(5.seconds).printlns.compile.drain

    val s2Failing      = Stream("a", "b", "c").covary[IO].metered(200.millis) ++ Stream.raiseError[IO](new Exception("s1 failed"))
    val s3RightFailing = s1Inf.merge(s2Failing)
    s3RightFailing.interruptAfter(5.seconds).printlns.compile.drain

    val s1Finite = Stream(1, 2, 3).covary[IO].metered(100.millis)
    val s2Finite = Stream(4, 5, 6).covary[IO].metered(200.millis)
    val s3Finite = s1Finite.merge(s2Finite)
    s3Finite.interruptAfter(5.seconds).printlns.compile.drain

    val s3Mixed = s1Finite.mergeHaltL(s2Inf) // finish when s1Finite is exhausted
    s3Mixed.interruptAfter(5.seconds).printlns.compile.drain

    // Exercise
    def fetchRandomQuoteFromSource1: IO[String] = IO(Random.nextString(5))
    def fetchRandomQuoteFromSource2: IO[String] = IO(Random.nextString(25))

    // Fetch 100 quotes from source 1, 150 from source 2, runs for 5 seconds, and prints the quotes to console
    val s1 = Stream.repeatEval(fetchRandomQuoteFromSource1).take(100)
    val s2 = Stream.repeatEval(fetchRandomQuoteFromSource2).take(150)
    s1.merge(s2).interruptAfter(5.seconds).printlns.compile.drain

  }

}
