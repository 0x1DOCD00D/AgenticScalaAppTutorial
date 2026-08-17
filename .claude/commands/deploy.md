---
description: Build, test, and deploy TaskForge to ECS Fargate with health gates and auto-rollback
allowed-tools: Bash(sbt *), Bash(./scripts/*), Bash(git *), Bash(aws ecs describe-services *), Bash(aws logs tail *)
---

Deploy TaskForge to $1 (default: staging).

Use the **deploy-engineer** agent to:
1. Verify preconditions (clean tree on main, `sbt check` green).
2. Run `./scripts/deploy.sh $1`.
3. Watch the ECS rollout to completion and run `./scripts/smoke-test.sh` against the $1 ALB.
4. If any gate fails: run `./scripts/rollback.sh $1`, verify recovery, and report the failure
   evidence in full.

Report: image tag, task definition revision, rollout duration, smoke-test results.
