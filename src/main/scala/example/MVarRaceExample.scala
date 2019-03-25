package example

import cats.effect.concurrent._
import cats.effect.{IO, _}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random


object MVarRaceExample {
  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
  implicit val timer = IO.timer(ExecutionContext.global)
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  def main(args: Array[String]): Unit = {
    val program = race(task(0), task(1))
    val value = program.unsafeRunSync()
    println(s"the winner is $value")
  }

  def task(index: Int): IO[Int] = {
    val sleep = Random.nextInt(4)
    IO {
      println(s"$index is sleeping for $sleep seconds")
    }.flatMap(_ => {
      IO.sleep(sleep.seconds).map(_ => {
        index
      })
    })
  }

  def race[A](taskA: IO[A], taskB: IO[A]): IO[A] = {
    val mvar: IO[MVar[IO, A]] = MVar[IO].empty[A]

    for {
      channel <- mvar
      tA = taskA.flatMap(value => channel.put(value))
      tB = taskB.flatMap(value => channel.put(value))
      fiberA <- tA.start
      fiberB <- tB.start
      value <- channel.take
      _ <- fiberA.cancel
      _ <- fiberB.cancel
    } yield value
  }
}