# Genesis prompt script — fully agent-driven

The copy-paste companion to `AGENTS-TUTORIAL.md` Part D, revised so that EVERY artifact —
including the factory itself and the build — is created from scratch by a (sub)agent. The
human contributes only: toolchain setup, prompts, ratifications, and `terraform apply`.
One phase per session (or `/clear`); one commit per phase; paste each agent's report into
the next prompt that needs it — reports are the only memory that crosses between agents.

## Phase 0 — Plant the seed (plain session; the ONLY file the orchestrator writes)

Create exactly one file, .claude/agents/factory-engineer.md, and nothing else: an agent
whose job is to create and maintain the agent system itself (CLAUDE.md, docs/agents.md,
all .claude/agents/*, hooks, settings.json, commands, .mcp.json) from an authority matrix.
Frontmatter: name factory-engineer; a routing-grade description ("Creates and maintains the
agent system itself FROM SCRATCH... prepares constitutional diffs; never self-ratifies");
tools Read, Grep, Glob, Write, Edit, Bash. Body iron laws: (1) transcribe the authority
matrix, never widen a fence or soften a law unless the matrix changed first; (2) every
.claude/** change is constitutional — full diff + justification, in force only after human
ratification and restart, never self-approved; (3) least privilege by default — no omitted
tools: fields, MCP read-only at server level, reviewer-class agents get no write tools;
(4) channel discipline — timeless role files, universal facts to CLAUDE.md (≤150 lines,
≤8 hard rules), one-run detail in task text; (5) floor invariants that may never be removed
(guard patterns, stop_hook_active check, formatter exit 0, deny rules for terraform
apply/destroy, force-push, .env reads). Procedure: read matrix → author files using the
five-section skeleton with collision/orphan audits → validate mechanically (json parse,
bash -n, chmod +x) → present diff and stop. Print the full file content in your reply.

→ HUMAN GATE (constitutional): read the seed file line by line; commit; restart the session.

## Phase 1 — The factory builds the factory (factory-engineer)

Use the factory-engineer agent to create the rest of the TaskForge agent system from this
authority matrix. System: Scala 3 three-tier task-management web app (http4s presentation
tier + static HTML/JS frontend; pure business-logic tier on cats-effect IO; doobie/
PostgreSQL data tier; upickle for ALL JSON), deployed on AWS ECS Fargate + RDS via
Terraform. Create: (1) CLAUDE.md — identity; three-tier May/Must-NOT table; commands with
`sbt check` as the definition of done; hard rules (one-owner-per-artifact with the
ownership map, upickle-only JSON, applied migrations immutable, every change ships a test,
secrets only via Secrets Manager, infra only via Terraform); @docs/agents.md import.
(2) docs/agents.md — the ARTIFACT OWNERSHIP MAP (artifact class → creating/owning agent →
version-bump agent → gate) and lifecycle table for TEN agents: factory-engineer (already
exists — list it), build-engineer (creates build.sbt/project/scalafmt/compose/gitignore
from scratch; sole owner of build structure), feature-implementer (src/** only; routes
dependency needs to build-engineer), test-engineer, code-reviewer (add an Ownership review
axis), db-migrator, infra-engineer (creates terraform + scripts + workflows from scratch;
plans only, human applies), deploy-engineer (executes scripts it does not author),
incident-responder, dependency-updater (version ledger only); plus the escalation policy
(rollbacks autonomous; data/schema/security to humans; destructive DDL human-signed;
applies human-run; constitutional changes human-ratified). (3) The nine remaining agent
files per the matrix, least-privilege fences (reviewer: Read/Grep/Glob/Bash only; migrator:
+ mcp__postgres__run_query; deploy: + mcp__aws-api__call_aws, mcp__ecs__ecs_resource_management;
responder: + ecs_troubleshooting_tool and postgres read; updater: + WebSearch/WebFetch, Edit
but not Write; infra: + mcp__aws-api__call_aws). (4) Hooks + settings.json — PostToolUse
scalafmt (exit 0); PreToolUse Bash guard (DROP/TRUNCATE/terraform destroy/force-deletes →
exit 2, human-must-do-this message); Stop hook via .claude/.last-test-run marker with
stop_hook_active guard; permissions: allow sbt/git bookkeeping/docker build/read-only
aws/terraform plan+validate; deny terraform apply+destroy, rds/ecr deletes, force-push,
.env reads. (5) .mcp.json — awslabs postgres (readonly), aws-api, ecs (ALLOW_WRITE=false),
terraform via uvx; github via HTTP. (6) .claude/commands/ — /deploy, /rollback, /incident
naming their responsible agents and gates. No application code. Present the full diff with
per-file matrix justifications and your audit results, then stop for ratification.

→ HUMAN GATE (constitutional): review every file against the matrix; commit; RESTART; then
   probe: ten agents listed; guard hook blocks `echo 'DROP TABLE tasks'`; boundary-probe
   feature-implementer with a build.sbt request (must route to build-engineer).

## Phase 2 — Build system (build-engineer)

Use the build-engineer agent to create the sbt build for TaskForge from scratch. Scala 3.3 LTS; pin
exact versions as named vals: http4s 0.23.x (ember-server, dsl; ember-client Test-scoped),
upickle 4.x as the ONLY JSON library, doobie 1.0.0-RC (core, hikari, postgres), Flyway (core +
postgres module, Runtime), PostgreSQL JDBC driver, logback (Runtime), munit +
munit-cats-effect (Test). scalacOptions -deprecation -feature -unchecked -Wunused:all, plus
-Werror only when the CI env var is set. sbt-native-packager Docker config:
eclipse-temurin:21-jre base, port 8080, non-root user, -XX:MaxRAMPercentage=75.0. A
markTestRun task touching .claude/.last-test-run, and aliases fmt / check (scalafmtCheckAll;
Test/compile; test; markTestRun) / dockerLocal. Also project/build.properties (current sbt
1.x), plugins.sbt (native-packager, scalafmt), .scalafmt.conf (scala3 dialect, maxColumn 100),
.gitignore (sbt/metals/terraform/.env + the marker), docker-compose.yml (healthchecked
postgres:16 db service; app service running taskforge:latest). Verify with `sbt Test/compile`;
report versions chosen and any deviation from this spec.

## Phase 3 — Domain + wire format

Use the feature-implementer agent to create the TaskForge domain in com.taskforge.domain, one
file: top-level `given ReadWriter[java.time.Instant]` via ISO-8601 (readwriter[String].bimap)
above the case classes; `enum TaskStatus derives ReadWriter` (Todo, InProgress, Done);
`Task(id: Long, title, description, status, createdAt, updatedAt) derives ReadWriter`;
CreateTaskRequest(title, description = ""); UpdateTaskRequest all-Option defaulted None;
ErrorResponse(error); sealed abstract class AppError(message) extends Exception with
NoStackTrace — TaskNotFound(id), ValidationFailed(reason), InvalidTransition(from, to). Also
com.taskforge.config.AppConfig: env-var config (HTTP_HOST/PORT, DB_URL/USER/PASSWORD/
POOL_SIZE) with local defaults, no config library. Then JsonCodecSuite (plain munit): Task
round-trip; enum encodes as bare "InProgress"; Instant ISO-8601; CreateTaskRequest parses
without description; UpdateTaskRequest parses from {}; unknown enum fails. Run `sbt check`;
report the exact JSON of one sample Task.

## Phase 4a — Schema (db-migrator)

Use the db-migrator agent to create V1 for TaskForge: tasks table — id BIGSERIAL PK; title
VARCHAR(200) NOT NULL; description TEXT NOT NULL DEFAULT ''; status VARCHAR(20) NOT NULL
DEFAULT 'Todo' CHECK (status IN ('Todo','InProgress','Done')); created_at/updated_at
TIMESTAMPTZ NOT NULL DEFAULT now(); index on status (the list endpoint filters by it). Header
comment: applied migrations are never edited. There is no live database yet — your inspect
step is vacuous this once; say so in your report. Verify via `docker compose up -d db` +
Flyway application; report compatibility analysis and rollback strategy.

## Phase 4b — Data tier (feature-implementer)

Use the feature-implementer agent to build the data tier against V1:
data/TaskRepository.scala — trait on IO (create / get / list by optional status / update /
delete). data/DoobieTaskRepository.scala — doobie implementation: sql interpolators only,
RETURNING on insert/update, .query[Task] column order exactly matching the case class,
companion `given Meta[TaskStatus]` via Meta[String].timap, java.time Metas from
`doobie.postgres.implicits.*` (do NOT hand-roll Meta[Instant]). data/Database.scala — Flyway
migrate as IO.blocking (idempotent, every boot) + HikariTransactor Resource with a fixed
thread pool sized to the connection pool. `sbt check`; report where schema and case class
could drift and what catches it.

## Phase 5a — Service tier (feature-implementer)

Use the feature-implementer agent to build the business tier: service/TaskService.scala
depending ONLY on the TaskRepository trait and domain — create (title trimmed, nonempty, ≤200)
/ get (absent → TaskNotFound) / list / update (validate new title; validate transitions;
absent → TaskNotFound) / delete (false → TaskNotFound). Legal transitions as a Set of
(from,to): Todo→InProgress, InProgress→Done, Done→Todo, InProgress→Todo, plus same-state
no-ops — rules as data, not if-trees. In src/test: InMemoryTaskRepository over
Ref[IO, Map[Long, Task]] + counter; TaskServiceSuite (munit-cats-effect): create/trim/reject,
every legal transition, one illegal, list filtering, delete-then-delete. `sbt check`; report
the transition set verbatim.

## Phase 5b — Adversarial hardening (test-engineer)

Use the test-engineer agent on the service tier. The implementer's report: [PASTE 5a REPORT].
Enumerate what it missed per your mission categories; add the tests; leave any failing test
failing and report it.

## Phase 6 — Web tier (feature-implementer)

Use the feature-implementer agent to build the presentation tier. (1)
web/UPickleEntityCodec.scala: given EntityEncoder for any upickle Writer
(stringEncoder.contramap + application/json content type); given EntityDecoder for any Reader
via EntityDecoder.decodeBy(application/json) reading bodyText, parse failures →
MalformedMessageBodyFailure. (2) web/TaskRoutes.scala: GET /api/tasks?status= (unknown →
ValidationFailed), GET/PATCH/DELETE /api/tasks/<id> via LongVar, POST /api/tasks → 201; routes
one-line-thin; companion handleErrors middleware using recoverWith — NOT handleErrorWith,
unmatched throwables pass through with stack traces intact — TaskNotFound→404,
ValidationFailed→400, InvalidTransition→409, DecodeFailure→400. (3) web/HealthRoutes.scala:
/healthz instant liveness; /readyz SELECT 1 through the transactor, 503 with reason (guard
null getMessage). (4) Main.scala: config → migrate → transactor Resource → wire
repo→service→routes; Router of api <+> health <+> explicit GET / redirect to /index.html <+>
resource service for /static; request logging; Ember at configured host/port. (5)
static/index.html: single-file vanilla HTML/CSS/JS task board on /api/tasks — create, filter,
advance status, delete, surface JSON error bodies. (6) TaskRoutesSuite via app.run directly:
201 create; 400 empty title; 400 malformed JSON (not 500); 404 missing id; 409 illegal
transition; 400 unknown status; full lifecycle round-trip. `sbt check`; report the route table
and each error's status code.

## Phase 7 — Full review (code-reviewer)

Use the code-reviewer agent on the full repository state (diff against the empty tree:
everything is new). Full procedure, all axes, verified findings only.

→ Route findings to owners; re-review to APPROVE; commit.

## Phase 8 — Infrastructure + scripts (infra-engineer)

Use the infra-engineer agent to design and write infra/terraform for TaskForge on AWS:
VPC (public subnets: ALB only; private: app + RDS), security groups chained
ALB→app:8080→db:5432; RDS Postgres 16 (encrypted, 7-day backups, deletion protection,
password generated → Secrets Manager only → ECS task definition secrets block); ECR (immutable
SHA tags, scan on push); ECS cluster + Fargate task definition (execution role reads the one
secret; task role empty) + service with deployment circuit breaker (enable+rollback) and
lifecycle ignore_changes on task_definition; ALB health-checking /healthz; four CloudWatch
alarms (ALB 5xx, unhealthy hosts, RDS CPU, RDS connections) → SNS; outputs: alb_dns_name,
ecr url, cluster/service names, log group. Use the terraform MCP server for provider lookups.
Run terraform validate and plan; present the plan — I will apply it myself.

Use the infra-engineer agent to write scripts/deploy.sh (refuse dirty tree; APP_VERSION=
git SHA sbt Docker/publishLocal; push to ECR; register new task-definition revision with the
new image via the AWS CLI; update service; wait services-stable; VERIFY the service landed on
the new revision — circuit breaker makes bare stable ambiguous — exit 2 with evidence if not),
scripts/rollback.sh (previous revision; wait; report), scripts/smoke-test.sh (healthz; readyz;
create/advance/delete round-trip; loud failures). Bash strict mode; greppable ==> step
markers; no interactive prompts. `bash -n` all three; report each script's gates.

## Phase 9 — First deploy

/deploy staging

## Phase 10 — Pipelines (infra-engineer)

Use the infra-engineer agent to write .github/workflows/ci.yml (push/PR: CI=true sbt
"scalafmtCheckAll; Test/compile; test"; image build; on main push to ECR via OIDC
role-to-assume — no long-lived keys), claude.yml (anthropics/claude-code-action@v1 on @claude
mentions, permissions for contents/PRs/issues/id-token, sbt toolchain preinstalled), and
maintenance.yml (weekly cron + workflow_dispatch: install claude-code; headless `claude -p`
running the dependency-updater playbook — safe patch/minor only, sbt check, changelog output —
with scoped --allowedTools and --max-turns; open a PR only if the tree changed). Report each
workflow's trigger, permissions, and gates.
