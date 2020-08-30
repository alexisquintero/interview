package forex.cache

import errors._
import forex.domain.Rate

trait Algebra[F[_], K, V] {
  def get(k: K): F[Error Either V]
}

object Algebra {
  type RateCache[F[_]] = Algebra[F, Rate.Pair, Rate]
}


