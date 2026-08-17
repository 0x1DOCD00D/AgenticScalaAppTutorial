---
description: Roll the ECS service back to the previous task definition revision
allowed-tools: Bash(./scripts/rollback.sh *), Bash(aws ecs describe-services *), Bash(./scripts/smoke-test.sh *)
---

Roll back TaskForge in $1 (default: staging) immediately.

1. Run `./scripts/rollback.sh $1`.
2. Poll `aws ecs describe-services` until the previous revision reaches steady state.
3. Run `./scripts/smoke-test.sh` to confirm recovery.
4. Report which revision is now live and open a follow-up: the incident-responder agent must
   determine why the rolled-back deploy failed before anyone deploys again.
