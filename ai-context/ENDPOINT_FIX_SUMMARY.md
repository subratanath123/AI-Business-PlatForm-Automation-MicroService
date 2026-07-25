# Endpoint Fix - Quick Summary

## Issue
Frontend was trying to use endpoint `/v1/api/social-media/schedule` which doesn't exist.

## Root Cause
Documentation had wrong endpoint path. Backend actually uses `/v1/api/social-posts` (not `/v1/api/social-media`).

## Solution

### ❌ WRONG (What frontend was using)
```
POST /v1/api/social-media/schedule
```

### ✅ CORRECT (What to use)
```
POST /v1/api/social-posts/schedule
```

---

## Files Updated

✅ SOCIAL_ASSET_FRONTEND_INTEGRATION.md
✅ FRONTEND_CODE_MIGRATION_EXAMPLES.md
✅ FRONTEND_CHANGES_QUICK_GUIDE.md
✅ FRONTEND_DOCUMENTATION_INDEX.md

---

## Complete Request Example

**POST /v1/api/social-posts/schedule**

```json
{
  "content": "Check this out!",
  "media": [
    {
      "mediaId": "social_asset_id_123",
      "mediaUrl": "https://api.../download/...",
      "fileName": "image.jpg"
    }
  ],
  "targetIds": ["facebook:page_id", "twitter:@username"],
  "scheduledAt": "2026-03-20T15:00:00Z",
  "immediate": false
}
```

---

## Key Points

✅ Upload endpoint: `/v1/api/social-media/upload` (no change)
✅ Schedule endpoint: `/v1/api/social-posts/schedule` (UPDATED)
✅ Get posts endpoint: `/v1/api/social-posts` (no change)

---

## What Changed

- **Endpoint URL:** `/v1/api/social-media/schedule` → `/v1/api/social-posts/schedule`
- **TargetIds:** Now required (format: `platform:accountId`)
- **Everything else:** No changes

---

## Next Steps for Frontend

1. Search for `/v1/api/social-media/schedule` in codebase
2. Replace with `/v1/api/social-posts/schedule`
3. Add `targetIds` field to request payload
4. Test the endpoint

---

## Reference Document

Full details: [FRONTEND_ENDPOINT_CORRECTION.md](FRONTEND_ENDPOINT_CORRECTION.md)
