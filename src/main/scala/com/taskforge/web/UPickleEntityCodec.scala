package com.taskforge.web

import cats.effect.IO
import org.http4s.{DecodeResult, EntityDecoder, EntityEncoder, MalformedMessageBodyFailure, MediaType}
import org.http4s.headers.`Content-Type`
import upickle.default.{read, write, Reader, Writer}

/** Bridges upickle to http4s.
  *
  * http4s ships first-class circe integration but nothing for upickle, so we provide the two
  * missing pieces ourselves — an EntityEncoder (case class -> JSON response body) and an
  * EntityDecoder (JSON request body -> case class) — generically, for anything that has a upickle
  * Reader/Writer. ~30 lines buys upickle end to end, and shows how http4s codecs actually work.
  */
object UPickleEntityCodec:

  /** Any A with a upickle Writer can be an http4s response entity. */
  given [A](using Writer[A]): EntityEncoder[IO, A] =
    EntityEncoder
      .stringEncoder[IO]
      .contramap[A](a => write(a))
      .withContentType(`Content-Type`(MediaType.application.json))

  /** Any A with a upickle Reader can be parsed from an application/json request body. A parse
    * failure becomes MalformedMessageBodyFailure — a typed DecodeFailure the error middleware
    * maps to 400 Bad Request, so client mistakes never surface as 500s.
    */
  given [A](using Reader[A]): EntityDecoder[IO, A] =
    EntityDecoder.decodeBy(MediaType.application.json) { msg =>
      DecodeResult {
        msg.bodyText.compile.string.map { body =>
          scala.util
            .Try(read[A](body))
            .toEither
            .left
            .map(e => MalformedMessageBodyFailure(s"Invalid JSON: ${e.getMessage}", Some(e)))
        }
      }
    }
