package com.taskforge.data

import cats.effect.IO

import com.taskforge.domain.{Task, TaskStatus}

/** Tier-3 port. The service tier depends on this interface only, never on doobie or SQL, which is
  * what lets the test suite swap in an in-memory implementation and lets agents change the storage
  * engine without touching business logic.
  */
trait TaskRepository:
  def create(title: String, description: String): IO[Task]
  def get(id: Long): IO[Option[Task]]
  def list(status: Option[TaskStatus]): IO[List[Task]]
  def update(task: Task): IO[Option[Task]]
  def delete(id: Long): IO[Boolean]
