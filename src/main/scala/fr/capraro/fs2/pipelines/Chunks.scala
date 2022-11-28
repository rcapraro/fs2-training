package fr.capraro.fs2.pipelines
import cats.effect.*
import fs2.*
import fs2.io.file.*

import scala.reflect.ClassTag

object Chunks extends IOApp.Simple {

  override def run: IO[Unit] = {

    val s1 = Stream(1, 2, 3, 4)
    IO.println(s1.chunks.toList)

    val s2 = Stream(Stream(1, 2, 3), Stream(4, 5, 6)).flatten
    IO.println(s2.chunks.toList)

    val s3 = Stream(1, 2) ++ Stream(3) ++ Stream(4, 5)
    IO.println(s3.chunks.toList)

    val s4 = Stream.repeatEval(IO(4)).take(5)
    s4.chunks.compile.toList.flatMap(IO.println)

    val s5 = Files[IO].readAll(Path("src/main/resources/sets.csv"))
    s5.chunks.map(_.size).compile.toList.flatMap(IO.println)

    // Fast concat
    // Fast indexing
    // Avoid copying
    // List-like interface
    val c: Chunk[Int] = Chunk(1, 2, 3)
    IO.println(c)

    val c2: Chunk[Int] = Chunk.array(Array(4, 5, 6))
    IO.println(c2)

    val c3: Chunk[Int] = Chunk.singleton(7)
    IO.println(c3)

    val c4: Chunk[Int] = Chunk.empty
    IO.println(c4)

    val c5 = c ++ c2 ++ c3 ++ c4
    IO.println(c5)
    IO.println(c(2))
    IO.println(c5.size)
    IO.println(c.map(_ * 2))
    IO.println(c.flatMap(i => Chunk(i, i + 1)))
    IO.println(c.filter(_ < 3))
    IO.println(c5.take(5))

    val a = new Array[Int](3)
    IO { c.copyToArray(a); println(a.toList) }
    IO.println(c5.compact)

    // Exo
    def compact[A: ClassTag](chunk: Chunk[A]): Chunk[A] = {
      val arr = new Array[A](chunk.size)
      chunk.copyToArray(arr)
      Chunk.array(arr)
    }

    IO.println(compact(c5))

  }

}
