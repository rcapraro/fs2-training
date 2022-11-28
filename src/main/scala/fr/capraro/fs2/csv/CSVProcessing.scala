package fr.capraro.fs2.csv

import cats.effect.{IO, IOApp}
import fs2.io.file.{Files, Path}
import fs2.text

import java.nio.file.{Files as JFiles, Paths}
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.*
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

object CSVProcessing extends IOApp.Simple {

  case class LegoSet(id: String, name: String, year: Int, themeId: Int, numParts: Int)

  def parseLegoSet(line: String): Option[LegoSet] = {
    val splitted = line.split(",")
    Try(LegoSet(
      id = splitted(0),
      name = splitted(1),
      year = splitted(2).toInt,
      themeId = splitted(3).toInt,
      numParts = splitted(4).toInt
    )).toOption
  }

  def readLegoSetImperative(fileName: String, p: LegoSet => Boolean, limit: Int): List[LegoSet] = {
    val legoSets: ListBuffer[LegoSet] = ListBuffer.empty
    var counter                       = 0

    Using(Source.fromResource(fileName).bufferedReader()) { reader =>
      var line: String = reader.readLine()
      while (line != null && counter < limit) {
        val legoSet = parseLegoSet(line)
        legoSet.filter(p).foreach { ls =>
          legoSets.append(ls)
          counter += 1
        }
        line = reader.readLine()
      }
    }

    legoSets.toList
  }

  def readLegoSetList(fileName: String, p: LegoSet => Boolean, limit: Int): List[LegoSet] = {
    JFiles
      .readAllLines(Paths.get(fileName)) // issue: we read all lines in memory !
      .asScala
      .flatMap(parseLegoSet)
      .filter(p)
      .take(limit)
      .toList
  }

  def readLegoSetIterator(fileName: String, p: LegoSet => Boolean, limit: Int): List[LegoSet] =
    Using(Source.fromResource(fileName)) { source =>
      source
        .getLines() // buffered :-)
        .flatMap(parseLegoSet)
        .filter(p)
        .take(limit)
        .toList
    }.get

  def readLegoSetStreams(fileName: String, p: LegoSet => Boolean, limit: Int): IO[List[LegoSet]] = {
    Files[IO].readAll(Path(fileName))
      .through(text.utf8.decode)
      .through(text.lines)
      .map(parseLegoSet) // sequential
      .evalTap(IO.println)
      .metered(1.second) // if we want to emit one line each second
      // .parEvalMapUnbounded(s => IO(parseLegoSet(s))) // parallel
      .unNone // drop None
      .filter(p)
      .take(limit)
      .compile
      .toList
  }

  override def run: IO[Unit] =
    // IO(readLegoSetImperative("sets.csv", _.year >= 1970, 5)).flatMap(IO.println)
    // IO(readLegoSetList("src/main/resources/sets.csv", _.year >= 1970, 5)).flatMap(IO.println)
    // IO(readLegoSetIterator("sets.csv", _.year >= 1970, 5)).flatMap(IO.println)
    readLegoSetStreams("src/main/resources/sets.csv", _.year >= 1970, 5).flatMap(IO.println)

}
