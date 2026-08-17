// =============================================================================
// TaskForge — Scala 3 three-tier application
//
// Tier 1 (presentation): http4s Ember server + static HTML/JS frontend
// Tier 2 (business logic): pure services on cats-effect IO
// Tier 3 (data): doobie (functional JDBC) + PostgreSQL + Flyway migrations
//
// JSON serialization: upickle (com-lihaoyi/upickle) end to end.
// Packaging: sbt-native-packager -> Docker image -> AWS ECR -> ECS Fargate.
// =============================================================================

ThisBuild / organization := "com.taskforge"
ThisBuild / scalaVersion := "3.3.6" // Scala 3 LTS line
ThisBuild / version      := sys.env.getOrElse("APP_VERSION", "0.1.0-SNAPSHOT")

val Http4sVersion     = "0.23.35"
val DoobieVersion     = "1.0.0-RC12"
val UpickleVersion    = "4.4.2"
val FlywayVersion     = "11.10.0"
val PostgresVersion   = "42.7.7"
val LogbackVersion    = "1.5.18"
val Log4CatsVersion   = "2.7.0"
val MunitVersion      = "1.1.1"
val MunitCEVersion    = "2.1.0"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    name := "taskforge",

    libraryDependencies ++= Seq(
      // ---- Tier 1: HTTP server (presentation) ----
      "org.http4s"    %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"    %% "http4s-ember-client" % Http4sVersion % Test,
      "org.http4s"    %% "http4s-dsl"          % Http4sVersion,

      // ---- JSON: upickle everywhere (wired to http4s via UPickleEntityCodec) ----
      "com.lihaoyi"   %% "upickle"             % UpickleVersion,

      // ---- Tier 3: database access ----
      "org.tpolecat"  %% "doobie-core"         % DoobieVersion,
      "org.tpolecat"  %% "doobie-hikari"       % DoobieVersion, // HikariCP connection pool
      "org.tpolecat"  %% "doobie-postgres"     % DoobieVersion, // PG-specific mappings
      "org.postgresql" % "postgresql"          % PostgresVersion,
      "org.flywaydb"   % "flyway-core"         % FlywayVersion,
      "org.flywaydb"   % "flyway-database-postgresql" % FlywayVersion % Runtime,

      // ---- Logging ----
      "org.typelevel" %% "log4cats-slf4j"      % Log4CatsVersion,
      "ch.qos.logback" % "logback-classic"     % LogbackVersion % Runtime,

      // ---- Tests ----
      "org.scalameta" %% "munit"               % MunitVersion   % Test,
      "org.typelevel" %% "munit-cats-effect"   % MunitCEVersion % Test
    ),

    // Fail the build on warnings in CI so agents catch problems early.
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:all"
    ) ++ (if (sys.env.contains("CI")) Seq("-Werror") else Seq.empty),

    Test / fork := true,

    // ---- Docker image (consumed by ECS Fargate) ----
    Docker / packageName    := "taskforge",
    dockerBaseImage         := "eclipse-temurin:21-jre-jammy",
    dockerExposedPorts      := Seq(8080),
    dockerUpdateLatest      := true,
    Docker / daemonUserUid  := Some("1001"),
    Docker / daemonUser     := "taskforge",
    dockerEnvVars           := Map("JAVA_OPTS" -> "-XX:MaxRAMPercentage=75.0")
  )

// Touches a marker file the Claude Code Stop hook checks, so the agent can prove
// "tests ran after the last source change" without parsing sbt output.
lazy val markTestRun = taskKey[Unit]("Record that the test suite ran (used by .claude hooks)")
markTestRun := {
  val f = baseDirectory.value / ".claude" / ".last-test-run"
  IO.touch(f)
}

// Convenience aliases the agents (and humans) use constantly.
addCommandAlias("fmt", "scalafmtAll; scalafmtSbt")
addCommandAlias("check", "scalafmtCheckAll; Test/compile; test; markTestRun")
addCommandAlias("dockerLocal", "Docker/publishLocal")
