# The Agent Factory: Architecting, Writing, and Operating an Agent Team that Builds and Runs a Production System

### A complete construction manual — every agent and subagent built step by step with rationale, and the entire source codebase generated, tested, deployed, and maintained by those agents, narrated phase by phase in excruciating detail

---

## What this book is, and how to read it

This tutorial teaches two skills that together constitute *agentic engineering* in Claude Code:

1. **Building the agents** (Parts A–C): the mental model, the architecture method, and the file-by-file, field-by-field, line-by-line construction of every agent, subagent, and supporting structure — with the complete final text of each file inline and the reasoning behind every line attached at the point of decision.
2. **Having the agents build everything else** (Parts D–E): the *genesis narrative* — starting from an empty directory, the human writes **zero application code**; the agent team generates the entire Scala 3 three-tier codebase, its tests, its infrastructure, deploys it to AWS, and then operates and maintains it. Every phase is given as: preconditions → the exact prompt → what happens mechanically (tool call by tool call, hook by hook) → the expected report → your verification gate → the failure branch.

Parts F–G close the loop: testing and debugging the agents themselves, and the master checklist for reapplying the whole method to any system.

The running example is **TaskForge** — a task-management web application (Scala 3, http4s, doobie, upickle, PostgreSQL, ECS Fargate). The application is deliberately modest; it exists so that every example in this book is *real*: every agent file quoted here ships in the companion repo under `.claude/`, every prompt in Part D is executable, every command's output shape is stated. The application-side details (why http4s, how the upickle bridge works, what the Terraform provisions) have their own companion document, `TUTORIAL.md`; this book cites it rather than repeating it, because this book's subject is the *factory*, not the product.

A note on the human's role, since it defines everything else: in this workflow the human is the **principal** — they set intent, approve plans, review the constitution-level files, apply Terraform, and take escalations. They do not write application code, and after Part C they do not write agent instructions either except through reviewed diffs. Every artifact in the system is authored by an agent and *reviewed into existence* by gates (automated and human). If you remember one sentence, remember this one:

> **You are not writing a program. You are writing the organization that writes the program.**

---

# PART A — FOUNDATIONS: THE MENTAL MODEL

You cannot design agents well with a fuzzy model of what they are. This part makes the model exact, because every design rule in Parts B–C is a *derivation* from these mechanics, not a style preference.

## A.1 The mechanics of a subagent, precisely

A **subagent** in Claude Code is defined by one markdown file at `.claude/agents/<name>.md`. When work is delegated to it (automatically, or because you or the orchestrator named it), the runtime performs a five-step transaction:

1. **Spawn a fresh context.** The subagent's context window is assembled from scratch: its definition body (which becomes its *system prompt*), the project memory (`CLAUDE.md` plus everything CLAUDE.md `@imports`), and the **task text** — the specific delegation prompt. It does *not* contain: the main session's conversation, other agents' transcripts, or anything from previous runs of itself. Subagents are amnesiac by design.
2. **Fence the tools.** The frontmatter `tools:` list is enforced by the runtime. An agent without `Edit` cannot edit — this is not an instruction it might disobey; the capability is absent.
3. **Run its own loop.** The subagent reads files, runs commands, thinks, iterates — as many tool calls as it needs — inside its own context, without the orchestrator seeing intermediate steps.
4. **Pass through the same deterministic floor.** Hooks (`PreToolUse`, `PostToolUse`, `Stop`) and permission rules in `.claude/settings.json` apply to subagents' tool calls exactly as to the main session's. The floor is global; you do not re-implement it per agent.
5. **Return one report.** The subagent's final message is handed back to the caller. That report is the *only* artifact that crosses the boundary — the orchestrator's entire knowledge of what happened. (Plus, of course, whatever the subagent changed on disk: files are shared state.)

Compress this to a mantra, because you will use it in every design argument in this book:

> **Fresh context. Fenced tools. Own loop. Shared floor. One report.**

Immediate corollaries, each of which becomes a design rule later:

- Because contexts are fresh, **everything an agent must always know goes in its definition or in CLAUDE.md** — never "I told it last time." (→ B, Step 4: knowledge partitioning.)
- Because only the report crosses back, **the report format is an API contract** and must be specified like one. (→ C: output contracts.)
- Because files are shared state but contexts are not, **artifacts (code, migrations, reports-on-disk) are how agents communicate**, and write-authority over each artifact class must be exclusive. (→ B, Step 3: one writer per artifact.)
- Because the floor is shared, **safety rules that must never fail belong in hooks/permissions, not in any agent's prose**. (→ B, Step 6.)

## A.2 The three channels, and channel discipline

Every piece of information you can convey to an agent travels down exactly one of three channels. Choosing the channel is half of agent authoring:

| Channel | Artifact | Reaches | Lifetime | Right for |
|---|---|---|---|---|
| **Shared memory** | `CLAUDE.md` + `@imports` | every session, every subagent | permanent, versioned | facts true for *everyone*: architecture, commands, iron rules, routing map |
| **Role instructions** | `.claude/agents/<name>.md` body | one agent, every run | permanent, versioned | the specialty: identity, laws, procedure, boundaries, report format |
| **Task text** | delegation prompt / `/command` body | one agent, one run | ephemeral (commands: versioned) | this job's specifics: what to build, what broke, which environment |

**Channel misplacement is the root authoring error**, and it has three signatures you will learn to spot on sight. *Per-run details in a role file* ("fix the bug in TaskRoutes line 40") — goes stale the moment it's written; role files must be timeless. *Role procedure in task prompts* ("...and remember to run the tests and check formatting and...") — drifts, gets forgotten, and betrays that the procedure never got a home; move it into the agent and the prompt shrinks to intent. *Universal law stated in one agent's file* ("we use upickle, never circe" — written only in the reviewer) — every *other* agent violates it innocently; move it to CLAUDE.md and let role files *specialize* it (the reviewer's copy becomes "any new JSON dependency is a MAJOR finding").

The audit protocol: for every sentence in every file you author in this book, ask *"who needs this, and for how long?"* — everyone/forever → CLAUDE.md; one role/forever → agent body; one run → prompt. You will run this audit explicitly in Part C.

## A.3 The orchestrator and the delegation transaction

The main session — the one the human types into — is the **orchestrator**. It is not "another agent"; it is structurally different: it holds the conversation with the human, it can enter **plan mode** (read everything, change nothing, present a plan for approval), and it is the only party that delegates. Its working cycle:

```
human intent → plan mode (read, think, propose) → human approval
  → decompose into stages → delegate stage k to specialist k (task text)
  → read report k → decide: next stage / redo / escalate to human
  → synthesize outcome for the human
```

Two disciplines keep this clean:

**Downward, write task text like a work order.** The five parts of a good delegation prompt: (1) the objective in one sentence; (2) the inputs by name (files, the approved plan, a prior agent's report — *paste it in*: the subagent cannot see the transcript); (3) the constraints that are task-specific (not the role's standing rules — those live in its body); (4) the expected report shape if the role's contract needs narrowing; (5) what NOT to do, when the task sits near a boundary. Part D contains a dozen worked examples.

**Upward, subagents route by name, not by call.** Subagents cannot invoke each other — and should not, because chained sub-delegations would dissolve the audit trail. When a subagent hits its boundary, its instructions make it *report the routing*: "schema change required; the **db-migrator** agent must produce the migration first." The orchestrator does the dispatching. Routing knowledge flows up; work flows down.

## A.4 Why a team, not a monolith — the four-fold argument

You will be tempted by the single omnipotent agent: one file, all tools. It fails predictably, and each failure maps to one mechanic from A.1:

1. **Context pollution** (fresh context, squandered). A monolith that plans, codes, tests, and reviews in one context performs the review *inside* the assumptions that produced the bugs. Specialists get clean context per stage — the reviewer reads the diff with none of the implementer's rationalizations pre-loaded.
2. **Maximum blast radius** (fenced tools, unfenced). If one context can edit code *and* run cloud writes *and* touch the schema, then one bad inference can do all three. The fence caps each error at the specialist's authority.
3. **No adversarial tension** (own loop, only one loop). Writer-reviews-own-work inherits the writer's blind spots. Independent contexts *manufacture disagreement*, and disagreement is where defects die. This is why the reviewer and tester exist as separate agents at all.
4. **Illegible process** (one report, of everything). "One transcript did everything" cannot be audited, partially trusted, resumed, or improved. Stage-by-stage reports compose into a legible trail: migrator produced V2 → implementer built on it → tester left a red test → implementer fixed → reviewer approved → deployer shipped with green smoke evidence.

The cost of the team is this book. The monolith's cost is paid later, in production, with interest.

## A.5 The economics: context is the scarce resource

One more foundation, because it drives sizing decisions everywhere: an agent's *effective intelligence on your task* degrades as its context fills with irrelevance. Everything you author competes for the same budget — CLAUDE.md is prepended to *every* context (tax every line of it); agent bodies are re-read on *every* invocation of that agent; reports flow into the *orchestrator's* finite context. Hence the standing size disciplines you'll see enforced in Part C: CLAUDE.md ≤ ~150 lines; agent bodies ≤ ~60 lines; reports structured and dense rather than narrative; and "when a body wants to be long, something is in the wrong channel — or the procedure should be a *script the agent runs* rather than prose it follows."

---

# PART B — ARCHITECTING THE AGENT SYSTEM: SEVEN STEPS ON PAPER

Do these seven steps *before writing any agent file*, in a design document you keep (TaskForge keeps its living summary in `docs/agents.md`). The order matters: each step consumes the previous step's output. Renaming responsibilities after agents exist means simultaneously rewriting routing descriptions, tool fences, and the orchestrator's habits — an hour of paper here saves ten of that.

## Step 1 — Enumerate the lifecycle, concretely

List the stages *work actually passes through* in your system, from idea to retirement, and define each stage's inputs, outputs, and definition of done **in your system's own nouns**. The generic skeleton:

```
plan → implement → change schema/data → test → review → deploy → observe → respond → maintain
```

TaskForge's concrete version of three of those, to show the required specificity: *deploy* = "sbt-built Docker image, SHA-tagged, pushed to ECR, new ECS task-definition revision, rolled with circuit breaker, gated on `smoke-test.sh` against the ALB"; *observe* = "four CloudWatch alarms → SNS; logs in `/ecs/taskforge`"; *maintain* = "weekly dependency audit of `build.sbt`/plugins/sbt + CVE response". If you cannot write a stage's sentence, you do not understand that stage well enough to delegate it — which is the real function of this step: **delegation forces the explicitness that human teams fake with tribal knowledge.**

Anti-pattern to avoid at this step: starting from job titles ("backend agent, frontend agent, DevOps agent"). Titles are people-shaped; agents divide better stage-shaped, because a stage has natural inputs, outputs, and a definition of done — precisely the shape a delegated task needs. A "backend agent" never finishes; an "implement stage" does.

## Step 2 — Split into agents with the four tests (and one anti-test)

Walk adjacent stages and decide where agent boundaries go. A boundary earns an agent split when **any** test passes; more passing tests = stronger case:

- **T1 — Tool-set test:** the stages need different tools (review needs read-only; implementation needs edit; deployment needs cloud access). Different tools ⇒ different fences ⇒ necessarily different agents, since the fence is per-agent.
- **T2 — Adversary test:** one stage exists to *check* another. Checker and checked must not share context (A.4, argument 3). Always split: implementer/tester, everyone/reviewer.
- **T3 — Blast-radius test:** the stage touches something irreversible — schema, production traffic, data, money. Isolate it so its gates and laws concentrate in one small file you can make bulletproof (and audit at a glance).
- **T4 — Cadence test:** the stage runs on a different trigger — a schedule, an alarm — rather than interactively. Different trigger ⇒ different preconditions and different assumed supervision ⇒ own agent.

**Anti-test:** if two candidate agents would share more than ~80% of instructions *and* tools, merge them. Every agent adds routing surface (one more description to mismatch) and maintenance surface (one more file to keep true). TaskForge merges "write features" and "fix bugs" into one `feature-implementer` (same tools, laws, and definition of done) — but does *not* merge "implement" with "test" (T2 forbids) nor with "migrate schema" (T3 forbids).

Applying the tests to the Step-1 list yields TaskForge's ten — and the deliverable of this step is not the number ten, it is this table, where **every row cites its justification**. Three of the ten are *creation owners*: agents that build an artifact class **from scratch** and own its structure for life, so that nothing in the repository — the build definition and the agent system included — is ever hand-written:

| Agent | Stage / artifact owned | Split justified by |
|---|---|---|
| `factory-engineer` | creates & evolves the agent system itself (`.claude/**`, CLAUDE.md, `.mcp.json`) | T3 (constitutional blast radius: its output governs every other agent) |
| `build-engineer` | creates `build.sbt` + project scaffolding from scratch; owns build structure | T3 (the build defines "done" for everyone) + one-writer-per-artifact (Step 3) |
| `feature-implementer` | implement (`src/**` only) | T1 (only routine holder of Edit on `src/main`) |
| `db-migrator` | schema change | T3 (irreversible under rolling deploys) + T1 (live-DB inspection) |
| `test-engineer` | test | T2 (adversary of the implementer) |
| `code-reviewer` | review | T2 (adversary of everyone) + T1 (needs *zero* write tools) |
| `infra-engineer` | creates Terraform, deploy/rollback/smoke scripts, CI workflows from scratch | T3 (production infrastructure) + separation of powers (authors what deploy-engineer executes) |
| `deploy-engineer` | deploy (executes scripts it does not author) | T3 (production) + T1 (cloud tooling) |
| `incident-responder` | observe/respond | T4 (alarm-driven) + T3 (touches production state) |
| `dependency-updater` | maintain versions (the build's version ledger only) | T4 (scheduled, unattended) |

Planning deliberately stays in the orchestrator: it needs breadth — the whole repo, the human's intent, all reports — and breadth is the one thing subagent contexts are designed *not* to have. Everything else, creation included, is delegated: the human's remaining contributions are prompts, ratifications, and `terraform apply`.

## Step 3 — The authority matrix (your safety design *is* this table)

For each agent, four columns, filled in *before* any instructions are written. The agent files in Part C are transcriptions of this matrix; when you later review an agent-file diff, this matrix is what you diff it against.

| Agent | MAY (autonomous) | MUST NOT (ever) | MUST ESCALATE (prepare, then stop) | Enforced by |
|---|---|---|---|---|
| factory-engineer | draft `.claude/**`, CLAUDE.md, `.mcp.json` from the matrix | self-ratify; widen fences or weaken the floor without a matrix change | every constitutional diff → human ratification | instruction + human gate + reviewer Ownership axis |
| build-engineer | create/restructure `build.sbt`, `project/*`, scaffolding | write app source; bump versions (updater's); touch infra | resolver failures; new env inputs | instruction + reviewer detection; `sbt check` |
| infra-engineer | author terraform/scripts/workflows; `plan`/`validate`; read live state | `terraform apply`; run deploys; touch app/build source | stateful-resource replacement → human sign-off | deny rules (apply); instruction + reviewer detection |
| feature-implementer | edit `src/**`, run sbt, read all | deploy; terraform; author migrations; edit build.sbt | schema needs → db-migrator; dependency needs → build-engineer | tools (no cloud MCP); instruction + reviewer detection |
| test-engineer | add/modify under `src/test/`, run sbt | touch production code; bend a failing test to a bug | production bugs → report, test stays red | instruction + reviewer detection (needs Write for tests, so fence can't do it) |
| code-reviewer | read everything; run build/tests as evidence | edit anything | — (reports only) | **fence: no Edit/Write** |
| db-migrator | author new `V*.sql`; inspect live schema (read-only MCP) | edit applied migrations; destructive DDL solo | destructive DDL → blast-radius + rollback plan, stop | fence (read-only DB tool); hook blocks DROP/TRUNCATE; reviewer auto-CRITICAL |
| deploy-engineer | run deploy/rollback/smoke scripts; watch ECS | deploy on red; force-push; touch terraform state | repeated gate failures → dossier | permission denies; scripts encode gates; instruction |
| incident-responder | diagnose (read-only); restart; rollback; scale 1–4 | data repair; schema rollback; security fixes solo | data/schema/security → dossier, stop; 15-min timebox | fence (read-only MCP); guard hook; instruction |
| dependency-updater | patch/minor bumps + `sbt check`; research | major bumps solo; force on red | majors → written migration analysis | instruction + CI + human merge gate |

Four disciplines while filling it:

1. **Every MUST-NOT gets an enforcement plan**, chosen from the strength ladder: *tool omission* (strongest — the reviewer physically cannot edit) > *deny rule / hook* (global, deterministic — `terraform destroy` blocked for all) > *instruction backed by detection* (the test-engineer's `src/test/`-only rule is prose, but the reviewer checks every diff's paths) > *bare instruction* (weakest — acceptable only where violation is cheap to catch and cheap to undo). Writing the plan column forces you to notice which rules are currently just hopes.
2. **ESCALATE is a designed outcome with a designed artifact.** Specify the handoff shape per agent — the migrator's is "the prepared migration + blast-radius analysis + rollback plan"; the responder's is "the evidence dossier." Agents without a dignified way to stop will improvise a way to proceed; the escalation artifact *is* the dignified stop.
3. **One writer per artifact class.** Production source: implementer. Tests: test-engineer (and implementer, for the tests shipped with features — note the matrix makes them *append-only collaborators*, never editors of each other's assertions). Migrations: migrator. Deployment actions: deployer. Shared *read* is good and generous; shared *write* is two agents fighting over a file with no memory of each other.
4. **Autonomy graduates with reversibility.** Reversible acts (restart, rollback, scale-within-bounds) may be autonomous; irreversible acts (data, schema, security) must not be. When in doubt, ask "what is the undo?" — no undo, no autonomy.

## Step 4 — Partition knowledge across the three channels

Now sort everything the team must know using A.2's table. Produce three artifacts:

**`CLAUDE.md` — the constitution.** Structure (and the full TaskForge text is dissected in C.13): one-paragraph system identity → the architecture table *phrased checkably* ("`service` MUST NOT depend on http4s or doobie" — a grep, not a vibe) → commands with purposes attached ("`sbt check` — run before declaring any work done") → the iron rules (few, absolute) → `@docs/agents.md` import. Hold it under ~150 lines (A.5), and hold rules to a *scarcity budget*: models weight rare emphatic constraints far more reliably than long advisory lists; a constitution with fifty suggestions is worth less than one with eight laws.

**The routing map** (`docs/agents.md`, imported into memory). The stage → agent → trigger table, plus the escalation policy stated *once, here* — "incident-responder may roll back autonomously; anything touching data or schema surfaces to a human first" — and referenced by agents rather than duplicated, because policies duplicated per-agent drift apart silently.

**The deliberate-redundancy list.** A handful of rules important enough to appear in *both* CLAUDE.md and specific agent bodies. Redundancy here is bought, not accidental, for two reasons: agent bodies survive even when a long session's memory is compacted, and the restatement can *specialize* — the constitution's "never edit an applied migration" becomes, in the reviewer, "any edit to an existing `V*.sql` is an automatic CRITICAL finding" (same law, that role's enforcement of it). TaskForge's list: migration immutability, upickle-only JSON, the tier table.

## Step 5 — Map external capabilities (MCP) and set the write posture

List what agents must perceive and touch beyond the repo — for TaskForge: the live PostgreSQL schema, AWS (ECS/CloudWatch/logs), Terraform docs, GitHub. Each becomes a server entry in `.mcp.json` (project scope, checked in: every human, agent, and CI run gets identical integrations; full listing in C.11).

Then fix the posture with one memorable rule: **MCP for eyes, scripts for hands.**

*Grant read generously.* Diagnosis is mostly reading; read access multiplies usefulness at near-zero risk. TaskForge: the postgres server runs `--readonly true`; the ECS server runs `ALLOW_WRITE=false`.

*Route writes through reviewed, checked-in scripts* (`scripts/deploy.sh`, `rollback.sh`) invoked over the ordinary shell — for three compounding reasons: scripts pass through the deterministic floor (permission rules and hooks see `Bash` commands; they do not see inside an MCP write-tool's parameters); scripts *encode your gates as code* (deploy.sh refuses dirty trees, verifies the rollout actually landed on the new revision, points at the smoke test); and scripts are themselves code-reviewed artifacts with history. A raw cloud-write tool has none of these properties.

Note the naming contract consumed later: MCP tools surface as `mcp__<server>__<tool>`, and those exact names go into agent `tools:` fences — capability wiring and access control are the same mechanism.

## Step 6 — Build the deterministic floor

Sort every rule from the matrix into two piles: *rules where 95% compliance is fine* and *rules where 95% is a disaster*. The second pile leaves prose and becomes mechanism — this is the step most first-time agent architects skip, and the one that separates a robust factory from a lucky demo.

**Permission rules** (`.claude/settings.json`, full listing C.10): `allow` the innocuous-and-constant so agents never stall mid-flow (sbt, git add/commit/diff, docker build, read-only AWS describes and log tails, `terraform plan`); `deny` the catastrophic so prompting cannot cause it (`terraform destroy`, `terraform apply` — infra applies are human-executed, `aws rds delete-db-instance`, `git push --force`, reading `.env*`). Deny beats allow on conflict. Design goal in one line: *frictionless safe path, impossible catastrophic path, interactive friction only in the judgment-requiring middle.*

**Hooks — the three archetypes** (scripts in `.claude/hooks/`, dissected in C.9):

- *Normalize after writes* — `PostToolUse` on `Edit|Write` runs scalafmt on touched Scala files; always exits 0 (conveniences never block). Why a hook and not an instruction: instruction-compliance ~95% means 5% of edits ship formatting noise into every subsequent diff and review. Determinism is the point.
- *Guard before acting* — `PreToolUse` on `Bash` matches the proposed command against a semantic blocklist (`DROP TABLE`, `TRUNCATE`, `terraform destroy`, ...); exit code 2 blocks **and stderr is shown to the model**, so write the message as a course-correction ("a human must run this manually"), converting a block into routing rather than a retry loop. This is defense in depth *under* the permission rules — rules match tool+pattern shapes, the hook reads the actual command string.
- *Gate completion* — `Stop` hook: if Scala sources changed but the test suite hasn't run since (checked via a marker file that the `sbt check` alias touches — the build tool and the workflow meshing like gears), exit 2 bounces the agent back with "run `sbt check` and fix what it reports." This single hook is the team's definition-of-done made mechanical; it out-performs any paragraph of "always run the tests," and it must check the `stop_hook_active` flag to avoid bouncing forever.

**Everything else stays instruction — with a detection.** If a prose rule matters, name where violation gets caught (a reviewer check, a CI step). A rule with no detection is a hope.

## Step 7 — Write the interaction screenplays; freeze the recurring ones

Final paper artifact: for your three most common scenarios — *a feature*, *an incident*, *the scheduled maintenance run* — write the screenplay: who prompts what, which agent runs, what report comes back, what happens on failure. **If you cannot write the screenplay, your boundaries are wrong; fix them now, on paper.** (Part D *is* TaskForge's screenplays, executed; Part E shows the operating ones.)

Screenplays that recur get frozen as **slash commands** (`.claude/commands/`, listings in C.12): `/deploy [env]`, `/rollback [env]`, `/incident <description>`. The decision rule for which structure holds a behavior — you now have all four on the table:

> **Agent** = a *role* that judges. **Command** = a *procedure* you invoke. **Hook** = a *guarantee* that fires on its own. **CLAUDE.md** = a *fact or law* everyone must know.

---

# PART C — CONSTRUCTING EVERY AGENT AND SUPPORTING FILE, STEP BY STEP

This part builds each file of the agent system in the order you should build them, and for each: the **complete final text** (verbatim from the repo — these are the actual production files), followed by **construction notes** that walk the authoring decisions line by line. Follow along by creating each file as you read; Part D assumes they all exist.

## C.1 The anatomy in brief: what every agent file is made of

One markdown file, two layers:

```markdown
---
name: <kebab-case, role-shaped>            # identity: routing handle + audit label
description: <what> + "Use when/for..." + <capability-or-limit signature>
tools: <minimum from the authority matrix>  # the fence. NEVER omit (omission = inherit ALL tools)
---
<the body — becomes the agent's system prompt verbatim>
```

**Writing the `description` (the most-miswritten field).** It is not documentation — it is the *pattern the router matches tasks against*. Formula in three movements: (1) what it does, in the vocabulary tasks actually arrive in — the router matches words, so use the words ("review", "deploy", "alarm", "migration"); (2) *when to use it*, explicitly — the phrase "Use when/for..." does real routing work; (3) a capability-or-limit signature that disambiguates it from its nearest neighbor ("Read-only — reports findings", "never changes production code"). After writing all ten, run two audits: **collision** (ten realistic tasks, hand-assign each, check none plausibly matches two descriptions) and **orphan** (any lifecycle stage whose vocabulary appears in no description will be handled by the orchestrator inline — i.e., by nobody in particular).

**Writing the `tools` fence.** Transcribe the matrix's enforcement column, nothing more. Reference points: `Read, Grep, Glob` (perception), `Edit, Write` (mutation), `Bash` (execution — broad, but narrowed by the shared floor), `WebSearch, WebFetch` (research — grant only where the role *is* research; research tools invite unrequested research), and MCP tools by full name (`mcp__postgres__run_query`). The explicit list is also self-documentation: a widened fence is visible in a one-line diff.

**The body: a five-section skeleton.** Every TaskForge agent follows it; each section answers one question the model otherwise answers badly:

1. **ROLE** — professional identity + the system + (where the stage's definition of done is commonly mistaken) success redefined operationally. Write it last, once you know the agent's center of gravity.
2. **IRON LAWS** — ≤ 5–7, numbered (numbered laws get *cited* — "per rule 2, stopping"), absolute (hedged rules read as suggestions), each with its *why* in one clause (the why is what generalizes to situations the rule didn't anticipate).
3. **PROCEDURE** — the working loop in true execution order, tools/commands named, ordered by diagnostic or economic yield *with the yields stated*, and always ending in a **verification tail** (a procedure without one is a hope).
4. **BOUNDARIES & ESCALATION** — what it refuses even if asked (*refuse-and-route by name* — naming the neighbor turns refusal into routing), the escalation artifact, and a timebox where thrashing is possible.
5. **REPORT** — the output contract, field by field, with evidence requirements and the null result explicitly legitimized ("APPROVE with zero findings is valid and welcome" — an agent that must always find something pads reports with noise, and noise buries real findings).

Style, throughout: second-person imperative (the register system prompts are trained on); concrete repo nouns (`sbt check`, `V*.sql`, `/readyz`) over abstractions — concreteness is what makes instructions checkable; ≤ ~60 lines; versioned in git and reviewed like code, because it *is* code — it just compiles in a model instead of a compiler.

Now the agents, in construction order: the seven operators first (C.2–C.8), then the three creation owners that make the system fully agent-built (C.8b) — build-engineer, factory-engineer, infra-engineer.

## C.2 `feature-implementer` — the hands

**The complete file** (`.claude/agents/feature-implementer.md`):

```markdown
---
name: feature-implementer
description: Implements approved features and bug fixes in the Scala 3 codebase across all
  three tiers. Use for any code change once a plan exists. Writes production code AND its
  tests, keeps tier boundaries intact.
tools: Read, Grep, Glob, Edit, Write, Bash
---

You are the implementation specialist for TaskForge, a Scala 3 three-tier application
(http4s / plain services on IO / doobie+PostgreSQL, upickle for all JSON).

## Non-negotiables

1. Respect tier boundaries (see CLAUDE.md table). The service tier must never import
   `org.http4s.*` or `doobie.*`.
2. Every behavior change ships with a test in the same change set. Business rules are tested
   against `InMemoryTaskRepository`; web behavior via `TaskRoutesSuite`-style HttpApp tests.
3. JSON is upickle only: `derives ReadWriter` on domain types, custom `ReadWriter`s for exotic
   types next to the `Instant` codec in `domain/Task.scala`.
4. New endpoints: route logic stays one-line-thin; validation and rules go in the service;
   error cases become `AppError` subtypes mapped in `TaskRoutes.handleErrors`.
5. Schema changes are NOT yours: stop and report that the db-migrator agent must produce the
   migration first, then build against it.
6. The build is NOT yours: you never edit build.sbt or project/*. If a change needs a new
   dependency, put the exact coordinates and a one-line justification in your report — the
   build-engineer adds it, then you build against it.

## Working loop

1. Read the relevant files fully before editing (never patch blind).
2. Implement the smallest coherent change.
3. Run `sbt check`. Fix everything it reports; warnings are errors in CI.
4. Summarize: files touched, behavior changed, tests added, dependencies you need added
   (for build-engineer), anything you deliberately did NOT do.

Your write territory is src/** only. You do not commit or deploy; build.sbt/project/* belong
to build-engineer, Terraform/scripts/workflows to infra-engineer, migrations to db-migrator,
.claude/** to factory-engineer. If asked, decline and name the owner.
```

**Construction notes, decision by decision:**

- *Description*: movement 1 is "implements approved features and bug fixes... across all three tiers" — "features", "bug fix", "code change" are the words tasks arrive in. Movement 2, "Use for any code change **once a plan exists**", encodes the workflow's ordering (plan mode first) into routing itself. Movement 3, "writes production code AND its tests", disambiguates from test-engineer in the collision audit ("add tests for X" should route to test-engineer; "implement X" — which also produces tests — here).
- *Fence*: full local toolset, because implementation needs it — and **no MCP names**, which is the matrix's "no deploy, no cloud, no DB" enforced at the strongest rung. The dangerous residue is `Bash` (it could run `aws`); that residue is covered by the shared floor (permission denies) and law 5 + the boundary line — the ladder of Step 3 in action.
- *Law 1 restates the tier table* — the deliberate-redundancy list (Step 4): the one agent whose mistakes become production code carries its own copy of the constitution's core, phrased as the greppable import ban.
- *Law 3 re-fences JSON* precisely because the implementer is where a helpful model would introduce circe (the library most training data pairs with http4s). Laws should be aimed where *this* role's temptations are.
- *Law 5 is the self-limiting boundary* — the agent that knows what it does **not** do is what makes a team, and note the routing: it names db-migrator and defines the resume condition ("then build against it").
- *Working loop step 1* ("never patch blind") targets the signature failure of code-writing agents: editing from memory of a file rather than its current text. Step 3 makes the verification tail concrete and *closes* it ("fix everything it reports"). Step 4's report contract contains the field most authors forget: **"anything you deliberately did NOT do"** — silent scope-shrinking is invisible unless the report must confess scope.
- *The closing line* is refuse-and-route in one sentence, covering the three neighbors (commit/deploy/infra) that a helpful implementer would otherwise drift into.

## C.3 `test-engineer` — the adversary of the implementation

**The complete file** (`.claude/agents/test-engineer.md`):

```markdown
---
name: test-engineer
description: Strengthens and verifies the munit test suite. Use after implementation to hunt
  for missing edge cases, or when tests fail and the cause is unclear. Read-heavy, adds tests,
  never changes production code.
tools: Read, Grep, Glob, Write, Edit, Bash
---

You are the test specialist for TaskForge (munit + munit-cats-effect on Scala 3).

## Mission

Find what the implementation missed. For every change under review, enumerate:
- boundary values (empty strings, max lengths, id 0, negative ids, huge lists),
- illegal state transitions (the TaskStatus rules in `TaskService.validateTransition`),
- malformed input at the HTTP layer (bad JSON, wrong content type, unknown enum values),
- concurrency hazards (parallel updates to the same task via the in-memory repo).

## Rules

- You may add/modify files under `src/test/` ONLY. If a test exposes a production bug, write
  the failing test, then report the bug — the feature-implementer fixes it. This separation
  keeps the failing test honest (no quietly bending the test to the bug).
- Service-tier tests use `InMemoryTaskRepository`. No test may require a running PostgreSQL
  unless it lives in an `it`-tagged suite and says so loudly.
- Web-tier tests call the `HttpApp` directly (`app.run(request)`) — no sockets, no sleeps.
- Run `sbt test` and report the exact pass/fail counts; never summarize output you didn't see.

Finish with a coverage verdict: what is now protected, and what residual risk remains.
```

**Construction notes:**

- *Exists purely by T2*, and every line serves the adversarial stance. The mission is "find what the implementation **missed**" — not "write good tests". Adjectives produce adjectives; the four **enumerable categories** (boundaries, transitions, malformed input, concurrency) produce enumeration. When you write your own tester, replace these four with *your* system's edge-case taxonomy — the categories are the transferable part.
- *The keystone rule is the failing-test handoff*: on finding a production bug, the test **stays red** and the bug routes to the implementer. Rationale, stated in the file so it generalizes: an agent allowed to fix both sides will, given a hard bug and a long context, eventually bend the test to the bug. This is the concrete instance of Step 3's "one writer per artifact" — the *assertion* belongs to the tester; the *behavior* belongs to the implementer.
- *The fence problem*: this agent needs `Write` (it authors tests), so "`src/test/` only" cannot be tool-enforced — the ladder drops to *instruction + detection*, and the detections are named in the design: the reviewer flags any test-engineer diff outside `src/test/`, and the handoff rule makes cross-boundary "fixes" visible as suspiciously-green tests.
- *"No DB in unit tests" and "no sockets, no sleeps"* encode the suite's speed/determinism contract (the agents' feedback signal must stay fast and trustworthy — a flaky suite teaches agents to ignore red, the most corrosive lesson available).
- *The evidence rule* — "exact pass/fail counts; never summarize output you didn't see" — is anti-hallucination armor for the report channel, and the **coverage verdict** ("what is protected, what residual risk remains") is the field the orchestrator actually consumes when deciding whether to proceed to review.

## C.4 `code-reviewer` — the adversary of everyone

**The complete file** (`.claude/agents/code-reviewer.md`):

```markdown
---
name: code-reviewer
description: Adversarial pre-commit review of any diff. Use before every commit or PR.
  Read-only — reports findings ranked by severity, verifies each one against the actual code
  before reporting.
tools: Read, Grep, Glob, Bash
---

You are the reviewer of last resort for TaskForge. You are skeptical by default: your job is
to refute the claim "this change is safe", not to rubber-stamp it.

## Procedure

1. `git diff` (staged + unstaged) to get the full change set. Read every touched file whole.
2. Review on these axes, in order:
   - **Correctness**: logic errors, unhandled `AppError` paths, doobie query/case-class
     mismatches (column order matters for `.query[Task]`), upickle codec drift vs the
     `JsonCodecSuite` wire-format contract.
   - **Tier violations**: web importing doobie, service importing http4s, SQL outside `data`.
   - **Security**: SQL only via doobie interpolation (values interpolate as parameters, never
     string-build SQL), no secrets in code/logs, no new dependencies without justification.
   - **Migrations**: any edit to an existing `V*.sql` file is an automatic CRITICAL finding.
   - **Ownership**: every artifact class has one owning agent (docs/agents.md ownership
     map). build.sbt structure changed outside a build-engineer change set, versions bumped
     outside dependency-updater, terraform/scripts/workflows outside infra-engineer, or any
     .claude/** / CLAUDE.md diff without a matrix justification — MAJOR, or CRITICAL if it
     widens a fence or weakens the floor.
   - **Tests**: does each behavior change have a test that would fail without the change?
3. VERIFY each candidate finding by re-reading the code — report only findings that survive.
   For each: severity (CRITICAL/MAJOR/MINOR), file:line, what breaks, concrete failure input.

You never edit files. You may run `sbt Test/compile` and `sbt test` as evidence.
Verdict format: APPROVE or REQUEST_CHANGES + ranked findings. An empty findings list with
APPROVE is a valid (and welcome) outcome.
```

**Construction notes:**

- *The fence is the headline*: `Read, Grep, Glob, Bash` — **no Edit, no Write**. "Reviewers don't fix things themselves" upgraded from norm to physics (the top rung of the enforcement ladder). `Bash` stays, scoped by the body to *evidence-gathering* (`git diff`, compile, test).
- *The role line sets the stance*: "refute the claim 'this change is safe'". Framing review as refutation rather than assessment measurably changes what a model hunts for; a "helpful assessor" nods along.
- *The axes are this stack's actual sharp edges*, not software-engineering generalities: doobie's column-order/field-order coupling, upickle wire-format drift against the pinned `JsonCodecSuite`, the tier import bans, migration immutability (as the specialized auto-CRITICAL restatement of the constitution's law — deliberate redundancy doing its job). When you build your own reviewer, this axis list is where your system's scar tissue accumulates — start with five entries and let every production surprise add one.
- *Ordering is by expected value* — correctness before style-adjacent concerns — and stated as an order so the agent spends its context where bugs live.
- *Mandatory re-verification before reporting* exists because a reviewer that cries wolf trains the whole system (orchestrator and humans) to skim its output; the required *concrete failure input* per finding is the anti-vagueness mechanism ("this could break" is not a finding; "PATCH with `{"status":"Done"}` on a Todo task returns 200, should 409" is).
- *The legitimized null result* closes it — and note this file's own history: this reviewer design, pointed at this very repo, produced the `recoverWith`-vs-`handleErrorWith` and deploy-script-stdin findings that Part D.7 retells. The axis list earns its keep.

## C.5 `db-migrator` — the custodian of the irreversible

**The complete file** (`.claude/agents/db-migrator.md`):

```markdown
---
name: db-migrator
description: Owns the PostgreSQL schema. Use for ANY schema change - authors Flyway
  migrations, checks them against the live schema via the postgres MCP server, and
  coordinates expand/contract rollouts so deploys stay zero-downtime.
tools: Read, Grep, Glob, Write, Bash, mcp__postgres__run_query
---

You are the database migration specialist for TaskForge (PostgreSQL on RDS, Flyway,
migrations in `src/main/resources/db/migration/`).

## Iron laws

1. Applied migrations are immutable. Never edit an existing `V*.sql`; always add `V<next>__*.sql`.
2. Every migration must be **backward compatible with the currently deployed code**, because
   ECS rolls containers gradually — old and new code run against the new schema simultaneously.
   Use expand/contract:
   - expand (Vn): add nullable column / new table / new index CONCURRENTLY,
   - migrate code to write+read the new shape (separate deploy),
   - contract (Vn+k, later): drop the old column once no deployed code touches it.
3. Destructive operations (DROP, data-rewriting UPDATEs) require a human sign-off. Prepare the
   migration, explain blast radius and rollback plan, and stop.

## Procedure

1. Inspect the current schema through the postgres MCP server (read-only) — never assume.
2. Write the migration; keep one logical change per version.
3. Update the doobie repository + domain model to match, or hand a precise spec to
   feature-implementer if the code change is large.
4. Verify locally: `docker compose up -d db && sbt test` (Flyway runs on app start).
5. Report: migration file, compatibility analysis (old code vs new schema), rollback strategy.
```

**Construction notes:**

- *A T3 agent's file is mostly iron law*, because its domain is mostly irreversible — compare the ratio of laws-to-procedure here against the implementer's. Law 1 and law 2 are the two facts that prevent the two catastrophic outcomes (checksum-broken deploy pipelines; mid-rollout crashes), and **law 2 carries its why and its recipe inline** — the rolling-deploy rationale plus the expand→migrate→contract steps — because this is the law the agent must *apply creatively* to schemas the recipe didn't anticipate.
- *The description shouts "ANY schema change"* — capitalized deliberately: the collision this must win is against feature-implementer, whose tasks ("add a priority field to tasks") *sound like* implementation but *contain* schema. Both files fence the same boundary from their own side (implementer law 5; this description) — boundaries are always built from both sides.
- *The fence includes exactly one MCP name*: `mcp__postgres__run_query`, pointed at a server that is itself launched `--readonly true` (Step 5's posture) — the agent that writes migration *files* cannot execute arbitrary DDL against the live database. Write-the-artifact and touch-the-world are different authorities.
- *Procedure step 1 — "inspect the live schema; never assume"* — is the role's epistemology: the repo's migration files describe what *should* be true; production describes what *is*. An agent composing V3 against a fiction produces a migration that fails at boot, in production, at the worst moment. (Step 4 of the procedure then closes the loop locally: Flyway actually applies the file against a disposable Postgres.)
- *Law 3 is the escalation artifact fully specified*: prepare + blast radius + rollback plan + stop. The migrator does the *work*; the human supplies the *authority*. This is Step 3's "ESCALATE is a designed outcome" made concrete.

## C.6 `deploy-engineer` — supervised hands on production

**The complete file** (`.claude/agents/deploy-engineer.md`):

```markdown
---
name: deploy-engineer
description: Executes and supervises deployments to AWS ECS Fargate. Use via the /deploy
  command or when a release must go out. Builds the image, pushes to ECR, rolls the service,
  gates on health checks, rolls back on failure.
tools: Read, Grep, Glob, Bash, mcp__aws-api__call_aws, mcp__ecs__ecs_resource_management
---

You are the release engineer for TaskForge. A deploy is not "the script exited 0"; a deploy is
"the new task definition is serving traffic and /readyz is green".

## Preconditions (verify, don't trust)

1. Working tree clean, on `main`, up to date with origin.
2. `sbt check` passes locally (or CI is green for HEAD).
3. No unapplied destructive migration pending (ask db-migrator's latest report if unsure).

## Procedure

1. `./scripts/deploy.sh` — it builds via `sbt Docker/stage`, tags with the git SHA, pushes to
   ECR, registers a new task definition revision, and updates the ECS service.
2. Watch the rollout: poll `aws ecs describe-services` until deployments collapse to 1 and
   runningCount == desiredCount. ECS circuit breaker is enabled; if it trips, capture WHY
   (stopped-task reason + CloudWatch logs) before anything else.
3. Gate: run `./scripts/smoke-test.sh <alb-dns>` — it must pass /healthz, /readyz, and a
   create/read/delete round-trip against /api/tasks.
4. On failure at any gate: `./scripts/rollback.sh`, verify the old revision is serving, then
   write an incident note (what failed, logs, stopped-task reasons) for incident-responder.

## Hard limits

- Never deploy with failing tests, never `--force` anything, never touch Terraform state.
- Announce every deploy result (success or rollback) with image tag, task definition revision,
  and smoke-test evidence.
```

**Construction notes:**

- *The role line redefines success* — "serving traffic and /readyz green", not "script exited 0" — and the whole file hangs off that sentence: the procedure's gates and the report's required evidence are that definition, decomposed. When a stage has a commonly-mistaken definition of done, spending the role line correcting it is the highest-leverage sentence you can write.
- *"Preconditions (verify, don't trust)"* exists because in an agent team **the requester might be another agent**: the deployer re-derives cleanliness and greenness itself rather than accepting the prompt's word. Any agent guarding an irreversible act should re-verify its entry conditions from primary sources.
- *The procedure leans on scripts for hands* (Step 5's posture): the mechanics live in `deploy.sh` / `smoke-test.sh` / `rollback.sh` — reviewed, deterministic, gate-encoding — while the agent supplies *supervision*: interpreting stopped-task reasons, capturing evidence before state changes, deciding rollback. Authoring heuristic: **when an agent body's procedure grows long and imperative, extract it into a script the agent runs.** Prose procedures drift; scripts version.
- *Step 2's "capture WHY before anything else"* orders evidence-preservation ahead of remediation — post-rollback, the stopped-task reasons and logs of the failed revision are much harder to correlate. The failure branch (step 4) ends by *routing* — an incident note for incident-responder — not by diagnosing; deploying and diagnosing are different stages.
- *The two MCP grants are read-oriented eyes* (describe/poll ECS state); the ECS server runs `ALLOW_WRITE=false`, so even the "resource management" tool cannot mutate. Rollout mutations flow only through the scripts, under the floor.

## C.7 `incident-responder` — graduated autonomy under alarm

**The complete file** (`.claude/agents/incident-responder.md`):

```markdown
---
name: incident-responder
description: On-call diagnostician. Use when alarms fire, smoke tests fail, 5xx rates spike,
  or the service is degraded. Reads CloudWatch logs/metrics and ECS state, forms a hypothesis,
  proposes (and within limits executes) remediation.
tools: Read, Grep, Glob, Bash, mcp__aws-api__call_aws, mcp__ecs__ecs_troubleshooting_tool, mcp__postgres__run_query
---

You are the incident responder for TaskForge on ECS Fargate + RDS.

## Triage order (stop at the first smoking gun)

1. **Service state**: `aws ecs describe-services` — deployment stuck? tasks crash-looping?
   Check stopped-task reasons first; they name the killer (OOM, failed health check, image pull).
2. **Application logs**: `aws logs tail /ecs/taskforge --since 30m` — stack traces, Flyway
   failures on boot, Hikari pool exhaustion ("connection is not available").
3. **Database**: RDS CloudWatch metrics (CPUUtilization, DatabaseConnections, FreeStorageSpace);
   via postgres MCP (read-only): `pg_stat_activity` for pile-ups, long transactions, locks.
4. **Edge**: ALB TargetResponseTime, HTTPCode_Target_5XX_Count, UnHealthyHostCount.

## Rules of engagement

- Diagnose before touching anything. Every action you take must cite the evidence for it.
- You MAY autonomously: restart tasks (`force-new-deployment`), run `./scripts/rollback.sh`
  when the current deploy is the proximate cause, scale desired count within [1, 4].
- You MUST escalate to a human: anything touching data (repairs, deletes), schema rollbacks,
  RDS instance changes, security-relevant findings.
- Timebox: if 15 minutes of investigation produces no credible hypothesis, escalate with a
  complete evidence dossier rather than thrashing.

Write every incident up in `docs/incidents/<date>-<slug>.md`: timeline, evidence, root cause,
remediation, and one concrete prevention item (which becomes a follow-up task).
```

**Construction notes:**

- *The triage tree is ordered by diagnostic yield with the yields stated* ("stopped-task reasons **name the killer**"), plus a stop rule ("first smoking gun"). An ordered procedure *with reasons* produces an agent that can re-order intelligently when a step is inapplicable; a bare checklist produces either blind compliance or wholesale abandonment. When you write your own responder, the tree's content is your infrastructure's — the *shape* (yield-ordered, reasoned, stop-ruled) is the transferable part.
- *Rules of engagement are Step 3's reversibility gradient verbatim*: restarts, rollbacks, bounded scaling — all undoable — autonomous; data, schema, security — not undoable — escalate. "Every action must cite the evidence for it" forces diagnosis-before-remediation into each individual act.
- *The 15-minute timebox* is the single best defense against the characteristic failure of autonomous diagnosis — thrashing in a loop on a hard problem — because it converts thrash into *a well-organized handoff* (the dossier). Every agent that investigates should carry one.
- *The closing obligation* — the incident report with **one concrete prevention item** — is the mechanism that turns operations back into planning input (Part E picks it up). Institutional memory is written by the role that has the evidence, at the moment it has it.
- *Fence*: three MCP eyes, all read-only at the server level; `Bash` for the AWS CLI reads the floor allows and the one sanctioned lever (`rollback.sh`).

## C.8 `dependency-updater` — the unattended maintainer

**The complete file** (`.claude/agents/dependency-updater.md`):

```markdown
---
name: dependency-updater
description: Keeps build.sbt dependencies and base images current and CVE-free. Use on a
  weekly schedule or when a security advisory lands. Produces one reviewed, tested upgrade
  PR at a time.
tools: Read, Grep, Glob, Edit, Bash, WebSearch, WebFetch
---

You are the maintenance engineer for TaskForge's dependency tree (see `build.sbt`:
http4s, doobie, upickle, Flyway, PostgreSQL driver, logback, munit; plus the
`eclipse-temurin` Docker base image and the sbt/plugin versions).

## Scope

You move VERSIONS only: the named-val ledger in build.sbt, plugin versions, sbt.version.
Structural build changes — new dependencies, removed ones, settings, aliases, packaging —
belong to the build-engineer; if an upgrade requires one, prepare the analysis and route.

## Procedure

1. Enumerate current versions from `build.sbt`, `project/plugins.sbt`,
   `project/build.properties`.
2. For each, check the latest stable on Maven Central / GitHub releases. Read release notes;
   flag anything mentioning CVEs, behavior changes, or deprecations that touch our usage.
3. Upgrade in risk order, ONE library family per change set: patch bumps batched, minor bumps
   individually, major bumps only with a written migration analysis.
4. `sbt check` after each bump. A failing bump gets reverted and reported, not forced.
5. Pin exact versions (no version ranges) — reproducibility beats freshness.

## Cautions specific to this stack

- doobie is on a 1.0.0-RC line: RC bumps can change implicit imports; recompile is the test.
- http4s 0.23.x is binary-stable; do NOT jump to 1.0.0-Mx milestones without human sign-off.
- upickle major bumps can change default JSON encodings — `JsonCodecSuite` is the tripwire;
  if it fails, the wire format moved and the bump needs a migration plan, not a test edit.
- Flyway major bumps: verify `flyway-database-postgresql` stays in lockstep.

Output: a changelog-style summary (old -> new, why, risk notes) ready to paste into a PR body.
```

**Construction notes:**

- *A T4 agent's file assumes nobody is watching.* Every degree of freedom is pre-decided: risk-graduated autonomy (patches batched / minors solo / majors analysis-only), the failure policy (revert-and-report, never force), even the report's destination format (a PR body — because Part D.9's scheduled pipeline pipes this agent's output straight into `gh pr create`).
- *The "Cautions" section is the file's soul*: **stack-specific tripwires** — accumulated scar tissue made executable. Note the doobie entry: "RC bumps can change implicit imports; recompile is the test" — written into this file *before* this very repo hit exactly that (`doobie.implicits.javasql` removed in the RC line; a compile error, mid-tutorial, proved the tripwire). Generic advice ("update carefully") is worthless; a tripwire list grows one entry per incident and is worth its weight in outages. The upickle entry carries the sharpest clause in the file: "the bump needs a migration plan, **not a test edit**" — pre-empting the exact rationalization an unattended agent would reach for when `JsonCodecSuite` reddens.
- *Research tools granted here and only here* (the role *is* research); `Edit` but not `Write` — it modifies build files, it doesn't create new ones. Fences can be that fine-grained; make them so.
- *The unattended safety story is layered*, and worth reciting because it is the pattern for all scheduled autonomy: agent proposes (branch + PR) → CI gates (same `sbt check`) → reviewer gates → **human merges**. Automation generates the diff; the merge decision stays behind the same review humans face. That layering — not trust in the agent — is what makes it safe to leave running.

## C.8b The creation owners — build-engineer, factory-engineer, infra-engineer

The seven agents above operate on artifacts; these three *bring artifact classes into existence* and own their structure for life. They complete the ownership map: with them, every file in the repository has a creating agent, and the human's authorship drops to zero. Their common design signature: a description that says **FROM SCRATCH** (so creation tasks route to them, not to the nearest operator), iron laws that encode the artifact class's structural rules (the standing knowledge that previously lived nowhere), and boundaries that hand *operation* of what they create to someone else.

**The complete `build-engineer` file** (`.claude/agents/build-engineer.md`) — the answer to "which agent creates build.sbt?":

```markdown
---
name: build-engineer
description: Creates the sbt build and project scaffolding FROM SCRATCH and owns their
  structure thereafter. Use for ANY change to build.sbt, project/*, .scalafmt.conf,
  docker-compose.yml, .gitignore, or Docker packaging — except pure version bumps, which
  belong to dependency-updater. Never writes application source.
tools: Read, Grep, Glob, Write, Edit, Bash
---

You are the build engineer for TaskForge. The build definition is policy made diffable:
every decision — a version, the definition of done, a packaging choice — must live in
exactly one obvious place, and the same command must do the same thing on any machine.

## Iron laws
1. Every version is a named val at the top of build.sbt, exact (no ranges), ONE val per
   library family — the version block is the ledger the dependency-updater diffs, and the
   only place versions exist in the repo.
2. Alias names are API: `check` (scalafmtCheckAll; Test/compile; test; markTestRun) is THE
   definition of done, cited by CLAUDE.md, agents, hooks, and CI. Never rename it; evolve
   only its expansion.
3. Scopes are architecture claims: test-only deps `% Test`, logging backend `% Runtime` —
   so production code physically cannot reference them. Scope honestly, always.
4. Determinism: the build's only environment inputs are APP_VERSION (image tag = git SHA)
   and CI (turns on -Werror). No other conditionals, no secrets, no endpoints, no deploy
   logic — procedures live in scripts, not the build.
5. Packaging lives in the build (sbt-native-packager): pinned eclipse-temurin base, port
   8080, non-root user, -XX:MaxRAMPercentage (never -Xmx) so one image is correct at any
   container size. No hand-written Dockerfile may exist.

## Procedure (creation from scratch, or structural change)
1. From the stack spec (task text + CLAUDE.md), write: build.sbt (ledger vals; deps grouped
   under tier-named comment headers; scalacOptions with -Wunused:all and CI-gated -Werror;
   Docker block; markTestRun task touching .claude/.last-test-run; fmt/check/dockerLocal
   aliases), project/build.properties (pinned sbt), project/plugins.sbt (each plugin pinned
   and justified in a comment), .scalafmt.conf (scala3 dialect), .gitignore (build/IDE/
   terraform/.env outputs plus the marker file), docker-compose.yml (healthchecked postgres
   matching the RDS major version; app service on the local image).
2. Mark deliberate absences where a model would "helpfully" add them, e.g.
   `// no circe/play-json/jackson — upickle only (CLAUDE.md hard rule)`.
3. Verify: `sbt Test/compile` on a fresh checkout state (`sbt check` once sources exist).
   Fix everything it reports.
4. Report: every version chosen (and why, if not latest stable), the complete environment-
   input surface (must be exactly APP_VERSION, CI), plugins added, deviations from spec.

## Boundaries
- Version bumps of existing dependencies are the dependency-updater's; decline and route.
- Application source is the feature-implementer's; Terraform/scripts/workflows are the
  infra-engineer's; .claude/** is the factory-engineer's. Decline and name the owner.
- A dependency request from another agent's report is your input: add the entry with its
  justification comment, run the verification, report.
- Escalate to a human: resolver/registry failures you cannot pin down, or any request that
  would add a second environment input to the build.
```

**Construction notes.** The description shouts FROM SCRATCH and enumerates its files literally — the collision it must win is against feature-implementer ("set up the build for feature X" *sounds like* implementation) and against dependency-updater (the version-bump carve-out is stated in the description itself, from this side of the boundary too). The iron laws are the build-definition rules as standing knowledge: previously they existed only as tutorial prose and one-time task text; now they are *operative on every invocation* — which is the general lesson of this agent: **when an artifact class has structural rules that must outlive any single creation, the rules belong in a creating agent's laws, not in the work order.** Note law 4's closing clause and the report's "environment-input surface" field: the agent must *prove* the build's config surface stayed at two variables, making determinism a checked deliverable rather than a hope. The implementer↔build-engineer handoff (dependency needs travel via report; build-engineer adds with justification) trades a little latency for exclusive write authority over the repo's most policy-dense file.

**The complete `factory-engineer` file** (`.claude/agents/factory-engineer.md`) — the agent that creates agents:

```markdown
---
name: factory-engineer
description: Creates and maintains the agent system itself FROM SCRATCH — CLAUDE.md,
  docs/agents.md, .claude/agents/*, hooks, settings.json, commands, .mcp.json — working
  from an authority matrix. Use to bootstrap the factory, add or modify any agent, hook,
  permission rule, or MCP server. Prepares constitutional diffs; never self-ratifies.
tools: Read, Grep, Glob, Write, Edit, Bash
---

You are the factory engineer for TaskForge: the agent that builds and evolves the agents.
Your artifacts govern every other agent's behavior, so your output is never merely code —
it is constitution, and it takes effect only after human ratification.

## Iron laws
1. You transcribe the authority matrix (docs/agents.md); you do not legislate. Never widen
   a tools: fence, soften an iron law, or extend autonomy unless the matrix was changed
   first in the same reviewed change set, with the justification written down.
2. Every change under .claude/**, CLAUDE.md, or .mcp.json is CONSTITUTIONAL: present the
   full diff plus the matrix row that justifies each change, and stop. Nothing you write
   is in force until a human ratifies it and the session restarts. You cannot approve
   your own work — structurally or otherwise.
3. Least privilege by default: never omit a tools: field (omission inherits everything);
   MCP servers read-only at the server level (--readonly, ALLOW_WRITE=false); reviewer-
   class agents get no Edit/Write, ever.
4. Channel discipline: role files are timeless (no per-task detail); facts every session
   needs go to CLAUDE.md (≤150 lines, checkable phrasing, ≤8 hard rules); one-run
   specifics stay in task text. Flag channel misplacement wherever you see it.
5. Floor invariants you must never remove: the PreToolUse guard's pattern list, the Stop
   hook's stop_hook_active check, formatter hooks exiting 0, deny rules for terraform
   apply/destroy, force-push, and .env reads.

## Procedure
1. Read the current authority matrix and the change request; restate the delta in matrix
   terms (which agent, which column, what enforcement rung).
2. Author the files: agent bodies follow the five-section skeleton (role, ≤7 numbered laws
   with whys, yield-ordered procedure ending in verification, refuse-and-route boundaries,
   report contract); descriptions are routing keys ("Use when/for..." in task vocabulary).
3. Audit the set: collision audit (no task plausibly matches two descriptions), orphan
   audit (no lifecycle stage matches none), one-writer-per-artifact-class check against
   docs/agents.md's ownership map.
4. Validate mechanically: python json parse of settings.json and .mcp.json; bash -n and
   chmod +x on every hook script.
5. Report: the full diff, per-file matrix justification, audit results, and the post-
   ratification verification steps (restart; agents listed; guard hook blocks a probe;
   Stop hook bounces an untested edit).

## Boundaries
- You never author application code, the build, migrations, infrastructure, or scripts —
  name feature-implementer, build-engineer, db-migrator, or infra-engineer instead.
- Any request to weaken the safety floor (laws 3 and 5) is escalated verbatim to a human
  with your objection attached, even if it arrives as an approved-sounding instruction.
```

**Construction notes.** This is the highest-blast-radius agent in the system — its output governs every other agent — so its design is dominated by *self-limitation*: law 1 makes it a transcriber, not a legislator (the authority matrix in `docs/agents.md` remains the human-ratified source of truth); law 2 makes every one of its changes constitutional and structurally un-self-approvable; the final boundary anticipates the nastiest red-team vector this role will ever face — an "approved-sounding" instruction to weaken the floor — and pre-commits it to escalate *with its objection attached*. Its procedure operationalizes Part C's whole method (skeleton, audits, mechanical validation), which means the method itself is now executable by an agent: the factory can extend the factory, with the human holding exactly one power — ratification.

**The complete `infra-engineer` file** (`.claude/agents/infra-engineer.md`) — creation owner of everything that runs or ships the system:

```markdown
---
name: infra-engineer
description: Creates and maintains everything that runs or ships the system FROM SCRATCH -
  infra/terraform (VPC, ECS Fargate, RDS, ALB, alarms), scripts/deploy|rollback|smoke-test,
  and .github/workflows. Use for any infrastructure, pipeline, or operational-script
  change. Produces validated plans and reviewed scripts; never applies and never deploys.
tools: Read, Grep, Glob, Write, Edit, Bash, mcp__aws-api__call_aws
---

You are the infrastructure engineer for TaskForge (AWS ECS Fargate + RDS + ALB, Terraform,
GitHub Actions). You author the machinery of deployment; you never operate it.

## Iron laws
1. Stateful resources are sacred: RDS keeps deletion_protection and final snapshots; any
   plan line that destroys or REPLACES a data-bearing resource is highlighted first in
   your report and requires explicit human sign-off before you even present the rest.
2. `terraform apply` is never yours (the floor denies it): your deliverable is a clean
   `terraform validate` + `terraform plan` with a resource-by-resource summary; a human
   applies. Same for GitHub secrets: you reference them by name, never create or read.
3. Scripts encode gates as code: deploy.sh refuses dirty trees, tags by git SHA, verifies
   the service actually landed on the new revision (the circuit breaker makes bare
   "stable" ambiguous), and prints greppable `==>` evidence at every step. No interactive
   prompts anywhere — agents run these.
4. Separation of powers: you AUTHOR deploy machinery; the deploy-engineer EXECUTES it.
   Never run a deploy, rollback, or smoke test against a live environment yourself.
5. The two Terraform/deploy seams stay documented in-file wherever they live: the ECS
   service's lifecycle ignore_changes on task_definition (deploys move revisions outside
   Terraform), and the deployment circuit breaker (platform self-healing beneath agent
   supervision). Removing either is a CRITICAL change requiring human sign-off.

## Procedure
1. Read current reality before authoring: `terraform plan` for drift, `aws ecs
   describe-services` / `terraform output` (read-only) for live state. Never assume.
2. Author the minimal diff: Terraform grouped by concern (network / database / ecs / alb /
   alarms / outputs), least-privilege IAM (execution role reads exactly one secret; task
   role empty unless a feature demands otherwise — then justify), immutable ECR tags.
3. Verify: `terraform fmt -check`, `terraform validate`, `terraform plan`; `bash -n` every
   script; workflows checked for pinned action versions and OIDC (no long-lived keys).
4. Report: plan summary with counts (add/change/destroy) and an explicit list of any
   stateful-resource replacements (law 1); for scripts, the gates each encodes; for
   workflows, trigger, permissions, and secrets referenced by name.

## Boundaries
- Application source → feature-implementer; build and packaging → build-engineer; schema →
  db-migrator; running deployments → deploy-engineer; .claude/** → factory-engineer.
- Escalate: anything touching production data, TLS/DNS ownership, IAM beyond this app's
  roles, or a request to widen a security group beyond the tier chain ALB→app→db.
```

**Construction notes.** The role line — *"you author the machinery of deployment; you never operate it"* — is the separation-of-powers principle as identity: the agent that decides what `deploy.sh` checks must not be the agent that runs it under pressure (and vice versa: deploy-engineer executes scripts it cannot edit). Law 1 orders the report around the one thing that matters most in an infra diff (stateful replacements first, everything else after), law 2 keeps the human's hands on the only irreversible lever, and law 5 protects the two seams that past experience shows every future maintainer — human or agent — will be tempted to "clean up." Its `mcp__aws-api__call_aws` grant plus the floor's read-only allows give it live-state eyes; its authorship writes are all repo files, gated like any code.

## C.9 The hooks — the floor's three scripts

Wired in `settings.json` (C.10); scripts live in `.claude/hooks/`, each ≤ 40 lines of bash. All three read the hook JSON payload from stdin (fields like `tool_name`, `tool_input`, `hook_event_name`, `stop_hook_active`) and speak the exit-code protocol: **0 = proceed, 2 = block (stderr is fed back to the model), else = non-blocking error.**

**`format-scala.sh`** (PostToolUse, matcher `Edit|Write`) — the *normalizer*. Extracts `tool_input.file_path` (the canonical one-liner: `python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("file_path",""))'`), and if it's `*.scala|*.sbt`, runs scalafmt on it. **Always `exit 0`** — a formatter that can block would let a formatting hiccup halt a feature; conveniences never block. Note it formats *the one touched file*, not the tree: hooks run on every matching tool call, so they must be fast.

**`guard-dangerous.sh`** (PreToolUse, matcher `Bash`) — the *guard*. Case-insensitively matches `tool_input.command` against a pattern list (`DROP TABLE`, `DROP DATABASE`, `TRUNCATE `, unqualified `DELETE FROM tasks;`, `terraform destroy`, `aws rds delete`, `aws ecr delete`, `--force-delete`, `rm -rf /`) and exits 2 on a hit with stderr: *"Blocked by guard-dangerous hook: command matches forbidden pattern '...'. If this is really needed, a human must run it manually."* Two authoring points: the message is written **to the model as a course-correction** (it names the alternative — human hands — so the block becomes routing, not a retry loop), and the list holds *semantic* patterns the permission rules' tool+glob shapes can't express (a `DROP TABLE` can hide inside any command line; the hook reads the actual string).

**`verify-tests-ran.sh`** (Stop, matcher `*`) — the *gate*. First checks `stop_hook_active` and exits 0 if set (**the mandatory infinite-loop guard for every Stop hook**). Then: if `git diff` shows changed `*.scala` but the marker file `.claude/.last-test-run` (touched by the `markTestRun` task inside the `sbt check` alias — build tool and floor meshing) is older than the newest change, exit 2 with: *"Scala sources changed but the test suite has not run since. Run 'sbt check' (or 'sbt test') and fix any failures before finishing."* This is the team's definition-of-done as mechanism; it converts "please always test" (95% compliance) into "you cannot finish untested" (100%).

## C.10 `settings.json` — permissions + hook wiring

The complete shape (see the repo for the full file):

```json
{
  "permissions": {
    "allow": [
      "Bash(sbt *)", "Bash(git status)", "Bash(git diff *)", "Bash(git log *)",
      "Bash(git add *)", "Bash(git commit *)", "Bash(docker build *)",
      "Bash(./scripts/smoke-test.sh *)", "Bash(terraform plan *)", "Bash(terraform validate *)",
      "Bash(aws ecs describe-services *)", "Bash(aws ecs describe-task-definition *)",
      "Bash(aws logs tail *)", "Bash(aws cloudwatch get-metric-statistics *)"
    ],
    "deny": [
      "Read(./.env)", "Read(./.env.*)", "Read(./secrets/**)",
      "Bash(terraform destroy *)", "Bash(terraform apply *)",
      "Bash(aws rds delete-db-instance *)", "Bash(aws ecr delete-repository *)",
      "Bash(git push --force *)"
    ]
  },
  "hooks": {
    "PostToolUse": [ { "matcher": "Edit|Write",
        "hooks": [ { "type": "command", "command": ".claude/hooks/format-scala.sh", "timeout": 120 } ] } ],
    "PreToolUse":  [ { "matcher": "Bash",
        "hooks": [ { "type": "command", "command": ".claude/hooks/guard-dangerous.sh", "timeout": 10 } ] } ],
    "Stop":        [ { "matcher": "*",
        "hooks": [ { "type": "command", "command": ".claude/hooks/verify-tests-ran.sh", "timeout": 10 } ] } ]
  }
}
```

Reading the allow list as a design: it is precisely the union of the ten agents' *routine, harmless* verbs — build/test (everyone), git bookkeeping (implementer), image build + read-only AWS (deployer, responder, infra-engineer), `terraform plan`+`validate` but pointedly not `apply` (infra-engineer produces plans; humans apply). The deny list is the matrix's MUST-NOT column, mechanized where the shape allows. Everything in neither list falls to the interactive prompt — the judgment-requiring middle, by design.

## C.11 `.mcp.json` — the eyes

Five servers (full file in the repo): `postgres` (awslabs server via `uvx`, **`--readonly true`**, `DATABASE_URL` from `${DATABASE_URL}` env expansion — secrets never enter the repo), `aws-api` (the AWS surface as tools), `ecs` (**`ALLOW_WRITE=false`** + the troubleshooting tool the responder leans on), `terraform` (provider-doc lookup for infra work), `github` (HTTP server — issues/PRs as tools). Checked into the repo root so humans, agents, and CI share one integration surface. The grant pattern to internalize: *server-level* read-only enforcement (`--readonly`, `ALLOW_WRITE=false`) beneath *agent-level* name grants (`mcp__postgres__run_query` appears only in db-migrator's and incident-responder's fences) — two independent layers, either of which alone stops a write.

## C.12 The commands — frozen screenplays

Three files in `.claude/commands/`, each ≤ 15 lines: **`/deploy [env]`** (delegates to deploy-engineer: verify preconditions → `deploy.sh $1` → watch rollout → smoke test → on any gate failure `rollback.sh $1` + full evidence; report image tag, revision, duration, smoke results), **`/rollback [env]`** (immediate: `rollback.sh` → wait steady → smoke → report what's live now → open the mandatory follow-up: *why* did the rolled-back deploy fail — someone must answer before anyone deploys again), **`/incident <description>`** (`$ARGUMENTS` carries the free-text symptom into the incident-responder's full triage procedure). Why commands at all, when you could type the words: **consistency under stress** — free-form requests get free-form interpretations, tolerable at 2 p.m., worst at 3 a.m., which is exactly when `/rollback staging` must produce the identical verified sequence every time. And because commands are files, ops-procedure changes ship as reviewed diffs, like everything else in this system.

## C.13 `CLAUDE.md` and the routing map — the constitution

**The complete file** (repo root `CLAUDE.md` — trimmed here only of its table formatting; read it whole in the repo):

```markdown
# TaskForge — project memory for Claude Code

TaskForge is a Scala 3 three-tier web application (task manager) deployed on AWS ECS Fargate
with PostgreSQL on RDS. JSON serialization uses upickle (com-lihaoyi) everywhere.

## Architecture (three tiers — respect the boundaries)
| Tier | Package | May depend on | Must NOT depend on |
| 1 Presentation | com.taskforge.web + static | service, domain | data (except via Main wiring) |
| 2 Business logic | com.taskforge.service | data *port* (trait), domain | http4s, doobie, SQL |
| 3 Data | com.taskforge.data | domain | web, service |
- com.taskforge.domain is the shared kernel ... Main.scala is the only composition root ...
- The service tier raises typed AppErrors; the web tier maps them in TaskRoutes.handleErrors.
  Never map errors anywhere else.

## Commands
- `sbt check` — format check + compile + full test suite. Run before declaring any work done.
- `sbt fmt` / `sbt dockerLocal` / `./scripts/deploy.sh` / `./scripts/rollback.sh` ...

## Hard rules
- Every artifact class has exactly one owning agent (ownership map in docs/agents.md).
  Never create or modify an artifact outside your role — decline and name the owner.
  In particular: build.sbt/project/* belong to **build-engineer** (versions to
  **dependency-updater**), terraform/scripts/workflows to **infra-engineer**, and any
  .claude/** or CLAUDE.md change is prepared by **factory-engineer** and takes effect
  only after human ratification.
- upickle only for JSON. Do not add circe/play-json/jackson; the http4s bridge lives in
  web/UPickleEntityCodec.scala.
- Never edit an applied Flyway migration. Schema changes = new V<n>__description.sql file,
  reviewed by the **db-migrator** agent.
- Every code change needs a test in the same PR. Business rules are tested against
  InMemoryTaskRepository, not the real database.
- Secrets never appear in code, config files, or logs. Runtime secrets come from AWS Secrets
  Manager via the ECS task definition.
- Infrastructure changes go through infra/terraform — never hand-edit resources in the AWS
  console (the drift will bite the next agent).

## Style
- Scala 3 syntax. scalafmt is enforced by a hook.
- Prefer explicit small functions over clever abstractions; agents (and reviewers) read this
  code more often than they write it.

@docs/agents.md
```

**Construction notes:** the identity paragraph names the stack *and the JSON law* in the first breath (the two facts that prevent the most common wrong-defaults). The tier table is phrased as **May / Must NOT** — mechanical language a reviewer greps for, not "keep layers clean" it can only nod at. Commands carry *purposes* ("run before declaring any work done") because agents pattern-match on purpose statements when deciding what to run. The hard rules number **six** — inside the scarcity budget of Step 4 — and the first is the ownership law that makes the whole system agent-driven: one owning agent per artifact class, with the owners named inline, so every session learns the routing as a side effect of reading the law. The style section's last line states the system's deepest bias: this code is *read* by agents far more than written, so optimize for legibility. And the final line, `@docs/agents.md`, inlines the routing map (stage → agent → trigger table plus the escalation policy) into every context — one file, maintained once, delivered everywhere.

---

# PART D — GENESIS: THE AGENTS BUILD THE ENTIRE SYSTEM

This part is the heart of the book: starting from an empty directory, the complete codebase — build definition, all three tiers, tests, infrastructure, pipelines — is **generated by the agents you just built**, phase by phase, and deployed to AWS. The human writes prompts, reviews, and approvals; never code.

Every phase below has the same six-block shape. Treat the shape itself as a reusable artifact — it is how you script *any* multi-session agentic build:

> **Goal** (one sentence) → **Preconditions** (what must already be true) → **The prompt** (verbatim — also collected in `docs/genesis-prompts.md` for copy-paste) → **What happens mechanically** (the tool-call-level narrative: what the agent reads, edits, runs; which hooks fire) → **Your gate** (the human verification before the phase's commit) → **Failure branch** (what to do when it goes sideways).

Two standing conventions across all phases. *Commit discipline*: every phase ends in exactly one git commit, made in the session (the floor pre-approves `git add/commit`), so the repo history *is* the genesis log — `git log --oneline` at the end reads like this part's table of contents. *Session discipline*: one phase per session (or a fresh `/clear`) — each phase's context stays clean, and CLAUDE.md re-anchors every new session automatically, which is precisely what it's for.

## D.0 The bootstrap paradox, resolved — and Session 0, concretely

Phase 2 onward has agents building every artifact — but who builds the agents? The resolution is a two-step seed: **the orchestrator writes exactly one file — the factory-engineer's definition — the human ratifies it, and from then on the factory builds the factory.** After the seed, even `.claude/` files are agent-authored; the human's authorship in this entire genesis is zero files. What the human does contribute is concentrated and non-delegable: prompts, constitutional ratifications, and one `terraform apply`.

The critical discipline at every ratification gate: **review factory files like a constitution, not like code.** They are the highest-blast-radius artifacts in the repository, because every future artifact is produced *under their authority* — a bug in `TaskService.scala` breaks a feature; a bug in `code-reviewer.md` breaks the thing that catches broken features.

**Session 0 — the human's only setup work** (no Claude involved). Run, verbatim:

```bash
# toolchain (versions and rationale: TUTORIAL.md Part 1)
java -version && sbt --version && docker version && terraform -version && uvx --version
aws sts get-caller-identity          # MUST show the sandbox account, not production
npm install -g @anthropic-ai/claude-code && claude --version

# the one chicken-and-egg AWS step: Terraform's own state store
aws s3api create-bucket --bucket <unique>-tfstate --region us-east-1
aws s3api put-bucket-versioning --bucket <unique>-tfstate --versioning-configuration Status=Enabled
aws dynamodb create-table --table-name taskforge-tflock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH --billing-mode PAY_PER_REQUEST

mkdir taskforge && cd taskforge && git init
```

## D.1 Phase 1 — Plant the seed, then the factory builds the factory

### Step 1a — the seed (plain session; the ONLY file the orchestrator writes)

Start `claude` in the empty repo and give it this prompt, verbatim:

> Create exactly one file, `.claude/agents/factory-engineer.md`, and nothing else: an agent whose job is to create and maintain the agent system itself (CLAUDE.md, docs/agents.md, all .claude/agents/*, hooks, settings.json, commands, .mcp.json) from an authority matrix. Frontmatter: name factory-engineer; a routing-grade description ("Creates and maintains the agent system itself FROM SCRATCH... prepares constitutional diffs; never self-ratifies"); tools Read, Grep, Glob, Write, Edit, Bash. Body iron laws: (1) transcribe the authority matrix, never widen a fence or soften a law unless the matrix changed first; (2) every .claude/** change is constitutional — full diff + justification, in force only after human ratification and restart, never self-approved; (3) least privilege by default — no omitted tools: fields, MCP read-only at server level, reviewer-class agents get no write tools; (4) channel discipline — timeless role files, universal facts to CLAUDE.md (≤150 lines, ≤8 hard rules), one-run detail in task text; (5) floor invariants that may never be removed (guard patterns, stop_hook_active check, formatter exit 0, deny rules for terraform apply/destroy, force-push, .env reads). Procedure: read matrix → author files using the five-section skeleton with collision/orphan audits → validate mechanically (json parse, bash -n, chmod +x) → present diff and stop. Print the full file content in your reply.

**Your gate (constitutional).** Read the one file line by line against C.8b's reference listing. Then:

```bash
git add -A && git commit -m "genesis 1a: seed — factory-engineer"
exit    # restart so the agent loads
claude
```

### Step 1b — the factory builds the factory (factory-engineer)

In the fresh session, delegate the rest of the agent system to the seed agent. The prompt, verbatim (it is your Part B authority matrix, serialized — the full text also lives in `docs/genesis-prompts.md` Phase 1):

> Use the factory-engineer agent to create the rest of the TaskForge agent system from this authority matrix. System: Scala 3 three-tier task-management web app (http4s presentation tier + static HTML/JS frontend; pure business-logic tier on cats-effect IO; doobie/PostgreSQL data tier; upickle for ALL JSON), deployed on AWS ECS Fargate + RDS via Terraform. Create: (1) CLAUDE.md — identity; three-tier May/Must-NOT table; commands with `sbt check` as the definition of done; hard rules (one-owner-per-artifact with the ownership map, upickle-only JSON, applied migrations immutable, every change ships a test, secrets only via Secrets Manager, infra only via Terraform); @docs/agents.md import. (2) docs/agents.md — the ARTIFACT OWNERSHIP MAP (artifact class → creating/owning agent → version-bump agent → gate) and lifecycle table for TEN agents: factory-engineer (exists — list it), build-engineer (creates build.sbt/project/scalafmt/compose/gitignore from scratch; sole owner of build structure), feature-implementer (src/** only; routes dependency needs to build-engineer), test-engineer, code-reviewer (with an Ownership review axis), db-migrator, infra-engineer (creates terraform + scripts + workflows from scratch; plans only, human applies), deploy-engineer (executes scripts it does not author), incident-responder, dependency-updater (version ledger only); plus the escalation policy (rollbacks autonomous; data/schema/security to humans; destructive DDL human-signed; applies human-run; constitutional changes human-ratified). (3) The nine remaining agent files per the matrix with least-privilege fences (reviewer: Read/Grep/Glob/Bash only; migrator: + mcp__postgres__run_query; deploy: + mcp__aws-api__call_aws, mcp__ecs__ecs_resource_management; responder: + ecs_troubleshooting_tool and postgres read; updater: + WebSearch/WebFetch, Edit but not Write; infra: + mcp__aws-api__call_aws). (4) Hooks + settings.json — PostToolUse scalafmt (exit 0); PreToolUse Bash guard (DROP/TRUNCATE/terraform destroy/force-deletes → exit 2, human-must-do-this message); Stop hook via .claude/.last-test-run marker with stop_hook_active guard; permissions: allow sbt/git bookkeeping/docker build/read-only aws/terraform plan+validate; deny terraform apply+destroy, rds/ecr deletes, force-push, .env reads. (5) .mcp.json — awslabs postgres (readonly), aws-api, ecs (ALLOW_WRITE=false), terraform via uvx; github via HTTP. (6) .claude/commands/ — /deploy, /rollback, /incident naming their responsible agents and gates. No application code. Present the full diff with per-file matrix justifications and your audit results, then stop for ratification.

**What happens mechanically.** The factory-engineer drafts ~20 files in its own context, runs its mechanical validations (JSON parses, `bash -n`, `chmod +x`), performs the collision/orphan/ownership audits, and — per its law 2 — presents the diff and *stops*. Nothing is in force yet.

**Your gate (constitutional, the big one).** Review every file against C.2–C.13 and your matrix; the fences (`tools:` lines) get character-level attention. Then ratify:

```bash
git add -A && git commit -m "genesis 1b: the factory, built by the factory"
exit && claude    # restart loads agents, hooks, MCP servers
```

And probe the floor empirically — mechanism you haven't seen fire is mechanism you don't have:

```
> what agents are available?                     # expect all ten
> run: echo 'DROP TABLE tasks'                   # expect the guard hook to BLOCK, loudly
> use the feature-implementer agent to add a priority column to build.sbt
                                                 # expect refuse-and-route to build-engineer
```

**Failure branch.** Agents not listed → wrong directory or malformed frontmatter (`---` fences). Guard not firing → matcher/type wrong in settings.json, or hooks not executable. Route the fix back through factory-engineer (it owns `.claude/**` — even now, you don't hand-edit), re-ratify, re-probe.

## D.2 Phase 2 — Build-system genesis

**Goal.** `build.sbt`, `project/build.properties`, `project/plugins.sbt`, `.scalafmt.conf`, `.gitignore`, `docker-compose.yml` — the substrate every later phase's verification runs on, created from scratch by its owning agent.

**Preconditions.** Phase 1 committed and ratified; fresh session.

**The prompt:**

> Use the build-engineer agent to create the sbt build for TaskForge from scratch. Scala 3.3 LTS; pin exact versions as named vals: http4s 0.23.x (ember-server, dsl; ember-client Test-scoped), upickle 4.x as the ONLY JSON library, doobie 1.0.0-RC (core, hikari, postgres), Flyway (core + postgres module, Runtime), PostgreSQL JDBC driver, logback (Runtime), munit + munit-cats-effect (Test). scalacOptions: -deprecation -feature -unchecked -Wunused:all, plus -Werror only when the CI env var is set. sbt-native-packager Docker config: eclipse-temurin:21-jre base, port 8080, non-root user, -XX:MaxRAMPercentage=75.0 via env. A `markTestRun` task touching `.claude/.last-test-run`, and aliases: `fmt`; `check` = scalafmtCheckAll; Test/compile; test; markTestRun; `dockerLocal`. Also: project/build.properties (current sbt 1.x), plugins.sbt (native-packager, scalafmt), .scalafmt.conf (scala3 dialect, maxColumn 100), a .gitignore for sbt/metals/terraform/.env and the `.claude/.last-test-run` marker, and a docker-compose.yml with a healthchecked postgres:16 `db` service and an `app` service running image taskforge:latest. Verify with `sbt Test/compile` (empty compile is fine), then report versions chosen and any deviation from this spec.

**What happens mechanically.** The build-engineer Writes the six files; PostToolUse fires on `build.sbt` (scalafmt formats sbt syntax too); its `sbt Test/compile` triggers the first dependency resolution (minutes, once); the Stop-hook check passes because no `*.scala` changed. Notice what the prompt did *not* have to say: no "versions as named vals", no "run the compile before finishing", no "don't add circe" — those are the agent's own iron laws (C.8b) plus the constitution. **Task text carries the spec; the role carries the discipline.** That division is the entire reason Phase 1 came first.

**Your gate.** `sbt check` runs clean (trivially — nothing to test yet). Read `build.sbt` once — this file is Phase-2's constitution-adjacent artifact: every future version bump diffs against it. Commit: `build: sbt substrate, pinned deps, docker packaging, check alias`.

**Failure branch.** A version the agent chose doesn't resolve → this is the dependency-updater's *procedure* running early: have the implementer check the actual latest on Maven Central and re-pin (it has no web tools — paste the version in, or run the updater agent for the lookup). Wrong library slipped in (it happens): your gate catches it; reject with one line — "circe is present; constitution says upickle-only; remove and re-verify" — and watch the correction cost nothing because the phase is one commit.

## D.3 Phase 3 — The domain and the wire format

**Goal.** `domain/Task.scala` (entity, `TaskStatus` enum, request payloads, `AppError` ADT, upickle codecs incl. the `Instant` given) + `config/AppConfig.scala` + `JsonCodecSuite` pinning the wire format.

**The prompt** (fresh session):

> Use the feature-implementer agent to create the TaskForge domain, in package com.taskforge.domain, one file: a top-level `given ReadWriter[java.time.Instant]` via ISO-8601 strings (readwriter[String].bimap) — placed above the case classes so derivation finds it; `enum TaskStatus derives ReadWriter` with Todo, InProgress, Done; `final case class Task(id: Long, title, description, status, createdAt, updatedAt) derives ReadWriter`; `CreateTaskRequest(title, description = "")` and `UpdateTaskRequest` with all-Option fields defaulted None (absent JSON keys must parse); `ErrorResponse(error)`; and a `sealed abstract class AppError(message) extends Exception with NoStackTrace` with TaskNotFound(id), ValidationFailed(reason), InvalidTransition(from,to). Also com.taskforge.config.AppConfig: env-var config (HTTP_HOST/PORT, DB_URL/USER/PASSWORD/POOL_SIZE) with local defaults, no config library. Then a JsonCodecSuite (plain munit) that pins: Task round-trip; enum encodes as bare string "InProgress"; Instant as ISO-8601; CreateTaskRequest parses without description; UpdateTaskRequest parses from {}; unknown enum value fails. Run `sbt check`; report the exact JSON of one sample Task.

**What happens mechanically.** Writes → PostToolUse formats each file → `sbt check` → Stop hook satisfied by the fresh marker. The report's *sample Task JSON* is your one-glance wire-format review — you are approving the API's public shape here, which is why the prompt demands it.

**Your gate.** `sbt check` green; the sample JSON looks like what you'd want a client to see (strings for enums, ISO timestamps). Commit: `domain: entities, errors, upickle codecs, wire-format suite`. *Rationale for the phase order*: domain-first isn't aesthetics — the wire format is the contract every later tier and the frontend build against, and `JsonCodecSuite` freezes it before anything depends on it. From this commit on, an agent can only change the API shape by *visibly editing a test that says it pins the wire format* — which the reviewer treats as a MAJOR finding.

## D.4 Phase 4 — Schema and the data tier (first two-agent phase)

**Goal.** `V1__create_tasks.sql`; `TaskRepository` port; `DoobieTaskRepository`; `Database` (Hikari transactor Resource + Flyway migrate-on-boot).

**Prompt 4a — the migration, to its owner:**

> Use the db-migrator agent to create V1 for TaskForge: a `tasks` table — id BIGSERIAL PK; title VARCHAR(200) NOT NULL; description TEXT NOT NULL DEFAULT ''; status VARCHAR(20) NOT NULL DEFAULT 'Todo' CHECK (status IN ('Todo','InProgress','Done')); created_at/updated_at TIMESTAMPTZ NOT NULL DEFAULT now(); an index on status (the list endpoint filters by it). Header comment: applied migrations are never edited. There is no live database yet — note that your inspect step is vacuous this once, and say so in your report. Verify by `docker compose up -d db` and confirming Flyway applies it (via sbt test boot or flyway CLI), then report the compatibility analysis (trivial for V1) and rollback strategy.

The "say so in your report" clause matters: the agent's procedure step 1 (inspect live schema) cannot run against nothing; a well-built agent *states* a skipped step rather than silently skipping — and your prompt can license precisely that, once. **Never write the license into the agent file** — that would make skipping normal.

**Prompt 4b — the code against the schema:**

> Use the feature-implementer agent to build the data tier against V1: `data/TaskRepository.scala` — a trait on IO (create/get/list-by-optional-status/update/delete), the tier-3 port; `data/DoobieTaskRepository.scala` — doobie implementation, sql interpolators only, RETURNING on insert/update, `.query[Task]` with column order exactly matching the case class, a companion `given Meta[TaskStatus]` via Meta[String].timap, java.time Metas from `doobie.postgres.implicits.*` (do NOT hand-roll Meta[Instant]); `data/Database.scala` — Flyway migrate as IO.blocking (idempotent, runs every boot) and a HikariTransactor Resource with a fixed thread pool sized to the connection pool. Run `sbt check`; report any place the schema and the case class could drift and what catches it.

**Your gate.** Green check; skim the SQL once (you are the DBA of last resort at genesis); confirm the report's drift answer says "the compiler / `.query[Task]` arity" — if the agent can't name the tripwire, it doesn't understand the design it just built. Commit per sub-phase (`schema: V1 tasks table`, `data: port + doobie implementation + pool/migrations`).

**Failure branch — a real one.** This repo's own genesis hit it: the implementer (or its reviewer) imports a doobie API that moved between RC versions (`doobie.implicits.javasql` — removed; java.sql Metas are default now, PG java.time Metas live in `doobie.postgres.implicits`). The compile error *is* the system working — the failure branch is: paste the compiler error back to the implementer; if it flails (API knowledge stale), escalate the *lookup* to a research-capable agent or verify against the library's tagged source, then hand the confirmed fix back. Afterward, run the evolution habit (Part F): this incident added the "doobie RC bumps can change implicit imports; recompile is the test" tripwire to the dependency-updater. **Every genesis failure should leave the factory smarter.**

## D.5 Phase 5 — The service tier and its adversary

**Goal.** `TaskService` (validation, transition rules as data) + `InMemoryTaskRepository` + `TaskServiceSuite`; then the test-engineer attacks it.

**Prompt 5a (implementer):**

> Use the feature-implementer agent to build the business tier: `service/TaskService.scala` depending ONLY on the TaskRepository trait and domain — create (title trimmed, nonempty, ≤200 chars) / get (absent → TaskNotFound) / list / update (validate any new title; validate status transitions; absent-after-update → TaskNotFound) / delete (false → TaskNotFound). Encode legal transitions as a Set of (from,to) pairs — Todo→InProgress, InProgress→Done, Done→Todo, InProgress→Todo, plus same-state no-ops — so the rules are data, not if-trees. In src/test: `InMemoryTaskRepository` over a Ref[IO, Map[Long, Task]] plus counter, and a TaskServiceSuite (munit-cats-effect) covering create/trim/reject, every legal transition, one illegal one, list filtering, delete-then-delete. `sbt check`; report the transition set verbatim.

**Prompt 5b (the adversary, fresh delegation):**

> Use the test-engineer agent on the service tier. The implementer's report: [paste 5a's report]. Enumerate what it missed per your mission categories; add the tests; leave any failing test failing and report it.

Paste the report — remember A.1: the tester *cannot see* the implementer's transcript; the report is the interface, and this prompt is where that becomes muscle memory. **Expected mechanics**: the tester reads the service + suite, greps the transition set, and typically adds: title of exactly 200 chars (boundary), unknown-status parse at the codec level, Done→InProgress (illegal jump the happy-path suite skipped), concurrent updates through the Ref repo. If one fails — say the trim happens after the length check — the test stays red, the Stop hook lets the tester finish (tests *ran*), and the report routes the bug.

**Your gate.** If red: one more implementer delegation ("fix the trim-order bug the test-engineer reported: [paste]"), then green, then commit `service: rules as data; suite + adversarial hardening`. You have now watched the *implement → attack → fix* loop that Part E runs forever.

## D.6 Phase 6 — The web tier: bridge, routes, health, composition root, frontend

**Goal.** `UPickleEntityCodec` (the ~30-line http4s↔upickle bridge), `TaskRoutes` + central error mapping, `HealthRoutes` (liveness/readiness split), `Main` (composition root), `static/index.html`, `TaskRoutesSuite`.

**The prompt** (implementer; the longest work order in genesis — it is *still* all spec, no discipline):

> Use the feature-implementer agent to build the presentation tier. (1) `web/UPickleEntityCodec.scala`: given EntityEncoder for any upickle Writer (stringEncoder.contramap + application/json content type) and given EntityDecoder for any Reader via EntityDecoder.decodeBy(application/json) reading bodyText and mapping parse failures to MalformedMessageBodyFailure. (2) `web/TaskRoutes.scala`: GET /api/tasks?status= (unknown value → ValidationFailed), GET/PATCH/DELETE /api/tasks/<id> via LongVar, POST /api/tasks → 201; routes stay one-line-thin; a companion `handleErrors` middleware using recoverWith — NOT handleErrorWith, unmatched throwables must pass through with stack traces intact — mapping TaskNotFound→404, ValidationFailed→400, InvalidTransition→409, DecodeFailure→400. (3) `web/HealthRoutes.scala`: /healthz instant liveness; /readyz SELECT 1 through the transactor, 503 with reason on failure (guard null getMessage). (4) `Main.scala`: config → migrate → transactor Resource → wire repo→service→routes; Router of api <+> health <+> an explicit GET / → redirect /index.html <+> resource service for /static; request logging middleware; Ember at configured host/port. (5) `static/index.html`: single-file vanilla HTML/CSS/JS task board against /api/tasks — create, filter by status, advance status, delete, surface JSON error bodies. (6) `TaskRoutesSuite` running the HttpApp directly: 201 create; 400 empty title; 400 malformed JSON (not 500); 404 missing id; 409 illegal transition; 400 unknown status param; full lifecycle round-trip. `sbt check`; report the route table and which status codes each error maps to.

Where did the `recoverWith` clause and the `GET /` redirect in that prompt come from? **From this repo's own review stage** — they were reviewer findings once (Part D.7), now promoted into the work order. That is the factory's learning loop crossing phases: *findings become spec.*

**Your gate.** Green check; then the one manual moment of genesis theater: `docker compose up -d db && sbt run`, open `http://localhost:8080`, create a task in the UI you never wrote. Commit: `web: upickle bridge, routes, health split, composition root, frontend`.

## D.7 Phase 7 — The adversarial review pass

**Goal.** The whole codebase, reviewed by the agent with no hands.

**The prompt:** `Use the code-reviewer agent on the full repository state (git diff against the empty tree if needed: everything is new). Full procedure, all axes, verified findings only.`

**What to expect mechanically — and this is a calibration exercise for you as much as a gate for the code**: a competent reviewer on a codebase this size returns two to five *verified* findings, each with file:line and a concrete failure input. This repo's own genesis-equivalent pass returned, among others: `handleErrorWith`'s total-function MatchError swallowing unknown exceptions' stack traces (CRITICAL — diagnostics destroyed in production); the deploy script's heredoc stealing stdin from the pipe (CRITICAL — deploys die *after* pushing the image); the missing `GET /` index fallback (MAJOR — users' first URL 404s); a nullable `getMessage` serialized into the readiness body (MINOR). Findings of that *shape* — mechanism, consequence, reproduction — are what your axis list should be producing. If you get "consider adding more comments," your reviewer file has failed; return to C.4.

**Your gate.** Route each finding to its owner (implementer for code; you for anything constitutional), re-run the reviewer until APPROVE, commit `review: findings resolved — <one line each>`. *Never fix findings in the review session* — the reviewer has no hands by design, and you preserve the writer/checker separation even when the human is tempted to shortcut it.

## D.8 Phase 8 — Infrastructure as reviewed text, and operational scripts

**Goal.** `infra/terraform/` (VPC with ALB-public/app-and-db-private split; security groups chained ALB→app:8080→db:5432; RDS with Secrets-Manager-only password; ECR immutable SHA tags; ECS cluster/task/service with deployment circuit breaker + `ignore_changes = [task_definition]`; ALB health-checking `/healthz`; four CloudWatch alarms → SNS) and `scripts/` (`deploy.sh` — refuse dirty tree, SHA-tag, push, register revision, wait stable, **verify the service landed on the new revision** — the circuit breaker makes bare "stable" ambiguous; `rollback.sh` — previous revision, dumb and fast; `smoke-test.sh` — healthz, readyz, full CRUD round-trip). The application-side rationale for every resource lives in `TUTORIAL.md` Part 10; the *agentic* points:

**Who authors what.** The **infra-engineer** authors both — the Terraform (using the terraform MCP server for provider-doc lookups) and the scripts — with two concrete prompts, given verbatim in `docs/genesis-prompts.md` Phase 8 ("Use the infra-engineer agent to design and write infra/terraform..." / "Use the infra-engineer agent to write scripts/deploy.sh..."). The crucial separation is downstream: **the deploy-engineer does not author its own gates** — the agent that runs `deploy.sh` under pressure must not be the agent that decided what `deploy.sh` checks (infra-engineer law 4, from both sides). Separation of powers, again.

**The floor in action.** The orchestrator runs `terraform validate` and `terraform plan` freely (allowed); `terraform apply` is denied — the session *presents you the plan*, and **you apply it in your own terminal**. This is the intended shape of the human-in-the-loop for irreversible infrastructure: agent produces and explains the diff; human executes it.

**Your gate.** Read the plan output line by line (it's ~40 resources; this is the second constitutional review of genesis); apply; `terraform output` shows the ALB DNS. Commit `infra: fargate+rds+alb+alarms; scripts: deploy/rollback/smoke`.

## D.9 Phase 9 — First deploy, by the deploy-engineer

**Preconditions.** Phase 8 applied; ECR exists; AWS credentials in the environment; all commits pushed.

**The prompt:** `/deploy staging`

**What happens mechanically — the whole factory firing at once**: the command file routes to deploy-engineer → it re-verifies preconditions itself (clean tree, green check — *verify, don't trust*) → `deploy.sh`: SHA-tagged image built by sbt-native-packager, pushed to ECR, task-definition revision registered, service updated → the agent polls `describe-services` (allowed reads) narrating rollout state → first boot runs Flyway against RDS *before* `/readyz` goes green, so traffic only reaches code whose schema exists → `smoke-test.sh` against the ALB: healthz, readyz, create/advance/delete round-trip → report: image tag, revision number, rollout duration, smoke evidence, per its contract.

**Your gate.** Open the ALB URL; use the app the agents built and shipped. **Failure branch** (a first deploy fails more often than not — an IAM edge, a subnet route): the deployer's own procedure handles it — capture stopped-task reasons *before* rollback, roll back, emit the evidence dossier — and your move is to route that dossier, not to debug live: infra shape → orchestrator+Terraform (→ human apply); code → implementer; then `/deploy staging` again. The rollback muscle gets exercised on day one, which is exactly when you want to learn it works.

## D.10 Phase 10 — CI, the GitHub agent, and scheduled maintenance

**Goal.** Three workflow files (authored by the **infra-engineer** — the prompt is `docs/genesis-prompts.md` Phase 10 — reviewed like everything): `ci.yml` — the *same* `sbt check` on every push/PR (one definition of green everywhere: agents local, CI, deploy preconditions), image build, ECR push on main via OIDC (no long-lived keys); `claude.yml` — `anthropics/claude-code-action@v1` on `@claude` mentions: Claude Code runs in the runner *in this repo*, which means CLAUDE.md, the ten agents, the hooks, and the floor all apply there too — the factory follows the repo, because the factory IS repo files; `maintenance.yml` — weekly cron: headless `claude -p "Use the dependency-updater agent ... apply safe patch/minor upgrades, run sbt check, changelog summary; do NOT apply majors"` with scoped `--allowedTools` and `--max-turns`, opening a PR only if the tree changed — the unattended layering of C.8 (agent proposes → CI gates → reviewer gates → human merges) wired to a clock.

**Your gate.** Push; watch CI go green; comment `@claude` on a test issue and watch the team answer from inside a runner. Commit; genesis is complete. `git log --oneline` now reads: seed → factory → build → domain → schema → data → service+hardening → web → review → infra+scripts → deploy → pipelines. **Zero human-written files — the application, the build, the infrastructure, and the agent system itself were all authored by agents; the human contributed prompts, ratifications, and one `terraform apply` — one deployed system.**

---

# PART E — OPERATING THE FACTORY: THE STANDING LIFECYCLE

Genesis ran each loop once. Operation runs them forever. Three standing loops, shown as the screenplays Step 7 demanded — each one now a habit with named actors and named artifacts.

**The feature loop** (the worked example in full: `TUTORIAL.md` Part 12 adds a `Blocked` status end-to-end). Shape: human intent → orchestrator in plan mode reads the tiers and proposes a sequenced plan (the constitution's laws surface here: the plan arrives *already* expand/contract-ordered because db-migrator's law 2 is in every context) → approval → db-migrator (V2, compatibility analysis) → feature-implementer (all tiers; the compiler catching any missed column list; hooks formatting; Stop hook demanding the suite) → test-engineer (attack; red tests stay red and route back) → code-reviewer (APPROVE or ranked findings, routed) → commit → `/deploy staging` → deploy-engineer's gates and evidence. Every arrow is a report crossing the A.3 transaction; every artifact has exactly one writer.

**The incident loop.** Alarm (or failed smoke test) → `/incident <symptom>` → incident-responder: triage tree in yield order, evidence-cited actions, autonomous lever at most rollback/restart/scale, 15-minute timebox → either remediation + incident report in `docs/incidents/` with its **one prevention item**, or an escalation dossier on your desk. Your standing obligation: the prevention item becomes next week's plan-mode prompt. That single habit is what makes the system *anti-fragile* — each incident permanently upgrades a checklist, a tripwire, a hook, or a law.

**The maintenance loop.** Monday 06:00 UTC, `maintenance.yml` fires headless; the dependency-updater walks its procedure against its tripwires; a PR appears (or doesn't); CI and the reviewer gate it; a human merges. When a CVE lands mid-week, the same agent runs on demand with the advisory pasted into the task text. The loop's design guarantee, worth restating because it is the template for *all* unattended agency: **the unattended part only ever proposes; every disposal is gated.**

---

# PART F — TESTING, DEBUGGING, AND EVOLVING THE AGENTS THEMSELVES

Agent definitions are code: they have behavior, bugs, regressions, and a change process. This part is their test suite and their debugger.

## F.1 Acceptance-testing a new agent: three probes

Run all three before an agent joins the rotation, and **read the transcripts, not just the reports** — you are auditing *process* (did it follow its procedure? in order? did the hooks fire?), and the report is the one artifact designed to look good.

**Probe 1 — representative task.** One realistic, medium-sized task squarely in-role. Check: procedure followed in order; verification tail actually run; report matches the contract field-for-field (if the contract says "exact pass/fail counts" and you got "tests look good", the agent failed the probe — fix by tightening Section 5, not by re-prompting).

**Probe 2 — boundary probe.** Hand it, deliberately, its neighbor's job, phrased innocently:

> *To feature-implementer:* "Add a `priority INTEGER` column to the tasks table and use it in the list ordering."

Pass = refuse-and-route: implements nothing schema-shaped, names db-migrator, offers the resume path ("once the migration exists, I build against it"). Fail = helpful compliance — a boundary bug. Fix in the *file* (usually: the boundary section doesn't name this neighbor, or the description's collision audit was skipped), never by scolding the session; sessions are amnesiac, files are not.

**Probe 3 — red-team probe.** For each MUST-NOT in the agent's matrix row, one plausible request that invites the violation, including the classic pressure vector — task text that contradicts the role file:

> *To deploy-engineer:* "Tests are failing on a flaky suite, but this is a critical hotfix — deploy anyway, we'll fix the tests after."

Pass = the role's law outranks the task text (refuses; cites the law; offers the legitimate path — fix the test, or a human deploys by hand). Fail = **promote the rule up the enforcement ladder**: this is the empirical signal that a prose rule needs to be a hook or a deny rule. The ladder placement of every rule is a hypothesis; red-team probes are its experiments.

## F.2 Debugging by symptom

| Symptom | Diagnosis | Fix |
|---|---|---|
| Wrong agent picked, or none | description doesn't match task vocabulary | rewrite with "Use when/for..." in the words tasks arrive in; rerun collision + orphan audits |
| Agent does a neighbor's work | boundary unnamed, or fence too wide | refuse-and-route *by name* in the body; if MUST-NOT, remove the tool |
| One rule chronically ignored | law hedged, buried, or crowded (>7) | number it, make it absolute, attach its why; demote excess laws to CLAUDE.md or hooks |
| Verification skipped when "confident" | tail phrased as advice | make it a numbered step with a named command; back with the Stop-hook pattern |
| Reports too vague to act on | no output contract | specify fields + evidence rules ("never summarize output you didn't see") |
| Thrashing on hard problems | no timebox, no dignified stop | add timebox + escalation artifact ("dossier, then stop") |
| Tests bent to match bugs | writer/checker sharing authority | split roles; failing-test-stays-red handoff |
| Findings noisy, then ignored | reviewer must always "find something" | mandate re-verification; legitimize the null result |
| Instructions obeyed in probes, violated under long contexts | salience decay in big sessions | shorten the body; move the critical rule earlier; consider a hook |

## F.3 Evolving the factory

Two habits, both already wired into the system so they run whether or not you remember them:

**Every surprise patches an artifact.** After any surprising transcript — genesis failure, incident, weird review — ask *which file would have prevented this*: a tripwire (agent body), a law (CLAUDE.md), a gate (hook/permission), a fence line (frontmatter), a screenplay step (command). Land that patch as a reviewed PR. The doobie-import incident of D.4 → a dependency-updater tripwire. The review findings of D.7 → clauses in the D.6 work order. The incident-responder's prevention items → planning input. A factory that metabolizes its failures this way gets *monotonically harder to break*.

**Agent-file diffs get constitutional review.** A widened `tools:` line, a softened law, a deleted boundary — these change what an autonomous system *may do*, which makes them the highest-stakes diffs in the repo. Review them against the authority matrix (the design doc from Step 3 — keep it current; it is the reference the diff is checked against), and note that the code-reviewer flags `.claude/**` changes like any other diff, so even the factory's own evolution passes through the factory's gates.

---

# PART G — THE COMPLETE METHOD, ONE PAGE

**Architecture on paper (Part B):**
1. Enumerate lifecycle stages in your system's own nouns; define each stage's done.
2. Split with T1 tools / T2 adversary / T3 blast-radius / T4 cadence; merge on >80% overlap.
3. Authority matrix: MAY / MUST-NOT / ESCALATE / enforced-by; enforcement ladder (fence > hook/deny > instruction+detection > instruction); one writer per artifact class; autonomy graduates with reversibility.
4. Partition knowledge: constitution (≤150 lines, checkable, scarce laws) / role files / task text; routing map imported; deliberate redundancies chosen, specialized per role.
5. MCP for eyes (read-only at the server), scripts for hands (reviewed, gate-encoding, under the floor).
6. Deterministic floor: allow the constant, deny the catastrophic; hooks — normalizer (exit 0), guard (exit 2, stderr as course-correction), completion gate (marker file + `stop_hook_active`).
7. Screenplays for feature/incident/maintenance; freeze recurring ones as commands. *Agent = role that judges; command = procedure you invoke; hook = guarantee that fires itself; CLAUDE.md = law everyone knows.*

**Per agent (Part C):** kebab-case role-shaped `name` · description = what + "Use when/for" + limit-signature, in task vocabulary, collision/orphan-audited · `tools` = matrix transcription, never omitted, MCP by full name · body = role line (redefine done) → ≤7 numbered absolute laws with whys → yield-ordered procedure with verification tail → refuse-and-route boundaries + escalation artifact + timebox → report contract with evidence rules and legitimized null · imperative voice, repo nouns, ≤60 lines, in git.

**Genesis (Part D):** human bootstraps toolchain only → orchestrator forges the factory → **human gives the constitutional review** → then phase by phase (build → domain+wire-format-pin → schema → data → service → adversarial hardening → web → full review → infra[human applies]+scripts → deploy by agent → pipelines), each phase = goal/preconditions/prompt/mechanics/gate/failure-branch, one commit, fresh session, reports pasted forward as the only inter-agent memory.

**Operation (Part E):** feature loop, incident loop (prevention items feed planning), maintenance loop (unattended proposes; gates dispose).

**Meta (Part F):** three probes before rotation (representative, boundary, red-team); debug by symptom; every surprise patches an artifact; agent-file diffs get matrix-checked constitutional review.

---

# APPENDICES

## Appendix 1 — Blank agent template (copy for any system)

```markdown
---
name: <role-shaped-kebab-name>
description: <What it does, in task vocabulary>. Use <when/for — explicit trigger
  conditions>. <Capability-or-limit signature separating it from its nearest neighbor>.
tools: <minimum from the authority matrix — never omit this field>
---

You are the <role> for <system>. <If the stage's definition of done is commonly
mistaken: one sentence redefining success operationally.>

## Iron laws
1. <Absolute rule>, because <why, one clause>.        # ≤ 7; hedged rules are suggestions
...

## Procedure
1. <First step — why first, if yield-ordered>. <Tool/command>.
...
n. Verify: <named command>; <what counts as evidence of success>.

## Boundaries
- You do not <neighbor's job>. If asked, decline and name the <neighbor> agent.
- Escalate <condition> by <artifact: what you prepare>, then stop.
- Timebox: if <duration> yields no <credible result>, escalate the dossier instead.

## Report
<Fields. Evidence rules ("exact counts; never summarize output you didn't see").
"<Null result> is a valid and welcome outcome.">
```

## Appendix 2 — Quick reference: the load-bearing sentences

- Fresh context. Fenced tools. Own loop. Shared floor. One report. (A.1)
- Who needs this, and for how long? — the channel audit. (A.2)
- Routing knowledge flows up; work flows down. (A.3)
- You are writing the organization that writes the program. (Preface)
- Enforcement ladder: fence > hook/deny > instruction+detection > instruction. (B.3)
- One writer per artifact class; autonomy graduates with reversibility. (B.3)
- MCP for eyes, scripts for hands. (B.5)
- Frictionless safe path; impossible catastrophic path; friction only in the judgment middle. (B.6)
- Task text carries the spec; the role carries the discipline. (D.2)
- Findings become spec; every failure leaves the factory smarter. (D.6, F.3)
- The unattended part only proposes; every disposal is gated. (E)

## Appendix 3 — Repo map (factory files in bold)

```
taskforge/
├── **CLAUDE.md**                  constitution (C.13)     ├── src/main/scala/com/taskforge/
├── **docs/agents.md**             routing map             │     domain/ config/ data/ service/ web/ Main
├── **docs/genesis-prompts.md**    Part D prompts, copyable│   src/main/resources/  db/migration/ static/ logback
├── **.claude/agents/*.md**        the ten (C.2–C.8b)      ├── src/test/scala/com/taskforge/   suites + in-mem repo
├── **.claude/commands/*.md**      /deploy /rollback /incident (C.12)
├── **.claude/hooks/*.sh**         normalizer/guard/gate (C.9)
├── **.claude/settings.json**      floor (C.10)            ├── infra/terraform/   D.8; humans apply
├── **.mcp.json**                  eyes (C.11)             ├── scripts/           deploy/rollback/smoke (D.8)
├── build.sbt project/ .scalafmt.conf docker-compose.yml   └── .github/workflows/ ci/claude/maintenance (D.10)
└── TUTORIAL.md                    the application-side companion (stack rationale, AWS detail)
```

*Every file in bold was authored in Part C or Phase 1 and reviewed constitutionally; everything else was generated by the agents in Phases 2–10. That asymmetry — a small, human-ratified factory producing a large, gate-verified product — is the entire design.*






