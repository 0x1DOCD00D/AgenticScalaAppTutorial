# What the Writer of Agents Must Know

### A standalone section: the body of knowledge required to write agents that generate an entire software application

*Companion to `AGENTS-TUTORIAL.md`; section references (C.4, D.1, F.3, ...) point into that document. This section stands before its method the way a preflight briefing stands before a checklist: the method tells you what to do; this tells you what you must know for the method to work in your hands.*

---

Writing agents that generate a *whole application* — not snippets, not one-off scripts, but a system with a build, tiers, tests, infrastructure, and an operating life — draws on seven distinct domains of knowledge. Some you likely have; some you may need to acquire; one (the third) is genuinely new to this discipline. For each domain, four things are stated: **what** you must know, **why** the factory fails without it, **how much is enough** — a litmus test, because the honest answer to "must I master this?" is usually "no, but you must clear a specific bar" — and **where** the knowledge is exercised in the tutorial, so you can see it working.

One unifying principle first, because every domain below is an instance of it:

> **You can only delegate what you can specify, and you can only accept what you can judge.** The agent-writer's knowledge concentrates at two points — the *specification* going in and the *gate* coming out — and can be economized everywhere in between. Where your knowledge is deep, gates can be light; where your knowledge is shallow, gates must be mechanical (compilers, tests, plans), precisely because your eyeball is not a reliable one there.

---

## 1. The target stack — at architect depth, not implementer depth

**What you must know.** The architecture styles available for the kind of application being generated (tiered, hexagonal, event-driven, ...); the specific frameworks and libraries chosen and why; and — most valuable of all — the stack's *sharp edges*: the places where plausible-looking code is wrong. In the TaskForge stack those are things like doobie's column-order/field-order coupling, upickle's default-encoding behaviors, http4s's total-vs-partial error-handler distinction, Flyway's checksum immutability, and ECS's old-and-new-code-run-simultaneously rolling deploys.

**Why the factory fails without it.** Three load-bearing artifacts can only come from this knowledge: the architecture invariants in CLAUDE.md (you must *choose* the tier boundaries and phrase them checkably); the code-reviewer's axis list (Tutorial C.4 — a review checklist is precisely a list of your stack's sharp edges); and the dependency-updater's tripwires (C.8). An agent-writer who does not know that old and new code run simultaneously during a rolling deploy *cannot write* the db-migrator's backward-compatibility law — and no amount of prompting skill substitutes, because the law's content is domain knowledge, not phrasing.

**How much is enough.** You do not need to be able to write every line the agents will write. You need three capabilities: **choose** the architecture and defend it; **specify** each component's contract (the genesis prompts in Tutorial Part D are the test — could you write the Phase-6 work order for *your* stack?); and **judge** output at review speed — recognize a wrong wire format, a leaked tier dependency, an unsafe migration in one read. Where you cannot yet judge at that speed, the compensating move is mechanical: pin the contracts in *executable* form early (TaskForge freezes its JSON wire format in `JsonCodecSuite` in Phase 3, before anything depends on it), so the test suite judges what your eye cannot.

**Exercised in:** CLAUDE.md's tables (C.13), the reviewer axes (C.4), the tripwires (C.8), every Part-D work order.

## 2. The full lifecycle — as an operator, not only a developer

**What you must know.** Build systems and dependency management; the testing pyramid and what makes suites fast and deterministic; database migrations and expand/contract; containerization; CI/CD; deployment topologies, health checks, rollbacks; observability (logs, metrics, alarms); incident response; dependency and CVE hygiene.

**Why the factory fails without it.** The method's very first step (Tutorial B, Step 1) demands you enumerate lifecycle stages *concretely, with definitions of done* — and you cannot decompose what you have never operated. This is the most common gap profile in practice: a strong developer who has never carried a pager writes a vivid feature-implementer and a fictional incident-responder ("check the logs and fix the issue"). The real responder's triage tree — stopped-task reasons first, *because they name the killer* — is operational scar tissue, not something derivable from first principles at a desk.

**How much is enough.** You must have operated *something like* the target: deployed it, watched it fail, rolled it back. If you have not, the honest path is to rehearse before authoring: run the deploy failure branch deliberately in a sandbox (break a deploy on purpose, walk the rollback), and then write the operating agents from what you saw. An operating agent authored from imagination is worse than none, because it acts confidently on a fictional model of production.

**Exercised in:** Steps 1–3 of Part B; the deploy/incident/maintenance agents (C.6–C.8); Phases 8–10 of genesis.

## 3. How LLMs fail — the catalog that drives every design rule

**What you must know.** This is the discipline's own body of knowledge — the empirical failure modes of model-driven work. The writer of agents must know this catalog the way a bridge engineer knows load failures, because every structural choice in an agent system is a counter to one of its rows:

| Failure mode | What it looks like | Design counter (where in the tutorial) |
|---|---|---|
| **Amnesia** | each session and each subagent starts knowing nothing | memory files + role files + reports-as-interfaces (A.1–A.3) |
| **Salience decay** | rules obeyed early in a long context get ignored late | short bodies, ≤7 laws, hooks for the critical rules (A.5, B.6) |
| **Helpfulness bias** | unrequested scope: extra libraries, "improved" APIs, the neighbor's work done too | scarce emphatic laws; refuse-and-route boundaries; "report what you did NOT do" (C.2) |
| **Confabulated APIs** | plausible calls against stale or imagined library versions | pinned versions; the compiler as first oracle; the D.4 failure branch; lookups routed to research-capable agents |
| **Task-text sycophancy** | "deploy anyway, it's urgent" obeyed over the role's law | role outranks task by design; red-team probes (F.1); rules that fail probes get promoted to hooks |
| **Premature completion** | "done" declared with tests unrun or failing | verification tails in every procedure; the Stop-hook gate (C.9) |
| **Test-bending** | the assertion quietly adjusted to match the bug | one writer per artifact; failing-test-stays-red handoff (C.3) |
| **Thrash loops** | endless retries on a hard problem, burning context | timeboxes + escalation artifacts (C.7) |
| **Report optimism** | summaries rosier than the transcript | evidence rules ("exact counts; never summarize output you didn't see"); transcript-reading in probes (F.1) |

**Why the factory fails without it.** Without this catalog you will write agents the way people write first prompts — as if instructing a diligent junior who remembers everything, never rationalizes, and never optimizes for looking finished. Every structure in the tutorial's Parts B–C exists because one of these rows exists; an author who doesn't know the rows produces the structures only by cargo-cult, and drops exactly the one that mattered.

**How much is enough.** Two tests. First: for any rule in any agent file, you can *name the failure mode it counters* — that is the difference between designing and decorating. Second: you treat the catalog as open — your own transcripts will add rows, and the tutorial's F.3 habit ("every surprise patches an artifact") is the mechanism by which they do.

**Exercised in:** everywhere. This table is the skeleton key to the whole tutorial.

## 4. The runtime's mechanics — exactly, not approximately

**What you must know.** The concrete machinery, as your platform actually implements it: where agent definitions live and how frontmatter is parsed; tool-fencing semantics — including the trap that an *omitted* `tools:` field inherits every tool; hook events, matcher syntax, the stdin JSON payload, and exit-code semantics (0 proceed, 2 block-with-stderr-shown-to-the-model); permission-rule syntax and precedence (deny beats allow); MCP configuration scopes, environment-variable expansion, and tool naming (`mcp__server__tool` — the names that go into fences); memory-file locations and import syntax; and headless/CI invocation with its flags.

**Why the factory fails without it.** The deterministic floor (Tutorial B.6) is built entirely from these details, and the floor fails *silently*: a matcher that never matches produces no error — just a guard that never guards. Approximate knowledge elsewhere costs a retry; approximate knowledge here costs an invisible hole in the safety system that you discover only when something walks through it.

**How much is enough.** You do not memorize the mechanics — you *verify* them: keep the platform documentation open while authoring the factory files, and then **test the floor empirically** before trusting it (the tutorial's D.1 gate: probe the guard hook with a forbidden command and watch it block; trip the Stop-hook bounce once on purpose). The bar is simple: mechanism you have not seen fire is mechanism you do not have.

**Exercised in:** C.9–C.11 (hooks, settings, MCP); D.1's verification gate.

## 5. Verification design — building the sensor system

**What you must know.** Agents perceive the world *only through command output*; therefore the quality of your oracles bounds the quality of everything the factory produces. You must know how to construct feedback that is **fast** (seconds to minutes — slower, and agents will be tempted to skip it, and orchestrations will crawl), **deterministic** (a flaky oracle teaches agents to ignore red — the most corrosive lesson an automated system can learn), and **binary** (green/red, not "looks fine"). The working toolbox: type systems and compilers as free first reviewers — and choosing stacks partly *for* this (TaskForge's `.query[Task]` turns schema drift into compile errors); dependency-free unit suites (the in-memory repository exists for exactly this); contract-pinning tests (the wire-format suite); dry-run previews for irreversible acts (`terraform plan` as the reviewable artifact, with apply gated to humans); end-to-end smoke tests with loud failures; and scripts that print greppable step markers, because *stdout is the agent's observability*.

**Why the factory fails without it.** Every phase of genesis ends in a gate, and every gate is one of these oracles. The allocation of human attention follows oracle strength: where oracles are strong, the human gate is a skim; where oracles are weak (the constitutional review of the factory files; reading the Terraform plan), the human gate is line-by-line. Get the oracles wrong and you either drown the human in review or ship what nobody judged.

**How much is enough.** For each artifact class the agents will produce, you can name its oracle and that oracle's latency *before* writing the producing agent. If no oracle is nameable for some class, either build one first, or consciously accept that a human gate must carry that class forever — the unacceptable third option is noticing the gap after the agent has been producing that class for a month.

**Exercised in:** every Part-D gate; `sbt check` as the single definition of done; the Stop hook's marker-file design.

## 6. Safety and irreversibility analysis

**What you must know.** The security engineer's habits, applied to agency: for every operation the system can perform, know its *undo* (no undo → it is in the most dangerous class); reason about blast radius; apply least privilege as the default posture, not an aspiration; layer defenses so that no single failure — including a failure of your own prompting — reaches production data; and design escalation as a first-class outcome with a defined artifact (a prepared migration plus blast-radius analysis; an evidence dossier), because agents without a dignified way to stop will improvise a way to proceed.

**Why the factory fails without it.** This knowledge is what fills the authority matrix (Tutorial B.3), and the matrix *is* the safety design — every fence, deny rule, and escalation clause merely transcribes it. It is also what makes graduated autonomy principled rather than vibes: the incident-responder may restart, roll back, and scale *because those have undo columns*; it must escalate data repairs, schema rollbacks, and security findings *because those do not*.

**How much is enough.** One question, applied to every autonomous permission you grant: **"if the model does this at the worst possible moment, with the worst plausible arguments in its context, what is the recovery?"** Any cell where you cannot answer moves to must-escalate until you can. This test takes minutes per agent and is the highest-value review you will perform.

**Exercised in:** B.3 and B.6; the deny list (C.10); the read-only MCP posture (C.11); every escalation clause in Part C.

## 7. Specification writing — the craft the prompts run on

**What you must know.** The old, unglamorous skill that agentic engineering suddenly repays at full rate: decomposing intent into stages with acceptance criteria; writing requirements that are *checkable* ("May depend / Must NOT depend", not "keep the layers clean"); pinning interfaces early and letting interiors float; stating non-goals explicitly ("do not write any application code yet"); and sequencing work so that each stage's output is the next stage's verifiable input. Study the Phase-6 work order in Tutorial D.6 with this lens: it is a requirements document — numbered deliverables, exact behaviors, named status codes — and it works not by prompt magic but by specification discipline.

**Why the factory fails without it.** All three channels of the agent system (memory, role files, task text) are specification media: CLAUDE.md specifies the system's invariants, an agent body specifies a role, task text specifies a job. A writer who cannot specify produces agents that cannot be judged — vague in, unjudgeable out — and then mistakes the resulting churn for model weakness.

**How much is enough.** The screenplay test, applied personally: for any feature you would ask the factory to build, can you write the sequence of work orders whose outputs chain, each with its acceptance check? When a step's acceptance check eludes you, that is not a prompting gap — the requirement itself is not yet understood, and without this discipline an agent would have discovered that for you, expensively, in the middle of building the wrong thing.

**Exercised in:** every prompt in Part D; `docs/genesis-prompts.md` is this skill's worked answer key.

---

## 8. What you do *not* need to know

Just as important, because over-preparing is a real failure mode that delays teams for months: you do **not** need encyclopedic API knowledge — the compiler, the documentation, and research-capable agents cover the interiors, and the tutorial's D.4 incident is the proof (a stale import was caught by the *system* — compile oracle plus escalated lookup — not by anyone's memory). You do **not** need to be able to hand-write every artifact the factory produces — only to specify and judge them, and where judging is hard, to build the oracle that judges for you. You do **not** need machine-learning theory — the failure catalog of section 3 is behavioral knowledge, learned from transcripts, not from papers.

Your knowledge belongs at the boundaries: specifications going in, gates coming out. The agents fill the interior — that asymmetry is the entire economic argument for building the factory at all.

## The self-assessment

Score yourself before starting the method; treat any "no" as a to-do, not a disqualification — each row names its remedy:

| # | Domain | Litmus test | If short, compensate by |
|---|---|---|---|
| 1 | Stack, at architect depth | Can you judge wrong output at review speed? Name five sharp edges of your stack? | pin contracts as executable tests early; strengthen oracles where your eye is weak |
| 2 | Lifecycle, as operator | Have you deployed, broken, and rolled back something like the target? | rehearse the failure branch in a sandbox before writing the operating agents |
| 3 | LLM failure catalog | Can you name the failure mode behind each rule you write? | study section 3; read your own transcripts, not just the reports |
| 4 | Runtime mechanics | Have you watched each hook and fence actually fire? | author with the docs open; probe the floor empirically before trusting it |
| 5 | Verification design | Can you name the oracle and its latency for every artifact class? | build the missing oracle before the producing agent |
| 6 | Irreversibility analysis | For every autonomous grant: what is the undo at the worst moment? | move unanswerable grants to must-escalate |
| 7 | Specification writing | Can you chain work orders with acceptance checks for a real feature? | write the screenplay on paper until you can |

Seven yeses and you are ready for the method — and, more to the point, ready for the moment the method meets reality: the failed deploy, the surprising transcript, the confabulated API. Those moments are not the system breaking; they are the system *teaching*, and this body of knowledge is what lets you hear the lesson and patch the right artifact.
