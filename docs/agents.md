# Agent team, artifact ownership, and escalation

This repo is operated — and was CREATED — by a team of Claude Code subagents defined in
`.claude/agents/`. Every artifact class has exactly one creating/owning agent; nothing in
this repository is hand-written except ratification decisions. Delegate to the owner;
never do a specialty inline in the main context.

## Artifact ownership map (one writer per artifact class)

| Artifact class | Created & owned by | Version bumps by | Gated by |
|---|---|---|---|
| `.claude/**`, `CLAUDE.md`, `.mcp.json`, `docs/agents.md` | **factory-engineer** | — | HUMAN ratification (constitutional) + code-reviewer |
| `build.sbt`, `project/*`, `.scalafmt.conf`, `docker-compose.yml`, `.gitignore` | **build-engineer** | **dependency-updater** (ledger vals only) | code-reviewer + `sbt check` |
| `src/main/**`, `src/main/resources/static/**` | **feature-implementer** | — | test-engineer, code-reviewer, `sbt check` |
| `src/test/**` | **test-engineer** (and implementer, tests shipped with features) | — | code-reviewer |
| `src/main/resources/db/migration/V*.sql` | **db-migrator** (immutable once applied) | — | code-reviewer (edits = CRITICAL) + human for destructive DDL |
| `infra/terraform/**`, `scripts/*.sh`, `.github/workflows/**` | **infra-engineer** | — | `terraform plan` read by HUMAN, who applies; code-reviewer |
| Deployments (ECS revisions) | **deploy-engineer** (executes scripts it does not author) | — | smoke-test gate, circuit breaker |
| `docs/incidents/**` | **incident-responder** | — | — |

## Lifecycle → agent map

| Lifecycle stage | Agent | Trigger |
|---|---|---|
| Bootstrap/evolve the factory | **factory-engineer** | new agent, hook, permission, MCP server |
| Create/change the build | **build-engineer** | new project, new dependency, packaging change |
| Design/plan | (main context, plan mode) | new feature request |
| Implement | **feature-implementer** | approved plan |
| Schema change | **db-migrator** | any change under `db/migration` |
| Test | **test-engineer** | after implementation, before review |
| Review | **code-reviewer** | before every commit/PR |
| Infra/pipelines/scripts | **infra-engineer** | any terraform/script/workflow change |
| Deploy | **deploy-engineer** | `/deploy` command |
| Monitor/diagnose | **incident-responder** | alarms, failed smoke tests, `/incident` |
| Maintain versions | **dependency-updater** | weekly schedule or CVE notice |

## Escalation policy (stated once, here)

- incident-responder may roll back, restart, and scale [1,4] autonomously; anything
  touching data, schema, or security surfaces to a human first.
- db-migrator prepares destructive DDL but a human signs off before it ships.
- infra-engineer produces plans; a human runs `terraform apply`.
- factory-engineer prepares constitutional diffs; a human ratifies before they take effect.
- Any agent asked to work outside its ownership row declines and names the owner.
