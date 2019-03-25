package example

import java.util.concurrent.Executors

import cats.effect._
import cats.effect.concurrent._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object MVarExample {
  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
  implicit val timer = IO.timer(ExecutionContext.global)

  val cachedThreadPool = Executors.newCachedThreadPool()
  val consumerThreadPool = ExecutionContext.fromExecutor(cachedThreadPool)
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  def main(args: Array[String]): Unit = {
    val mvar: IO[MVar[IO, Int]] = MVar[IO].empty[Int]

    val program = for {
      channel <- mvar
      pTask = producer(channel)
      cTask = consumer(channel)
      fp <- pTask.start
      fc <- cTask.start
      _ <- fp.join
      value <- fc.join
      _ <- IO(cachedThreadPool.shutdown)
    } yield value

    val result = program.unsafeRunSync()
    println(s"value: $result")
  }

  def producer(channel: MVar[IO, Int]): IO[Unit] = IO.sleep(1.seconds)
    .flatMap(_ => IO {
      println("producer thread: " + Thread.currentThread().getName())
    })
    .flatMap(_ => channel.put(42))

  def consumer(channel: MVar[IO, Int]): IO[Int] = IO.shift(consumerThreadPool)
    .flatMap(_ => IO {
      println("consumer thread: " + Thread.currentThread().getName())
    })
    .flatMap(_ => channel.take)

}