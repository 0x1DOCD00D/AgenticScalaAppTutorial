# Building and Operating a Scala 3 Three-Tier Application with an Agentic Claude Code Workflow

### A hands-on tutorial: http4s + doobie + upickle, deployed on AWS ECS Fargate, with agents owning the entire production and maintenance lifecycle

---

## How to use this tutorial

This document accompanies a **complete, working repository** (`taskforge/`). Every file mentioned here exists in that repo; nothing is pseudocode. You can either follow along building the project from scratch — the tutorial gives you every command and every prompt in order — or open the finished repo next to this document and study it file by file.

The tutorial has two intertwined subjects, and it is honest about which is which:

1. **The application**: TaskForge, a deliberately small but production-shaped three-tier task manager in Scala 3 — an http4s web tier, a pure business-logic tier, and a doobie/PostgreSQL data tier, glued together with upickle JSON, containerized by sbt-native-packager, and run on AWS ECS Fargate behind an ALB with RDS PostgreSQL.
2. **The workflow**: a Claude Code setup in which *agents, not ad-hoc prompting*, carry the software through its whole lifecycle — plan → implement → migrate schema → test → review → deploy → monitor → respond to incidents → maintain dependencies. This lives in `CLAUDE.md`, `.claude/agents/`, `.claude/commands/`, `.claude/hooks/`, `.claude/settings.json`, `.mcp.json`, and `.github/workflows/`.

The central thesis, which every section will reinforce: **an agentic workflow is an engineering artifact, not a prompt.** You build it the way you build the application itself — with explicit interfaces (agent definitions), invariants (hooks and permission rules), shared state (CLAUDE.md), integration points (MCP servers), and tests (the deploy gates and smoke tests). When the workflow is engineered this way, "maintain the app" becomes a task you can *delegate* rather than perform.

---

## Part 0 — Architecture of the system and of the workflow

### 0.1 Why a three-tier architecture, stated precisely

"Three-tier" is often used loosely; here it means three code layers with **enforced, one-directional dependencies**:

| Tier | Package | Responsibility | Depends on |
|---|---|---|---|
| 1. Presentation | `com.taskforge.web` + `static/` | HTTP, JSON encoding/decoding, status codes, the browser UI | Tier 2, domain |
| 2. Business logic | `com.taskforge.service` | Validation, state-transition rules, orchestration | Tier 3's *interface*, domain |
| 3. Data | `com.taskforge.data` | SQL, connection pooling, migrations | domain |

A fourth, dependency-free package — `com.taskforge.domain` — holds the entities, request/response types, typed errors, and their upickle codecs. It is the *shared kernel*: everything may depend on it; it depends on nothing.

The rationale is not aesthetic. Each boundary buys something concrete:

- **Web ⟂ Service**: the service tier never imports `org.http4s.*`. Consequence: business rules are testable by calling plain methods, and the web framework can be swapped (or a second interface — gRPC, CLI — added) without touching a rule.
- **Service ⟂ Data**: the service depends on `TaskRepository`, a *trait* (a port, in hexagonal-architecture vocabulary), never on doobie. Consequence: the entire business-logic test suite runs against an in-memory repository in milliseconds, with no database process, no Docker, no flakiness. This single decision is what makes the agentic loop fast enough to be pleasant — agents run `sbt test` after every change.
- **Composition root**: `Main.scala` is the only file where concrete implementations meet. There is exactly one place to look to understand the wiring, and exactly one place agents must edit to add a dependency.

These boundaries matter *more* in an agentic workflow than in a human one. A human developer holds the architecture in their head; an agent holds only what its context window and its instructions give it. Boundaries that are **mechanically checkable** ("does `service/` import http4s? then reject") convert architectural intent into something a code-review agent can enforce reliably. You will see this exact check in `.claude/agents/code-reviewer.md`.

### 0.2 Why this specific stack

**Scala 3.3 (LTS).** The LTS line gets patch releases without source breakage — the right stability/currency trade-off for a service that agents will maintain unattended for months. Scala 3's significant-indentation syntax, `enum`, and `derives` clauses also make the code *smaller*, and smaller code is disproportionately valuable to agents: less context consumed per file read, fewer places for a patch to go wrong.

**http4s 0.23 (Ember).** http4s models an HTTP app as an immutable value: `HttpApp[IO]` is literally a function `Request[IO] => IO[Response[IO]]`. Two consequences drive the whole tutorial: (a) routes compose with ordinary function combinators (`<+>`, middleware are function wrappers), and (b) **the web tier is testable without a socket** — tests call `app.run(request)` directly. The 0.23.x line is the binary-stable production line; the 1.0.0-Mx milestones are explicitly not production targets (the dependency-updater agent is told this in writing).

**doobie 1.0-RC (with HikariCP).** doobie refuses to hide SQL — you write real SQL in `sql"..."` interpolators — but makes every query a *pure value* with a type. `.query[Task]` derives the row-mapper from the case class at compile time; interpolated variables become JDBC parameters (SQL injection is impossible by construction, a fact the reviewer agent relies on). An ORM would save nothing here and would cost the agents dearly: ORM behavior lives in runtime configuration and session state, exactly the kind of implicit knowledge agents lack.

**upickle 4 (com-lihaoyi).** Your requirement, and a good one: upickle is small, fast, dependency-free, and compile-time derived (`derives ReadWriter`). http4s has no built-in upickle support — which becomes a *feature* of the tutorial: Part 4.5 builds the http4s↔upickle bridge in ~30 lines, and in doing so exposes exactly how http4s entity codecs work. The wire format is locked by tests (`JsonCodecSuite`), so if any future change — human or agent — alters the JSON shape, the build fails before a client notices.

**PostgreSQL on RDS, ECS Fargate, ALB, Terraform.** Managed database (backups, patching, failover are AWS's problem — one less thing the maintenance agents must own), serverless containers (no EC2 fleet to patch — same reason), and infrastructure as *reviewable text*. The IaC point is fundamental to the agentic story: agents can read, diff, and `terraform plan` a text description of production. They cannot "read" a hand-configured console. Drift from clicking around the console is the workflow's natural enemy, and CLAUDE.md forbids it in writing.

### 0.3 The lifecycle → agent map

The production and maintenance lifecycle is decomposed into stages, and each stage gets a **specialist subagent** with its own instructions, its own tool allowlist, and its own definition file:

| Stage | Agent (`.claude/agents/`) | Key powers | Deliberately withheld |
|---|---|---|---|
| Implement | `feature-implementer` | Edit/Write, sbt | deploy, Terraform, migrations |
| Schema | `db-migrator` | Write migrations, query DB via MCP (read-only) | editing applied migrations, destructive DDL without human sign-off |
| Test | `test-engineer` | Write under `src/test/` only | touching production code |
| Review | `code-reviewer` | Read + sbt as evidence | any edit at all |
| Deploy | `deploy-engineer` | deploy/rollback scripts, ECS via MCP | force-pushes, Terraform state, deploys with red tests |
| Monitor/respond | `incident-responder` | logs, metrics, ECS troubleshooting, rollback | data repair, schema rollback (must escalate) |
| Maintain | `dependency-updater` | Edit build files, web research | major bumps without written analysis |

Why specialists rather than one omnipotent agent? Four reasons, each of which you will see operationalized later:

1. **Focused context.** Each subagent starts with a clean context containing its instructions and only the files it reads. The reviewer isn't dragging the implementer's dead-ends around; the incident responder isn't paying for the feature discussion.
2. **Least privilege.** Tool grants are per-agent. The reviewer *cannot* edit; the test engineer *cannot* touch production code. When a model errs, the blast radius is whatever its tools allow — so shrink the tools.
3. **Adversarial separation.** The reviewer's instructions frame its job as *refuting* "this change is safe." If the same context that wrote the code reviews the code, it inherits the same blind spots. Separation is how you manufacture disagreement, and disagreement is where bugs get caught.
4. **Auditable process.** "The db-migrator produced V3, the reviewer approved, the deploy-engineer shipped it and the smoke test passed" is a legible trail. One agent doing everything in one transcript is not.

---

## Part 1 — Prerequisites and environment setup

You need, in excruciating enumeration:

**Local toolchain.**

```bash
# Java 21 (LTS) — Temurin recommended; matches the Docker base image so
# "works locally" and "works in the container" mean the same thing.
java -version        # openjdk 21.x

# sbt (via SDKMAN, Homebrew, or your package manager)
sbt --version        # sbt 1.11.x

# Docker (or a drop-in like Podman with the docker CLI shim)
docker version

# AWS CLI v2, authenticated. Verify identity explicitly — agents will run
# aws commands, and running them against the wrong account is the classic disaster.
aws sts get-caller-identity

# Terraform >= 1.7
terraform -version

# uv (provides uvx, which runs the AWS MCP servers)
uvx --version

# Node 18+ (runs Claude Code itself)
node --version
```

**Claude Code.**

```bash
npm install -g @anthropic-ai/claude-code
claude --version
claude               # first run walks you through authentication
```

**Why each of these and not alternatives:** Java 21 because it is the current LTS and our `dockerBaseImage` is `eclipse-temurin:21-jre-jammy` — dev/prod JVM parity eliminates a whole category of "but it worked on my machine" that agents are especially bad at diagnosing (they can't shrug and try your machine). `uv`/`uvx` because the awslabs MCP servers are distributed as Python packages and `uvx` runs them in isolated, cached environments with zero project pollution — `.mcp.json` refers to `uvx` directly.

**AWS account hygiene, before anything else.** Use a sandbox/dev account, not your production account, for the tutorial. Create the Terraform state bucket and lock table once, by hand (this is the one and only console/CLI-by-hand step, because Terraform cannot create the bucket its own state lives in without a chicken-and-egg dance):

```bash
aws s3api create-bucket --bucket <your-unique-name>-tfstate --region us-east-1
aws s3api put-bucket-versioning --bucket <your-unique-name>-tfstate \
  --versioning-configuration Status=Enabled
aws dynamodb create-table --table-name taskforge-tflock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

Then edit `infra/terraform/main.tf` to point the `backend "s3"` block at your bucket name. Versioning on the state bucket is non-negotiable: state corruption is recoverable only if you can retrieve yesterday's state.

---

## Part 2 — Bootstrap: memory before code

Initialize the repository and — *before writing any Scala* — write `CLAUDE.md`. This ordering is the single most important habit in agentic development, so here is the full rationale.

`CLAUDE.md` at the repo root is loaded into context automatically at the start of **every** Claude Code session in this directory, and is inherited by subagents' sessions. It is the project's *institutional memory*: architecture invariants, commands, hard rules. Everything you put here is something you will never have to say in a prompt again — and more importantly, something a *scheduled, unattended* session (Part 11's maintenance workflow) will know without anyone there to say it.

Two failure modes it prevents, both of which you'd otherwise meet within the first week:

- **Rule drift.** You tell one session "we use upickle, don't add circe." The next session, with no memory, helpfully adds circe — it's the JSON library most Scala training data uses with http4s. Written in CLAUDE.md, the rule binds every session, forever.
- **Command guessing.** Without a documented `sbt check`, each session improvises its own verification (`sbt compile`? `sbt test`? nothing?). The Stop hook (Part 8) makes this concrete: sources changed ⇒ the *documented* check must run.

Read `CLAUDE.md` in the repo now. Note four design choices:

1. **The tier table is phrased as *May depend on / Must NOT depend on*** — mechanical language a reviewer agent can check with grep, not vibes ("keep the code clean") it can only nod at.
2. **Commands are named with their purpose** (`sbt check` — "run before declaring any work done"). Agents pattern-match on purpose statements.
3. **Hard rules are few and absolute.** A CLAUDE.md with fifty soft suggestions is worth less than one with eight iron laws; models weight scarce, emphatic constraints more reliably.
4. **`@docs/agents.md` import.** The `@path` syntax inlines another file at load time. The agent-team map lives in its own file so humans find it where they'd look (docs/), while agents still get it in every session.

Also created at bootstrap: `.gitignore` (note it ignores `.claude/.last-test-run` — hook bookkeeping, not source), `.scalafmt.conf` (pinned formatter version; deterministic formatting is what lets a PostToolUse hook format files without creating diff noise), and an empty `docs/incidents/` (the incident-responder writes its reports there; creating the directory *is* the convention).

```bash
git init taskforge && cd taskforge
# create CLAUDE.md, .gitignore, .scalafmt.conf as in the repo
git add -A && git commit -m "bootstrap: project memory and hygiene before any code"
```

### 2.1 Plan mode: how features enter the pipeline

Claude Code has a plan mode (Shift+Tab twice, or `claude --permission-mode plan`) in which the model may read anything but change nothing, and must present a plan for approval. The workflow convention — written into `docs/agents.md` — is that **every feature starts in plan mode in the main session**. The main session is the *orchestrator*: it plans, gets approval, then delegates stages to the specialist subagents. Rationale: planning wants breadth (read anything, consider everything), execution wants narrowness (edit these three files, run these two commands). Separating the modes gets you both, and the approval step pins the plan *in the transcript*, where every subsequent subagent invocation can reference it.

---

## Part 3 — The build definition, line by line

Open `build.sbt`. Nothing in it is boilerplate; every stanza earns its place.

**Version pinning at the top.**

```scala
val Http4sVersion  = "0.23.35"
val DoobieVersion  = "1.0.0-RC12"
val UpickleVersion = "4.4.2"
```

Exact versions, no ranges, gathered into named vals. Reproducibility is the obvious reason. The agentic reason: the `dependency-updater` agent's entire job is diffing these vals against upstream — one obvious place, one obvious diff, one reviewable PR.

**`ThisBuild / version := sys.env.getOrElse("APP_VERSION", "0.1.0-SNAPSHOT")`.** The Docker image tag equals the app version equals — in CI and deploys — the **git SHA** (`deploy.sh` exports `APP_VERSION=$GIT_SHA`). This is the traceability spine of the whole operation: from a CloudWatch log line you get the task definition, from it the image tag, from it the exact commit. When the incident-responder asks "what code is running?", the answer is mechanical.

**Dependency groups map one-to-one to tiers** — ember-server/dsl for tier 1, upickle as the single JSON library, doobie-core/hikari/postgres + Flyway + the JDBC driver for tier 3, munit + munit-cats-effect for tests. Notice what is *absent*: no circe (upickle covers JSON), no ORM, no DI framework (the composition root is 30 lines of plain code — a DI container would add magic for agents to misread), no config library (six env vars, read explicitly in `AppConfig.scala`).

**Warnings as errors, but only in CI.**

```scala
scalacOptions ++= ... ++ (if (sys.env.contains("CI")) Seq("-Werror") else Seq.empty)
```

`-Wunused:all` catches the classic agent-edit residue: an import left behind after refactoring. Locally it's a warning (don't block exploration); in CI it's an error (don't merge residue). The asymmetry is deliberate.

**The Docker block** configures sbt-native-packager: Temurin 21 JRE base, port 8080, a non-root user (`daemonUser`/`daemonUserUid` — Fargate will run it regardless, but images should be safe wherever they land), container-aware heap (`-XX:MaxRAMPercentage=75.0` instead of a fixed `-Xmx`, so the same image is correct at any Fargate memory size). `sbt Docker/publishLocal` gives you a runnable image with zero Dockerfile authorship; the generated Dockerfile is a standard two-stage layout you can inspect under `target/docker/`.

**The `markTestRun` task and the aliases.**

```scala
addCommandAlias("check", "scalafmtCheckAll; Test/compile; test; markTestRun")
```

`check` is *the* definition of done, referenced by CLAUDE.md, by three agent definitions, and by CI (same steps). `markTestRun` touches `.claude/.last-test-run`, which the Stop hook (Part 8) compares against source mtimes to answer, deterministically, "did tests run after the last edit?" — build tool and workflow tooling meshing like gears.

`project/build.properties` pins sbt itself (`1.11.7`); `project/plugins.sbt` adds exactly two plugins (native-packager, scalafmt). Every plugin is surface area the maintenance agent must track — spend that budget reluctantly.

---

## Part 4 — The application, tier by tier

This part walks the Scala sources in dependency order — domain, data, service, web, composition root, frontend — because that is the order in which the code can be *understood* (and the order in which agents are instructed to read it).

### 4.1 The domain (`domain/Task.scala`)

One file, ~70 lines, containing the entity, the enum, request payloads, the error ADT, and the one non-derivable JSON codec. Points of note, each with its why:

**The `Instant` codec comes first:**

```scala
given ReadWriter[Instant] =
  readwriter[String].bimap[Instant](_.toString, Instant.parse)
```

upickle doesn't ship java.time codecs; `bimap` on the String codec gets ISO-8601 (`Instant.toString` emits it; `Instant.parse` reads it) — human-readable in logs, lexicographically sortable, JS-`Date`-parseable. It is a *top-level given in the same file as the case classes* so every `derives ReadWriter` below it resolves it; had it lived elsewhere, derivation would fail with an implicit-not-found that beginners (and occasionally agents) lose an afternoon to.

**The enum:**

```scala
enum TaskStatus derives ReadWriter:
  case Todo, InProgress, Done
```

Parameterless enum cases serialize as bare strings (`"Todo"`), which is what an API client wants. The three-state machine is deliberately minimal — Part 12 adds a fourth state *as the worked example of the whole agentic loop*, precisely because a status enum touches every tier (DB CHECK constraint, Meta codec, transition rules, JSON, frontend): the perfect probe of whether the workflow keeps the tiers consistent.

**Typed errors:**

```scala
sealed abstract class AppError(val message: String)
    extends Exception(message) with scala.util.control.NoStackTrace
```

A sealed hierarchy means the web tier's error mapping can be *checked for exhaustiveness*; extending `Exception` lets errors travel IO's error channel with no `Either` plumbing through three tiers; `NoStackTrace` because these are expected business outcomes, not defects — capturing a stack trace for "task 42 not found" is pure waste. `TaskNotFound` → 404, `ValidationFailed` → 400, `InvalidTransition` → 409: the mapping table appears once, in `TaskRoutes.handleErrors`.

**Request payloads with defaults** (`description: String = ""`, `title: Option[String] = None`): upickle applies defaults for absent keys, so `{"title":"x"}` parses without ceremony — the API is forgiving where being strict buys nothing. `JsonCodecSuite` pins this behavior so it can't silently change.

### 4.2 The data tier (`data/`)

**The port** (`TaskRepository.scala`) is five methods on `IO`. It could be abstracted over an effect `F[_]` (tagless final); it is not, on purpose. Tagless final buys effect-polymorphism this application will never use, at the cost of a type-parameter tax on every signature — a tax agents pay in comprehension on every read. The tutorial's rule: abstraction must pay rent in the same repo it lives in.

**The doobie implementation** (`DoobieTaskRepository.scala`). Anatomy of one method:

```scala
def create(title: String, description: String): IO[Task] =
  sql"""INSERT INTO tasks (title, description, status)
        VALUES ($title, $description, ${TaskStatus.Todo})
        RETURNING id, title, description, status, created_at, updated_at"""
    .query[Task].unique.transact(xa)
```

Four things are happening. (1) The interpolated `$title` is a **JDBC parameter**, not string concatenation — doobie makes injection impossible by construction, which is why the reviewer agent's security check is "SQL via interpolators only" rather than "audit every query". (2) `RETURNING` sends one round-trip and returns the row *as the database computed it* — ids, timestamps — so application and database never disagree about what was written. (3) `.query[Task]` derives the row decoder from the case class; **column order must match field order**, a real trap the reviewer agent is explicitly told to check. (4) `.transact(xa)` is the only place effects happen; everything before it is a pure description.

The companion holds the one custom column codec — `Meta[TaskStatus]` via strings (readable in `psql`, CHECK-constrained in the schema; a DB enum type would resist the expand/contract migrations Part 10 relies on). `Meta[Instant]` comes from `import doobie.postgres.implicits.*`: since doobie 1.0.0-RC2 the old `doobie.implicits.javasql`-style opt-in imports are gone (`java.sql.*` Metas are default now), and each database module ships its own PG/MySQL-native `java.time` instances — the PG one maps `Instant` through `timestamptz` with accurate column type checks.

**`Database.scala`** does two jobs. `migrate` runs Flyway on boot — the app *converges* its own schema, so "deploy new code" and "apply its migration" cannot be done in the wrong order; idempotence makes it safe on every boot. `transactor` builds the HikariCP pool as a `Resource`, so the pool closes on any exit path; the fixed thread pool for JDBC waits is sized to the connection pool because a thread waiting for a connection that a saturated pool will never give it is just a deadlock with extra steps.

**The migration** (`V1__create_tasks.sql`): `BIGSERIAL` primary key, `VARCHAR(200)` mirroring the service-tier validation (defense in depth: the invariant holds even against code that bypasses the service), a `CHECK` on status, `TIMESTAMPTZ` (never naive timestamps), and an index on `status` because the list endpoint filters by it. The header comment — *never edit an applied migration* — is the db-migrator agent's first law, restated where every future reader will trip over it.

### 4.3 The service tier (`service/TaskService.scala`)

Pure orchestration: validate → call port → map absence to typed error. The most instructive method is the transition rule:

```scala
private def validateTransition(from: TaskStatus, to: TaskStatus): IO[Unit] =
  val legal: Set[(TaskStatus, TaskStatus)] =
    Set((Todo, InProgress), (InProgress, Done), (Done, Todo), (InProgress, Todo))
```

The legal state machine is **data** — a set literal — not a tangle of if/else. To change the rules you edit a set and a test; the shape of the code *is* the specification. When Part 12's worked example adds a `Blocked` state, watch how cheap this makes the change.

Note also what the service does *not* do: no JSON, no status codes, no SQL. Every line here is a business statement, which is why its test suite (Part 5) reads like a requirements document.

### 4.4 The upickle ↔ http4s bridge (`web/UPickleEntityCodec.scala`)

http4s knows nothing about upickle, so we teach it — generically, once:

```scala
given [A](using Writer[A]): EntityEncoder[IO, A] =
  EntityEncoder.stringEncoder[IO].contramap[A](write(_))
    .withContentType(`Content-Type`(MediaType.application.json))

given [A](using Reader[A]): EntityDecoder[IO, A] =
  EntityDecoder.decodeBy(MediaType.application.json) { msg =>
    DecodeResult { msg.bodyText.compile.string.map { body =>
      Try(read[A](body)).toEither.left.map(e =>
        MalformedMessageBodyFailure(s"Invalid JSON: ${e.getMessage}", Some(e)))
    }}
  }
```

The encoder is a `contramap`: "to encode an `A`, write it to a JSON string, then use the existing String encoder" — plus the correct Content-Type. The decoder declares (via `decodeBy`) that it consumes `application/json` *only* — a request with the wrong Content-Type is rejected with a typed failure rather than garbling — and converts parse failures into `MalformedMessageBodyFailure`, which the error middleware maps to **400**. The test "malformed JSON returns 400, not 500" exists because this is precisely the seam where lazy implementations leak 500s, and a 500 is an alarm (Part 10 wires ALB 5xx alarms) while a 400 is the client's homework.

Because both givens are conditional on upickle instances, *every* `derives ReadWriter` type is instantly usable in routes — `Ok(task)`, `req.as[CreateTaskRequest]` — with no per-type ceremony. One import (`UPickleEntityCodec.given`) is the entire integration.

### 4.5 Routes and error mapping (`web/TaskRoutes.scala`, `web/HealthRoutes.scala`)

Each route is deliberately one line of translation:

```scala
case GET -> Root / "api" / "tasks" / LongVar(id) =>
  service.get(id).flatMap(task => Ok(task))
```

`LongVar` fails non-numeric ids into a 404 at the pattern level; the service raises `TaskNotFound` for missing rows; neither concern leaks into the other. All error translation happens in one middleware:

```scala
routes.run(req).value.recoverWith {
  case AppError.TaskNotFound(id)      => NotFound(...)
  case e: AppError.ValidationFailed   => BadRequest(...)
  case e: AppError.InvalidTransition  => Conflict(...)
  case e: DecodeFailure               => BadRequest(...)
}
```

Two subtleties here were caught by this project's own review stage, and they're worth internalizing. First: `recoverWith` (a partial function) rather than `handleErrorWith` (a total one) — with a total function, an unmatched `SQLException` becomes a `MatchError` that *destroys the original stack trace*; with a partial function it passes through untouched to http4s' 500 handler and gets logged whole. Second: catching `DecodeFailure` here is what turns body-parse failures into clean 400s when routes call `req.as[...]`. Both are exactly the class of bug that motivates an adversarial code-reviewer agent: the code compiles, the happy path works, and the defect only bites in production diagnostics.

`HealthRoutes` splits **liveness** (`/healthz`: "the JVM answers"; the ALB health check uses it — a database outage must *not* convince ECS to restart perfectly healthy containers, which would turn a DB incident into a DB-plus-fleet incident) from **readiness** (`/readyz`: `SELECT 1` round-trip; deploy gates and smoke tests use it — a deploy is not done until the new tasks can actually reach the database).

### 4.6 Composition root and frontend

`Main.scala` reads top-to-bottom as the system's assembly manual: load config → migrate → open pool → wire repo→service→routes → add middleware → serve. The router line encodes precedence explicitly (`api <+> health <+> index <+> static`), and the `index` route exists because the static resource service maps exact paths only — `GET /` needs an explicit redirect to `/index.html`. (Also a review-stage catch: the kind of "works when you test /index.html, 404s on the URL users actually type" gap that hides until someone opens the ALB DNS name bare.)

The frontend (`static/index.html`) is ~200 lines of framework-free HTML/CSS/JS served from the classpath: fetch-based CRUD against `/api/tasks`, status filters, and error surfacing from the JSON `error` field. No build step, no node_modules — the presentation tier stays fully inspectable in one file, and the tutorial's complexity budget stays spent on the workflow, which is the point. (The upgrade path — Scala.js with upickle on both sides, or a bundled SPA shipped into `static/` — changes nothing about the other two tiers; that's the boundary doing its job.)

---

## Part 5 — Tests: the agent's ground truth

An agentic workflow lives or dies by its feedback signal. Agents cannot *see* the app; they see command output. The test suite is therefore not just quality assurance — it is the primary sensory organ through which every agent perceives whether its change worked. This dictates the suite's three design properties: **fast** (no DB, no sockets — the whole suite runs in seconds, so agents run it after every edit, not once at the end), **deterministic** (a flaky test teaches an agent to ignore red, the single most corrosive lesson it can learn), and **behavior-pinning** (the tests state contracts, so a violated contract — not a stylistic opinion — is what fails).

Four files:

- **`InMemoryTaskRepository`** — the port implemented over a `Ref[IO, Map[Long, Task]]` (atomic, concurrency-correct, ~40 lines). This is the dividend of the Part 4.2 architecture decision, collected.
- **`TaskServiceSuite`** — business rules as executable requirements: trimming, length limits, every legal and illegal status transition, filtered listing, delete-then-delete. Read the test names aloud and you have the spec.
- **`TaskRoutesSuite`** — the web tier end to end *without a server*: `app.run(request)`. Asserts status codes (201/400/404/409/204), JSON bodies (parsed with upickle — the tests eat the same dog food), and crucially the failure shapes: malformed JSON → 400, unknown enum → 400, illegal transition → 409.
- **`JsonCodecSuite`** — the wire-format contract: round-trips, enum-as-string, ISO-8601 instants, default-field tolerance. This suite is named in `dependency-updater`'s instructions as the tripwire for upickle major bumps: if it reddens, the wire format moved, and that's a migration project, not a version bump.

The division of labor between agents mirrors the suite: `test-engineer` may only write under `src/test/` — when it finds a production bug it must *leave the failing test failing* and hand off, because an agent allowed to touch both sides will, given a hard bug and a long context, eventually "fix" the test. The permission boundary makes the temptation structurally impossible. That is the general trick of this whole tutorial: **turn discipline into configuration.**

---

## Part 6 — MCP: giving agents real tools

Model Context Protocol servers extend Claude Code with tools beyond the filesystem and shell. The project's `.mcp.json` (checked in, so every collaborator and every CI run gets the same integrations) wires five:

| Server | Runs via | Gives the agents | Used chiefly by |
|---|---|---|---|
| `postgres` (awslabs) | `uvx` | live schema inspection & queries — **read-only** | db-migrator, incident-responder |
| `aws-api` (awslabs) | `uvx` | the whole AWS CLI surface as tools | deploy-engineer, incident-responder |
| `ecs` (awslabs) | `uvx` | ECS resource ops + guided troubleshooting — `ALLOW_WRITE=false` | deploy-engineer, incident-responder |
| `terraform` (awslabs) | `uvx` | provider-doc lookup, plan analysis | infra work in the main session |
| `github` | HTTP (`api.githubcopilot.com/mcp/`) | issues/PRs/reviews as tools | the whole team |

Format notes you'll reuse forever: each stdio server is `{type, command, args, env}`; `${VAR}` / `${VAR:-default}` expand from the parent environment at launch, which is how `DATABASE_URL` reaches the postgres server without any secret entering the repo. Check the file into git; personal/experimental servers can go in user scope (`claude mcp add --scope user`) without touching the project.

The **read-only defaults** are the security posture, not an accident: the postgres server gets `--readonly true`, the ECS server `ALLOW_WRITE=false`. The rationale is an asymmetry argument. Reading (inspect schema, tail logs, describe services) multiplies agent usefulness enormously — diagnosis is mostly reading. Writing through the same channels multiplies risk faster than usefulness, because the write paths that *matter* (deploy, rollback) already exist as reviewed, gated scripts (`deploy.sh`, `rollback.sh`) that the agents invoke through the ordinary shell with permission rules and hooks in the loop. So: MCP for eyes, scripts for hands. When an incident-responder needs `pg_stat_activity`, it has it; when it "needs" to `UPDATE tasks SET ...` in production, it structurally can't, and its own instructions tell it to escalate to a human instead.

MCP tools surface to agents as `mcp__<server>__<tool>` — the names you'll see in agent `tools:` frontmatter (e.g. `mcp__postgres__run_query` granted to db-migrator). An agent with no MCP names in its allowlist simply cannot call them: capability wiring and access control in one mechanism.

---

## Part 7 — The agent team in detail

Subagents live in `.claude/agents/<name>.md`: YAML frontmatter (`name`, `description`, `tools`) above a markdown body that becomes the agent's system prompt. Claude Code routes work to them in two ways — automatically, by matching the task against `description` fields, or explicitly ("Use the code-reviewer agent to..."). Two authoring rules follow directly from that mechanism:

1. **The `description` is a routing key, not documentation.** Write it as "when to use me": *"Use for any code change once a plan exists"*, *"Use when alarms fire, smoke tests fail, 5xx rates spike"*. Vague descriptions cause misrouting; misrouting causes the wrong specialist (or no specialist) to handle a stage.
2. **The `tools` list is the real contract.** Everything else is persuasion; the allowlist is physics. Design each agent by first asking "what is the *least* it needs?"

Read the seven definitions in the repo; here is the design intent behind each, compressed:

- **feature-implementer** — the only agent that routinely edits production code. Its body restates the tier rules (redundancy with CLAUDE.md is intentional: subagent instructions survive even when a long session's context gets compacted), demands a test per behavior change, and — critically — *defines its own boundary*: schema changes get refused and routed to db-migrator. Agents that know what they don't do are what make a team, rather than a mob.
- **test-engineer** — write access restricted to `src/test/` by instruction, enforced by review; its brief is adversarial enumeration (boundaries, illegal transitions, malformed input, concurrency). The failing-test-stays-failing handoff rule is its keystone (Part 5).
- **code-reviewer** — *no edit tools at all*, the strongest least-privilege statement in the repo. Its procedure is ordered by expected value: correctness → tier violations → security → migrations → test adequacy. It must *verify* each finding against the code before reporting, because a reviewer that cries wolf trains everyone — humans and orchestrator alike — to skim its output. Findings ranked CRITICAL/MAJOR/MINOR with a concrete failure input each; "APPROVE with zero findings" is an explicitly legitimate outcome (a reviewer forced to find *something* pads reports with noise).
- **db-migrator** — owns the two iron laws (immutable applied migrations; expand/contract for zero-downtime — Part 10.3 explains why ECS's rolling deploys make backward-compatible schemas mandatory, not nice-to-have). It inspects the live schema via the read-only postgres MCP rather than trusting the repo's migration files to describe reality — *verify, don't assume* is its whole epistemology. Destructive DDL: prepare, explain blast radius, stop for a human.
- **deploy-engineer** — operates Part 10's scripts and owns the definition "deployed = new revision serving traffic *and* smoke test green", with auto-rollback on any gate failure. It verifies preconditions itself (clean tree, green `check`) rather than trusting the requester — the requester might be another agent.
- **incident-responder** — a triage decision tree ordered by diagnostic yield (ECS stopped-task reasons first: they *name* the killer — OOM, health check, image pull — before any log spelunking). Its rules of engagement draw the human line precisely: restarts/rollbacks/bounded scaling autonomously, anything touching data or schema escalates with an evidence dossier. The 15-minute timebox converts "agent thrashing in a loop" — the characteristic failure mode of autonomous diagnosis — into "human gets a well-organized dossier."
- **dependency-updater** — the maintenance half of the lifecycle. Risk-ordered upgrades (patches batched, minors solo, majors need written analysis), one family per PR, revert-don't-force on red, plus stack-specific tripwires (http4s milestone ban, the JsonCodecSuite rule, Flyway lockstep). Part 11 puts it on a weekly clock.

The main session orchestrates: plan mode → approval → delegate per `docs/agents.md`'s map → collect results. Escalation is written down once, in that file: rollbacks yes, schema/data repair no.

---

## Part 8 — Hooks and permissions: determinism where it counts

Prompts influence; hooks and permission rules *decide*. Anything you genuinely cannot afford to have skipped must not live in prose. TaskForge draws the line as follows.

**Permission rules** (`.claude/settings.json`): `allow` pre-approves the innocuous-but-constant (sbt, git add/commit/diff, docker build, read-only aws describes/tails, `terraform plan`) so agents don't stall on prompts; `deny` hard-blocks the catastrophic (`terraform destroy`, `terraform apply` — infra changes are human-executed after agent-produced plans, `aws rds delete-db-instance`, `git push --force`, reading `.env*`). Deny always beats allow, and rules compose as `Tool(pattern)` globs. The philosophy: **make the safe path frictionless and the catastrophic path impossible; leave friction only for the genuinely judgment-requiring middle** (that's what the interactive prompt is for).

**Three hooks**, each chosen to illustrate a hook class:

1. **PostToolUse → `format-scala.sh`** (matcher `Edit|Write`): reads the tool-call JSON from stdin, and if the touched file is Scala, formats it. Always exits 0 — formatting is a convenience, never a blocker. Why a hook and not "please run scalafmt" in CLAUDE.md? Because formatting-by-instruction happens 95% of the time, and the missing 5% becomes diff noise that pollutes every subsequent review. Determinism is the point.
2. **PreToolUse → `guard-dangerous.sh`** (matcher `Bash`): pattern-matches the proposed command against a blocklist (`DROP TABLE`, `TRUNCATE`, `terraform destroy`, ...) and **exits 2 to block**, with the reason on stderr — which Claude sees, so it learns *why* and routes around properly (e.g., asks the human) rather than retrying blindly. This is defense in depth under the permission rules: rules match tool+pattern, the hook inspects the actual command string semantically. Two different nets catch different fish.
3. **Stop → `verify-tests-ran.sh`**: when Claude tries to finish its turn with Scala sources modified but no test run since the last modification (the `markTestRun` marker from Part 3), exit 2 pushes it back to work with the instruction to run `sbt check`. The `stop_hook_active` guard prevents infinite loops. This one hook is the workflow's definition-of-done made mechanical: *you are not finished until the suite has spoken.*

Exit-code semantics to memorize: 0 = proceed (stdout can carry structured JSON for finer control), 2 = block with stderr fed back to the model, anything else = non-blocking error. Hooks receive a JSON payload on stdin (`tool_name`, `tool_input`, `hook_event_name`, ...) — both scripts show the canonical `python3 -c 'json.load(sys.stdin)...'` extraction pattern.

---

## Part 9 — Slash commands: runbooks as executables

`.claude/commands/<name>.md` files become `/name` commands: the markdown body is a parameterized prompt (`$1`, `$ARGUMENTS`), the frontmatter can scope `allowed-tools`. The repo defines three — `/deploy [env]`, `/rollback [env]`, `/incident <description>` — and each is a *runbook*: it names the responsible agent, the exact procedure, the gates, and the required report.

Why commands when you could just type "deploy to staging"? Consistency under stress. Free-form requests get free-form interpretations — usually fine, worst at 3 a.m. during an incident, which is exactly when typing `/rollback staging` and getting the *identical* verified procedure every time matters most. Commands are also the natural unit of on-call documentation: new team member asks "how do we deploy?"; the answer is a file in the repo that both humans and the model read. When ops procedure changes, you edit the command file in a reviewed PR — process changes ship like code changes.

---

## Part 10 — AWS: infrastructure, deployment, and why each guardrail exists

### 10.1 The Terraform layout

`infra/terraform/` is one flat, readable module: `network.tf` (VPC with public subnets for the ALB only; app tasks and RDS in private subnets; security groups chained ALB→app:8080→db:5432 so each tier accepts traffic *only* from the tier above — the three-tier architecture reproduced at the network layer), `database.tf` (RDS Postgres 16, encrypted, 7-day backups, `deletion_protection = true`, and the generated password flowing *only* through Secrets Manager into the task definition's `secrets` block — it never exists in the repo, the image, or plain env files), `ecs.tf` (ECR with **immutable git-SHA tags** — "what is `latest`?" is not a question anyone debugging production should ever face — plus cluster, split execution/task IAM roles with the task role deliberately empty: the app needs zero AWS permissions, so it gets zero), `alb.tf` (health-checks `/healthz` — liveness, per Part 4.5), `alarms.tf` (Part 11), `outputs.tf` (the values `deploy.sh` and the smoke test consume).

Two Terraform-vs-scripts seams deserve their rationale spelled out:

- **`lifecycle { ignore_changes = [task_definition] }`** on the ECS service: deploys register new task-definition revisions *outside* Terraform (via `deploy.sh`), so without this, every `terraform plan` would try to roll production back to the revision Terraform last saw. Terraform owns the *shape* of the infrastructure; the deploy pipeline owns the *contents* rolling through it. Document this seam or suffer it.
- **`deployment_circuit_breaker { enable = true, rollback = true }`**: ECS itself watches a deploy and auto-rolls-back if new tasks can't reach steady state. This is the safety net *under* the agent — if the deploy-engineer's session dies mid-deploy, the platform still recovers alone. Agentic operations should always be layered over platform-native self-healing, never substituted for it.

`terraform apply` is human-executed (denied to agents in settings.json): agents produce and analyze plans; a person applies them. Infra changes are rare, high-blast-radius, and cheap to gate.

### 10.2 First deploy, in order

```bash
cd infra/terraform
terraform init && terraform plan   # read the plan. actually read it.
terraform apply                    # human hands, ~10 min (RDS is slow)
cd ../..
./scripts/deploy.sh staging
./scripts/smoke-test.sh "$(terraform -chdir=infra/terraform output -raw alb_dns_name)"
open "http://$(terraform -chdir=infra/terraform output -raw alb_dns_name)"
```

### 10.3 Anatomy of `deploy.sh` — a script written for agents

Read it in the repo; every design choice serves agent-operability. It refuses dirty working trees (a deployed image must equal a commit, or the traceability spine of Part 3 snaps). It prints greppable evidence at each step (`==> Pushed ...`, `==> Registered task definition taskforge:12`) because the *agent's* observability is stdout. It registers the new task definition by pulling the current one, swapping the image (JSON surgery in inline python — the read-only fields must be stripped or the register API rejects it), and letting `aws ecs wait services-stable` block until the rollout settles. Then the crucial epilogue: it *verifies* the service actually landed on the new revision, because if the circuit breaker rolled back, `wait services-stable` still returns success — the deploy script must distinguish "stable on the new code" from merely "stable". Exit 2 with the discrepancy on stderr turns silent platform rollback into a loud, diagnosable failure — precisely the transformation (implicit → explicit) that agents need everywhere.

`rollback.sh` is its dumb, fast inverse (previous revision, wait, report) — rollbacks must not be clever. `smoke-test.sh` is the post-deploy gate: liveness, readiness, then a full create→advance→delete round-trip through all three tiers, each step failing loudly with evidence. The deploy-engineer's definition of done *is* this script's exit code.

### 10.4 Zero-downtime and the schema

Rolling deploys (`minimum_healthy_percent = 100`) mean **old and new code run simultaneously against one database** during every deploy. This is why db-migrator's expand/contract law is law: adding a NOT NULL column with no default in V2 breaks the *old* code still serving traffic mid-rollout. Expand (nullable/new, Vn) → deploy code that handles both → contract (drop old, Vn+k, deploys later). The agent's instructions encode the whole discipline, including "verify against the live schema via MCP, not against your assumptions."

---

## Part 11 — CI/CD and the maintenance loop

Three GitHub Actions workflows close the lifecycle:

**`ci.yml`** — on every push/PR: `scalafmtCheckAll; Test/compile; test` with `CI=true` (so `-Werror` bites), then image build, and on `main` a push to ECR via **OIDC** (`id-token: write` + an assumed role — no long-lived AWS keys sitting in GitHub secrets waiting to leak). CI runs *the same `sbt check` agents run locally*: one definition of green, no "passed locally, failed CI" ambiguity for agents to get confused by.

**`claude.yml`** — `anthropics/claude-code-action@v1`: mention `@claude` on an issue or PR and Claude Code runs *in the runner, in this repo* — which means CLAUDE.md, the agent definitions, and the hooks all apply there too. The workflow you built for local development turns out to be the workflow for GitHub-native operation; that's the payoff of putting everything in checked-in files rather than in anyone's head or anyone's local config.

**`maintenance.yml`** — the scheduled half of "agents handle maintenance": every Monday (and on demand), a headless `claude -p` run executes the dependency-updater playbook — audit `build.sbt`/plugins/sbt itself, apply safe bumps, `sbt check`, emit a changelog — under a scoped `--allowedTools` grant and `--max-turns` budget, and the job opens a PR only if the tree changed. The PR then flows through the *same* review gate as any human change (CI + code-reviewer + human merge). Unattended agents propose; gates dispose. That division — automation generates the diff, the merge decision stays behind the same review the humans use — is what makes scheduled autonomy safe enough to actually leave running.

Alarms (`alarms.tf`) complete the loop back into the workflow: ALB 5xx, unhealthy hosts, RDS CPU and connection pressure → SNS. When one fires, a human (or a triggered automation) opens the repo and types `/incident ALB 5xx alarm firing since 14:02 UTC` — and the incident-responder picks it up with the triage tree of Part 7, the read-only MCP eyes of Part 6, and the rollback lever of Part 9. Its report lands in `docs/incidents/`, and its "one concrete prevention item" feeds the next planning session. That is the full circle: **monitoring → diagnosis → remediation → institutional memory → prevention**, with a human touchpoint exactly where the escalation policy puts one.

---

## Part 12 — The whole loop, end to end: a worked example

Everything above, exercised by one realistic change. The feature: **tasks can be `Blocked`** — from `InProgress` a task may be marked Blocked with a reason, and unblocked back to `InProgress`. It is the ideal probe: it touches the enum (domain), the CHECK constraint (schema), the transition set (service), the JSON wire format (codecs + frontend), and the API surface (web). Here is the session, prompt by prompt, with what happens and why it happens that way.

**1. Plan.** In the repo: `claude`, switch to plan mode, and:

> Add a "Blocked" status to tasks. A task can only become Blocked from InProgress, must carry a non-empty blockedReason, and unblocking returns it to InProgress with the reason cleared. Plan the change across all tiers, including the DB migration and rollout order.

Claude (read-only) walks the tiers and returns a plan; because CLAUDE.md's tier table and db-migrator's expand/contract law are in context, a good plan arrives *already sequenced*: expand migration (nullable `blocked_reason` column + widened CHECK) → domain/service/web changes tolerant of both schemas → tests → review → deploy → (later) contract if ever needed. You approve. The plan is now pinned in the transcript, and every delegation below references it.

**2. Schema.** "Use the db-migrator agent to produce the migration from the approved plan." It inspects the *live* schema via the postgres MCP server, writes `V2__add_blocked_status.sql` (ADD COLUMN nullable; CHECK widened to include `'Blocked'`), and reports the compatibility analysis: old code never reads the new column and still satisfies the widened CHECK — safe under rolling deploy.

**3. Implement.** "Use the feature-implementer agent to implement the plan against the V2 schema." It edits `TaskStatus` (new case), `Task` (`blockedReason: Option[String] = None`), the transition set (two tuples added to the `legal` literal — Part 4.3's data-not-code paying off), the service (validate the reason on block, clear it on unblock), the repository (column added to every column list — and if it forgets one, `.query[Task]` arity breaks the compile: the compiler is the first reviewer), and the frontend's status maps. The PostToolUse hook formats each file as it's written. When the agent tries to finish, the Stop hook checks for a test run — it ran `sbt check`, marker is fresh, it may stop.

**4. Harden.** "Use the test-engineer agent to attack the Blocked feature." It adds the cases the implementer didn't: blocking with an empty reason (400), blocking from `Todo` (409), the JSON shape of `blockedReason` when absent vs present, unblock clearing the reason. Suppose one fails — unblock leaves the reason set. The test *stays red*; the report routes the bug back to feature-implementer, which fixes the service and re-runs `check`. The two-agent separation just paid for itself in the most literal way.

**5. Review.** "Use the code-reviewer agent on the full diff." Read-only, it verifies: column order vs field order in every query, wire-format changes against JsonCodecSuite, the CHECK/enum/valueOf triple-agreement, tier imports, and that V1 was untouched. Verdict: APPROVE (or findings ranked, fixed, re-reviewed). Commit.

**6. Ship.** `/deploy staging`. The deploy-engineer verifies preconditions, runs `deploy.sh` (SHA-tagged image → ECR → new task definition revision → rolling update; Flyway applies V2 on the first new task's boot — before that task passes `/readyz`, so traffic only reaches code whose schema exists), watches for steady state, confirms the revision actually landed (circuit-breaker check), runs `smoke-test.sh`, and reports: image tag, revision, rollout time, smoke evidence. If any gate had failed: `rollback.sh`, evidence dossier, incident note.

**7. Operate.** Monday, `maintenance.yml` bumps the Postgres driver; CI and the reviewer wave it through; a human merges. Wednesday, the 5xx alarm fires; `/incident ...`; the responder finds the deploy 20 minutes prior is the proximate cause, rolls back autonomously (within its rules), and files the report with a prevention item — which becomes next week's plan-mode prompt.

That is the entire production and maintenance lifecycle, and at no point did anyone paste a stack trace into a chat window and hope.

---

## Appendix A — Local development and troubleshooting

```bash
docker compose up -d db    # Postgres 16 on :5432, matching RDS major version
sbt run                    # Flyway migrates, Ember serves http://localhost:8080
sbt check                  # the definition of done
sbt dockerLocal && docker compose up app   # prod image, local run
```

| Symptom | Likely cause | Fix |
|---|---|---|
| `implicit not found: ReadWriter[...]` | codec for a member type (e.g. a new java.time type) missing at derivation site | add a `given ReadWriter` next to the Instant one in `domain/Task.scala` |
| `.query[Task]` arity/type mismatch | SELECT column list ≠ case-class fields (order matters) | fix the column list; this is the compiler catching schema drift — say thank you |
| App boots, `/readyz` 503 | DB unreachable / bad credentials | check `DB_URL`/`DB_USER`/`DB_PASSWORD`; locally: is the compose db healthy? |
| Flyway "Migration checksum mismatch" | an applied migration file was edited | restore the file from git; add a new V<n> instead (this is the iron law) |
| ECS deploy "settled on old revision" from deploy.sh | circuit breaker rolled back | `aws ecs describe-services` stopped-task reasons + `aws logs tail /ecs/taskforge` — the reason is named there |
| Agent stalls on permission prompts in CI | tool not in `--allowedTools` | extend the grant deliberately; never reach for `--dangerously-skip-permissions` outside a sandboxed container |
| Stop hook keeps bouncing the session | tests genuinely not run since last edit | run `sbt check`; the hook is doing its job |

## Appendix B — Complete file map

```
taskforge/
├── CLAUDE.md                     # project memory: architecture, commands, iron rules (Part 2)
├── docs/agents.md                # lifecycle → agent map, escalation policy (imported by CLAUDE.md)
├── build.sbt                     # deps, -Werror in CI, Docker packaging, check/markTestRun (Part 3)
├── project/{build.properties, plugins.sbt}
├── .scalafmt.conf
├── src/main/scala/com/taskforge/
│   ├── domain/Task.scala         # entities, enum, errors, upickle codecs (4.1)
│   ├── config/AppConfig.scala    # env-var config, no library (4.6)
│   ├── data/                     # port, doobie impl, pool+Flyway (4.2)
│   ├── service/TaskService.scala # rules; transitions as data (4.3)
│   ├── web/                      # upickle bridge, routes, error middleware, health (4.4–4.5)
│   └── Main.scala                # composition root (4.6)
├── src/main/resources/
│   ├── db/migration/V1__create_tasks.sql
│   ├── static/index.html         # framework-free frontend
│   └── logback.xml
├── src/test/scala/com/taskforge/ # in-memory repo + 3 suites (Part 5)
├── .mcp.json                     # postgres/aws-api/ecs/terraform/github servers (Part 6)
├── .claude/
│   ├── agents/                   # the seven specialists (Part 7)
│   ├── commands/                 # /deploy /rollback /incident (Part 9)
│   ├── hooks/                    # format, guard, verify-tests (Part 8)
│   └── settings.json             # permissions + hook wiring (Part 8)
├── infra/terraform/              # VPC/ALB/ECS/RDS/alarms (Part 10)
├── scripts/                      # deploy.sh, rollback.sh, smoke-test.sh (10.3)
├── .github/workflows/            # ci.yml, claude.yml, maintenance.yml (Part 11)
└── docker-compose.yml            # local Postgres + prod-image run (App. A)
```

---

*Versions pinned in this tutorial: Scala 3.3.6, http4s 0.23.35, doobie 1.0.0-RC12, upickle 4.4.2, Flyway 11.10, sbt 1.11.7, sbt-native-packager 1.11.1, munit 1.1.1, munit-cats-effect 2.1.0, Terraform AWS provider ~> 5.0. Check them against upstream before starting — better yet, make it the dependency-updater agent's first assignment.*
