package forex.services.rates.interpreters

import forex.services.rates.Algebra
import forex.domain.Rate
import forex.domain.Currency.show
import forex.services.rates.errors._
import forex.http.rates.Protocol._

import cats._
import cats.data._
import cats.implicits._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import cats.effect.Sync
import io.circe.Json

class OneFrameDocker[F[_]: Applicative: Sync](client: Client[F]) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] = {

      val headers: Headers = Headers(List(Header("token", "10dc303535874aeccc86a8251e6992f5")))

      val request: Error Either Request[F] =
          Uri.fromString(s"http://localhost:8081/rates?pair=${pair.from.show}${pair.to.show}") match {
            case Right(uri) => Request(uri = uri, headers = headers).asRight[Error]
            case Left(parseError) => Error.RequestDecodeFailed(parseError.message).asLeft[Request[F]]
          }

      def requestToResponse(req: Request[F]): F[Error Either Rate] =
        client.expect[Json](req).map { json =>
          json.as[List[GetApiResponse]] match {
            case Right(apiResponses) => apiResponses.headOption match {
              case Some(value) => Rate(Rate.Pair(value.from, value.to), value.price, value.time_stamp).asRight[Error]
              case None => Error.OneFrameLookupFailed("Empty response").asLeft[Rate]
            }
            case Left(error) => Error.ResponseDecodeFailed(error.message).asLeft[Rate]
          }
        }

      (for {
        req <- EitherT.fromEither[F](request)
        resp <- EitherT(requestToResponse(req))
      } yield resp).value
  }

}
