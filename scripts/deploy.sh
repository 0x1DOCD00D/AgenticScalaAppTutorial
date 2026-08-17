#!/usr/bin/env bash
# =============================================================================
# deploy.sh [environment] — build, push, and roll TaskForge on ECS Fargate.
#
# Written to be run BY AN AGENT: every step prints machine-greppable evidence,
# fails fast, and never asks interactive questions.
# =============================================================================
set -euo pipefail

ENVIRONMENT="${1:-staging}"
REGION="${AWS_REGION:-us-east-1}"
CLUSTER="taskforge-${ENVIRONMENT}"
SERVICE="taskforge"
FAMILY="taskforge"

# --- 0. Preconditions -------------------------------------------------------
if [[ -n "$(git status --porcelain)" ]]; then
  echo "FATAL: working tree is dirty; commit or stash before deploying." >&2
  exit 1
fi
GIT_SHA=$(git rev-parse --short=12 HEAD)
echo "==> Deploying commit ${GIT_SHA} to ${ENVIRONMENT}"

ECR_URL=$(aws ecr describe-repositories --repository-names taskforge \
  --region "$REGION" --query 'repositories[0].repositoryUri' --output text)

# --- 1. Build the image with sbt-native-packager ----------------------------
echo "==> sbt Docker/publishLocal"
APP_VERSION="$GIT_SHA" sbt -batch Docker/publishLocal

# --- 2. Push to ECR (immutable git-SHA tag) ---------------------------------
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "${ECR_URL%%/*}"
docker tag "taskforge:${GIT_SHA}" "${ECR_URL}:${GIT_SHA}"
docker push "${ECR_URL}:${GIT_SHA}"
echo "==> Pushed ${ECR_URL}:${GIT_SHA}"

# --- 3. Register a new task definition revision with the new image ----------
CURRENT_TD=$(aws ecs describe-task-definition --task-definition "$FAMILY" \
  --region "$REGION" --query 'taskDefinition')
NEW_TD=$(echo "$CURRENT_TD" | python3 -c '
import json, sys
td = json.load(sys.stdin)
ecr, sha = sys.argv[1], sys.argv[2]
td["containerDefinitions"][0]["image"] = f"{ecr}:{sha}"
# Strip read-only fields the register API rejects.
for k in ["taskDefinitionArn","revision","status","requiresAttributes",
          "compatibilities","registeredAt","registeredBy","deregisteredAt"]:
    td.pop(k, None)
print(json.dumps(td))' "$ECR_URL" "$GIT_SHA")
REVISION=$(aws ecs register-task-definition --region "$REGION" \
  --cli-input-json "$NEW_TD" --query 'taskDefinition.revision' --output text)
echo "==> Registered task definition ${FAMILY}:${REVISION}"

# --- 4. Roll the service ----------------------------------------------------
aws ecs update-service --region "$REGION" --cluster "$CLUSTER" --service "$SERVICE" \
  --task-definition "${FAMILY}:${REVISION}" >/dev/null
echo "==> Service updated; waiting for steady state (circuit breaker will auto-rollback on failure)"

aws ecs wait services-stable --region "$REGION" --cluster "$CLUSTER" --services "$SERVICE"

LIVE=$(aws ecs describe-services --region "$REGION" --cluster "$CLUSTER" --services "$SERVICE" \
  --query 'services[0].taskDefinition' --output text)
if [[ "$LIVE" != *":${REVISION}" ]]; then
  echo "FATAL: service settled on ${LIVE}, not revision ${REVISION} — circuit breaker rolled back." >&2
  exit 2
fi

echo "==> DEPLOY OK family=${FAMILY} revision=${REVISION} image=${ECR_URL}:${GIT_SHA}"
echo "==> Next: ./scripts/smoke-test.sh \$(terraform -chdir=infra/terraform output -raw alb_dns_name)"
