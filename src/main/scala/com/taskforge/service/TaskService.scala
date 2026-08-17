package com.taskforge.service

import cats.effect.IO
import cats.syntax.all.*

import com.taskforge.data.TaskRepository
import com.taskforge.domain.*

/** Tier 2: business rules. No HTTP, no SQL — only domain types and the repository port. Every
  * rule lives here exactly once, so the web tier stays a thin translation layer and the rules are
  * testable with a fast in-memory repository.
  */
final class TaskService(repo: TaskRepository):

  private val MaxTitleLength = 200

  def create(req: CreateTaskRequest): IO[Task] =
    for
      title <- validateTitle(req.title)
      task  <- repo.create(title, req.description.trim)
    yield task

  def get(id: Long): IO[Task] =
    repo.get(id).flatMap {
      case Some(task) => IO.pure(task)
      case None       => IO.raiseError(AppError.TaskNotFound(id))
    }

  def list(status: Option[TaskStatus]): IO[List[Task]] =
    repo.list(status)

  def update(id: Long, req: UpdateTaskRequest): IO[Task] =
    for
      current  <- get(id)
      title    <- req.title.traverse(validateTitle)
      _        <- req.status.traverse(validateTransition(current.status, _))
      modified = current.copy(
        title = title.getOrElse(current.title),
        description = req.description.map(_.trim).getOrElse(current.description),
        status = req.status.getOrElse(current.status)
      )
      saved <- repo.update(modified).flatMap {
        case Some(task) => IO.pure(task)
        case None       => IO.raiseError(AppError.TaskNotFound(id))
      }
    yield saved

  def delete(id: Long): IO[Unit] =
    repo.delete(id).flatMap {
      case true  => IO.unit
      case false => IO.raiseError(AppError.TaskNotFound(id))
    }

  private def validateTitle(raw: String): IO[String] =
    val title = raw.trim
    if title.isEmpty then
      IO.raiseError(AppError.ValidationFailed("Title must not be empty"))
    else if title.length > MaxTitleLength then
      IO.raiseError(AppError.ValidationFailed(s"Title must be at most $MaxTitleLength characters"))
    else IO.pure(title)

  /** Business rule: a task can only move forward one step at a time or be reopened from Done to
    * Todo. Encoding legal transitions explicitly makes illegal states unrepresentable in the API.
    */
  private def validateTransition(from: TaskStatus, to: TaskStatus): IO[Unit] =
    import TaskStatus.*
    val legal: Set[(TaskStatus, TaskStatus)] =
      Set(
        (Todo, InProgress),
        (InProgress, Done),
        (Done, Todo),           // reopen
        (InProgress, Todo)      // push back
      )
    if from == to || legal((from, to)) then IO.unit
    else IO.raiseError(AppError.InvalidTransition(from, to))
