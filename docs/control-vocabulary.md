# The control vocabulary of the prompts

Every prompt in this system is written in a deliberately chosen working vocabulary. The
prompts live in `docs/genesis-prompts.md`, the gate lines between phases, `CLAUDE.md`,
`docs/agents.md`, the agent bodies in `.claude/agents/`, and the routing corpus in
`docs/routing-tests.md`. A phrase like refuse a dirty tree is not how a layman would state
the constraint (something like "make sure there are no unsaved changes first"), and the
difference is not decoration. Each of these words does one of three jobs: it imports a
term of art whose training-data register carries exact entailments the lay phrasing
lacks; it names a failure, a default, or a direction precisely enough to be checkable; or
it serves as a stable token that recurs identically in the prompt, the agent's law, and
the agent's report, so that humans can grep for it and the orchestrator can key on it.

The selection test for a row: a phrase earns one only if replacing it with a lay synonym
would change what an agent does.

## Authority and disposal words

| Phrase | Where | Semantics here | Why this word |
|---|---|---|---|
| authority matrix | Phase 0, Phase 1 | The upstream table of agent by authority class: may do autonomously, must never, must escalate, enforced by. Sole source from which agent files are derived. | Matrix, not list: two dimensions force every cell to be decided, so silence is impossible. Authority, not responsibility: it governs permission and prohibition, not job descriptions. |
| constitutional | Phase 0, Phase 1, CLAUDE.md | The class of files (.claude/**, CLAUDE.md, docs/agents.md, .mcp.json) whose change alters who may do what. Requires human ratification and a restart. | Imports the legal idea that rules about rules need a higher amendment procedure than rules themselves. One word carries the whole two-tier change model into any prompt that uses it. |
| in force | Phase 0 | Distinguishes a file existing on disk from a file governing behavior. Agent files load at session start, so a ratified change governs only the next session. | Legal register. "Active" or "live" would not force the restart insight: the running session still holds the old constitution in memory even after the commit. |
| ratify, ratification | Phase 0, gate lines, docs/agents.md | The human disposal act for constitutional diffs: read the diff, commit, restart. A composed procedure, deliberately not a command. | "Approve" is chat register and models use it loosely. Ratify is rare enough that its appearance always denotes this exact procedure, and rare tokens make reliable grep keys. |
| never self-ratifies | Phase 0, factory-engineer description | The factory-engineer produces constitutional diffs but may not commit them or treat them as effective. | Negating ratify bans the specific composed act, including the subtle version (committing its own diff). Negating "approve" would only ban an opinion. |
| present the diff and stop | Phase 0, Phase 1 | Producing the proposal ends the turn. Applying it is not the agent's act. | Models continue helpfully by default. Stop is an explicit turn-boundary token, and pairing it with the artifact defines done as shown, not applied. |
| HUMAN GATE (constitutional) | Gate lines between phases | A step the human performs; the script cannot pass through it automatically. | Gate makes the checkpoint a named, first-class step that phase tables and the orchestrator can key on. The parenthetical states which disposal act this gate is. |
| plans only, human applies; I will apply it myself | Phase 1, Phase 8 | terraform plan is the infra-engineer's terminal artifact; apply is the human act, run in the human's terminal. | Stating the split in first person inside the prompt makes it part of the task definition, so the agent's own success criteria end at the plan. |
| written sign-off | docs/agents.md, db-migrator body | The disposal act for destructive DDL: an explicit recorded yes. | Written excludes conversational approval and silence. The record is what a postmortem later checks. |
| destructive DDL | Phase 1, db-migrator body | DROP, TRUNCATE, type narrowing, anything that loses data. Prepared with analysis, shipped only after sign-off. | Classifies statements by consequence, not by verb list, so it covers cases the guard regex misses. DDL keeps it schema-scoped; deleting rows stays application behavior. |
| blast-radius analysis | db-migrator body, proposal diagram | The report section enumerating what is affected and what becomes unrecoverable if the DDL ships. | Incident-engineering register: the bounded set of things a failure can reach. Demands enumeration, not reassurance. |
| escalate, escalation policy | Phase 1, docs/agents.md | Stop, report state, hand the decision up. Boundary lines listed once per agent. | The ops register gives it exact content: halt plus handoff. "Ask if unsure" is satisfied by a rhetorical question followed by proceeding anyway. |
| autonomous; scale [1,4] | docs/agents.md, incident-responder body | The whitelist of acts the responder may take without a human, with numeric bounds. | Autonomy defined as an enumerated list with ranges turns "use judgment during incidents" into checkable limits. The interval notation is unambiguous to reader and model alike. |
| stated once, here | docs/agents.md | The escalation policy lives in exactly one place; agent files reference it rather than restate it. | Restated copies drift independently. Naming the single location in the heading makes any second copy a reviewable defect. |
| plant the seed; seed | Phase 0 heading | The one hand-written file (factory-engineer.md) from which every other artifact is agent-made. Genesis commits carry the genesis prefix. | The biological metaphor encodes both minimality (one file) and growth (everything else follows), and marks the unique sanctioned exception to nothing-hand-written. |
| the factory builds the factory | Phase 1 heading | The factory-engineer's first job is creating the other nine agents plus the constitution: self-application. | The industrial metaphor makes the closure property thinkable: if agents make everything, something must make agents, and that something must itself be an agent. |
| orchestrator | Phase 0, docs, tutorial | The main session: routes work, holds gates, owns no specialty, and during genesis writes exactly one file. | Coordinates performers without playing an instrument. "Manager" or "main chat" would not carry the no-specialty-inline rule. |

## Scope and fence words

| Phrase | Where | Semantics here | Why this word |
|---|---|---|---|
| one writer per artifact class | docs/agents.md, Phase 1 | A uniqueness constraint: each artifact class has exactly one agent that may create or modify it. | Writer, not owner alone: write access is what conflicts and what reviews trace. Uniqueness makes both accountability and routing decidable. |
| fence | Phase 0, Phase 1, tutorial | The tools line in frontmatter: a capability boundary enforced by the harness, not by agreement. | A fence works whether or not the fenced-in party cooperates, which is exactly the contrast with instructions. "Tool list" describes the syntax; fence describes the guarantee. |
| least privilege by default | Phase 0 law 3 | Tools lists start from nothing and add only what the role requires. | Security term of art with exact entailments (deny by default, capabilities granted not assumed). "Only give needed tools" invites reasoning about need; the term says start from zero. |
| no omitted tools: fields | Phase 0 law 3 | Every agent file must state its tools line, because omission inherits every tool. | The dangerous case is the default, not a wrong value. Naming the omission as the violation is the only way to ban a default. |
| reviewer-class | Phase 0 law 3 | Any agent whose job is judgment gets Read, Grep, Glob, Bash and no write tools. | Naming the class rather than the one current reviewer makes the rule bind future reviewers the factory might create, closing the loophole before it exists. |
| Edit but not Write | Phase 1 (dependency-updater fence) | The updater may modify existing lines but cannot create files. | The tool split maps exactly to its scope: bumping a version edits a ledger line; adding a dependency or module creates structure, which belongs to build-engineer. |
| read-only at server level; --readonly; ALLOW_WRITE=false | Phase 1 (.mcp.json spec) | Write prevention configured in the MCP server process itself. | "At server level" places the enforcement below the model entirely: it holds even if every instruction fails, unlike a read-only rule written in prose. |
| version ledger only | Phase 1, dependency-updater body | The named vals block in build.sbt is the updater's entire writable surface. | Ledger frames versions as bookkeeping entries in a record, editable entry by entry, and makes structure versus entry the scope boundary. "The versions" would not exclude scalacOptions or module lists as cleanly. |
| executes scripts it does not author | Phase 1, docs/agents.md | deploy-engineer runs scripts/deploy.sh; infra-engineer writes it. | The author/execute pair is classic separation of duties. One clause creates two non-overlapping ownership rows and blocks the deployer from patching the script mid-incident. |
| ONLY, only via (upickle the ONLY JSON library; depending ONLY on the trait; secrets only via Secrets Manager; sql interpolators only) | Phases 1, 2, 5a | Exclusivity constraints that close off alternatives, placed adjacent to the noun they restrict. | Models satisfy "use X" while also adding Y. Only is the token that excludes Y. Capitalization survives paraphrase and flags the word as load-bearing. |
| exactly one file ... and nothing else; No application code | Phase 0, Phase 1 | Cardinality and negative-scope closers: the phase's output set is closed, not open-ended. | Counts are mechanically checkable after the fact (ls, git status); adjectives like "minimal" are not. The negative clause pre-empts the model's reflex to helpfully scaffold extras. |
| iron laws | Phase 0 | The numbered body rules that survive any conflicting task text. | "Guidelines" invite weighing. Iron imports non-negotiability, so on conflict the agent treats the law as senior rather than as one consideration among several. |
| transcribe (the matrix) | Phase 0 law 1 | Agent files are projections of the matrix; policy never originates in a file. | "Implement" or "follow" permit interpretation. Transcription is copying, which makes any file/matrix divergence a defect by definition, reviewable cell by cell. |
| widen a fence, soften a law | Phase 0 law 1 | Asymmetric change control: narrowing is routine; widening or weakening requires the matrix to change first. | Plain "change" is directionless. These verbs name the only dangerous direction, so the law needs no case enumeration. |
| channel discipline | Phase 0 law 4 | Three channels with three lifetimes: agent body (timeless role), CLAUDE.md (universal facts, capped at 150 lines and 8 hard rules), task text (one run). Content goes to the channel matching its lifetime. | Discipline signals an allocation rule to enforce, not a style preference. Channel unifies three destinations into one system. The numeric caps exist because budgets are enforceable in review and "keep it short" is not. |
| timeless (role files) | Phase 0 law 4 | Nothing session-specific or dated in an agent body. | A one-word test applicable to any sentence: would this still be true in a year? "General" and "reusable" have no such test. |

## Routing words proper

| Phrase | Where | Semantics here | Why this word |
|---|---|---|---|
| routing-grade description | Phase 0 | The description is written for the automatic delegation matcher; the first sentence must carry the match alone. | Grade imports fitness for purpose: a description can be accurate and still fail routing. The phrase tells the factory which audience the sentence serves, a machine matcher, not a human reader. |
| FROM SCRATCH | Phase 0, Phase 1, Phase 2, descriptions | The agent claims greenfield creation of its artifact class, not just maintenance. | Routers match user phrasings like "set up X" and "create X". Without this token the description reads maintenance-only and greenfield requests fall to the main context. Capitalization marks it as a routing token, not prose. |
| Use the X agent to ... | Every genesis phase | Explicit delegation that bypasses description matching. | During genesis the router cannot be trusted: the descriptions are themselves under construction. The explicit form is the one invocation Claude Code honors deterministically. |
| boundary-probe | Phase 1 gate | After ratification, send an agent a request belonging to a different agent and verify it declines and names the owner. | Probe is testing register: a stimulus chosen to reveal one property. Boundary says the property under test is the fence, not competence. |
| must route to | Gate lines, routing corpus | The expected-agent assertion of a routing test row. | Keeps the vocabulary aligned with the mechanism under test, so failures read as routing defects to fix in descriptions, not agent misbehavior to fix in prompts. |
| declines and names the owner | CLAUDE.md, docs/agents.md | Refusal has a mandatory second half: the redirect. | A bare decline strands the request. Naming the owner converts a fence bounce into routing information the orchestrator can act on immediately. |
| hard negatives | routing corpus | Test rows whose vocabulary belongs to one agent but whose work belongs to another (migrate to the new sbt version). | ML evaluation term of art: the near-misses that actually measure a matcher. "Tricky cases" would not tell a maintainer what makes a good row. |
| polysemy registry | routing corpus | Each ambiguous word (migration, build, update, deploy, test, version, monitor, rollback) has one bare-form owner; every other description must qualify it. | Names the failure (one word, many senses) instead of the symptom (misrouting). Registry implies a single authoritative allocation, like a port registry. |
| never repair by naming agents in prompts | routing corpus header | Misroutes are fixed in descriptions, then verified by the corpus. | A prompt workaround fixes one session; a description fix routes every future session. The sentence bans the locally easier repair because it does not accumulate. |

## Evidence, determinism, and report words

| Phrase | Where | Semantics here | Why this word |
|---|---|---|---|
| floor | Phase 0 law 5, tutorial | The deterministic layer (permissions plus hooks) that holds even when every instruction fails. | The spatial metaphor encodes the ordering: instructions can raise behavior above the floor, nothing can fall through it. A safety net catches after failure; a floor prevents entry. |
| floor invariants that may never be removed | Phase 0 law 5 | The named list (guard patterns, stop_hook_active check, formatter exit 0, the deny rules) that survives every factory rewrite. | Invariant imports the loop-invariant discipline: a property preserved by every iteration of change, which is exactly the factory's relationship to the floor. |
| validate mechanically | Phase 0 procedure | Run deterministic checks (json parse, bash -n, chmod +x), not self-assessment. | Mechanically excludes the model's judgment from the verification step. Without it, "validate" tends to produce a paragraph of confident prose instead of exit codes. |
| definition of done (sbt check as) | Phase 1 | One command whose exit 0 is what done means for any code change; also the source of the stop hook's marker. | Borrowed from Scrum, where it means an explicit agreed checklist. Binding it to one command converts a social agreement into an exit code. |
| markTestRun; the .last-test-run marker | Phase 2 | The build touches a marker file when tests pass; the Stop hook checks its timestamp. | Hooks can only check the filesystem and commands, so "tests ran" must become a filesystem fact. Marker names a file whose only meaning is its timestamp. |
| stop_hook_active guard | Phase 0 law 5, Phase 1 | The Stop hook must check this flag or the hook and the agent ping-pong forever. | Naming the flag in the law makes the loop-prevention check a listed invariant rather than an implementation detail a rewrite might drop. |
| exit 2, human-must-do-this message | Phase 1 hooks spec | PreToolUse guards block with exit 2 and put an instruction on stderr, which the model reads as course correction. | The phrase makes stderr an instruction channel, not an error dump: the message must say what the human does instead, converting a block into a routed handoff. |
| exit 2 with evidence | Phase 8 scripts prompt | Failure paths carry proof (the observed revision, the failing output), not just a nonzero code. | Evidence binds the failure to the report contract so orchestrator and human can dispose of it. A bare exit code says only that something happened. |
| verified findings only | Phase 7 | The reviewer reports a finding only after re-checking it against the code, citing lines. | Model reviewers overproduce plausible speculation. Verified gates the report on a second look, trading recall for precision, and precision is what the human gate consumes. |
| verbatim | Phase 5a | Paste the literal code (the transition set) into the report, not a summary. | Reports are the only memory; verbatim makes the report carry the artifact itself so the next gate can diff it without opening files. |
| reports are the only memory | genesis-prompts header | Agents share no context; the pasted report is the sole inter-agent channel, so its content is an API. | Stated as a memory model, not a workflow tip, it explains why every prompt demands specific report fields: what is not in the report does not exist for the next agent. |
| report ... any deviation from this spec; say so in your report | Phase 2, Phase 4a | Deviating is legal, hiding it is not. | Gives the model a compliant path to disclose drift instead of suppressing it to appear successful. Disclosure demands work where prohibitions fail. |
| vacuous (your inspect step is vacuous this once) | Phase 4a | A standing procedure step that cannot apply on this run (no live database yet) must be declared, not faked or silently skipped. | Logic register: true but empty. It suspends one step for one run without teaching the agent that the step is optional in general. |
| leave any failing test failing and report it | Phase 5b | A red test revealing a real defect is the deliverable; the fix belongs to the code's owner. | Models reflexively fix what they break. The sentence bans the reflex and re-routes the fix through the report, preserving the one-writer rule under pressure. |
| loud failures | Phase 8 (smoke-test) | Failures print expected versus observed and exit nonzero; no warnings, no silent skips. | Loud is the inverse of the real hazard: a green pipeline over a broken check. It directs effort at the failure path, which prompts almost never mention. |
| greppable ==> step markers | Phase 8 | Every script step prints a ==> prefixed line. | Greppable names the consumer of the output (a program or a grep, not a reader), which changes how the model formats. The literal token is specified so all scripts share one marker. |
| no interactive prompts | Phase 8 | Nothing may block on stdin; scripts run under CI, the orchestrator, and claude -p. | Names the concrete failure (a read waiting forever in a headless run) rather than the vague aspiration "automation friendly". |
| fresh session; restart | Phase headers, gate lines | Begin with a new context so the just-ratified constitution is the one in force and no stale reasoning leaks forward. | Fresh names the mechanism (context loads at session start). "New chat" says what to click, not why it matters. |
| one phase per session; one commit per phase | genesis-prompts header | Context hygiene and bisectable history: each gate maps to exactly one commit. | The 1:1:1 pairing of phase, session, and commit makes ratification meaningful: what you commit is exactly and only what the phase produced. |

## Engineering words inside the task specs

| Phrase | Where | Semantics here | Why this word |
|---|---|---|---|
| refuse dirty tree | Phase 8 (deploy.sh) | The script exits before building if git status --porcelain is nonempty, so every image traces to a commit SHA. | Dirty tree is the git term of art with an exact test. Refuse places the check before any side effect; "warn" or "check" would let the build proceed. |
| circuit breaker makes bare stable ambiguous | Phase 8 (deploy.sh) | aws waits services-stable also succeeds when ECS auto-rolled the deploy back, so the script must verify the live revision equals the new one. | Bare marks the anti-pattern (trusting the wait alone); ambiguous states why: one exit code, two outcomes. Six words encode an operational war story the model would otherwise not anticipate. |
| idempotent | Phase 4b (Flyway on boot), BLOCKED-ON protocol | Running the step twice equals running it once; boots and retries are safe. | Exact mathematical term the model maps to known idioms (checksums, already-applied detection, IF NOT EXISTS). "Safe to re-run" is the definition; the term also pulls in the implementations. |
| immutable once applied | docs/agents.md, Phase 4a header comment | An applied V*.sql is never edited; changes are new files; an edit is a CRITICAL review finding. | Immutable imports the append-only ledger model that matches Flyway's checksum behavior exactly. "Do not edit" states the rule; immutable states why it can never be relaxed. |
| expand and contract | db-migrator body, routing corpus | Zero-downtime schema pattern: add the new alongside the old, migrate readers, remove the old later. | Naming the two ends of the pattern gives "add a column" and "drop a column" different risk classes automatically, which is what the escalation rule needs. |
| drift | Phase 4b report demand, CLAUDE.md | Two representations of one fact diverging silently (schema versus case class, console versus terraform). | The ops term implies gradual unnoticed divergence, which demands a detector rather than care. The prompt asks what catches it, making the detector part of the deliverable. |
| honest scopes | build-engineer law | A dependency's declared scope must match use: compile if the tier calls its API, Runtime if loaded reflectively, Test if tests only. | Frames a technical rule as a truthfulness norm the model already weights heavily. The live-run Flyway drift occurred exactly where the prompt's scope wording was ambiguous. |
| pin exact versions as named vals | Phase 2 | No ranges, no latest; each version is a val the ledger owner bumps one line at a time. | Pin is the ecosystem term for freezing resolution. Named vals names the concrete structure that makes the ledger greppable and diffable per bump. |
| determinism: APP_VERSION and CI only | build-engineer law, Phase 2 | The build's output depends on the repo plus exactly two declared environment variables. | Enumerating the allowed nondeterminism is stronger than "make builds reproducible": any third env var read is a violation by count, not by judgment. |
| adversarial hardening | Phase 5b heading | The test-engineer assumes the implementation is wrong and searches for the input that proves it. | One word flips the objective from make it pass to make it fail. "Thorough" raises effort; adversarial changes direction. |
| mission categories | Phase 5b, test-engineer body | The enumerated failure classes (boundaries, concurrency, error paths, encoding) the engineer must cover and report against. | Coverage against a named checklist is auditable; "test more" is not. Mission ties the checklist to the agent's identity so it persists across tasks. |
| rules as data, not if-trees | Phase 5a | Legal transitions live in a Set of (from, to) pairs; code checks membership. | Names the target and the anti-pattern in one clause. Data can be printed verbatim in the report and diffed against the spec; an if-tree cannot. |
| one-line-thin (routes) | Phase 6 | Each http4s route parses, delegates, encodes; no logic in the web tier. | A measurable thickness test: a route that cannot fit one delegation line fails it. "Keep routes simple" has no failure condition. |
| unmatched throwables pass through with stack traces intact | Phase 6 | Error middleware is partial (recoverWith): mapped errors get status codes, unknown ones keep their diagnostics. | Names the total-function hazard: a catch-all that converts every bug into a generic 500 and destroys the evidence. The clause came from a real MatchError found in review. |
| do NOT hand-roll Meta[Instant] | Phase 4b | Use doobie.postgres.implicits for java.time; do not write a custom Meta. | Negative instructions work when they name the exact tempting artifact rather than a category. This one encodes a real compile failure (javasql removed in RC12) back into the spec. |
| diff against the empty tree | Phase 7 | The review scope is the entire repository: everything is new relative to git's empty tree object. | Git's own term makes "review the whole repo" a mechanical statement with a defined baseline, not a mood. |
| safe patch and minor only | Phase 10 (maintenance.yml) | The headless updater bumps patch and minor versions; majors go to a human-initiated session. | Semver register: patch and minor carry exact compatibility semantics that "small updates" lacks. |
| no long-lived keys; OIDC role-to-assume | Phase 10 (ci.yml) | CI assumes an AWS role via OIDC; no static credentials stored as secrets. | Names the hazard class (a credential that persists and can leak) rather than the mechanism alone, so the rule survives tooling changes. |
| headless (claude -p) | Phase 10 | The maintenance loop runs an agent as a batch job: no human, no stdin, machine-readable output. | Headless names the constraint that shapes the whole workflow file; every other flag in the invocation follows from it. |
| scoped --allowedTools and --max-turns | Phase 10 | The cron-launched session runs with an explicit tool whitelist and a turn budget. | Scoped signals these flags are the headless equivalent of the fence: the same least-privilege model applied to a session no one is watching. |
| open a PR only if the tree changed | Phase 10 | The maintenance workflow's no-op path is silence, not an empty PR. | The condition is a git test, not a judgment call, encoding the idempotence norm at the workflow level. |

## How the words were chosen

Five rules generated this vocabulary. First, prefer a term of art over a paraphrase
whenever the term's register carries entailments for free: least privilege, idempotent,
hard negatives, blast radius, semver's patch and minor. The model has read thousands of
pages using each term precisely, and the single word activates all of it. Second, name
the hazard, the default, or the direction, never the wish: no omitted tools fields, widen
a fence, bare stable ambiguous, no long-lived keys. Defaults and directions are where
drift enters, and wishes ("be careful", "keep it clean") have no failure condition.
Third, make load-bearing words rare and greppable: ratify, vacuous, BLOCKED-ON, the ==>
marker. A word that never occurs in ordinary prose can serve as a report-contract token
that scripts and the orchestrator match mechanically. Fourth, prefer phrasings with a
mechanical test over phrasings with a judgment: exactly one file, 150 lines,
one-line-thin, exit 2 with evidence, diff against the empty tree. Whatever can be counted
or diffed can be gated. Fifth, one word per concept, reused identically in the prompt,
the law, and the report, which is the same discipline the polysemy registry imposes on
routing descriptions, applied to the system's own vocabulary: if ratify meant three
slightly different things in three files, the gates built on it would drift exactly the
way unqualified "migration" once misrouted.
