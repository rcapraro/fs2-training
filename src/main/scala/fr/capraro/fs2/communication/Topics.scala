package fr.capraro.fs2.communication

import cats.effect.*
import fs2.*
import fs2.concurrent.*

import scala.concurrent.duration.*
import scala.util.Random

object Topics extends IOApp.Simple {

  override def run: IO[Unit] = {

    Stream.eval(Topic[IO, Int]).flatMap { topic =>
      val p  = Stream.iterate(1)(_ + 1).covary[IO].through(topic.publish).drain
      val c1 = topic.subscribe(10).evalMap(i => IO.println(s"Read $i from c1")).drain
      val c2 = topic.subscribe(10).evalMap(i => IO.println(s"Read $i from c2")).metered(100.millis).drain
      Stream(p, c1, c2).parJoinUnbounded
    }.interruptAfter(3.seconds).compile.drain

    case class CarPosition(carId: Long, lat: Double, lng: Double)

    def createCar(carId: Long, topic: Topic[IO, CarPosition]): Stream[IO, Nothing] = {
      Stream
        .repeatEval(IO(CarPosition(carId, Random.between(-90.0, 90.0), Random.between(-180.0, 180.0))))
        .metered(1.second)
        .through(topic.publish)
        .drain
    }

    def createGoogleMapUpdater(topic: Topic[IO, CarPosition]): Stream[IO, Nothing] = {
      topic
        .subscribe(10)
        .evalMap(pos => IO.println(f"Drawing position (${pos.lat}%.2f, ${pos.lng}%.2f) for car ${pos.carId} in map..."))
        .drain
    }

    def createDriverNotifier(
      topic: Topic[IO, CarPosition],
      shouldNotify: CarPosition => Boolean,
      notify: CarPosition => IO[Unit]
    ): Stream[IO, Nothing] = {
      topic
        .subscribe(10)
        .evalMap(pos => if (shouldNotify(pos)) notify(pos) else IO.unit)
        .drain
    }

    Stream.eval(Topic[IO, CarPosition]).flatMap { topic =>
      val cars             = Stream.range(1, 10).map(carId => createCar(carId, topic))
      val googleMapUpdater = createGoogleMapUpdater(topic)
      val driverNotifier = createDriverNotifier(
        topic = topic,
        shouldNotify = pos => pos.lat > 0.0,
        notify = pos => IO.println(f"Car ${pos.carId}: you are above the equator! (${pos.lat}%.2f, ${pos.lng}%.2f)")
      )
      (cars ++ Stream(googleMapUpdater, driverNotifier)).parJoinUnbounded
    }.interruptAfter(3.seconds).compile.drain

  }

}
