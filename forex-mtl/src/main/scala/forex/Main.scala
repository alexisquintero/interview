package forex

import cats.effect._
import cats.syntax.functor._
import forex.config._
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.global
import org.http4s.client.Client

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO](global).resource.use { client =>
      new Application[IO].stream(client).compile.drain.as(ExitCode.Success)
    }

}

class Application[F[_]: ConcurrentEffect: Timer] {

  implicit val client: BlazeClientBuilder[F] = BlazeClientBuilder[F](global)

  def stream(client: Client[F]): Stream[F, Unit] =
    for {
      config <- Config.stream("app")
      module = new Module[F](config, client)
      _ <- BlazeServerBuilder[F]
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()

}
