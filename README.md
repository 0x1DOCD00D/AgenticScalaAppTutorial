# AgenticScalaAppTutorial

A tutorial project in which Claude Code agents create, deploy, and maintain an entire
Scala 3 three-tier application (TaskForge) from scratch. Start with
`AgenticScalaAppTutorial.md`: the self-contained, step-by-step tutorial with the exact
commands, prompts, and gates, and per-phase explanations of how each agent's instructions
produce each step.

- **Tier 1 — presentation**: http4s (Ember) REST API + vanilla HTML/JS frontend
- **Tier 2 — business logic**: pure services on cats-effect `IO`
- **Tier 3 — data**: doobie + PostgreSQL, Flyway migrations
- **JSON**: upickle (com-lihaoyi) end to end, bridged to http4s in ~30 lines
- **Deploy**: sbt-native-packager → Docker → ECR → ECS Fargate behind an ALB, RDS PostgreSQL,
  all defined in `infra/terraform`
- **Agents**: `.claude/agents/` defines a ten-agent team that CREATES and maintains every
  artifact class in this repo — factory-engineer (builds the agent system itself),
  build-engineer (creates build.sbt + scaffolding from scratch), feature-implementer,
  test-engineer, code-reviewer, db-migrator, infra-engineer (creates terraform/scripts/
  workflows), deploy-engineer, incident-responder, dependency-updater. The artifact
  ownership map lives in `docs/agents.md`; `.claude/settings.json` wires hooks and
  permissions; `.mcp.json` connects Postgres/AWS/Terraform/GitHub MCP servers.

## Quick start (local)

```bash
docker compose up -d db     # PostgreSQL 16 on :5432
sbt run                     # migrates schema, serves http://localhost:8080
sbt check                   # format check + compile + tests
```

## Quick start (agentic)

```bash
claude                      # in the repo root — CLAUDE.md and the agent team load automatically
> Add a "priority" field to tasks       # watch plan -> db-migrator -> implementer -> tests -> review
> /deploy staging                       # deploy-engineer takes it from here
```

## The tutorials

- `AgenticScalaAppTutorial.md` (start here) — self-contained: the precise sequence of
  commands and agent prompts that create build.sbt and the entire project from scratch,
  with per-phase tables mapping every observed behavior to the agent instruction that
  causes it, plus reference agent files and troubleshooting.
- `AGENTS-TUTORIAL.md` — the deep companion on designing and writing agent systems: the
  architecture method, every agent file dissected line by line, testing and evolving
  agents. Its copy-paste prompt script is `docs/genesis-prompts.md`.
- `TUTORIAL.md` — the application-side companion: the Scala/AWS stack rationale, tier by
  tier, and the deployment architecture in detail.
- `WHAT-THE-AGENT-WRITER-MUST-KNOW.md` — the prerequisite body of knowledge for authoring
  agents that generate applications.
