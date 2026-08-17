# TaskForge — project memory for Claude Code

TaskForge is a Scala 3 three-tier web application (task manager) deployed on AWS ECS Fargate
with PostgreSQL on RDS. JSON serialization uses upickle (com-lihaoyi) everywhere.

## Architecture (three tiers — respect the boundaries)

| Tier | Package | May depend on | Must NOT depend on |
|------|---------|---------------|--------------------|
| 1 Presentation | `com.taskforge.web` + `resources/static` | service, domain | data (except via Main wiring) |
| 2 Business logic | `com.taskforge.service` | data *port* (trait), domain | http4s, doobie, SQL |
| 3 Data | `com.taskforge.data` | domain | web, service |

- `com.taskforge.domain` is the shared kernel: entities, requests, errors, upickle codecs.
- `Main.scala` is the only composition root. Wire new dependencies there.
- The service tier raises typed `AppError`s; the web tier maps them to HTTP codes in
  `TaskRoutes.handleErrors`. Never map errors anywhere else.

## Commands

- `sbt check` — format check + compile + full test suite. Run before declaring any work done.
- `sbt fmt` — apply scalafmt.
- `sbt dockerLocal` — build the Docker image locally.
- `./scripts/deploy.sh` — build, push to ECR, roll the ECS service (use the /deploy command).
- `./scripts/rollback.sh` — re-point the ECS service at the previous task definition.

## Hard rules

- Every artifact class has exactly one owning agent (ownership map in `docs/agents.md`).
  Never create or modify an artifact outside your role — decline and name the owner.
  In particular: build.sbt/project/* belong to **build-engineer** (versions to
  **dependency-updater**), terraform/scripts/workflows to **infra-engineer**, and any
  `.claude/**` or CLAUDE.md change is prepared by **factory-engineer** and takes effect
  only after human ratification.
- upickle only for JSON. Do not add circe/play-json/jackson; the http4s bridge lives in
  `web/UPickleEntityCodec.scala`.
- Never edit an applied Flyway migration. Schema changes = new `V<n>__description.sql` file,
  reviewed by the **db-migrator** agent.
- Every code change needs a test in the same PR. Business rules are tested against
  `InMemoryTaskRepository`, not the real database.
- Secrets never appear in code, config files, or logs. Runtime secrets come from AWS Secrets
  Manager via the ECS task definition.
- Infrastructure changes go through `infra/terraform` — never hand-edit resources in the AWS
  console (the drift will bite the next agent).

## Style

- Scala 3 syntax (significant indentation, `enum`, `given`). scalafmt is enforced by a hook.
- Prefer explicit small functions over clever abstractions; agents (and reviewers) read this
  code more often than they write it.

@docs/agents.md
