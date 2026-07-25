# Docker Deployment Guide

This guide explains how to deploy the application using Docker on Digital Ocean via GitHub Actions.

## Overview

The deployment process now uses Docker containers instead of running JAR files directly. This provides:
- Better isolation
- Easier rollbacks
- Consistent environments
- Health checks
- Resource management

## Prerequisites

### Server Requirements

1. **Docker installed** on the Digital Ocean droplet:
   ```bash
   # Install Docker
   curl -fsSL https://get.docker.com -o get-docker.sh
   sudo sh get-docker.sh
   
   # Install Docker Compose (if not using Docker Compose v2)
   sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
   sudo chmod +x /usr/local/bin/docker-compose
   ```

2. **Verify installation**:
   ```bash
   docker --version
   docker-compose --version  # or: docker compose version
   ```

3. **Add user to docker group** (optional, for non-root access):
   ```bash
   sudo usermod -aG docker $USER
   # Log out and back in for changes to take effect
   ```

## Deployment Process

### Automatic Deployment via GitHub Actions

1. **Push to main/master branch**: Automatically deploys to `prod` environment
2. **Manual deployment**: Go to Actions → Deploy to Digital Ocean → Run workflow → Select environment

### Manual Deployment

SSH into your server and run:

```bash
# Build JAR locally first
./gradlew bootJar

# Copy files to server (or use the deployment package from GitHub Actions)
# Then run:
sudo /path/to/deploy.sh [dev|qa|prod]
```

## Deployment Script Usage

The `deploy.sh` script supports three environments:

```bash
# Production (default)
sudo ./deploy.sh prod

# QA
sudo ./deploy.sh qa

# Development
sudo ./deploy.sh dev
```

### What the Script Does

1. **Validates environment** (dev, qa, or prod)
2. **Checks Docker installation**
3. **Backs up existing Docker image** (keeps last 5 backups)
4. **Stops existing containers**
5. **Builds new Docker image** from the JAR file
6. **Deploys using docker-compose**
7. **Verifies container health**
8. **Shows deployment status**

## Environment Configuration

### Development (`docker-compose.dev.yml`)
- Profile: `dev`
- Memory: 512MB
- Restart: `unless-stopped`
- Suitable for local development

### QA (`docker-compose.qa.yml`)
- Profile: `qa`
- Memory: 768MB
- Restart: `unless-stopped`
- Suitable for testing

### Production (`docker-compose.prod.yml`)
- Profile: `prod`
- Memory: 1024MB
- Restart: `always`
- Logging configured
- Suitable for production

## Environment Variables

Set environment variables in your compose files or via `.env` file:

```env
# MongoDB
MONGODB_URI=mongodb://your-mongodb-host:27017/chatbot

# Redis
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password

# Eureka (if used)
EUREKA_ENABLED=false
EUREKA_SERVER_URL=http://eureka-server:8761/eureka/

# N8N Webhooks
N8N_TRAIN_WEBHOOK_URL=http://n8n:5678/webhook/train
N8N_CHAT_WEBHOOK_URL=http://n8n:5678/webhook/chat
```

## Managing Containers

### View Logs

```bash
# View application logs
docker logs -f jade-ai-bot-prod

# View logs with docker-compose
cd /opt/jade-ai-bot
docker-compose -f docker-compose.prod.yml logs -f
```

### Stop/Start Containers

```bash
# Stop
cd /opt/jade-ai-bot
docker-compose -f docker-compose.prod.yml down

# Start
docker-compose -f docker-compose.prod.yml up -d

# Restart
docker-compose -f docker-compose.prod.yml restart
```

### Check Status

```bash
# List running containers
docker ps --filter "name=jade-ai-bot"

# Check container health
docker inspect --format='{{.State.Health.Status}}' jade-ai-bot-prod

# View resource usage
docker stats jade-ai-bot-prod
```

## Rollback

If a deployment fails, the script automatically attempts to rollback. You can also manually rollback:

```bash
# List backup images
docker images | grep jade-ai-bot

# Tag backup image as current
docker tag jade-ai-bot:prod-backup-20240101-120000 jade-ai-bot:prod

# Restart with backup image
cd /opt/jade-ai-bot
docker-compose -f docker-compose.prod.yml up -d
```

## Troubleshooting

### Container Won't Start

```bash
# Check logs
docker logs jade-ai-bot-prod

# Check container status
docker ps -a | grep jade-ai-bot

# Inspect container
docker inspect jade-ai-bot-prod
```

### Image Build Fails

```bash
# Verify JAR exists
ls -lh build/libs/JadeAiBot.jar

# Build manually
docker build -t jade-ai-bot:prod .
```

### Health Check Failing

```bash
# Check if application is responding
curl http://localhost:8080/actuator/health

# Check container logs
docker logs jade-ai-bot-prod | tail -50
```

### Port Already in Use

```bash
# Find process using port
sudo lsof -i :8080

# Or check with netstat
sudo netstat -tulpn | grep 8080
```

## Cleanup

### Remove Old Images

```bash
# Remove unused images
docker image prune -a

# Remove specific old images
docker rmi jade-ai-bot:prod-backup-20240101-120000
```

### Remove Stopped Containers

```bash
# Remove all stopped containers
docker container prune

# Remove specific container
docker rm jade-ai-bot-prod
```

## Monitoring

### Health Checks

The container includes a health check that monitors `/actuator/health`:
- Interval: 30 seconds
- Timeout: 10 seconds
- Retries: 3
- Start period: 60 seconds

### Logs

Logs are stored in Docker's logging driver (json-file by default):
- Max size: 10MB per file
- Max files: 3
- Location: `/var/lib/docker/containers/`

View logs:
```bash
docker logs -f jade-ai-bot-prod
```

## Best Practices

1. **Always build JAR before deployment**: The Dockerfile expects the JAR to exist
2. **Use environment-specific compose files**: Don't mix dev/qa/prod configurations
3. **Set resource limits**: Configure memory limits in compose files
4. **Monitor health checks**: Set up alerts for unhealthy containers
5. **Regular backups**: Backup Docker images and volumes
6. **Keep images updated**: Regularly update base images for security
7. **Use secrets management**: Don't hardcode passwords in compose files

## GitHub Actions Integration

The workflow automatically:
1. Builds the JAR file
2. Packages deployment files (JAR, Dockerfile, compose files, deploy script)
3. Transfers to server via SCP
4. Runs deployment script
5. Verifies deployment

### Workflow Inputs

When manually triggering the workflow, you can select:
- **Environment**: dev, qa, or prod (default: prod)

### Workflow Secrets

Required secrets:
- `DO_HOST`: Droplet IP or domain
- `DO_USER`: SSH username
- `DO_SSH_KEY`: Private SSH key
- `DO_PORT`: SSH port (optional, default: 22)

## Migration from Systemd

If you were previously using systemd, the new Docker deployment:
- Replaces systemd service with Docker containers
- Uses docker-compose for orchestration
- Maintains the same application functionality
- Provides better isolation and management

To migrate:
1. Stop old systemd service: `sudo systemctl stop jade-ai-bot.service`
2. Disable service: `sudo systemctl disable jade-ai-bot.service`
3. Deploy using new Docker method
4. Verify container is running: `docker ps | grep jade-ai-bot`

---

For more information, see:
- [DOCKER_USAGE.md](./DOCKER_USAGE.md) - Local Docker usage
- [DEPLOYMENT.md](./DEPLOYMENT.md) - General deployment guide

