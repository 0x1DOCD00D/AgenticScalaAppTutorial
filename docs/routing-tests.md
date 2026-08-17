# Routing test corpus

The behavioral contract for agent routing. Every row is one request as a user would type
it, the agent that must win, and the reason (which makes repairs reviewable). Run the suite
whenever any description changes and after any router model upgrade; the runner script is
in the tutorial section "Routing tests and the description expansion loop".

Maintenance protocol, mirroring the failing-test handoff: when a real request misroutes,
add it here first as a failing row, then repair the description through the
factory-engineer, then re-run to green. Never repair by naming agents in prompts; a prompt
workaround fixes one session, a description fix routes every future session.

## Positive rows (each agent must win its own work)

| Request | Expected agent | Why |
|---|---|---|
| add a new agent for security scanning | factory-engineer | new .claude/agents file is constitutional territory |
| add a new MCP server for Redis | factory-engineer | .mcp.json is factory territory |
| tighten the code-reviewer's tool list | factory-engineer | fence changes transcribe the matrix |
| change the /deploy command to require a ticket number | factory-engineer | .claude/commands is factory territory |
| update CLAUDE.md to document the new alias | factory-engineer | memory file changes are constitutional |
| add upickle to the project | build-engineer | dependency set is build structure |
| set up the sbt build for a new module | build-engineer | build structure from scratch |
| switch the Docker base image to temurin 22 jammy | build-engineer | packaging lives in the build |
| set scalafmt maxColumn to 120 | build-engineer | .scalafmt.conf is build scaffolding |
| add an sbt alias that runs integration tests | build-engineer | alias structure; check stays stable |
| bump doobie to the latest RC | dependency-updater | version ledger only |
| upgrade http4s to the newest 0.23 patch | dependency-updater | routine patch bump |
| are any dependencies affected by this CVE | dependency-updater | advisory response is its trigger |
| run the weekly dependency audit now | dependency-updater | its scheduled playbook, on demand |
| make task titles searchable | feature-implementer | src feature work |
| add pagination to GET /api/tasks | feature-implementer | route plus service change |
| fix the bug where empty descriptions return 500 | feature-implementer | production code fix |
| add a dark mode toggle to the frontend | feature-implementer | static frontend is src/main/resources |
| add edge case tests for the transition rules | test-engineer | adversarial hardening of src/test |
| our suite misses concurrency cases, harden it | test-engineer | its mission categories |
| find out why TaskServiceSuite is flaky | test-engineer | test diagnosis, no production edits |
| review my staged changes before I commit | code-reviewer | pre-commit review |
| is this diff safe to merge | code-reviewer | review verdict with findings |
| check the repo for tier violations | code-reviewer | its tier-violations axis |
| add a priority column to tasks | db-migrator | schema change |
| create an index on updated_at | db-migrator | schema change, expand step |
| we need a new table for task comments | db-migrator | schema change |
| drop the legacy status column | db-migrator | destructive DDL; it prepares and escalates |
| add an HTTPS listener with an ACM cert | infra-engineer | ALB is terraform territory |
| add a staging environment to terraform | infra-engineer | infrastructure as code |
| the smoke test should also check latency | infra-engineer | scripts are its artifacts |
| pin the GitHub Actions versions in CI | infra-engineer | workflows are its artifacts |
| ship current main to staging | deploy-engineer | deployment execution |
| deploy the latest build | deploy-engineer | deployment execution |
| roll back staging to the previous version | deploy-engineer | direct rollback request, no diagnosis needed |
| the 5xx alarm is firing | incident-responder | alarm trigger |
| the app is slow since noon, find out why | incident-responder | degradation diagnosis |
| readyz returns 503 in production | incident-responder | production triage |
| tasks are disappearing, investigate | incident-responder | investigates, then escalates the data issue |

## Hard negatives (boundary rows; vocabulary belongs to one agent, work to another)

| Request | Expected agent | Why |
|---|---|---|
| upgrade the schema to support priorities | db-migrator | updater vocabulary, migrator work |
| migrate to the new sbt version | dependency-updater | migrator vocabulary; sbt.version is a ledger bump |
| migrate the app to Scala 3.4 | build-engineer | platform change alters build structure and flags, beyond a routine bump |
| CI fails: compilation error in TaskService.scala | feature-implementer | build vocabulary, code defect |
| CI fails: cannot resolve doobie-core from Maven Central | build-engineer | build vocabulary, resolver and build structure |
| CI fails: the workflow cannot assume the AWS role | infra-engineer | build vocabulary, workflow artifact |
| update the task status transition rules | feature-implementer | updater vocabulary, business rule change |
| update the schema for comments | db-migrator | updater vocabulary, schema work |
| monitor dependencies for new CVEs | dependency-updater | responder vocabulary, maintenance work |
| keep an eye on error rates today | incident-responder | monitoring the live service |
| watch the rollout of the deploy I just started | deploy-engineer | rollout supervision is part of deploying |
| add a smoke test for the new endpoint | infra-engineer | test vocabulary, script artifact |
| add tests for the deploy script | infra-engineer | test vocabulary, scripts are its artifacts |
| the deploy script needs a dry-run flag | infra-engineer | deploy vocabulary, authoring work |
| run the deploy | deploy-engineer | deploy vocabulary, execution work |
| the migration failed on app startup in production | incident-responder | migrator vocabulary, production triage first |
| review the new agent file I added | code-reviewer | factory vocabulary, review work |
| give the reviewer permission to edit files | factory-engineer | fence change; expect objection per its law 1 |
| bump the Docker base image to the latest tag | dependency-updater | packaging vocabulary, version work per its scope |
| add version 2 of the API endpoints | feature-implementer | version vocabulary, feature work |
| register a new task-definition revision | deploy-engineer | version vocabulary, deploy mechanics |
| the weekly update PR broke the build, revert it | dependency-updater | its failing-bump rule: revert and report |
| upgrade Postgres from 16 to 17 on RDS | infra-engineer | upgrade vocabulary, stateful infra; expect escalation |

## Polysemy registry

A polysemous word appears unqualified in at most one description; every other description
that needs it must qualify it. When a new sense appears, extend this table and add a hard
negative above in the same change.

| Word | Bare form owned by | Others must qualify as |
|---|---|---|
| migration | db-migrator | migrating library or Scala versions (dependency-updater, build-engineer) |
| build | build-engineer | the CI build (infra-engineer); build a feature (feature-implementer) |
| update | dependency-updater | update the schema (db-migrator); update via the API (feature-implementer) |
| deploy | deploy-engineer | deploy scripts as authored artifacts (infra-engineer) |
| test | test-engineer | smoke test scripts (infra-engineer); smoke test as deploy gate (deploy-engineer) |
| version | dependency-updater | sbt scalaVersion structure (build-engineer); task-definition revision (deploy-engineer) |
| monitor | incident-responder | monitor dependencies for CVEs (dependency-updater) |
| rollback | deploy-engineer | rollback as incident remediation (incident-responder, within its rules) |
