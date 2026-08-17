# RDS PostgreSQL — the persistent tier. Password is generated here, stored in
# Secrets Manager, and injected into the app via the ECS task definition's
# `secrets` block, so it never exists in code, image, or plain environment files.

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "db_password" {
  name_prefix = "taskforge-db-password-"
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db.result
}

resource "aws_db_subnet_group" "main" {
  name       = "taskforge-db"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier     = "taskforge-${var.environment}"
  engine         = "postgres"
  engine_version = "16"

  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  storage_encrypted = true

  db_name  = "taskforge"
  username = "taskforge"
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false # true in production

  backup_retention_period = 7
  deletion_protection     = true
  skip_final_snapshot     = false
  final_snapshot_identifier = "taskforge-${var.environment}-final"

  # Terraform must not fight RDS auto minor version upgrades.
  auto_minor_version_upgrade = true
  lifecycle {
    ignore_changes = [engine_version]
  }
}
