# Social Media Suite API - Complete Specification (Backend Implementation)

**Base URL:** `https://subratapc.net`  
**Authentication:** All endpoints require `Authorization: Bearer <clerk_jwt>`  
**User Resolution:** Backend automatically resolves `userId` from JWT `sub` claim  
**Content-Type:** JSON unless specified otherwise

---

## 1. Connect Facebook Account

Store long-lived Facebook token with multiple pages.

**Endpoint:** `POST /v1/api/social-accounts/facebook`

**Request:**
```json
{
  "longLivedToken": "EAAxxxxxxxx",
  "pages": [
    {
      "pageId": "123456789012345",
      "pageName": "My Business Page",
      "pageAccessToken": "EAAxxxxxxxx"
    }
  ],
  "expiresIn": 5184000
}
```

**Response 200:**
```json
{
  "success": true,
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "platform": "facebook",
  "pagesCount": 1
}
```

**Notes:**
- Always adds new account (never overwrites)
- One Facebook login can contain multiple pages
- Tokens encrypted at rest using AES-256-GCM

---

## 2. Connect Twitter/X Account

Store Twitter OAuth 2.0 credentials.

**Endpoint:** `POST /v1/api/social-accounts/twitter`

**Request:**
```json
{
  "accessToken": "xxxxxxxx",
  "refreshToken": "xxxxxxxx",
  "expiresIn": 7200,
  "username": "myhandle"
}
```

**Response 200:**
```json
{
  "success": true,
  "accountId": "550e8400-e29b-41d4-a716-446655440001",
  "platform": "twitter"
}
```

---

## 3. List Connected Accounts

Get all social accounts for "My Accounts" page.

**Endpoint:** `GET /v1/api/social-accounts`

**Response 200:**
```json
{
  "accounts": [
    {
      "accountId": "550e8400-e29b-41d4-a716-446655440000",
      "platform": "facebook",
      "connectedAt": "2026-02-20T12:00:00Z",
      "pages": [
        {
          "pageId": "123456789012345",
          "pageName": "My Business Page"
        }
      ],
      "username": null
    },
    {
      "accountId": "550e8400-e29b-41d4-a716-446655440001",
      "platform": "twitter",
      "connectedAt": "2026-02-20T12:05:00Z",
      "pages": null,
      "username": "myhandle"
    }
  ]
}
```

---

## 4. List Posting Targets

Get available targets for "Create Post" dropdown.

**Endpoint:** `GET /v1/api/social-accounts/targets?platform={platform}`

**Query Parameters:**
- `platform` (optional): `facebook` or `twitter`

**Response 200:**
```json
{
  "targets": [
    {
      "targetId": "550e8400-e29b-41d4-a716-446655440000:123456789012345",
      "accountId": "550e8400-e29b-41d4-a716-446655440000",
      "platform": "facebook",
      "displayName": "Facebook - My Business Page",
      "pageId": "123456789012345",
      "pageName": "My Business Page",
      "username": null
    },
    {
      "targetId": "550e8400-e29b-41d4-a716-446655440001",
      "accountId": "550e8400-e29b-41d4-a716-446655440001",
      "platform": "twitter",
      "displayName": "X (Twitter) - @myhandle",
      "pageId": null,
      "pageName": null,
      "username": "myhandle"
    }
  ]
}
```

**Target ID Format:**
- Facebook: `{accountId}:{pageId}`
- Twitter: `{accountId}`

---

## 5. Disconnect Account

Remove a connected social account.

**Endpoint:** `DELETE /v1/api/social-accounts/{accountId}`

**Response 200:**
```json
{
  "success": true
}
```

**Response 404:**
```json
{
  "error": "Not Found",
  "message": "Account not found"
}
```

---

## 6. Upload Media (NEW)

Upload media files for social posts.

**Endpoint:** `POST /v1/api/social-media/upload`

**Content-Type:** `multipart/form-data`

**Form Fields:**
- `files` (multiple file parts) OR `file` (single file)
- `purpose` (optional string, e.g., "social_post")

**Accepted MIME Types:**
- Images: `image/*` (jpeg, png, gif, webp, etc.)
- Videos: `video/*` (mp4, webm, mov, avi, etc.)
- Documents: `application/pdf`

**Example Request (JavaScript):**
```javascript
const formData = new FormData();
formData.append('file', imageFile); // Single file
// OR for multiple:
// files.forEach(f => formData.append('files', f));

const response = await fetch('https://subratapc.net/v1/api/social-media/upload', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${jwtToken}`,
  },
  body: formData,
});
```

**Response 201 Created:**
```json
{
  "items": [
    {
      "mediaId": "m_7f0f2c",
      "mediaUrl": "https://api.jadeordersmedia.com/v1/api/user/attachments/download/m_7f0f2c",
      "mimeType": "image/jpeg",
      "fileName": "campaign-image.jpg",
      "sizeBytes": 234567,
      "width": 1080,
      "height": 1080,
      "durationMs": null,
      "thumbnailUrl": null
    },
    {
      "mediaId": "m_8a9b1d",
      "mediaUrl": "https://api.jadeordersmedia.com/v1/api/user/attachments/download/m_8a9b1d",
      "mimeType": "application/pdf",
      "fileName": "offer.pdf",
      "sizeBytes": 145678,
      "width": null,
      "height": null,
      "durationMs": null,
      "thumbnailUrl": null
    }
  ]
}
```

**Errors:**
- `400` - Invalid file type / empty file
- `413` - File too large (>50MB)
- `415` - Unsupported media type

**Notes:**
- Files stored in MongoDB with user ownership
- Image dimensions extracted automatically
- Max file size: 50MB per file
- All uploaded media verified for ownership before posting

---

## 7. Schedule/Publish Post (UPDATED for media)

Create a post (immediate or scheduled).

**Endpoint:** `POST /v1/api/social-posts/schedule`

**Request:**
```json
{
  "targetIds": [
    "550e8400-e29b-41d4-a716-446655440000:123456789012345",
    "550e8400-e29b-41d4-a716-446655440001"
  ],
  "content": "Launching now! Check details below.",
  "media": [
    {
      "mediaId": "m_7f0f2c",
      "mediaUrl": "https://api.jadeordersmedia.com/v1/api/user/attachments/download/m_7f0f2c",
      "mimeType": "image/jpeg",
      "fileName": "campaign-image.jpg",
      "sizeBytes": 234567
    }
  ],
  "scheduledAt": "2026-02-21T14:00:00Z",
  "immediate": false
}
```

**Request Validation:**
- `targetIds` (required): Array with minimum 1 target
- `immediate` (required): Boolean
- `scheduledAt` (required if `immediate=false`): Future ISO 8601 timestamp
- **At least one of:**
  - Non-empty `content` string
  - Non-empty `media` array
- Every `media[].mediaId` must belong to authenticated user (403 if not)

**Response 200 (scheduled):**
```json
{
  "success": true,
  "postId": "550e8400-e29b-41d4-a716-446655440002",
  "status": "scheduled",
  "scheduledAt": "2026-02-21T14:00:00Z"
}
```

**Response 200 (immediate):**
```json
{
  "success": true,
  "postId": "550e8400-e29b-41d4-a716-446655440002",
  "status": "published",
  "scheduledAt": "2026-02-20T14:30:00Z"
}
```

**Publishing Strategy:**
- **Facebook scheduled:** Uses native Facebook scheduling API
- **Twitter scheduled:** Saved to DB, published by backend cron job (every 60s)
- **Both immediate:** Published immediately by controller

**Status Values:**
- `scheduled` - Waiting for cron job (Twitter only)
- `pending_publish` - Being published now
- `published` - Successfully published
- `published_with_errors` - Partially published (some targets failed)
- `publish_failed` - Failed to publish

---

## 8. List Posts (Calendar + Scheduled Tabs)

Retrieve posts by date range for calendar display.

**Endpoint:** `GET /v1/api/social-posts`

**Query Parameters:**
| Parameter | Required | Type | Description |
|-----------|----------|------|-------------|
| `startDate` | Yes | ISO 8601 | Start of date range |
| `endDate` | Yes | ISO 8601 | End of date range |
| `platform` | No | String | Filter: `facebook` or `twitter` |
| `status` | No | String | Filter: `scheduled`, `published`, etc. |

**Example Request:**
```
GET /v1/api/social-posts?startDate=2026-02-01T00:00:00Z&endDate=2026-02-28T23:59:59Z&platform=facebook
Authorization: Bearer <jwt_token>
```

**Response 200:**
```json
{
  "posts": [
    {
      "postId": "550e8400-e29b-41d4-a716-446655440002",
      "userId": "user_2abc123def",
      "targetIds": [
        "550e8400-e29b-41d4-a716-446655440000:123456789012345"
      ],
      "content": "Hello from my scheduled post!",
      "status": "scheduled",
      "scheduledAt": "2026-02-21T14:00:00Z",
      "publishedAt": null,
      "createdAt": "2026-02-20T12:00:00Z",
      "targets": [
        {
          "targetId": "550e8400-e29b-41d4-a716-446655440000:123456789012345",
          "platform": "facebook",
          "displayName": "Facebook - My Business Page"
        }
      ],
      "media": [
        {
          "mediaId": "m_7f0f2c",
          "mediaUrl": "https://api.jadeordersmedia.com/v1/api/user/attachments/download/m_7f0f2c",
          "mimeType": "image/jpeg",
          "fileName": "campaign-image.jpg",
          "sizeBytes": 234567
        }
      ]
    }
  ],
  "totalCount": 1
}
```

**Important:** All timestamps returned as ISO 8601 strings consistently.

---

## 9. Resolve Token for Worker (Internal)

Internal endpoint for publishing workers/schedulers.

**Endpoint:** `POST /v1/api/social-accounts/targets/token`

**Request:**
```json
{
  "targetId": "550e8400-e29b-41d4-a716-446655440000:123456789012345"
}
```

**Response (Facebook):**
```json
{
  "platform": "facebook",
  "pageAccessToken": "EAAxxxxxxxx",
  "pageId": "123456789012345",
  "accessToken": null,
  "username": null
}
```

**Response (Twitter):**
```json
{
  "platform": "twitter",
  "pageAccessToken": null,
  "pageId": null,
  "accessToken": "xxxxxxxx",
  "username": "myhandle"
}
```

---

## Common Error Format

```json
{
  "error": "Bad Request",
  "message": "Validation failed: scheduledAt is required when immediate=false",
  "timestamp": 1772172475159,
  "path": "/v1/api/social-posts/schedule"
}
```

**Status Codes:**
- `400` - Bad Request (validation errors)
- `401` - Unauthorized (missing/invalid JWT)
- `403` - Forbidden (insufficient permissions, e.g., media ownership)
- `404` - Not Found (resource doesn't exist)
- `413` - Payload Too Large (file size exceeded)
- `415` - Unsupported Media Type
- `500` - Internal Server Error

---

## Backend Implementation Notes (Must-Have)

1. **Token Encryption**: All access tokens encrypted at rest using AES-256-GCM
2. **User Isolation**: Enforce by JWT `sub` claim - no cross-user access
3. **Media Ownership**: Verify uploaded media belongs to user before scheduling
4. **Publishing Pipeline:**
   - **Facebook:** Page token + media attach via Graph API
   - **Twitter:** Upload media to X API, then create tweet
   - **Partial Failures:** Map to `published_with_errors` status
5. **Scheduled Job:** Cron runs every 60 seconds to publish due Twitter posts
6. **MongoDB Storage:** All files stored as binary in MongoDB with metadata

---

## TypeScript Types (Frontend)

```typescript
// Media Upload
interface MediaItem {
  mediaId: string;
  mediaUrl: string;
  mimeType: string;
  fileName: string;
  sizeBytes: number;
  width?: number;
  height?: number;
  durationMs?: number;
  thumbnailUrl?: string;
}

interface MediaUploadResponse {
  items: MediaItem[];
}

// Posts
interface SchedulePostRequest {
  targetIds: string[];
  content: string;
  media?: MediaItem[];
  scheduledAt?: string; // ISO 8601
  immediate: boolean;
}

interface SchedulePostResponse {
  success: boolean;
  postId: string;
  status: 'scheduled' | 'published' | 'pending_publish' | 'published_with_errors' | 'publish_failed';
  scheduledAt: string; // ISO 8601
}

interface SocialPost {
  postId: string;
  userId: string;
  targetIds: string[];
  content: string;
  media?: MediaItem[];
  status: string;
  scheduledAt: string; // ISO 8601
  publishedAt: string | null; // ISO 8601
  createdAt: string; // ISO 8601
  targets: Array<{
    targetId: string;
    platform: 'facebook' | 'twitter';
    displayName: string;
  }>;
}
```

---

## Example Integration (Frontend)

```javascript
// Step 1: Upload media
const uploadMedia = async (file) => {
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await fetch(
    'https://subratapc.net/v1/api/social-media/upload',
    {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${jwtToken}` },
      body: formData,
    }
  );
  
  const { items } = await response.json();
  return items[0]; // First uploaded item
};

// Step 2: Create post with media
const createPostWithMedia = async (mediaFile, content, targetIds) => {
  const mediaItem = await uploadMedia(mediaFile);
  
  const postData = {
    targetIds: targetIds,
    content: content,
    media: [mediaItem],
    scheduledAt: '2026-02-21T14:00:00Z',
    immediate: false
  };
  
  const response = await fetch(
    'https://subratapc.net/v1/api/social-posts/schedule',
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${jwtToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(postData)
    }
  );
  
  return await response.json();
};
```

---

## Summary of Changes from Previous Spec

1. ✅ **NEW Endpoint**: `POST /v1/api/social-media/upload` for media upload
2. ✅ **Changed Field**: `attachmentFileIds` → `media` array in schedule request
3. ✅ **Media Structure**: Returns full `MediaItem` objects with metadata
4. ✅ **Validation**: At least one of `content` or `media` required
5. ✅ **Ownership Verification**: Backend checks `mediaId` ownership (403 if invalid)
6. ✅ **Response Format**: Posts include `media` array in list/calendar responses

**Backend Status**: ✅ Fully implemented and compiled successfully
