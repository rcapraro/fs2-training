package fr.capraro.fs2.communication

import cats.*
import cats.effect.*
import cats.syntax.order.*
import fs2.*
import fs2.concurrent.*

import scala.concurrent.duration.*
import scala.util.Random

object Channels extends IOApp.Simple {

  override def run: IO[Unit] = {

    Stream.eval(Channel.bounded[IO, Int](1)).flatMap { channel =>
      val p = Stream.iterate(1)(_ + 1).covary[IO].evalMap(channel.send).drain
      val c = channel.stream.metered(200.millis).evalMap(i => IO.println(s"Read $i")).drain
      c.concurrently(p).interruptAfter(3.seconds)
    }.compile.drain

    sealed trait Measurement
    case class Temperature(value: Double) extends Measurement
    case class Humidity(value: Double)    extends Measurement

    implicit val ordHum: Order[Humidity]     = Order.by(_.value)
    implicit val ordTemp: Order[Temperature] = Order.by(_.value)

    def createTemperatureSensor(alarm: Channel[IO, Measurement], threshold: Temperature): Stream[IO, Nothing] =
      Stream
        .repeatEval(IO(Temperature(Random.between(-20.0, 40.0))))
        .evalTap(t => IO.println(f"Current temperature: ${t.value}%.1f °C"))
        .evalMap(t => if (t > threshold) alarm.send(t) else IO.unit)
        .metered(300.millis)
        .drain

    def createHumiditySensor(alarm: Channel[IO, Measurement], threshold: Humidity): Stream[IO, Nothing] =
      Stream
        .repeatEval(IO(Humidity(Random.between(0.0, 100.0))))
        .evalTap(h => IO.println(f"Current humidity: ${h.value}%.1f percent"))
        .evalMap(h => if (h < threshold) alarm.send(h) else IO.unit)
        .metered(100.millis)
        .drain

    def createCooler(alarm: Channel[IO, Measurement]): Stream[IO, Nothing] =
      alarm
        .stream
        .evalMap {
          case Temperature(t) => IO.println(f"$t%.1f °C is too hot! Cooling down...")
          case Humidity(h) => IO.println(f"$h%.1f percent is too dry! Humidifying...")
        }
        .drain

    val temperatureThreshold = Temperature(20.0)
    val humidityThreshold = Humidity(10.0)

    val program = Stream.eval(Channel.unbounded[IO, Measurement]).flatMap { alarmChannel =>
      val temperatureSensor = createTemperatureSensor(alarmChannel, temperatureThreshold)
      val humiditySensor = createHumiditySensor(alarmChannel, humidityThreshold)
      val cooler = createCooler(alarmChannel)
      Stream(temperatureSensor, humiditySensor, cooler).parJoinUnbounded
    }

    program.interruptAfter(5.seconds).compile.drain
  }

}
