# Social Media Suite - Frontend API Specification

**Base URL:** `https://subratapc.net`  
**Authentication:** All endpoints require `Authorization: Bearer <clerk_jwt>` header  
**User ID:** Automatically resolved from JWT `sub` claim

---

## 1. Connect Facebook Account

Store long-lived Facebook token with multiple pages.

**Endpoint:** `POST /v1/api/social-accounts/facebook`

**Request Body:**
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

**Response:** `200 OK`
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
- One Facebook login can have multiple pages
- Tokens are encrypted before storage

---

## 2. Connect Twitter/X Account

Store Twitter OAuth 2.0 credentials.

**Endpoint:** `POST /v1/api/social-accounts/twitter`

**Request Body:**
```json
{
  "accessToken": "xxxxxxxx",
  "refreshToken": "xxxxxxxx",
  "expiresIn": 7200,
  "username": "myhandle"
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "accountId": "550e8400-e29b-41d4-a716-446655440001",
  "platform": "twitter"
}
```

---

## 3. List Connected Accounts

Get all connected social accounts for "My Accounts" page.

**Endpoint:** `GET /v1/api/social-accounts`

**Response:** `200 OK`
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
- `platform` (optional): Filter by `facebook` or `twitter`

**Response:** `200 OK`
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
- Facebook: `{accountId}:{pageId}` (e.g., `abc123:page456`)
- Twitter: `{accountId}` (e.g., `xyz789`)

---

## 5. Disconnect Account

Remove a connected social account.

**Endpoint:** `DELETE /v1/api/social-accounts/{accountId}`

**Response:** `200 OK`
```json
{
  "success": true
}
```

**Response:** `404 Not Found` (if account not found or not owned by user)

---

## 6. Schedule/Publish Post

Create a post (immediate or scheduled).

**Endpoint:** `POST /v1/api/social-posts/schedule`

**Request Body:**
```json
{
  "targetIds": [
    "550e8400-e29b-41d4-a716-446655440000:123456789012345"
  ],
  "content": "Hello from my scheduled post!",
  "attachmentFileIds": ["65abc123def456789", "65abc123def456790"],
  "scheduledAt": "2026-02-21T14:00:00Z",
  "immediate": false
}
```

**Request Fields:**
- `targetIds` (required): Array of target IDs to post to
- `content` (required): Post content/text
- `attachmentFileIds` (optional): Array of file IDs from `/v1/api/user/attachments/upload` (images, videos, documents)
- `scheduledAt` (required if `immediate=false`): ISO 8601 timestamp
- `immediate` (required): `true` = publish now, `false` = schedule for later

**Response (Scheduled):** `200 OK`
```json
{
  "success": true,
  "postId": "550e8400-e29b-41d4-a716-446655440002",
  "status": "scheduled",
  "scheduledAt": "2026-02-21T14:00:00Z"
}
```

**Response (Immediate):** `200 OK`
```json
{
  "success": true,
  "postId": "550e8400-e29b-41d4-a716-446655440002",
  "status": "published",
  "scheduledAt": "2026-02-20T14:30:00Z"
}
```

**Publishing Behavior:**
- **Facebook scheduled:** Uses native Facebook scheduling API (no cron needed)
- **Twitter scheduled:** Saved to DB, published by backend cron job
- **Both immediate:** Published immediately

**Status Values:**
- `scheduled` - Waiting for cron job (Twitter only)
- `pending_publish` - Being published now
- `published` - Successfully published
- `published_with_errors` - Partially published (some targets failed)
- `publish_failed` - Failed to publish

---

## 7. Get Posts for Calendar

Retrieve posts by date range for calendar display.

**Endpoint:** `GET /v1/api/social-posts`

**Query Parameters:**
| Parameter | Required | Type | Description | Example |
|-----------|----------|------|-------------|---------|
| `startDate` | Yes | ISO 8601 | Start of date range | `2026-02-01T00:00:00Z` |
| `endDate` | Yes | ISO 8601 | End of date range | `2026-02-28T23:59:59Z` |
| `platform` | No | String | Filter by platform | `facebook` or `twitter` |
| `status` | No | String | Filter by status | `scheduled`, `published`, etc. |

**Example Request:**
```
GET /v1/api/social-posts?startDate=2026-02-01T00:00:00Z&endDate=2026-02-28T23:59:59Z&platform=facebook
Authorization: Bearer <jwt_token>
```

**Response:** `200 OK`
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
      "attachmentFileIds": ["65abc123def456789"],
      "status": "scheduled",
      "scheduledAt": "2026-02-15T14:00:00Z",
      "publishedAt": null,
      "createdAt": "2026-02-01T10:00:00Z",
      "targets": [
        {
          "targetId": "550e8400-e29b-41d4-a716-446655440000:123456789012345",
          "platform": "facebook",
          "displayName": "Facebook - My Business Page"
        }
      ]
    },
    {
      "postId": "550e8400-e29b-41d4-a716-446655440003",
      "userId": "user_2abc123def",
      "targetIds": [
        "550e8400-e29b-41d4-a716-446655440001"
      ],
      "content": "Tweet scheduled for tomorrow",
      "status": "scheduled",
      "scheduledAt": "2026-02-16T09:00:00Z",
      "publishedAt": null,
      "createdAt": "2026-02-01T11:00:00Z",
      "targets": [
        {
          "targetId": "550e8400-e29b-41d4-a716-446655440001",
          "platform": "twitter",
          "displayName": "X (Twitter) - @myhandle"
        }
      ]
    }
  ],
  "totalCount": 2
}
```

**Response Fields:**
- `posts`: Array of post objects sorted by `scheduledAt` (ascending)
- `totalCount`: Total number of posts matching filters
- Each post includes:
  - `targets`: Array with platform and display name for each target
  - `scheduledAt`: When the post is/was scheduled to publish
  - `publishedAt`: When the post was actually published (null if not yet published)
  - `status`: Current post status

**Use Cases:**
- Display posts in calendar month view
- Show scheduled vs published posts with different colors
- Filter by platform to show only Facebook or Twitter posts
- Click on calendar date to see posts for that day

---

## 8. Internal Token Resolution (For Workers)

Resolve access tokens for publishing. Internal use only.

**Endpoint:** `POST /v1/api/social-accounts/targets/token`

**Request Body:**
```json
{
  "targetId": "550e8400-e29b-41d4-a716-446655440000:123456789012345"
}
```

**Response (Facebook):** `200 OK`
```json
{
  "platform": "facebook",
  "pageAccessToken": "EAAxxxxxxxx",
  "pageId": "123456789012345",
  "accessToken": null,
  "username": null
}
```

**Response (Twitter):** `200 OK`
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

## Error Responses

**401 Unauthorized:**
```json
{
  "error": "Unauthorized",
  "message": "Authentication required"
}
```

**404 Not Found:**
```json
{
  "error": "Not Found",
  "message": "Resource not found"
}
```

**400 Bad Request:**
```json
{
  "error": "Bad Request",
  "message": "Missing required field: targetIds"
}
```

---

## Authentication Flow

1. User logs in via Clerk
2. Frontend receives JWT token
3. Include token in all API requests:
   ```javascript
   headers: {
     'Authorization': `Bearer ${jwtToken}`,
     'Content-Type': 'application/json'
   }
   ```
4. Backend extracts `userId` from JWT `sub` claim
5. All operations scoped to authenticated user

---

## TypeScript Types (Optional)

```typescript
// Accounts
interface ConnectFacebookRequest {
  longLivedToken: string;
  pages: {
    pageId: string;
    pageName: string;
    pageAccessToken: string;
  }[];
  expiresIn: number;
}

interface ConnectTwitterRequest {
  accessToken: string;
  refreshToken?: string;
  expiresIn?: number;
  username: string;
}

interface SocialAccount {
  accountId: string;
  platform: 'facebook' | 'twitter';
  connectedAt: string; // ISO 8601
  pages?: { pageId: string; pageName: string }[];
  username?: string;
}

interface SocialTarget {
  targetId: string;
  accountId: string;
  platform: 'facebook' | 'twitter';
  displayName: string;
  pageId?: string;
  pageName?: string;
  username?: string;
}

// Posts
interface SchedulePostRequest {
  targetIds: string[];
  content: string;
  scheduledAt?: string; // ISO 8601
  immediate: boolean;
}

interface SchedulePostResponse {
  success: boolean;
  postId: string;
  status: 'scheduled' | 'published' | 'pending_publish' | 'published_with_errors' | 'publish_failed';
  scheduledAt: string; // ISO 8601
}

interface SocialPostResponse {
  postId: string;
  userId: string;
  targetIds: string[];
  content: string;
  attachmentFileIds?: string[];
  status: string;
  scheduledAt: string; // ISO 8601
  publishedAt: string | null; // ISO 8601
  createdAt: string; // ISO 8601
  targets: {
    targetId: string;
    platform: 'facebook' | 'twitter';
    displayName: string;
  }[];
}

interface GetPostsResponse {
  posts: SocialPostResponse[];
  totalCount: number;
}
```

---

## Calendar Integration Example

```typescript
// Fetch February 2026 posts
const fetchCalendarPosts = async (month: number, year: number) => {
  const startDate = new Date(year, month - 1, 1).toISOString();
  const endDate = new Date(year, month, 0, 23, 59, 59).toISOString();
  
  const response = await fetch(
    `https://subratapc.net/v1/api/social-posts?` +
    `startDate=${startDate}&endDate=${endDate}`,
    {
      headers: {
        'Authorization': `Bearer ${getClerkToken()}`,
      }
    }
  );
  
  const { posts, totalCount } = await response.json();
  
  // Group by date for calendar display
  const postsByDate = posts.reduce((acc, post) => {
    const date = post.scheduledAt.split('T')[0]; // YYYY-MM-DD
    if (!acc[date]) acc[date] = [];
    acc[date].push(post);
    return acc;
  }, {});
  
  return postsByDate;
};
```

---

## 9. User Attachments (File Upload)

Upload files for the authenticated user (images, videos, documents for social posts).

### Upload File

**Endpoint:** `POST /v1/api/user/attachments/upload`

**Content-Type:** `multipart/form-data`

**Authentication:** Required (JWT)

**Form Data:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | File | Yes | The file to upload |

**Example Request (JavaScript):**
```javascript
const formData = new FormData();
formData.append('file', imageFile); // File from input[type="file"]

const response = await fetch('https://subratapc.net/v1/api/user/attachments/upload', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${jwtToken}`,
  },
  body: formData,
  // Note: Don't set Content-Type header, browser sets it with boundary
});

const result = await response.json();
```

**Response:** `201 Created`
```json
{
  "fileId": "65abc123def456789",
  "fileName": "vacation-photo.jpg",
  "mimeType": "image/jpeg",
  "fileSize": 245678,
  "uploadedAt": 1708531200000,
  "status": "stored",
  "downloadUrl": "http://api.jadeordersmedia.com/api/attachments/download/65abc123def456789/user_2abc123def"
}
```

**Supported File Types:**
- Images: jpg, jpeg, png, gif, webp
- Videos: mp4, webm, mov, avi
- Documents: pdf, docx, txt, csv, xlsx

**Notes:**
- Files automatically scoped to authenticated user
- No need to pass userId (extracted from JWT)
- Returns `downloadUrl` for immediate access
- Files stored in MongoDB

---

### Download File

**Endpoint:** `GET /v1/api/user/attachments/download/{fileId}`

**Authentication:** Required (JWT)

**Response:** Binary file content with appropriate headers

---

### Get File Metadata

**Endpoint:** `GET /v1/api/user/attachments/metadata/{fileId}`

**Authentication:** Required (JWT)

**Response:** `200 OK`
```json
{
  "fileId": "65abc123def456789",
  "fileName": "vacation-photo.jpg",
  "mimeType": "image/jpeg",
  "fileSize": 245678,
  "uploadedAt": 1708531200000,
  "status": "stored"
}
```

---

### List User's Files

**Endpoint:** `GET /v1/api/user/attachments`

**Authentication:** Required (JWT)

**Response:** `200 OK`
```json
{
  "userId": "user_2abc123def",
  "totalFiles": 5,
  "files": [
    {
      "fileId": "65abc123def456789",
      "fileName": "vacation-photo.jpg",
      "mimeType": "image/jpeg",
      "fileSize": 245678,
      "uploadedAt": 1708531200000,
      "status": "stored"
    }
  ]
}
```

---

### Delete File

**Endpoint:** `DELETE /v1/api/user/attachments/{fileId}`

**Authentication:** Required (JWT)

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "File deleted"
}
```

---

### Usage with Social Posts

**Example: Upload image and create post with it**

```javascript
// Step 1: Upload image
const uploadImage = async (file) => {
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await fetch(
    'https://subratapc.net/v1/api/user/attachments/upload',
    {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${jwtToken}` },
      body: formData,
    }
  );
  
  return await response.json();
};

// Step 2: Create post with image URL
const createPostWithImage = async (imageFile, postContent) => {
  // Upload image first
  const { fileId, downloadUrl } = await uploadImage(imageFile);
  
  // Create post with image reference
  const postData = {
    targetIds: ['accountId:pageId'],
    content: postContent,
    attachmentFileIds: [fileId], // Attach uploaded file(s)
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

**Important Notes:**

1. **Ownership Verification**: The backend verifies that all `attachmentFileIds` belong to the authenticated user before creating the post. If any file doesn't belong to the user, the request will fail with `403 Forbidden`.

2. **File Access**: When posts are published to social platforms, the files are downloaded from the backend using the user's stored file IDs.

3. **Multiple Files**: You can attach multiple files by providing an array of file IDs: `attachmentFileIds: ["fileId1", "fileId2", "fileId3"]`

---

## Notes

- All timestamps are in ISO 8601 format (UTC)
- All endpoints require Clerk JWT authentication
- User isolation: Users can only access their own data
- Tokens are encrypted at rest
- Facebook uses native scheduling (no backend cron needed)
- Twitter uses backend cron job (runs every 60 seconds)
- Posts are sorted by `scheduledAt` ascending for calendar display
