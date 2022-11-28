package fr.capraro.fs2.concurrency

import fs2.*
import cats.effect.*
import scala.concurrent.duration.*

object Concurrently extends IOApp.Simple {

  override def run: IO[Unit] = {

    val s1 = Stream(1, 2, 3).covary[IO].printlns
    val s2 = Stream(4, 5, 6).covary[IO].printlns
    s1.concurrently(s2).compile.drain // from now, pretty much like merge


    val s1Inf = Stream.iterate(0)(_ + 1).covary[IO].printlns
    s1Inf.concurrently(s2).interruptAfter(3.seconds).compile.drain // from now, pretty much like merge

    val s2Inf = Stream.iterate(2_000)(_ + 1).covary[IO].printlns
    s1.concurrently(s2Inf).compile.drain // biased toward s1 (s1 = main stream, s2Inf = background, stops when s1 stops)

    val s1Failing = Stream.repeatEval(IO(42)).take(500).printlns ++ Stream.raiseError[IO](new Exception("s1 failed"))
    val s2Failing = Stream.repeatEval(IO(600)).take(500).printlns ++ Stream.raiseError[IO](new Exception("s2 failed"))
    s1Inf.concurrently(s2Failing).compile.drain
    s1Failing.concurrently(s2Inf).compile.drain

    val s3 =  Stream.iterate(3_000)(_ + 1).covary[IO]
    val s4 =  Stream.iterate(4_000)(_ + 1).covary[IO]
    s3.concurrently(s4).take(100).compile.toList.flatMap(IO.println) // different from merge, emits s3 elements only

    // Exercise
    val numItems = 30

    def processor(itemsProcessed: Ref[IO, Int]) =
      Stream
        .repeatEval(itemsProcessed.update(_ + 1))
        .take(numItems)
        .metered(100.millis)
        .drain

    def progressTracker(itemsProcessed: Ref[IO, Int]) =
      Stream
        .repeatEval(itemsProcessed.get.flatMap(n => IO.println(s"Progress: ${n * 100 / numItems} %")))
        .metered(100.millis)
        .drain

    // Create a stream that emit a ref (initially 0)
    // Run the processor and the progressTracker concurrently
    Stream.eval(Ref.of[IO, Int](0)).flatMap { itemsProcessed =>
      processor(itemsProcessed).concurrently(progressTracker(itemsProcessed)) //processor on the left, it's the one doing the "real" work
    }.compile.drain


  }

}
