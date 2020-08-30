package forex
package cache

object errors {
  sealed trait Error
  object Error {
    final case class CacheFailedFetch(msg: String)         extends Error
    final case class CacheFailedGet(msg: String)           extends Error
    final case class CacheRequestDecodeFailed(msg: String) extends Error
  }
}
