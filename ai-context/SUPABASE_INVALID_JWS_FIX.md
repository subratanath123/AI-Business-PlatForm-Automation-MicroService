# Supabase "Invalid Compact JWS" Error - Quick Fix

## The Problem

Your upload is failing with:
```
400 Bad Request: {"statusCode":"403","error":"Unauthorized","message":"Invalid Compact JWS"}
```

**This means: Supabase rejected your authentication token (service role key)**

## 90% Chance: You're Using the Wrong Key

### Check Your Config Server

Go to your config server and find the `supabase.service-role-key` property.

**Verify it's the SERVICE_ROLE key, not ANON:**

```bash
# ❌ WRONG - This is the ANON key (public, read-only)
supabase.service-role-key=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

# ✅ CORRECT - This is the SERVICE_ROLE key (private, full access)
supabase.service-role-key=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### How to Get the Correct Key

1. Go to [Supabase Dashboard](https://supabase.com/dashboard)
2. Select your project
3. Click **Settings** (bottom left)
4. Click **API**
5. Under **Project API Keys**, find:
   - `service_role` - **This is the one you need** ⭐
   - `anon (public)` - This is NOT what you need
6. Copy the full `service_role` key

### Common Mistakes

| Mistake | Wrong | Right |
|---------|-------|-------|
| Using anon key | `ey...` (shorter) | `ey...` (much longer) |
| Copy-paste | Extra spaces | No spaces |
| Truncated | `eyJhbGc...` | `eyJhbGciOiJIUzI1NiIsIn...fQ` |
| Wrong environment | Dev key in prod | Prod key in prod |

## Quick Verification

Replace values and run:

```bash
# Test Supabase connection
curl -X POST "https://your-project.supabase.co/storage/v1/object/test/test.txt" \
  -H "Authorization: Bearer YOUR_SERVICE_ROLE_KEY" \
  -H "Content-Type: text/plain" \
  -d "test"

# If you get 403 "Invalid Compact JWS" -> Wrong key
# If you get another error -> Right key, different issue
```

## After Fixing

1. Update config server with correct `service_role` key
2. Restart backend application
3. Try uploading again
4. Should work!

## Still Not Working?

See full troubleshooting: [SUPABASE_TROUBLESHOOTING.md](./SUPABASE_TROUBLESHOOTING.md)
