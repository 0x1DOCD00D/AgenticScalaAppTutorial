package com.taskforge.web

import cats.effect.IO
import doobie.Transactor
import doobie.implicits.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import upickle.default.ReadWriter

import com.taskforge.web.UPickleEntityCodec.given

final case class HealthStatus(status: String, database: String) derives ReadWriter

/** Liveness vs readiness, split on purpose:
  *   - /healthz answers instantly and only proves the JVM is serving requests. The ECS/ALB
  *     liveness check uses it — a slow database must never cause container restarts.
  *   - /readyz round-trips the database (SELECT 1). Deploy scripts and smoke tests gate on it.
  */
final class HealthRoutes(xa: Transactor[IO]):

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case GET -> Root / "healthz" =>
      Ok(HealthStatus(status = "ok", database = "unchecked"))

    case GET -> Root / "readyz" =>
      sql"SELECT 1".query[Int].unique.transact(xa).attempt.flatMap {
        case Right(_) => Ok(HealthStatus(status = "ok", database = "ok"))
        case Left(e) =>
          // getMessage can be null; never let the failure report itself fail to serialize.
          val reason = Option(e.getMessage).getOrElse(e.getClass.getName)
          ServiceUnavailable(HealthStatus(status = "degraded", database = reason))
      }
  }
