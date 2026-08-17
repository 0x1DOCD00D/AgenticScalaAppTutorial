package com.taskforge.config

import cats.effect.IO

/** All runtime configuration comes from environment variables — the twelve-factor convention that
  * ECS Fargate task definitions (and local `docker run -e`) both speak natively. We deliberately
  * avoid a config library: for six values, `sys.env` plus explicit defaults is easier for both
  * humans and agents to audit.
  */
final case class DbConfig(
    url: String,
    user: String,
    password: String,
    poolSize: Int
)

final case class AppConfig(
    host: String,
    port: Int,
    db: DbConfig
)

object AppConfig:

  def load: IO[AppConfig] = IO {
    val env = sys.env
    AppConfig(
      host = env.getOrElse("HTTP_HOST", "0.0.0.0"),
      port = env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080),
      db = DbConfig(
        url = env.getOrElse("DB_URL", "jdbc:postgresql://localhost:5432/taskforge"),
        user = env.getOrElse("DB_USER", "taskforge"),
        password = env.getOrElse("DB_PASSWORD", "taskforge"),
        poolSize = env.get("DB_POOL_SIZE").flatMap(_.toIntOption).getOrElse(8)
      )
    )
  }
