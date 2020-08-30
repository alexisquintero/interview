package forex

import cats.effect._
import cats.syntax.functor._
import forex.config._
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.global
import cats.effect.concurrent.Semaphore
import forex.cache.{ Algebra, CacheProgram }

object Main extends IOApp {

  // TODO: Better resource creation
  // TODO: Error handling
  // TODO: Test
  override def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO](global).resource.use { client =>
      Semaphore[IO](1).flatMap { sem =>
        CacheProgram(client, sem).flatMap { cache =>
          new Application[IO].stream(cache).compile.drain.as(ExitCode.Success)
        }
      }
    }

}

class Application[F[_]: ConcurrentEffect: Timer] {

  def stream(cache: Algebra.RateCache[F]): Stream[F, Unit] =
    for {
      config <- Config.stream("app")
      module = new Module[F](config, cache)
      _ <- BlazeServerBuilder[F]
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()

}
