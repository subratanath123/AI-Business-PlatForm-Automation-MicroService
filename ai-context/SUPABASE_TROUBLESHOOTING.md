# Supabase Configuration Troubleshooting

## Error 1: "Invalid Compact JWS"

**Symptoms:**
```
400 Bad Request: "{"statusCode":"403","error":"Unauthorized","message":"Invalid Compact JWS"}"
```

This error means Supabase rejected your authentication token (service role key).

### Causes and Solutions

#### 1. Wrong Key (Most Common)

You might be using the **anon key** instead of the **service_role key**.

**Check:**
1. Go to Supabase Dashboard
2. Select your project
3. Settings → API
4. You should see three keys:
   - `anon (public)` - Client-side, limited access
   - `service_role` - Server-side, full access (⚠️ Keep secret!)
   - `jwt_secret` - Internal use only

**Fix:** Make sure you're using `service_role`, not `anon`.

#### 2. Key Was Copy-Pasted Incorrectly

The key might have:
- Extra spaces
- Newlines or tabs
- Was truncated during copy-paste

**Check:**
```properties
# ❌ Wrong - has spaces or is truncated
supabase.service-role-key=eyJhbGc... eyJz...

# ✅ Correct - complete key, no spaces
supabase.service-role-key=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNnZW5hbWthcWV6eWxweWlxYnNiIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTYxNTc3MzYwMCwiZXhwIjoxOTkxNDExMjAwfQ.tXM_z3c3z3c3z3c3z3c3z3c3z3c3z3c3z3c3z3c
```

**Fix:** Re-copy the key from Supabase, ensuring no extra spaces or line breaks.

#### 3. Key Was Regenerated

If you regenerated the key in Supabase, the old key is now invalid.

**Check:** Did you recently rotate your Supabase keys?

**Fix:** Get the new `service_role` key from Settings → API and update your config server.

#### 4. Key Has Expired or Revoked

Rarely, Supabase might invalidate keys for security reasons.

**Fix:**
1. Go to Supabase Dashboard
2. Settings → API
3. Regenerate the `service_role` key
4. Update config server with new key

---

## Error 2: "Unable to connect to Redis"

**Symptoms:**
```
org.springframework.data.redis.RedisConnectionFailureException: Unable to connect to Redis
io.netty.resolver.dns.DnsResolveContext$SearchDomainUnknownHostException: Failed to resolve 'redis-18550.c99.us-east-1-4.ec2.cloud.redislabs.com'
```

This is a Redis connection issue, not Supabase-related. The backend can't reach the Redis server.

### Causes

1. **Redis Server is Down**: Your Redis instance might be paused or crashed
2. **Network Issue**: DNS resolution failing for `redis-18550.c99.us-east-1-4.ec2.cloud.redislabs.com`
3. **Wrong Redis URL**: Configuration points to invalid Redis server
4. **Firewall/Security Group**: Network access is blocked

### Solutions

#### 1. Check Redis Status

Go to Redis Labs (or your Redis provider) dashboard:
- Verify the instance is running
- Check it's not paused
- Verify the endpoint/URL is correct

#### 2. Check Network Connectivity

```bash
# Test DNS resolution
nslookup redis-18550.c99.us-east-1-4.ec2.cloud.redislabs.com

# Or ping the host
ping redis-18550.c99.us-east-1-4.ec2.cloud.redislabs.com

# Or try telnet to the port
telnet redis-18550.c99.us-east-1-4.ec2.cloud.redislabs.com 18550
```

#### 3. Check Spring Configuration

Verify your Redis configuration in config server:

```properties
# Check these properties exist
spring.redis.host=redis-18550.c99.us-east-1-4.ec2.cloud.redislabs.com
spring.redis.port=18550
spring.redis.password=<your-redis-password>
```

#### 4. Add Redis Timeout Configuration

Add to your config server to prevent long hangs:

```properties
# application-prod.properties
spring.redis.timeout=2000ms
spring.redis.connect-timeout=2000ms
spring.redis.jedis.pool.max-active=8
spring.redis.jedis.pool.max-idle=8
spring.redis.jedis.pool.min-idle=0
```

Or in YAML:

```yaml
spring:
  redis:
    timeout: 2000ms
    connect-timeout: 2000ms
    jedis:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

#### 5. Make Redis Optional (If Not Critical)

If Redis is not critical for media uploads, make it optional:

```properties
spring.data.redis.repositories.enabled=false
```

---

## Verification Steps

### 1. Verify Supabase Configuration

```bash
# Check if Supabase key is valid by attempting a simple test
curl -X GET "https://your-project.supabase.co/storage/v1/object" \
  -H "Authorization: Bearer <service-role-key>"

# Should return 400 (missing object path), not 403 (auth error)
```

### 2. Verify Redis Configuration

```bash
# Test Redis connection
redis-cli -h redis-18550.c99.us-east-1-4.ec2.cloud.redislabs.com \
  -p 18550 \
  -a <password> \
  ping

# Should return: PONG
```

### 3. Test Media Upload (once fixed)

```bash
curl -X POST "http://localhost:8080/v1/api/assets/upload" \
  -H "Authorization: Bearer <jwt_token>" \
  -F "files=@test.jpg"

# Should return successful response or clear error message
```

---

## Summary Checklist

### For "Invalid Compact JWS" Error:
- [ ] Confirmed using `service_role` key (not `anon`)
- [ ] Key was copied completely with no spaces/newlines
- [ ] Key is current (not regenerated recently)
- [ ] Supabase project is active (not paused)
- [ ] Service role key is correctly entered in config server

### For "Unable to connect to Redis" Error:
- [ ] Redis instance is running (not paused)
- [ ] Redis endpoint is correct
- [ ] Network connectivity to Redis is working
- [ ] Firewall/Security group allows connection
- [ ] Redis password is correct (if required)
- [ ] Optional: Set Redis timeout configuration

---

## Getting Help

If issues persist:

1. **Check Backend Logs**: Look for detailed error messages
2. **Verify Credentials**: Double-check all configuration values
3. **Test Connectivity**: Use tools like `nslookup`, `ping`, `telnet` or `redis-cli`
4. **Contact Support**:
   - Supabase: [support.supabase.com](https://support.supabase.com)
   - Redis: Your Redis provider's support
5. **Check Status Pages**:
   - Supabase Status: [status.supabase.com](https://status.supabase.com)
   - Your Redis provider's status page

