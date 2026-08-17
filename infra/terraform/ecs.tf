# ECR + ECS Fargate. The deployment circuit breaker is the safety net that makes
# agent-driven deploys survivable: a bad image rolls itself back even if the
# supervising agent dies mid-deploy.

resource "aws_ecr_repository" "app" {
  name                 = "taskforge"
  image_tag_mutability = "IMMUTABLE" # git-SHA tags; "latest" convenience lives locally only

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "keep last 20 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/taskforge"
  retention_in_days = 30
}

resource "aws_ecs_cluster" "main" {
  name = "taskforge-${var.environment}"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# --- IAM: execution role (pull image, write logs, read secrets) vs task role (the app itself,
# which needs no AWS permissions at all — least privilege by construction). ---

data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name_prefix        = "taskforge-exec-"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_secrets" {
  name_prefix = "read-db-secret-"
  role        = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = [aws_secretsmanager_secret.db_password.arn]
    }]
  })
}

resource "aws_iam_role" "task" {
  name_prefix        = "taskforge-task-"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

# --- Task definition + service ---

resource "aws_ecs_task_definition" "app" {
  family                   = "taskforge"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.app_cpu
  memory                   = var.app_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([{
    name      = "taskforge"
    image     = "${aws_ecr_repository.app.repository_url}:${var.app_image_tag}"
    essential = true

    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    environment = [
      { name = "HTTP_PORT", value = "8080" },
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/taskforge" },
      { name = "DB_USER", value = "taskforge" }
    ]

    secrets = [
      { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password.arn }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "app"
      }
    }

    # No container-level healthCheck on purpose: the temurin JRE image ships neither curl nor
    # wget, and the ALB target group already health-checks /healthz. One health authority,
    # no phantom restarts from a missing binary.
  }])
}

resource "aws_ecs_service" "app" {
  name            = "taskforge"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.app_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = aws_subnet.private[*].id
    security_groups = [aws_security_group.app.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "taskforge"
    container_port   = 8080
  }

  # Auto-rollback on failed deploys — the agent's seatbelt.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100

  # deploy.sh registers new task definition revisions outside Terraform;
  # don't let plan/apply flip the service back to an old revision.
  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener.http]
}
