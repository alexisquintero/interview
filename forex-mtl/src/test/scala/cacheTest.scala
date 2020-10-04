package forex.test

import forex.cache._
import forex.domain.Rate
import errors._

import org.scalatest.flatspec._
import org.scalatest.matchers._

import cats._
import cats.effect._
import cats.implicits._
import cats.effect.concurrent.Semaphore

import org.http4s.Request

import scala.concurrent.ExecutionContext
import forex.domain.Currency
import forex.domain.Price
import forex.domain.Timestamp
import cats.effect.concurrent.Ref

// TODO: Prop teesting
class CacheSpec extends AnyFlatSpec with should.Matchers {
  implicit val executionContext: ExecutionContext =
    ExecutionContext.Implicits.global
  implicit val timer: cats.effect.Timer[IO] =
    IO.timer(executionContext)
  implicit val cs: ContextShift[IO] =
    IO.contextShift(executionContext)

  val pair: Rate.Pair = Rate.Pair(Currency.USD, Currency.EUR)

  def goodFetcher[F[_]: Applicative](counter: Ref[F, Int]): Request[F] => F[Error Either List[Rate]] =
    _ => counter.update(_ + 1) *> Applicative[F].pure(
      List(
        Rate(
          pair,
          Price(BigDecimal(1.0)),
          Timestamp.now
        )
      ).asRight[Error]
    )

    val getError: Error.CacheFailedGet = Error.CacheFailedGet("Can't find value")

  def badFetcher[F[_]: Applicative](err: Error): Request[F] => F[Error Either List[Rate]] =
    _ => Applicative[F].pure(
      err.asLeft[List[Rate]]
    )

  def testBadCache[F[_]: Sync: Concurrent](
    ms: Long,
    fetcher: Request[F] => F[Error Either List[Rate]]
  ): F[Algebra.RateCache[F]] = {
    Semaphore[F](1).flatMap { sem =>
      CacheProgram.test(ms, fetcher, sem)
    }
  }

  def testGoodCache[F[_]: Sync: Concurrent: Applicative](
    ms: Long,
    counter: Ref[F, Int],
  ): F[Algebra.RateCache[F]] = {
    Semaphore[F](1).flatMap { sem =>
      CacheProgram.test(ms, goodFetcher(counter), sem)
    }
  }

  "Cache" should "fail to get when fetch can't get values" in {
    val cacheFailedFetch: IO[Algebra.RateCache[IO]] =
      testBadCache[IO](1L, badFetcher(getError))

    cacheFailedFetch.flatMap(_.get(pair)).unsafeRunSync shouldBe getError.asLeft[Rate]
  }

  "Cache" should "fail to get when ..." in {
    // TODO
  }

  "Cache" should "only fetch when needed" in {
    val counter0: IO[Ref[IO, Int]] = Ref.of[IO, Int](0)

    val counter: Int =
      counter0.flatMap { counter =>
        testGoodCache[IO](100L, counter)
        counter.get
      }.unsafeRunSync

    counter shouldBe (0)
  }

  "Cache" should "only fetch again after cache is invalidated" in {
    val counter1: IO[Ref[IO, Int]] = Ref.of[IO, Int](0)

    val counter: Int =
      counter1.flatMap { counter =>
        testGoodCache[IO](100L, counter).flatMap { alg =>
            alg.get(pair) *>
            alg.get(pair) *>
            alg.get(pair) *>
            alg.get(pair) *>
            alg.get(pair) *>
            counter.get
        }
      }.unsafeRunSync

    counter shouldBe (1)
  }

  "Cache" should "fetch again after cache is invalidated" in {
    val counter3: IO[Ref[IO, Int]] = Ref.of[IO, Int](0)

    val counter: Int =
      counter3.flatMap { counter =>
        testGoodCache[IO](0L, counter).flatMap { alg =>
            alg.get(pair) *>
            IO.delay(Thread.sleep(10)) *>
            alg.get(pair) *>
            IO.delay(Thread.sleep(10)) *>
            alg.get(pair) *>
            counter.get
        }
      }.unsafeRunSync

    counter shouldBe (3)
  }
}

