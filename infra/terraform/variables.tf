variable "region" {
  type    = string
  default = "us-east-1"
}

variable "environment" {
  type    = string
  default = "staging"
}

variable "app_image_tag" {
  description = "Docker image tag (git SHA) to deploy"
  type        = string
  default     = "latest"
}

variable "app_count" {
  description = "Desired number of Fargate tasks"
  type        = number
  default     = 2
}

variable "app_cpu" {
  type    = number
  default = 512 # 0.5 vCPU
}

variable "app_memory" {
  type    = number
  default = 1024 # MiB
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_allocated_storage" {
  type    = number
  default = 20
}
