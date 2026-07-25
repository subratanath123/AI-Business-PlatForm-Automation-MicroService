# Schedule Post API Reference

Complete API reference for scheduling social media posts with media.

---

## Endpoint

```
POST /v1/api/social-posts/schedule
```

**Base URL:** `https://api.jadeordersmedia.com`

**Full URL:** `https://api.jadeordersmedia.com/v1/api/social-posts/schedule`

---

## Authentication

**Header:** `Authorization: Bearer <jwt_token>`

**Token Source:** Clerk JWT (from authentication)

---

## Request

### Headers
```http
POST /v1/api/social-posts/schedule HTTP/1.1
Host: api.jadeordersmedia.com
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6Ik...
```

### Body Schema

```typescript
{
  // Required: Post content
  "content": string,                    // Max 280 chars for Twitter, longer for others
  
  // Optional: Media files to attach
  "media": [
    {
      "mediaId": string,                // From /v1/api/social-media/upload response
      "mediaUrl": string,               // CDN URL to media file
      "fileName": string                // Original filename
    }
  ],
  
  // Required: Target accounts
  "targetIds": [
    "platform:account_id"               // Format: facebook:123456789, twitter:@username, etc.
  ],
  
  // Optional: Schedule for later
  "scheduledAt": string,                // ISO 8601: "2026-03-20T15:00:00Z" or omit for now
  
  // Optional: Post immediately
  "immediate": boolean                  // true = post now, false = schedule
}
```

---

## Target ID Formats

### Facebook
```
facebook:page_id

Example: facebook:123456789
```

### Twitter
```
twitter:@username

Example: twitter:@myaccount
```

### LinkedIn
```
linkedin:user_id

Example: linkedin:987654321
```

### Multiple Targets
```json
{
  "targetIds": [
    "facebook:page_123",
    "twitter:@user",
    "linkedin:user_456"
  ]
}
```

---

## Request Examples

### Example 1: Immediate Post with Media (Facebook)
```bash
curl -X POST https://api.jadeordersmedia.com/v1/api/social-posts/schedule \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Check out this image!",
    "media": [
      {
        "mediaId": "507f1f77bcf86cd799439011",
        "mediaUrl": "https://api.jadeordersmedia.com/v1/api/user/attachments/download/attachment_123",
        "fileName": "photo.jpg"
      }
    ],
    "targetIds": ["facebook:123456789"],
    "immediate": true
  }'
```

### Example 2: Scheduled Post (Twitter)
```bash
curl -X POST https://api.jadeordersmedia.com/v1/api/social-posts/schedule \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Excited to announce our new product! 🚀",
    "media": [
      {
        "mediaId": "507f1f77bcf86cd799439012",
        "mediaUrl": "https://api.jadeordersmedia.com/v1/api/user/attachments/download/attachment_124",
        "fileName": "announcement.jpg"
      }
    ],
    "targetIds": ["twitter:@mycompany"],
    "scheduledAt": "2026-03-20T15:00:00Z"
  }'
```

### Example 3: Multi-Platform Post
```bash
curl -X POST https://api.jadeordersmedia.com/v1/api/social-posts/schedule \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Great news for everyone!",
    "media": [
      {
        "mediaId": "507f1f77bcf86cd799439013",
        "mediaUrl": "https://api.jadeordersmedia.com/v1/api/user/attachments/download/attachment_125",
        "fileName": "news.jpg"
      }
    ],
    "targetIds": [
      "facebook:123456789",
      "twitter:@company",
      "linkedin:987654321"
    ],
    "scheduledAt": "2026-03-21T10:00:00Z"
  }'
```

### Example 4: Text-Only Post (No Media)
```bash
curl -X POST https://api.jadeordersmedia.com/v1/api/social-posts/schedule \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Join us for a live webinar tomorrow at 2 PM EST!",
    "targetIds": ["facebook:123456789"],
    "immediate": true
  }'
```

---

## Response

### Success (200 OK)
```json
{
  "postId": "507f1f77bcf86cd799439014",
  "status": "published",
  "content": "Check out this image!",
  "scheduledAt": "2026-03-20T15:00:00Z",
  "createdAt": "2026-03-19T10:00:00Z",
  "targets": [
    {
      "platform": "facebook",
      "accountId": "123456789",
      "accountName": "My Business Page",
      "status": "published"
    }
  ]
}
```

### Bad Request (400)
```json
{
  "error": "Bad Request",
  "message": "mediaId is required for all media items"
}
```

### Unauthorized (401)
```json
{
  "error": "Unauthorized",
  "message": "Authentication required"
}
```

### Forbidden (403)
```json
{
  "error": "Forbidden",
  "message": "You do not have permission to use media: <mediaId>"
}
```

### Server Error (500)
```json
{
  "error": "Internal Server Error",
  "message": "Failed to schedule post"
}
```

---

## TypeScript Types

```typescript
interface SchedulePostRequest {
  content: string;
  media?: MediaItem[];
  targetIds: string[];
  scheduledAt?: string;
  immediate?: boolean;
}

interface MediaItem {
  mediaId: string;
  mediaUrl: string;
  fileName: string;
}

interface SchedulePostResponse {
  postId: string;
  status: 'published' | 'pending_publish' | 'published_with_errors' | 'publish_failed';
  content: string;
  scheduledAt?: string;
  createdAt: string;
  targets: TargetStatus[];
}

interface TargetStatus {
  platform: 'facebook' | 'twitter' | 'linkedin';
  accountId: string;
  accountName: string;
  status: 'published' | 'pending' | 'failed';
  error?: string;
}
```

---

## Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Post scheduled/published |
| 400 | Bad Request | Fix request body |
| 401 | Unauthorized | Check authentication token |
| 403 | Forbidden | User can't use this media |
| 500 | Server Error | Retry or contact support |

---

## Response Status Values

| Status | Meaning | Next Steps |
|--------|---------|-----------|
| `published` | Post live | Monitor engagement |
| `pending_publish` | Queued for publishing | Wait for processing |
| `published_with_errors` | Some platforms failed | Check target errors |
| `publish_failed` | All failed | Check error message |

---

## Common Errors & Solutions

### Error: "mediaId is required"
**Problem:** Media array has an item without mediaId
**Solution:** Ensure all media items have `mediaId` from upload response

### Error: "You do not have permission to use media"
**Problem:** Using someone else's media
**Solution:** Use media from your own uploads

### Error: "Authentication required"
**Problem:** Missing or invalid Bearer token
**Solution:** Include valid JWT in Authorization header

### Error: "Failed to schedule post"
**Problem:** Server-side error
**Solution:** Check network/retry or contact support

---

## Best Practices

1. **Always verify media IDs** before scheduling
2. **Use ISO 8601 format** for scheduledAt: `YYYY-MM-DDTHH:mm:ssZ`
3. **Test with text-only post** first if new to API
4. **Monitor scheduled posts** via GET /v1/api/social-posts
5. **Handle partial failures** (some platforms may fail)
6. **Validate targetIds** format before sending request

---

## Content Limits

| Platform | Limit | Notes |
|----------|-------|-------|
| Facebook | 63,206 chars | With links/media |
| Twitter | 280 chars | Including spaces |
| LinkedIn | 3,000 chars | With media |

---

## Media Support

| Platform | Image | Video | Format |
|----------|-------|-------|--------|
| Facebook | ✅ | ✅ | JPG, PNG, MP4 |
| Twitter | ✅ | ✅ | JPG, PNG, GIF, MP4 |
| LinkedIn | ✅ | ❌ | JPG, PNG |

---

## Timing

- **Immediate:** Posted instantly (5-30 seconds)
- **Scheduled:** Queued until specified time
- **Retry:** Automatic retry if platform temporarily unavailable

---

## Rate Limits

- **Requests:** 60 per minute per user
- **Posts:** 10 scheduled posts per day

---

## Complete React Example

```typescript
const schedulePost = async (
  token: string,
  content: string,
  media: MediaItem[],
  targetIds: string[],
  scheduledAt?: string
) => {
  try {
    const response = await fetch(
      'https://api.jadeordersmedia.com/v1/api/social-posts/schedule',
      {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          content,
          media: media.length > 0 ? media : undefined,
          targetIds,
          scheduledAt,
          immediate: !scheduledAt,
        }),
      }
    );

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message);
    }

    const result = await response.json();
    console.log('Post scheduled:', result.postId);
    return result;
  } catch (error) {
    console.error('Failed to schedule post:', error);
    throw error;
  }
};
```

---

## Summary

| Item | Value |
|------|-------|
| **Endpoint** | `POST /v1/api/social-posts/schedule` |
| **Auth** | Bearer JWT token |
| **Content-Type** | application/json |
| **Required Fields** | content, targetIds |
| **Optional Fields** | media, scheduledAt, immediate |
| **Success Code** | 200 OK |
| **Rate Limit** | 60/min per user |
