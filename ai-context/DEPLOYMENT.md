# Deployment Guide: Digital Ocean Droplet via GitHub Actions

This guide explains how to deploy the Chat API application to a Digital Ocean droplet using GitHub Actions.

## Prerequisites

1. **Digital Ocean Droplet**
   - Ubuntu 20.04 or later (recommended)
   - At least 2GB RAM
   - SSH access configured
   - Java 17 installed
   - MongoDB installed and running (or use external MongoDB)

2. **GitHub Repository**
   - Repository with the application code
   - GitHub Actions enabled

3. **Required Services**
   - MongoDB (can be on the same droplet or external)
   - Redis (if used)
   - AWS S3 credentials configured

## Initial Server Setup

### 1. Connect to Your Droplet

```bash
ssh root@your-droplet-ip
```

### 2. Install Java 17

```bash
# Update package list
apt update

# Install Java 17
apt install -y openjdk-17-jdk

# Verify installation
java -version
```

### 3. Install MongoDB (if running locally)

```bash
# Import MongoDB GPG key
wget -qO - https://www.mongodb.org/static/pgp/server-7.0.asc | apt-key add -

# Add MongoDB repository
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu focal/mongodb-org/7.0 multiverse" | tee /etc/apt/sources.list.d/mongodb-org-7.0.list

# Update and install
apt update
apt install -y mongodb-org

# Start and enable MongoDB
systemctl start mongod
systemctl enable mongod
```

### 4. Install Redis (if needed)

```bash
apt install -y redis-server
systemctl start redis-server
systemctl enable redis-server
```

### 5. Create Application Directory

```bash
mkdir -p /opt/jade-ai-bot
mkdir -p /opt/jade-ai-bot/backups
mkdir -p /var/log/jade-ai-bot
```

### 6. Create Application User

```bash
useradd -r -s /bin/false -d /opt/jade-ai-bot jade-ai-bot
chown -R jade-ai-bot:jade-ai-bot /opt/jade-ai-bot
chown -R jade-ai-bot:jade-ai-bot /var/log/jade-ai-bot
```

### 7. Configure Firewall (if using UFW)

```bash
# Allow SSH
ufw allow 22/tcp

# Allow application port (default 8080)
ufw allow 8080/tcp

# Enable firewall
ufw enable
```

## GitHub Secrets Configuration

Configure the following secrets in your GitHub repository:

1. Go to your repository on GitHub
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret** and add:

### Required Secrets

- **`DO_HOST`**: Your Digital Ocean droplet IP address or domain name
  - Example: `123.45.67.89` or `api.yourdomain.com`

- **`DO_USER`**: SSH username for your droplet
  - Example: `root` or `ubuntu`

- **`DO_SSH_KEY`**: Private SSH key for authentication
  - Generate a key pair if you don't have one:
    ```bash
    ssh-keygen -t ed25519 -C "github-actions"
    ```
  - Copy the private key content (including `-----BEGIN` and `-----END` lines)
  - Add the public key to your droplet:
    ```bash
    # On your local machine
    ssh-copy-id -i ~/.ssh/id_ed25519.pub root@your-droplet-ip
    ```

- **`DO_PORT`** (Optional): SSH port (default: 22)
  - Only needed if you're using a non-standard SSH port

## Application Configuration

### Environment Variables

Create a production configuration file or set environment variables on the server:

1. **Create application-prod.yml** (optional, if using Spring profiles):

```yaml
server:
  port: 8080

spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/your-database-name
      # Or for external MongoDB:
      # uri: mongodb://username:password@mongodb-host:27017/your-database-name

  redis:
    host: localhost
    port: 6379

# AWS S3 Configuration
aws:
  s3:
    bucket-name: your-bucket-name
    region: us-east-1

# Add other production-specific configurations
```

2. **Set Environment Variables** (alternative approach):

Create `/etc/systemd/system/jade-ai-bot.service.d/override.conf`:

```ini
[Service]
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/your-database"
Environment="AWS_ACCESS_KEY_ID=your-access-key"
Environment="AWS_SECRET_ACCESS_KEY=your-secret-key"
```

## Deployment Workflow

### Automatic Deployment

The GitHub Actions workflow automatically deploys when you push to the `main` or `master` branch.

### Manual Deployment

1. Go to your GitHub repository
2. Click on **Actions** tab
3. Select **Deploy to Digital Ocean** workflow
4. Click **Run workflow**
5. Select the branch and click **Run workflow**

### Deployment Process

The workflow performs the following steps:

1. **Build**: Compiles the Java application using Gradle
2. **Package**: Creates a deployment package with JAR and deployment script
3. **Transfer**: Uploads the package to the droplet via SCP
4. **Deploy**: Runs the deployment script on the droplet
5. **Verify**: Checks if the service is running

## Monitoring and Logs

### View Application Logs

```bash
# View service logs
sudo journalctl -u jade-ai-bot.service -f

# View application logs
tail -f /var/log/jade-ai-bot/app.log

# View error logs
tail -f /var/log/jade-ai-bot/error.log
```

### Service Management

```bash
# Check service status
sudo systemctl status jade-ai-bot.service

# Start service
sudo systemctl start jade-ai-bot.service

# Stop service
sudo systemctl stop jade-ai-bot.service

# Restart service
sudo systemctl restart jade-ai-bot.service

# View service logs
sudo journalctl -u jade-ai-bot.service -n 100
```

## Troubleshooting

### Service Won't Start

1. **Check logs**:
   ```bash
   sudo journalctl -u jade-ai-bot.service -n 50
   ```

2. **Verify Java installation**:
   ```bash
   java -version
   ```

3. **Check JAR file**:
   ```bash
   ls -lh /opt/jade-ai-bot/JadeAiBot.jar
   ```

4. **Test JAR manually**:
   ```bash
   cd /opt/jade-ai-bot
   java -jar JadeAiBot.jar
   ```

### Connection Issues

1. **Check MongoDB connection**:
   ```bash
   mongo --eval "db.adminCommand('ping')"
   ```

2. **Check Redis connection**:
   ```bash
   redis-cli ping
   ```

3. **Verify network connectivity**:
   ```bash
   netstat -tulpn | grep 8080
   ```

### Rollback to Previous Version

If a deployment fails, you can rollback:

```bash
# List backups
ls -lt /opt/jade-ai-bot/backups/

# Stop service
sudo systemctl stop jade-ai-bot.service

# Restore previous JAR
sudo cp /opt/jade-ai-bot/backups/JadeAiBot-YYYYMMDD-HHMMSS.jar /opt/jade-ai-bot/JadeAiBot.jar

# Start service
sudo systemctl start jade-ai-bot.service
```

## Security Best Practices

1. **Use SSH Keys**: Always use SSH keys instead of passwords
2. **Firewall**: Configure UFW or iptables to restrict access
3. **Non-root User**: The application runs as a non-root user (`jade-ai-bot`)
4. **Secrets Management**: Store sensitive data in GitHub Secrets, not in code
5. **Regular Updates**: Keep your server and dependencies updated
6. **SSL/TLS**: Use a reverse proxy (Nginx) with SSL certificates for HTTPS

## Reverse Proxy Setup (Optional but Recommended)

### Install Nginx

```bash
apt install -y nginx
```

### Configure Nginx

Create `/etc/nginx/sites-available/jade-ai-bot`:

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable the site:

```bash
ln -s /etc/nginx/sites-available/jade-ai-bot /etc/nginx/sites-enabled/
nginx -t
systemctl reload nginx
```

### SSL with Let's Encrypt

```bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d your-domain.com
```

## Backup Strategy

The deployment script automatically creates backups of previous JAR files. Consider implementing:

1. **Database Backups**: Regular MongoDB backups
2. **Configuration Backups**: Backup application configuration files
3. **Automated Backups**: Set up cron jobs for regular backups

Example MongoDB backup script:

```bash
#!/bin/bash
BACKUP_DIR="/opt/backups/mongodb"
DATE=$(date +%Y%m%d-%H%M%S)
mkdir -p $BACKUP_DIR
mongodump --out $BACKUP_DIR/dump-$DATE
```

## Performance Tuning

### JVM Options

Adjust JVM options in the systemd service file based on your droplet's resources:

- **1GB RAM**: `-Xmx512m -Xms256m`
- **2GB RAM**: `-Xmx1024m -Xms512m`
- **4GB RAM**: `-Xmx2048m -Xms1024m`

### Database Optimization

- Enable MongoDB indexes
- Configure connection pooling
- Monitor query performance

## Support and Maintenance

- **Health Checks**: The application includes Spring Boot Actuator endpoints
- **Monitoring**: Set up monitoring tools (e.g., Prometheus, Grafana)
- **Alerts**: Configure alerts for service failures
- **Updates**: Regularly update dependencies and security patches

## Additional Resources

- [Digital Ocean Documentation](https://docs.digitalocean.com/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Spring Boot Deployment Guide](https://spring.io/guides/gs/spring-boot-for-azure/)
- [Systemd Service Management](https://www.freedesktop.org/software/systemd/man/systemd.service.html)

---

**Last Updated**: $(date)
**Maintained By**: Development Team

