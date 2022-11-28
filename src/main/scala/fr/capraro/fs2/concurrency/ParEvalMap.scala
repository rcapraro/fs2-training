package fr.capraro.fs2.concurrency

import cats.effect.*
import fs2.*

import scala.concurrent.duration.*

object ParEvalMap extends IOApp.Simple {

  override def run: IO[Unit] = {

    trait JobState
    case object Created   extends JobState
    case object Processed extends JobState

    case class Job(id: Long, state: JobState)

    def processJob(job: Job): IO[Job] = {
      IO.println(s"Processing job ${job.id}") *>
        IO.sleep(1.second) *>
        IO.pure(job.copy(state = Processed))
    }

    val jobs: Stream[IO, Job] = Stream.unfold(1)(id => Some(Job(id, Created), id + 1)).covary[IO]

    jobs
      // .parEvalMapUnbounded(processJob)
      // .parEvalMap(4)(processJob)
      .parEvalMapUnordered(16)(processJob)
      .interruptAfter(5.seconds)
      .compile
      .toList
      .flatMap(IO.println)

    // Exercise
    case class Event(jobId: Long, seqNo: Long)
    def processJobS(job: Job): IO[List[Event]] = {
      IO.println(s"Processing job ${job.id}") *>
        IO.sleep(1.second) *>
        IO.pure(List.range(1, 10).map(seqNo => Event(job.id, seqNo)))
    }

    extension[A] (s: Stream[IO, A]) {

      def parEvalMapSeq[B](maxConcurrent: Int)(f: A => IO[List[B]]): Stream[IO, B] =
        s.parEvalMap(maxConcurrent)(f).flatMap(Stream.emits)

      def parEvalMapSeqUnbounded[B](f: A => IO[List[B]]): Stream[IO, B] =
        parEvalMapSeq(Int.MaxValue)(f)
    }

    jobs
      .parEvalMapSeq(5)(processJobS)
      .interruptAfter(5.seconds)
      .compile
      .toList
      .flatMap(IO.println)
  }

}
