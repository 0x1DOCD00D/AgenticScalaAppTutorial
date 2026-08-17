# =============================================================================
# TaskForge infrastructure — ECS Fargate + RDS PostgreSQL + ALB
#
# Deliberately a single small module: at tutorial scale, one readable file set
# beats a nest of module indirections. Agents run `terraform plan` freely;
# `terraform apply` is human-gated (denied in .claude/settings.json).
# =============================================================================

terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state so humans, CI, and agents all see the same world.
  # Create the bucket + DynamoDB lock table once, out of band.
  backend "s3" {
    bucket         = "taskforge-tfstate"          # change to a globally unique name
    key            = "taskforge/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "taskforge-tflock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "taskforge"
      ManagedBy = "terraform"
      Env       = var.environment
    }
  }
}
