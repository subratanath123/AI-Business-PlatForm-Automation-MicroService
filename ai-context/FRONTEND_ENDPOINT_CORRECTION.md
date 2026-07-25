# Frontend Endpoint Correction

## Issue Found

The frontend documentation had an **incorrect endpoint** for scheduling posts.

---

## Correction

### ❌ WRONG
```
POST /v1/api/social-media/schedule
```

### ✅ CORRECT
```
POST /v1/api/social-posts/schedule
```

---

## Updated Endpoint Reference

| Operation | Endpoint | Method | Status |
|-----------|----------|--------|--------|
| Upload Media | `/v1/api/social-media/upload` | POST | ✅ Correct |
| Schedule Post | `/v1/api/social-posts/schedule` | POST | ✅ Fixed |
| Get Posts | `/v1/api/social-posts` | GET | ✅ Correct |

---

## Complete Schedule Post Request

**Endpoint:** `POST /v1/api/social-posts/schedule`

**Headers:**
```
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "content": "Check this out!",
  "media": [
    {
      "mediaId": "social_asset_id_123",
      "mediaUrl": "https://api.../download/attachment_id",
      "fileName": "image.jpg"
    }
  ],
  "targetIds": ["facebook:page_id_123", "twitter:account_id_456"],
  "scheduledAt": "2026-03-20T15:00:00Z",
  "immediate": false
}
```

**Important Fields:**
- `targetIds`: Format is `platform:accountId`
  - Facebook: `facebook:page_id`
  - Twitter: `twitter:username`
  - LinkedIn: `linkedin:user_id`
- `media`: Array of media items from upload response
- `scheduledAt`: ISO 8601 timestamp (or omit for immediate)
- `immediate`: Boolean (true = post now, false = schedule)

**Response:**
```json
{
  "postId": "post_id_123",
  "status": "pending_publish",
  "scheduledAt": "2026-03-20T15:00:00Z",
  "content": "Check this out!",
  "targets": [
    {
      "platform": "facebook",
      "accountId": "page_id_123",
      "status": "pending"
    }
  ]
}
```

---

## cURL Example

```bash
curl -X POST https://api.jadeordersmedia.com/v1/api/social-posts/schedule \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Check this out!",
    "media": [
      {
        "mediaId": "507f1f77bcf86cd799439011",
        "mediaUrl": "https://api.jadeordersmedia.com/v1/api/user/attachments/download/attachment_123",
        "fileName": "image.jpg"
      }
    ],
    "targetIds": ["facebook:page_123456789", "twitter:@username"],
    "scheduledAt": "2026-03-20T15:00:00Z",
    "immediate": false
  }'
```

---

## TypeScript Update

```typescript
// Update your fetch call
const schedulePost = async (
  content: string,
  media: MediaItem[],
  targetIds: string[],
  scheduledAt?: string
) => {
  const response = await fetch('/v1/api/social-posts/schedule', {  // ✅ Updated endpoint
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      content,
      media: media.map(m => ({
        mediaId: m.mediaId,
        mediaUrl: m.mediaUrl,
        fileName: m.fileName,
      })),
      targetIds,  // ✅ Add target IDs
      scheduledAt,
      immediate: !scheduledAt,  // Post immediately if no scheduledAt
    }),
  });

  return response.json();
};
```

---

## Frontend Code Update - All Locations

### 1. React Component
```typescript
// Change this:
await fetch('/v1/api/social-media/schedule', {...})

// To this:
await fetch('/v1/api/social-posts/schedule', {...})
```

### 2. API Service
```typescript
// Update your API service
export const schedulePost = (payload: SchedulePostRequest) => {
  return fetch('/v1/api/social-posts/schedule', {  // ✅ Updated
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
};
```

### 3. Custom Hook
```typescript
// Update hook endpoint
export const useSchedulePost = (token: string) => {
  const schedule = useCallback(async (payload: SchedulePostRequest) => {
    const response = await fetch('/v1/api/social-posts/schedule', {  // ✅ Updated
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });
    return response.json();
  }, [token]);

  return { schedule };
};
```

---

## Important: TargetIds Format

When scheduling posts, you must provide `targetIds` in the correct format:

```
facebook:page_id              // Facebook business page
facebook:page_id_1,page_id_2  // Multiple pages (comma-separated in same string)

twitter:@username             // Twitter account (with @)
twitter:@user1,@user2         // Multiple accounts

linkedin:user_id              // LinkedIn user ID
```

Example:
```typescript
const targetIds = [
  'facebook:123456789',
  'twitter:@myaccount',
  'linkedin:987654321'
];
```

---

## What Still Works

✅ Upload endpoint: `/v1/api/social-media/upload` (no change)
✅ Response format: Same `mediaId`, `mediaUrl`, etc.
✅ Media item structure: No changes
✅ Authentication: Bearer token same
✅ File size limit: 50MB still applies

---

## Summary

| Item | Before | After | Action |
|------|--------|-------|--------|
| Schedule endpoint | `/v1/api/social-media/schedule` | `/v1/api/social-posts/schedule` | 🔴 **Update** |
| Upload endpoint | `/v1/api/social-media/upload` | Same | ✅ No change |
| targetIds | Not required | Required | 🟡 Add to request |
| Response format | Same | Same | ✅ No change |

---

## Affected Documents

Updated documentation:
- ✅ SOCIAL_ASSET_FRONTEND_INTEGRATION.md
- ✅ FRONTEND_CODE_MIGRATION_EXAMPLES.md
- ✅ FRONTEND_CHANGES_QUICK_GUIDE.md

Please review these files again for the correct endpoint.

---

## Testing the Update

```bash
# Test the correct endpoint
curl -X POST https://api.../v1/api/social-posts/schedule \
  -H "Authorization: Bearer token" \
  -H "Content-Type: application/json" \
  -d '{"content": "test", "media": [], "targetIds": ["facebook:123"]}'

# Should return 200 OK with post details
# Not 404 Not Found
```

---

## Next Steps

1. **Update your frontend code** to use `/v1/api/social-posts/schedule`
2. **Add targetIds** to your schedule post request
3. **Test the endpoint** with the corrected URL
4. **Verify media scheduling** works end-to-end

The backend is ready. The endpoint exists and works correctly at the address above.
