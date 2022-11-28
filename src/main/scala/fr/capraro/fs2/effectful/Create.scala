package fr.capraro.fs2.effectful

import cats.effect.*
import fs2.*

object Create extends IOApp.Simple {

  override def run: IO[Unit] = {

    val s: Stream[IO, Unit] = Stream.eval(IO.println("My first effectful stream!"))
    s.compile.toList.flatMap(IO.println) // values + effect
    s.compile.drain                      // only effect, discard produced values

    val s2: Stream[IO, Nothing] = Stream.exec(IO.println("My second effectful stream!"))
    s2.compile.drain

    val fromPure: Stream[IO, Int] = Stream(1, 2, 3).covary[IO]
    fromPure.compile.toList.flatMap(IO.println)

    val natsEval: Stream[IO, Int] = Stream.iterateEval(1)(a => IO.println(s"Producing ${a + 1}") *> IO(a + 1))
    natsEval.take(10).compile.toList.flatMap(IO.println)

    val alphabet = Stream.unfoldEval('a') { c =>
      if (c == 'z' + 1) IO.println("finishing...") *> IO(None)
      else IO.println(s"Producing $c") *> IO(Some(c, (c + 1).toChar))
    }

    alphabet.compile.toList.flatMap(IO.println)

    // Exo - fetch pages
    val data     = List.range(1, 100)
    val pageSize = 20

    def fetchPage(pageNumber: Int): IO[List[Int]] = {
      val start = pageNumber * pageSize
      val end   = start + pageSize
      IO.println(s"Fetching page $pageNumber").as(data.slice(start, end))
    }

    // use unfoldEval + flatten
    def fetchAll(): Stream[IO, Int] = {
      Stream.unfoldEval(0) { pageNumber =>
        fetchPage(pageNumber).map { pageElems =>
          if (pageElems.isEmpty) None
          else Some(Stream.emits(pageElems), pageNumber + 1)
        }
      }.flatten
    }

    fetchAll().compile.toList.flatMap(IO.println)
  }
}
