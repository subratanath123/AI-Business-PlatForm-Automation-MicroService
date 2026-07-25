# Deploying to Render.com

This guide will help you deploy your Chat API application to Render.com.

## Prerequisites

- A Render.com account
- Your application code pushed to a Git repository (GitHub, GitLab, etc.)

## Deployment Steps

### 1. Connect Your Repository

1. Log in to [Render.com](https://render.com)
2. Click "New +" and select "Web Service"
3. Connect your Git repository
4. Select the repository containing your Chat API

### 2. Configure the Service

- **Name**: `chat-api` (or your preferred name)
- **Environment**: `Docker`
- **Region**: Choose the region closest to your users
- **Branch**: `main` (or your default branch)
- **Build Command**: `docker build -t chat-api .`
- **Start Command**: `docker run -p $PORT:8000 chat-api`

### 3. Environment Variables

Set the following environment variables in Render.com:

#### Required Variables:
- `SPRING_PROFILES_ACTIVE`: `production`
- `PORT`: `8000` (Render will set this automatically)
- `JAVA_OPTS`: `-Xmx512m -Xms256m`

#### Optional Variables (configure as needed):
- `MONGODB_URI`: Your MongoDB connection string
- `REDIS_URL`: Your Redis connection string
- `OPENAI_API_KEY`: Your OpenAI API key
- `AWS_ACCESS_KEY_ID`: Your AWS access key
- `AWS_SECRET_ACCESS_KEY`: Your AWS secret key
- `AWS_REGION`: Your AWS region

### 4. Advanced Settings

- **Health Check Path**: `/actuator/health`
- **Auto-Deploy**: Enable to automatically deploy on push to main branch

### 5. Deploy

Click "Create Web Service" and Render will:
1. Build your Docker image
2. Deploy it to their infrastructure
3. Provide you with a public URL

## Health Checks

The application includes a health check endpoint at `/actuator/health` that Render.com will use to monitor your service.

## Monitoring

- **Logs**: View real-time logs in the Render dashboard
- **Metrics**: Monitor performance and resource usage
- **Alerts**: Set up alerts for downtime or errors

## Troubleshooting

### Common Issues:

1. **Build Failures**: Check that all dependencies are properly specified in `build.gradle`
2. **Port Conflicts**: Ensure your application uses the `PORT` environment variable
3. **Memory Issues**: Adjust `JAVA_OPTS` if you encounter memory problems
4. **Health Check Failures**: Verify the `/actuator/health` endpoint is accessible

### Debug Commands:

```bash
# Check application logs
docker logs <container_id>

# Access container shell
docker exec -it <container_id> /bin/bash

# Check health endpoint
curl http://localhost:8000/actuator/health
```

## Cost Optimization

- Start with the "Starter" plan ($7/month)
- Monitor resource usage and scale as needed
- Use auto-scaling for traffic spikes

## Security Notes

- Never commit sensitive environment variables to your repository
- Use Render's environment variable management for secrets
- Enable HTTPS (automatic with Render.com)
- Consider setting up custom domains

## Support

If you encounter issues:
1. Check the Render.com documentation
2. Review your application logs
3. Verify environment variable configuration
4. Contact Render.com support if needed
