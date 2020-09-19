package forex

import cats.implicits._
import cats.effect._
import cats.effect.concurrent.Semaphore
import fs2.Stream

import forex.config._
import forex.cache.{ Algebra, CacheProgram }

import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.global

object Main extends IOApp {

  def buildAndRun[F[_]: ConcurrentEffect: Timer]: F[ExitCode] =
    BlazeClientBuilder[F](global).resource.use { client =>
      Semaphore[F](1).flatMap { sem =>
        CacheProgram(client, sem).flatMap { cache =>
          new Application[F].stream(cache).compile.drain.as(ExitCode.Success)
        }
      }
    }

  // TODO: Test
  override def run(args: List[String]): IO[ExitCode] =
    buildAndRun[IO]

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
