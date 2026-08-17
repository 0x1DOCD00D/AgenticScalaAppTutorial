package com.taskforge.web

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*

import com.taskforge.domain.*
import com.taskforge.service.TaskService
import com.taskforge.web.UPickleEntityCodec.given

/** Tier 1: the REST surface. Each route is one line of translation — parse input, call the
  * service, encode output. All error mapping is centralized in `handleErrors`, so a new route
  * cannot forget to translate AppError into the right status code.
  */
final class TaskRoutes(service: TaskService):

  private object StatusParam extends OptionalQueryParamDecoderMatcher[String]("status")

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // GET /api/tasks?status=Todo|InProgress|Done
    case GET -> Root / "api" / "tasks" :? StatusParam(raw) =>
      parseStatus(raw).flatMap(s => service.list(s).flatMap(tasks => Ok(tasks)))

    case GET -> Root / "api" / "tasks" / LongVar(id) =>
      service.get(id).flatMap(task => Ok(task))

    case req @ POST -> Root / "api" / "tasks" =>
      for
        body    <- req.as[CreateTaskRequest]
        task    <- service.create(body)
        created <- Created(task)
      yield created

    case req @ PATCH -> Root / "api" / "tasks" / LongVar(id) =>
      for
        body    <- req.as[UpdateTaskRequest]
        task    <- service.update(id, body)
        updated <- Ok(task)
      yield updated

    case DELETE -> Root / "api" / "tasks" / LongVar(id) =>
      service.delete(id) *> NoContent()
  }

  private def parseStatus(raw: Option[String]): IO[Option[TaskStatus]] =
    raw match
      case None => IO.pure(None)
      case Some(s) =>
        TaskStatus.values.find(_.toString.equalsIgnoreCase(s)) match
          case Some(status) => IO.pure(Some(status))
          case None =>
            IO.raiseError(
              AppError.ValidationFailed(
                s"Unknown status '$s'. Expected one of: ${TaskStatus.values.mkString(", ")}"
              )
            )

object TaskRoutes:

  /** Central AppError -> HTTP translation, applied as middleware over the whole API. Unknown
    * exceptions are deliberately NOT caught here: they bubble to http4s' default 500 handler and
    * get logged with a stack trace, keeping real bugs loud.
    */
  def handleErrors(routes: HttpRoutes[IO]): HttpRoutes[IO] =
    import cats.data.{Kleisli, OptionT}
    import org.http4s.{DecodeFailure, Request}
    import com.taskforge.web.UPickleEntityCodec.given

    Kleisli { (req: Request[IO]) =>
      OptionT {
        // recoverWith (partial function), NOT handleErrorWith (total): unmatched throwables
        // must pass through untouched to http4s' 500 handler with their stack traces intact.
        routes.run(req).value.recoverWith {
          case AppError.TaskNotFound(id) =>
            NotFound(ErrorResponse(s"Task $id not found")).map(Some(_))
          case e: AppError.ValidationFailed =>
            BadRequest(ErrorResponse(e.message)).map(Some(_))
          case e: AppError.InvalidTransition =>
            Conflict(ErrorResponse(e.message)).map(Some(_))
          case e: DecodeFailure =>
            // Malformed/missing JSON body. Raised by req.as[...]; 400, never 500.
            BadRequest(ErrorResponse(e.message)).map(Some(_))
        }
      }
    }
