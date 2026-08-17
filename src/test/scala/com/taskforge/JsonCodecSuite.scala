package com.taskforge

import java.time.Instant

import munit.FunSuite
import upickle.default.{read, write}

import com.taskforge.domain.*

/** Locks down the wire format. If a refactor accidentally changes the JSON shape (a renamed
  * field, an enum encoded differently), these tests fail before any client ever sees it.
  */
class JsonCodecSuite extends FunSuite:

  private val instant = Instant.parse("2026-01-15T10:30:00Z")
  private val task =
    Task(1L, "Write tutorial", "In excruciating detail", TaskStatus.InProgress, instant, instant)

  test("Task round-trips through JSON") {
    assertEquals(read[Task](write(task)), task)
  }

  test("TaskStatus serializes as a plain string") {
    assertEquals(write[TaskStatus](TaskStatus.InProgress), "\"InProgress\"")
  }

  test("Instant serializes as ISO-8601") {
    assert(write(task).contains("2026-01-15T10:30:00Z"))
  }

  test("CreateTaskRequest tolerates a missing description") {
    val req = read[CreateTaskRequest]("""{"title":"Only a title"}""")
    assertEquals(req, CreateTaskRequest("Only a title", ""))
  }

  test("UpdateTaskRequest tolerates an empty object") {
    assertEquals(read[UpdateTaskRequest]("{}"), UpdateTaskRequest())
  }

  test("unknown status value fails to parse") {
    intercept[Exception] {
      read[UpdateTaskRequest]("""{"status":"Bogus"}""")
    }
  }
