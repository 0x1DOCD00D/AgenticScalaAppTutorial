output "alb_dns_name" {
  description = "Public URL of the application (http://...)"
  value       = aws_lb.main.dns_name
}

output "ecr_repository_url" {
  description = "Push target for deploy.sh"
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "db_endpoint" {
  value = aws_db_instance.main.address
}

output "log_group" {
  value = aws_cloudwatch_log_group.app.name
}

output "alerts_topic_arn" {
  value = aws_sns_topic.alerts.arn
}
