package com.taskforge.data

import cats.effect.IO
import doobie.Transactor
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.meta.Meta

import com.taskforge.domain.{Task, TaskStatus}

/** PostgreSQL implementation of the repository port, written with doobie.
  *
  * doobie keeps SQL as SQL (no ORM magic) but makes every fragment a pure value that composes and
  * type-checks: `.query[Task]` derives a row mapper from the case class at compile time, so a
  * schema/model mismatch is a compile error or an immediately failing test, not a production bug.
  */
final class DoobieTaskRepository(xa: Transactor[IO]) extends TaskRepository:

  import DoobieTaskRepository.given

  private val selectCols =
    fr"SELECT id, title, description, status, created_at, updated_at FROM tasks"

  def create(title: String, description: String): IO[Task] =
    sql"""INSERT INTO tasks (title, description, status)
          VALUES ($title, $description, ${TaskStatus.Todo})
          RETURNING id, title, description, status, created_at, updated_at"""
      .query[Task]
      .unique
      .transact(xa)

  def get(id: Long): IO[Option[Task]] =
    (selectCols ++ fr"WHERE id = $id").query[Task].option.transact(xa)

  def list(status: Option[TaskStatus]): IO[List[Task]] =
    val filtered = status match
      case Some(s) => selectCols ++ fr"WHERE status = $s"
      case None    => selectCols
    (filtered ++ fr"ORDER BY id").query[Task].to[List].transact(xa)

  def update(task: Task): IO[Option[Task]] =
    sql"""UPDATE tasks
          SET title = ${task.title},
              description = ${task.description},
              status = ${task.status},
              updated_at = now()
          WHERE id = ${task.id}
          RETURNING id, title, description, status, created_at, updated_at"""
      .query[Task]
      .option
      .transact(xa)

  def delete(id: Long): IO[Boolean] =
    sql"DELETE FROM tasks WHERE id = $id".update.run.transact(xa).map(_ > 0)

object DoobieTaskRepository:

  /** The one column mapping doobie cannot derive on its own: TaskStatus is stored as TEXT.
    *
    * Meta[java.time.Instant] needs no definition here: `doobie.postgres.implicits.*` (imported
    * at the top of this file) provides the PG-native instance, which maps Instant through
    * `timestamptz` with accurate column type checks. (Since doobie 1.0.0-RC2 the old
    * `doobie.implicits.javasql` / `javatimedrivernative`-style opt-in imports were reorganized:
    * java.sql.* Metas are now in the Meta companion by default, and each database module ships
    * its own java.time instances.)
    */
  given Meta[TaskStatus] = Meta[String].timap(TaskStatus.valueOf)(_.toString)
