# Supabase Configuration - Config Server Setup

## Overview

Supabase credentials (URL and service role key) are managed by the Spring Cloud Config Server, not local `.env` files.

## Configuration Flow

```
Config Server Repository
    ↓
    (app-dev.properties or app-prod.properties)
    ↓
    Spring Cloud Config Server
    ↓
    Backend Application reads via @Value
    ↓
    SupabaseStorageService uses credentials
```

## Setup Steps

### 1. Get Supabase Credentials

Go to [Supabase Dashboard](https://supabase.com/dashboard):
1. Select your project
2. Settings → API
3. Note the following:
   - **Project URL**: `https://your-project.supabase.co`
   - **service_role**: Long secret key (⚠️ Keep secret!)

### 2. Add to Config Server Repository

In your Spring Cloud Config Server repository, add to the appropriate profile file:

**`application-dev.properties`** (development):
```properties
# Supabase Configuration
supabase.url=https://your-dev-project.supabase.co
supabase.service-role-key=<dev-service-role-key>
supabase.bucket=social-media-assets
```

**`application-prod.properties`** (production):
```properties
# Supabase Configuration
supabase.url=https://your-prod-project.supabase.co
supabase.service-role-key=<prod-service-role-key>
supabase.bucket=social-media-assets
```

### 3. Or in YAML Format

**`application-dev.yml`**:
```yaml
supabase:
  url: https://your-dev-project.supabase.co
  service-role-key: <dev-service-role-key>
  bucket: social-media-assets
```

**`application-prod.yml`**:
```yaml
supabase:
  url: https://your-prod-project.supabase.co
  service-role-key: <prod-service-role-key>
  bucket: social-media-assets
```

### 4. Local Development (Optional)

If you don't have a config server for local development, temporarily add to `src/main/resources/application.yml`:

```yaml
supabase:
  url: https://your-local-project.supabase.co
  service-role-key: your-local-service-role-key
  bucket: social-media-assets
```

**⚠️ Remember to remove before committing!**

### 5. Restart Backend

After updating config server, restart the backend application:
```bash
./gradlew bootRun
```

The backend will now fetch Supabase credentials from the config server.

## Validation

### Check Configuration is Loaded

**Debug log:** Watch for no config errors on startup. If configuration is missing, you'll see:
```
IllegalStateException: Supabase URL is not configured. Check config server settings.
```

### Test Upload

```bash
curl -X POST "http://localhost:8080/v1/api/assets/upload" \
  -H "Authorization: Bearer <jwt_token>" \
  -F "files=@test.jpg"
```

If configuration is correct, the file will upload to Supabase.

If configuration is missing, you'll get:
```json
{
  "timestamp": "2024-01-15T10:00:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Supabase URL is not configured. Check config server settings."
}
```

## Important Notes

### Service Role Key Security

- **Never commit** `supabase.service-role-key` to version control
- **Only store** in config server (encrypted at rest)
- **Never expose** to frontend/client
- **Rotate regularly** in production

### Config Server Best Practices

1. **Encrypt secrets** in config server (Spring Cloud Config encryption)
2. **Separate by environment**: dev, staging, prod
3. **Use different Supabase projects** per environment
4. **Audit access** to config server
5. **Version control** config server repository

### Multiple Environments

The backend automatically selects the right profile:
- Local: `application.yml` (fallback)
- Development: `application-dev.properties` or `application-dev.yml`
- Production: `application-prod.properties` or `application-prod.yml`

Set via environment variable or runtime property:
```bash
# Option 1: Environment variable
export SPRING_PROFILES_ACTIVE=dev
# or
export SPRING_PROFILES_ACTIVE=prod

# Option 2: Command line
java -jar app.jar --spring.profiles.active=prod

# Option 3: application.yml
spring:
  profiles:
    active: prod
```

### Config Server URL

Your backend must be configured to reach the config server. Typically in `application.yml`:

```yaml
spring:
  cloud:
    config:
      uri: http://config-server:8888
      # or
      uri: https://your-config-server-domain.com
```

## Troubleshooting

### Error: "Supabase URL is not configured"

**Check:**
1. Config server is running
2. Correct profile is active (dev/prod)
3. Properties file exists in config server repository
4. Property name exactly matches: `supabase.url` and `supabase.service-role-key`
5. Backend can reach config server

**Debug:**
```bash
# Check active profiles
curl http://localhost:8080/actuator/env | grep spring.profiles.active

# Check config server directly
curl http://config-server:8888/application-dev.properties
```

### Error: "Invalid service role key"

**Check:**
1. Copied correct key (check dashboard again)
2. Key is not accidentally quoted in config file
3. No extra spaces or newlines in key
4. Using `service_role` key, not `anon` key

### Error: "Bucket not found or not public"

**Check:**
1. Bucket `social-media-assets` exists in Supabase
2. Bucket is set to **Public**
3. `supabase.bucket` property matches bucket name exactly

## Summary

Supabase configuration comes from **Config Server**, not local environment variables:

```
Config Server (secure, encrypted)
    ↓
Backend reads via @Value annotations
    ↓
SupabaseStorageService validates on use
    ↓
Clear errors if config missing
```

This approach:
✅ Keeps secrets secure  
✅ Centralizes configuration  
✅ Allows environment-specific values  
✅ Enables easy credential rotation  
✅ Provides audit trail  

For help, check:
1. Config server logs
2. Backend startup logs (look for "Supabase URL" errors)
3. Spring Cloud Config documentation
