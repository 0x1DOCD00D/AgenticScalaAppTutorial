package com.taskforge

import java.time.Instant

import cats.effect.{IO, Ref}

import com.taskforge.data.TaskRepository
import com.taskforge.domain.{Task, TaskStatus}

/** In-memory implementation of the tier-3 port, backed by a Ref (atomic mutable cell). Because
  * the service tier only knows the TaskRepository trait, the whole business-logic and web-tier
  * test suite runs in milliseconds with no database.
  */
final class InMemoryTaskRepository(
    state: Ref[IO, Map[Long, Task]],
    counter: Ref[IO, Long]
) extends TaskRepository:

  def create(title: String, description: String): IO[Task] =
    for
      id <- counter.updateAndGet(_ + 1)
      now = Instant.now()
      task = Task(id, title, description, TaskStatus.Todo, now, now)
      _ <- state.update(_ + (id -> task))
    yield task

  def get(id: Long): IO[Option[Task]] =
    state.get.map(_.get(id))

  def list(status: Option[TaskStatus]): IO[List[Task]] =
    state.get.map { m =>
      val all = m.values.toList.sortBy(_.id)
      status.fold(all)(s => all.filter(_.status == s))
    }

  def update(task: Task): IO[Option[Task]] =
    state.modify { m =>
      m.get(task.id) match
        case Some(_) =>
          val updated = task.copy(updatedAt = Instant.now())
          (m + (task.id -> updated), Some(updated))
        case None => (m, None)
    }

  def delete(id: Long): IO[Boolean] =
    state.modify(m => (m - id, m.contains(id)))

object InMemoryTaskRepository:
  def make: IO[InMemoryTaskRepository] =
    for
      state   <- Ref.of[IO, Map[Long, Task]](Map.empty)
      counter <- Ref.of[IO, Long](0L)
    yield InMemoryTaskRepository(state, counter)
