package forex.services.rates

import cats.Applicative
import interpreters._
import cats.effect.Concurrent
import org.http4s.client.Client

object Interpreters {
  def dummy[F[_]: Applicative](): Algebra[F] = new OneFrameDummy[F]()
  def docker[F[_]: Applicative: Concurrent](client: Client[F]): Algebra[F] =
    new OneFrameDocker[F](client)
}
