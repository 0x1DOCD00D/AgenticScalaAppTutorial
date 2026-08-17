#!/usr/bin/env bash
# rollback.sh [environment] — re-point the ECS service at the previous task
# definition revision. Fast, dumb, reliable: exactly what you want at 3 a.m.
set -euo pipefail

ENVIRONMENT="${1:-staging}"
REGION="${AWS_REGION:-us-east-1}"
CLUSTER="taskforge-${ENVIRONMENT}"
SERVICE="taskforge"
FAMILY="taskforge"

CURRENT=$(aws ecs describe-services --region "$REGION" --cluster "$CLUSTER" \
  --services "$SERVICE" --query 'services[0].taskDefinition' --output text)
CURRENT_REV=${CURRENT##*:}
TARGET_REV=$((CURRENT_REV - 1))

if (( TARGET_REV < 1 )); then
  echo "FATAL: no previous revision to roll back to (current: ${CURRENT_REV})." >&2
  exit 1
fi

echo "==> Rolling back ${SERVICE} from revision ${CURRENT_REV} to ${TARGET_REV}"
aws ecs update-service --region "$REGION" --cluster "$CLUSTER" --service "$SERVICE" \
  --task-definition "${FAMILY}:${TARGET_REV}" >/dev/null

aws ecs wait services-stable --region "$REGION" --cluster "$CLUSTER" --services "$SERVICE"
echo "==> ROLLBACK OK now serving ${FAMILY}:${TARGET_REV}"
