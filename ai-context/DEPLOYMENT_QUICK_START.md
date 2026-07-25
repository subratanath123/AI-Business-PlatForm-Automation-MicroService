# Quick Start: Digital Ocean Deployment

## Prerequisites Checklist

- [ ] Digital Ocean droplet created (Ubuntu 20.04+)
- [ ] Java 17 installed on droplet
- [ ] MongoDB installed and running
- [ ] SSH access configured
- [ ] GitHub repository with Actions enabled

## Step 1: Configure GitHub Secrets

Go to: **Repository Settings → Secrets and variables → Actions**

Add these secrets:

| Secret Name | Description | Example |
|------------|-------------|---------|
| `DO_HOST` | Droplet IP or domain | `123.45.67.89` |
| `DO_USER` | SSH username | `root` or `ubuntu` |
| `DO_SSH_KEY` | Private SSH key | `-----BEGIN OPENSSH PRIVATE KEY-----...` |
| `DO_PORT` | SSH port (optional) | `22` |

### Generate SSH Key (if needed)

```bash
ssh-keygen -t ed25519 -C "github-actions"
# Copy private key to GitHub Secret: DO_SSH_KEY
# Add public key to droplet:
ssh-copy-id -i ~/.ssh/id_ed25519.pub root@your-droplet-ip
```

## Step 2: Initial Server Setup

SSH into your droplet and run:

```bash
# Install Java 17
apt update && apt install -y openjdk-17-jdk

# Install MongoDB (if local)
wget -qO - https://www.mongodb.org/static/pgp/server-7.0.asc | apt-key add -
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu focal/mongodb-org/7.0 multiverse" | tee /etc/apt/sources.list.d/mongodb-org-7.0.list
apt update && apt install -y mongodb-org
systemctl start mongod && systemctl enable mongod

# Create directories
mkdir -p /opt/jade-ai-bot/backups /var/log/jade-ai-bot
useradd -r -s /bin/false -d /opt/jade-ai-bot jade-ai-bot
chown -R jade-ai-bot:jade-ai-bot /opt/jade-ai-bot /var/log/jade-ai-bot
```

## Step 3: Configure Application

### Option A: Environment Variables

Create `/etc/systemd/system/jade-ai-bot.service.d/override.conf`:

```ini
[Service]
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="MONGODB_URI=mongodb://localhost:27017/chatbot"
Environment="REDIS_HOST=localhost"
Environment="REDIS_PORT=6379"
```

### Option B: Update application-prod.yml

Edit `src/main/resources/application-prod.yml` with your production settings.

## Step 4: Deploy

### Automatic Deployment
Push to `main` or `master` branch - deployment happens automatically!

### Manual Deployment
1. Go to **Actions** tab in GitHub
2. Select **Deploy to Digital Ocean**
3. Click **Run workflow**

## Step 5: Verify

```bash
# Check service status
ssh root@your-droplet-ip
systemctl status jade-ai-bot.service

# View logs
journalctl -u jade-ai-bot.service -f
```

## Common Commands

```bash
# Service management
sudo systemctl start jade-ai-bot.service
sudo systemctl stop jade-ai-bot.service
sudo systemctl restart jade-ai-bot.service
sudo systemctl status jade-ai-bot.service

# View logs
sudo journalctl -u jade-ai-bot.service -n 100
tail -f /var/log/jade-ai-bot/app.log

# Rollback
sudo systemctl stop jade-ai-bot.service
sudo cp /opt/jade-ai-bot/backups/JadeAiBot-YYYYMMDD-HHMMSS.jar /opt/jade-ai-bot/JadeAiBot.jar
sudo systemctl start jade-ai-bot.service
```

## Troubleshooting

**Service won't start?**
```bash
journalctl -u jade-ai-bot.service -n 50
java -jar /opt/jade-ai-bot/JadeAiBot.jar  # Test manually
```

**Connection issues?**
```bash
# Check MongoDB
mongo --eval "db.adminCommand('ping')"

# Check port
netstat -tulpn | grep 8080
```

## Next Steps

- Set up reverse proxy (Nginx) for HTTPS
- Configure SSL certificates (Let's Encrypt)
- Set up monitoring and alerts
- Configure automated backups

For detailed information, see [DEPLOYMENT.md](./DEPLOYMENT.md)

