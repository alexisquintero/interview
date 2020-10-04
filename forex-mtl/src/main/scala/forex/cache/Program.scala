package forex.cache

import errors._
import forex.domain.Rate
import forex.http.rates.Protocol._

import cats.data._
import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.{ Ref, Semaphore }
import cats.Applicative

import org.http4s._
import org.http4s.client.Client

import java.time.Instant

class CacheProgram[F[_]: Sync](
  msToVoid: Long,
  fetcher: Request[F] => F[Error Either List[Rate]],
  semaphore: Semaphore[F],
  state: Ref[F, Map[Rate.Pair, Rate]]
) extends Algebra.RateCache[F] {
  private def get1(k: Rate.Pair): F[Option[Rate]] =
    state.get.map(_.get(k))

  private def update(k: Rate.Pair): F[Error Either Unit] = {

    val headers: Headers = Headers(List(Header("token", "10dc303535874aeccc86a8251e6992f5")))

    val request: Error Either Request[F] =
      Uri
        .fromString(s"http://localhost:8081/rates?pair=${k.from.show}${k.to.show}")
        .bimap(
        { error: ParseFailure => Error.CacheRequestDecodeFailed(error.message) },
        { uri: Uri => Request(uri = uri, headers = headers) })

    val makeUpdate: EitherT[F, Error, Unit] =
      for {
        req  <- EitherT.fromEither[F](request)
        resp <- EitherT(fetcher(req))
        respMap = resp.foldLeft(Map.empty[Rate.Pair, Rate])((acc, cur) => acc + (cur.pair -> cur))
        _    <- EitherT.liftF[F, Error, Unit](state.update(cur => cur ++ respMap))
      } yield ()

    for {
      _ <- semaphore.acquire
      f <- get1(k).map(_.fold(true)(checkTimestamp))
      e <- if (f) makeUpdate.value else Applicative[F].unit.map(_.asRight[Error])
      _ <- semaphore.release
    } yield e

  }

  def valueOptToErr(v: Option[Rate]): Error Either Rate =
    v match {
      case None => Error.CacheFailedGet(s"Can't find value").asLeft[Rate]
      case Some(value) => value.asRight[Error]
    }

  def checkTimestamp(rate: Rate): Boolean =
    rate.timestamp.value.toInstant.compareTo(Instant.now.minusSeconds(msToVoid)) < 0

  def updateAndGet(k: Rate.Pair): F[Error Either Rate] =
    update(k) *> get1(k).map(valueOptToErr)

  override def get(k: Rate.Pair): F[Error Either Rate] = {
    get1(k).flatMap { v =>
      v match {
        case None => updateAndGet(k)
        case ov @ Some(value) =>
          if (checkTimestamp(value)) updateAndGet(k)
          else Applicative[F].pure(valueOptToErr(ov))
      }
    }
  }
}

object CacheProgram {

  def fetcher[F[_]: Sync](client: Client[F])(request: Request[F]): F[Error Either List[Rate]] =
    client
      .expect[List[GetApiResponse]](request)
      .attempt
      .map(
        _.bimap(
        { t: Throwable => Error.CacheFailedFetch(t.getMessage) },
        { la: List[GetApiResponse] => la.map(a => Rate(Rate.Pair(a.from, a.to), a.price, a.time_stamp)) }))

  def apply[F[_]: Sync](
    msToVoid: Long,
    client: Client[F],
    semaphore: Semaphore[F]
  ): F[Algebra.RateCache[F]] = {
    Ref.of[F, Map[Rate.Pair, Rate]](Map.empty).map { state =>
      new CacheProgram[F](msToVoid, fetcher[F](client), semaphore, state)
    }
  }

  def test[F[_]: Sync](
    msToVoid: Long,
    fetcher: Request[F] => F[Error Either List[Rate]],
    semaphore: Semaphore[F]
  ): F[Algebra.RateCache[F]] = {
    Ref.of[F, Map[Rate.Pair, Rate]](Map.empty).map { state =>
      new CacheProgram[F](msToVoid, fetcher, semaphore, state)
    }
  }
}
