package com.taskforge.data

import cats.effect.{IO, Resource}
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway

import com.taskforge.config.DbConfig

object Database:

  /** Runs Flyway migrations from classpath:db/migration before the app takes traffic. Executed on
    * a blocking thread because Flyway uses plain JDBC. Idempotent by design: already-applied
    * versions are skipped, so every deploy can (and does) call this unconditionally.
    */
  def migrate(cfg: DbConfig): IO[Unit] =
    IO.blocking {
      Flyway
        .configure()
        .dataSource(cfg.url, cfg.user, cfg.password)
        .load()
        .migrate()
    }.void

  /** A pooled transactor as a Resource: acquisition opens the HikariCP pool, release closes it —
    * even on crash paths. The fixed thread pool sizes the *await* side (threads blocked on JDBC),
    * matching the pool size so we never queue on a saturated pool from inside the pool.
    */
  def transactor(cfg: DbConfig): Resource[IO, HikariTransactor[IO]] =
    for
      ce <- ExecutionContexts.fixedThreadPool[IO](cfg.poolSize)
      xa <- HikariTransactor.newHikariTransactor[IO](
        driverClassName = "org.postgresql.Driver",
        url = cfg.url,
        user = cfg.user,
        pass = cfg.password,
        connectEC = ce
      )
    yield xa
