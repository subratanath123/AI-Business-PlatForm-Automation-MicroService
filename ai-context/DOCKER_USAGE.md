# Docker Compose Usage Guide

This project includes three Docker Compose configurations for different environments: Development, QA, and Production.

## Prerequisites

1. **Build the JAR file first** (required before building Docker image):
   ```bash
   ./gradlew bootJar
   ```
   This creates the JAR file at `build/libs/JadeAiBot.jar` which will be copied into the Docker image.

2. **Docker and Docker Compose** installed on your system.

## Quick Start

### Development Environment

```bash
# Build the JAR
./gradlew bootJar

# Build and start all services (dev profile)
docker-compose -f docker-compose.dev.yml up --build

# Or run in detached mode
docker-compose -f docker-compose.dev.yml up -d --build
```

### QA Environment

```bash
# Build the JAR
./gradlew bootJar

# Build and start all services (qa profile)
docker-compose -f docker-compose.qa.yml up --build

# Or run in detached mode
docker-compose -f docker-compose.qa.yml up -d --build
```

### Production Environment

```bash
# Build the JAR
./gradlew bootJar

# Set environment variables (create .env file or export)
export MONGODB_URI=mongodb://mongodb:27017/chatbot
export MONGO_ROOT_PASSWORD=your-secure-password
export REDIS_PASSWORD=your-redis-password

# Build and start all services (prod profile)
docker-compose -f docker-compose.prod.yml up --build

# Or run in detached mode
docker-compose -f docker-compose.prod.yml up -d --build
```

## Environment Profiles

Each compose file sets the Spring profile via the `SPRING_PROFILES_ACTIVE` environment variable:

- **Development**: `SPRING_PROFILES_ACTIVE=dev` → Uses `application-dev.yml`
- **QA**: `SPRING_PROFILES_ACTIVE=qa` → Uses `application-qa.yml`
- **Production**: `SPRING_PROFILES_ACTIVE=prod` → Uses `application-prod.yml`

## Common Commands

### View Logs

```bash
# Development
docker-compose -f docker-compose.dev.yml logs -f

# QA
docker-compose -f docker-compose.qa.yml logs -f

# Production
docker-compose -f docker-compose.prod.yml logs -f

# View logs for specific service
docker-compose -f docker-compose.dev.yml logs -f jade-ai-bot
```

### Stop Services

```bash
# Development
docker-compose -f docker-compose.dev.yml down

# QA
docker-compose -f docker-compose.qa.yml down

# Production
docker-compose -f docker-compose.prod.yml down
```

### Stop and Remove Volumes

```bash
# Development
docker-compose -f docker-compose.dev.yml down -v

# QA
docker-compose -f docker-compose.qa.yml down -v

# Production
docker-compose -f docker-compose.prod.yml down -v
```

### Rebuild After Code Changes

```bash
# 1. Rebuild JAR
./gradlew bootJar

# 2. Rebuild and restart containers
docker-compose -f docker-compose.dev.yml up --build -d
```

### Check Service Status

```bash
# Development
docker-compose -f docker-compose.dev.yml ps

# QA
docker-compose -f docker-compose.qa.yml ps

# Production
docker-compose -f docker-compose.prod.yml ps
```

## Environment Variables

### Development (docker-compose.dev.yml)

Uses default values suitable for local development:
- MongoDB: No authentication, database: `chatbot`
- Redis: No password
- Spring Profile: `dev`

### QA (docker-compose.qa.yml)

Supports environment variables for configuration:
- `MONGODB_URI`: MongoDB connection string
- `REDIS_HOST`: Redis host
- `REDIS_PORT`: Redis port
- `REDIS_PASSWORD`: Redis password (optional)
- `EUREKA_ENABLED`: Enable/disable Eureka (default: false)
- `N8N_TRAIN_WEBHOOK_URL`: N8N training webhook URL
- `N8N_CHAT_WEBHOOK_URL`: N8N chat webhook URL

### Production (docker-compose.prod.yml)

Requires environment variables for security:
- `MONGODB_URI`: MongoDB connection string
- `MONGO_ROOT_PASSWORD`: MongoDB root password (required)
- `MONGO_DATABASE`: MongoDB database name
- `MONGO_ROOT_USERNAME`: MongoDB root username
- `REDIS_HOST`: Redis host
- `REDIS_PORT`: Redis port
- `REDIS_PASSWORD`: Redis password
- `EUREKA_ENABLED`: Enable/disable Eureka
- `EUREKA_SERVER_URL`: Eureka server URL
- `N8N_TRAIN_WEBHOOK_URL`: N8N training webhook URL
- `N8N_CHAT_WEBHOOK_URL`: N8N chat webhook URL

### Using .env File

Create a `.env` file in the project root:

```env
# MongoDB
MONGODB_URI=mongodb://mongodb:27017/chatbot
MONGO_ROOT_PASSWORD=secure-password
MONGO_DATABASE=chatbot
MONGO_ROOT_USERNAME=root

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=redis-password

# Eureka
EUREKA_ENABLED=false
EUREKA_SERVER_URL=http://localhost:8761/eureka/

# N8N
N8N_TRAIN_WEBHOOK_URL=http://n8n:5678/webhook/train
N8N_CHAT_WEBHOOK_URL=http://n8n:5678/webhook/chat
```

Docker Compose will automatically load variables from `.env` file.

## Service Details

### Application Service (jade-ai-bot)

- **Port**: 8080 (mapped to host)
- **Health Check**: `/actuator/health` endpoint
- **Restart Policy**: 
  - Dev/QA: `unless-stopped`
  - Prod: `always`
- **Memory**: Configurable via `JAVA_OPTS`

### MongoDB Service

- **Port**: 27017 (mapped to host)
- **Data Persistence**: Docker volumes
- **Authentication**: 
  - Dev/QA: Basic authentication
  - Prod: Requires password

### Redis Service

- **Port**: 6379 (mapped to host)
- **Data Persistence**: AOF (Append Only File) enabled
- **Authentication**: 
  - Dev/QA: No password
  - Prod: Password required

## Troubleshooting

### JAR File Not Found

**Error**: `COPY failed: file not found in build context`

**Solution**: Build the JAR first:
```bash
./gradlew bootJar
```

### Port Already in Use

**Error**: `Bind for 0.0.0.0:8080 failed: port is already allocated`

**Solution**: 
- Stop the service using the port
- Or change the port mapping in the compose file

### MongoDB Connection Issues

**Error**: Cannot connect to MongoDB

**Solution**:
1. Check if MongoDB container is running: `docker-compose ps`
2. Check MongoDB logs: `docker-compose logs mongodb`
3. Verify connection string in environment variables

### Health Check Failing

**Error**: Health check fails

**Solution**:
1. Check application logs: `docker-compose logs jade-ai-bot`
2. Verify the application started successfully
3. Check if actuator endpoint is accessible: `curl http://localhost:8080/actuator/health`

## Building Docker Image Separately

If you want to build the Docker image without using docker-compose:

```bash
# Build JAR first
./gradlew bootJar

# Build Docker image
docker build -t jade-ai-bot:latest .

# Run container
docker run -d \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e MONGODB_URI=mongodb://host.docker.internal:27017/chatbot \
  --name jade-ai-bot \
  jade-ai-bot:latest
```

## Best Practices

1. **Always build JAR before Docker build**: The Dockerfile expects the JAR to exist
2. **Use .env file for secrets**: Never commit secrets to version control
3. **Use specific image tags**: Use tags like `dev`, `qa`, `prod` instead of `latest`
4. **Regular backups**: Backup MongoDB and Redis volumes regularly
5. **Monitor logs**: Regularly check application and service logs
6. **Health checks**: Monitor health check endpoints
7. **Resource limits**: Set appropriate memory and CPU limits for production

## CI/CD Integration

For CI/CD pipelines, you can use these commands:

```bash
# Build and test
./gradlew build

# Build JAR
./gradlew bootJar

# Build Docker image
docker build -t jade-ai-bot:$BUILD_NUMBER .

# Push to registry (if needed)
docker tag jade-ai-bot:$BUILD_NUMBER your-registry/jade-ai-bot:$BUILD_NUMBER
docker push your-registry/jade-ai-bot:$BUILD_NUMBER

# Deploy with compose
docker-compose -f docker-compose.prod.yml up -d
```

---

For more information about deployment, see [DEPLOYMENT.md](./DEPLOYMENT.md)

