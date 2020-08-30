package forex.services.rates.interpreters

import forex.services.rates.Algebra
import forex.domain.Rate
import forex.services.rates.errors._

import cats.Applicative
import cats.implicits._
import forex.cache.{ Algebra => CacheAlgebra }
import forex.cache.errors.Error._

class OneFrameDocker[F[_]: Applicative](cache: CacheAlgebra[F, Rate.Pair, Rate]) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.get(pair).map(_.leftMap[Error](e => e match {
      case CacheFailedFetch(msg) => Error.OneFrameLookupFailed(msg)
      case CacheFailedGet(msg) => Error.OneFrameLookupFailed(msg)
      case CacheRequestDecodeFailed(msg) => Error.OneFrameLookupFailed(msg)
    }))
}
