package com.taskforge

import cats.effect.IO
import munit.CatsEffectSuite

import com.taskforge.domain.*
import com.taskforge.service.TaskService

/** Business rules tested against the in-memory repository — fast, deterministic, DB-free. */
class TaskServiceSuite extends CatsEffectSuite:

  private def newService: IO[TaskService] =
    InMemoryTaskRepository.make.map(TaskService(_))

  test("create trims the title and starts in Todo") {
    for
      svc  <- newService
      task <- svc.create(CreateTaskRequest("  Ship it  ", "desc"))
    yield
      assertEquals(task.title, "Ship it")
      assertEquals(task.status, TaskStatus.Todo)
  }

  test("create rejects an empty title") {
    interceptMessageIO[AppError.ValidationFailed]("Title must not be empty")(
      newService.flatMap(_.create(CreateTaskRequest("   ")))
    )
  }

  test("create rejects a title over 200 characters") {
    interceptIO[AppError.ValidationFailed](
      newService.flatMap(_.create(CreateTaskRequest("x" * 201)))
    )
  }

  test("get raises TaskNotFound for a missing id") {
    interceptIO[AppError.TaskNotFound](newService.flatMap(_.get(42L)))
  }

  test("update moves Todo -> InProgress -> Done") {
    for
      svc  <- newService
      t    <- svc.create(CreateTaskRequest("Task"))
      t1   <- svc.update(t.id, UpdateTaskRequest(status = Some(TaskStatus.InProgress)))
      t2   <- svc.update(t.id, UpdateTaskRequest(status = Some(TaskStatus.Done)))
    yield
      assertEquals(t1.status, TaskStatus.InProgress)
      assertEquals(t2.status, TaskStatus.Done)
  }

  test("update rejects the illegal jump Todo -> Done") {
    for
      svc <- newService
      t   <- svc.create(CreateTaskRequest("Task"))
      res <- svc.update(t.id, UpdateTaskRequest(status = Some(TaskStatus.Done))).attempt
    yield res match
      case Left(AppError.InvalidTransition(TaskStatus.Todo, TaskStatus.Done)) => ()
      case other => fail(s"Expected InvalidTransition, got $other")
  }

  test("Done can be reopened to Todo") {
    for
      svc <- newService
      t   <- svc.create(CreateTaskRequest("Task"))
      _   <- svc.update(t.id, UpdateTaskRequest(status = Some(TaskStatus.InProgress)))
      _   <- svc.update(t.id, UpdateTaskRequest(status = Some(TaskStatus.Done)))
      t3  <- svc.update(t.id, UpdateTaskRequest(status = Some(TaskStatus.Todo)))
    yield assertEquals(t3.status, TaskStatus.Todo)
  }

  test("list filters by status") {
    for
      svc <- newService
      a   <- svc.create(CreateTaskRequest("A"))
      _   <- svc.create(CreateTaskRequest("B"))
      _   <- svc.update(a.id, UpdateTaskRequest(status = Some(TaskStatus.InProgress)))
      todo       <- svc.list(Some(TaskStatus.Todo))
      inProgress <- svc.list(Some(TaskStatus.InProgress))
      all        <- svc.list(None)
    yield
      assertEquals(todo.map(_.title), List("B"))
      assertEquals(inProgress.map(_.title), List("A"))
      assertEquals(all.size, 2)
  }

  test("delete removes the task; second delete raises TaskNotFound") {
    for
      svc <- newService
      t   <- svc.create(CreateTaskRequest("Task"))
      _   <- svc.delete(t.id)
      res <- svc.delete(t.id).attempt
    yield assert(res.isLeft)
  }
