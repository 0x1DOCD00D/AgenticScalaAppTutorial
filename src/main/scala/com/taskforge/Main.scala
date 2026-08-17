package com.taskforge

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import com.comcast.ip4s.{Host, Port}
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import org.http4s.server.staticcontent.resourceServiceBuilder

import com.taskforge.config.AppConfig
import com.taskforge.data.{Database, DoobieTaskRepository}
import com.taskforge.service.TaskService
import com.taskforge.web.{HealthRoutes, TaskRoutes}

/** Composition root. The ONLY place where concrete implementations meet: config is loaded,
  * migrations run, the doobie repository is wired into the service, the service into the routes,
  * and Ember serves the result. Every tier above this file depends on interfaces.
  */
object Main extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    for
      cfg <- AppConfig.load
      _   <- Database.migrate(cfg.db)
      code <- Database.transactor(cfg.db).use { xa =>
        val repo    = DoobieTaskRepository(xa)
        val service = TaskService(repo)

        val api    = TaskRoutes.handleErrors(TaskRoutes(service).routes)
        val health = HealthRoutes(xa).routes
        val static = resourceServiceBuilder[IO]("/static").toRoutes

        // The resource service maps exact paths only (GET /index.html -> static/index.html);
        // it does not rewrite "/" to the index, so we do it explicitly.
        val index = HttpRoutes.of[IO] { case GET -> Root =>
          TemporaryRedirect(Location(uri"/index.html"))
        }

        // Order matters: API and health first, static fallback last.
        val httpApp = Logger.httpApp(logHeaders = true, logBody = false)(
          Router("/" -> (api <+> health <+> index <+> static)).orNotFound
        )

        EmberServerBuilder
          .default[IO]
          .withHost(Host.fromString(cfg.host).getOrElse(Host.fromString("0.0.0.0").get))
          .withPort(Port.fromInt(cfg.port).getOrElse(Port.fromInt(8080).get))
          .withHttpApp(httpApp)
          .build
          .useForever
          .as(ExitCode.Success)
      }
    yield code
