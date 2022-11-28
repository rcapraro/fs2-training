package fr.capraro.fs2.effectful

import cats.effect.*
import fs2.*

import java.io.{BufferedReader, FileReader}

object Resources extends IOApp.Simple {

  override def run: IO[Unit] = {

    val acquireReader = IO.blocking(new BufferedReader(new FileReader("src/main/resources/sets.csv")))
    val releaseReader = (reader: BufferedReader) => IO.println("Releasing") *> IO.blocking(reader.close())

    def readLines(reader: BufferedReader): Stream[IO, String] = {
      Stream
        .repeatEval(IO.blocking(reader.readLine()))
        .takeWhile(_ != null)
    }

    Stream
      .bracket(acquireReader)(releaseReader)
      .flatMap(readLines)
      .drop(1)
      .take(10)
      .printlns
      .compile
      .drain

    // already have the resource ?
    val readerResource: Resource[IO, BufferedReader] = Resource.make(acquireReader)(releaseReader)

    Stream
      .resource(readerResource)
      .flatMap(readLines)
      .drop(1)
      .take(10)
      .printlns
      .compile
      .drain

    // BufferedReader extends AutoCloseable
    Stream
      .fromAutoCloseable(acquireReader)
      .flatMap(readLines)
      .drop(1)
      .take(10)
      .printlns
      .compile
      .drain
  }

}
