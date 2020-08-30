package forex.services.rates

import cats.Applicative
import interpreters._
import cats.effect.Concurrent
import forex.cache.{ Algebra => CacheAlgebra }

object Interpreters {
  def dummy[F[_]: Applicative](): Algebra[F] = new OneFrameDummy[F]()
  def docker[F[_]: Applicative: Concurrent](cache: CacheAlgebra.RateCache[F]): Algebra[F] =
    new OneFrameDocker[F](cache)
}
