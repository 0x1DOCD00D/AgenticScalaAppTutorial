package com.taskforge

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.implicits.*
import upickle.default.read

import com.taskforge.domain.*
import com.taskforge.service.TaskService
import com.taskforge.web.TaskRoutes

/** Exercises the web tier end to end (routing, JSON codecs, error mapping) without a server
  * socket: HttpApp is just a function Request => IO[Response], so we call it directly.
  */
class TaskRoutesSuite extends CatsEffectSuite:

  private def newApp: IO[HttpApp[IO]] =
    InMemoryTaskRepository.make.map { repo =>
      TaskRoutes.handleErrors(TaskRoutes(TaskService(repo)).routes).orNotFound
    }

  private def jsonRequest(method: Method, uri: Uri, body: String): Request[IO] =
    Request[IO](method, uri)
      .withEntity(body)
      .withContentType(headers.`Content-Type`(MediaType.application.json))

  test("POST /api/tasks creates a task and returns 201") {
    for
      app  <- newApp
      res  <- app.run(jsonRequest(Method.POST, uri"/api/tasks", """{"title":"First"}"""))
      body <- res.as[String]
    yield
      assertEquals(res.status, Status.Created)
      val task = read[Task](body)
      assertEquals(task.title, "First")
      assertEquals(task.status, TaskStatus.Todo)
  }

  test("POST with an empty title returns 400 with a JSON error body") {
    for
      app  <- newApp
      res  <- app.run(jsonRequest(Method.POST, uri"/api/tasks", """{"title":"  "}"""))
      body <- res.as[String]
    yield
      assertEquals(res.status, Status.BadRequest)
      assertEquals(read[ErrorResponse](body).error, "Title must not be empty")
  }

  test("POST with malformed JSON returns 400, not 500") {
    for
      app <- newApp
      res <- app.run(jsonRequest(Method.POST, uri"/api/tasks", """{"title": """))
    yield assertEquals(res.status, Status.BadRequest)
  }

  test("GET /api/tasks/<missing> returns 404") {
    for
      app <- newApp
      res <- app.run(Request[IO](Method.GET, uri"/api/tasks/999"))
    yield assertEquals(res.status, Status.NotFound)
  }

  test("PATCH with an illegal transition returns 409") {
    for
      app <- newApp
      _   <- app.run(jsonRequest(Method.POST, uri"/api/tasks", """{"title":"T"}"""))
      res <- app.run(jsonRequest(Method.PATCH, uri"/api/tasks/1", """{"status":"Done"}"""))
    yield assertEquals(res.status, Status.Conflict)
  }

  test("GET /api/tasks?status=Bogus returns 400") {
    for
      app <- newApp
      res <- app.run(Request[IO](Method.GET, uri"/api/tasks?status=Bogus"))
    yield assertEquals(res.status, Status.BadRequest)
  }

  test("full lifecycle: create, advance, list by status, delete") {
    for
      app <- newApp
      _   <- app.run(jsonRequest(Method.POST, uri"/api/tasks", """{"title":"Lifecycle"}"""))
      _   <- app.run(jsonRequest(Method.PATCH, uri"/api/tasks/1", """{"status":"InProgress"}"""))
      lst <- app.run(Request[IO](Method.GET, uri"/api/tasks?status=InProgress"))
      body <- lst.as[String]
      del <- app.run(Request[IO](Method.DELETE, uri"/api/tasks/1"))
      gone <- app.run(Request[IO](Method.GET, uri"/api/tasks/1"))
    yield
      assertEquals(read[List[Task]](body).map(_.title), List("Lifecycle"))
      assertEquals(del.status, Status.NoContent)
      assertEquals(gone.status, Status.NotFound)
  }
