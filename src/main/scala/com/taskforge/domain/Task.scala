package com.taskforge.domain

import java.time.Instant

import upickle.default.{readwriter, ReadWriter}

/** upickle codec for java.time.Instant.
  *
  * upickle has no built-in Instant support, so we map it through its ISO-8601 string form. Defined
  * as a top-level given so every `derives ReadWriter` in this package picks it up.
  */
given ReadWriter[Instant] =
  readwriter[String].bimap[Instant](_.toString, Instant.parse)

/** Task lifecycle states. Simple (parameterless) Scala 3 enum cases serialize as plain JSON
  * strings under upickle, e.g. "Todo" — exactly what the frontend and API clients want.
  */
enum TaskStatus derives ReadWriter:
  case Todo, InProgress, Done

/** The core domain entity, shared by all three tiers.
  *
  * `derives ReadWriter` asks upickle to generate the JSON codec at compile time — no reflection,
  * no runtime surprises: if a field is not serializable the build fails.
  */
final case class Task(
    id: Long,
    title: String,
    description: String,
    status: TaskStatus,
    createdAt: Instant,
    updatedAt: Instant
) derives ReadWriter

/** API request payloads live in the domain so the service tier can validate them without
  * depending on the web tier. Option fields default to None so absent JSON keys parse cleanly.
  */
final case class CreateTaskRequest(
    title: String,
    description: String = ""
) derives ReadWriter

final case class UpdateTaskRequest(
    title: Option[String] = None,
    description: Option[String] = None,
    status: Option[TaskStatus] = None
) derives ReadWriter

/** Uniform JSON error body returned by the web tier. */
final case class ErrorResponse(error: String) derives ReadWriter

/** Typed application errors. The service tier raises these; the web tier maps them to HTTP
  * status codes. Extending Throwable (with NoStackTrace) lets them travel through IO's error
  * channel without paying for stack-trace capture.
  */
sealed abstract class AppError(val message: String)
    extends Exception(message)
    with scala.util.control.NoStackTrace

object AppError:
  final case class TaskNotFound(id: Long) extends AppError(s"Task $id not found")
  final case class ValidationFailed(reason: String) extends AppError(reason)
  final case class InvalidTransition(from: TaskStatus, to: TaskStatus)
      extends AppError(s"Cannot move a task from $from to $to")
