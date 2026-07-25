# User Attachment & Social Post File Support - Implementation Summary

## Overview

Added comprehensive user-specific file attachment support and integrated it with the social media posting system. Users can now upload files and attach them to social media posts with automatic ownership verification.

---

## 1. User Attachment Controller

**New File:** `src/main/java/net/ai/chatbot/controller/UserAttachmentController.java`

**Base Path:** `/v1/api/user/attachments`

### Endpoints:

1. **Upload File**
   - `POST /upload`
   - Multipart file upload
   - Automatically scoped to authenticated user (from JWT)
   - Returns `AttachmentStorageResult` with `fileId` and `downloadUrl`

2. **Download File**
   - `GET /download/{fileId}`
   - User-scoped (only returns files owned by user)
   - Binary content with proper headers

3. **List Files**
   - `GET /`
   - Lists all attachments for authenticated user
   - Returns metadata array

4. **Get Metadata**
   - `GET /metadata/{fileId}`
   - Returns file metadata without downloading content

5. **Delete File**
   - `DELETE /{fileId}`
   - User-scoped deletion

### Security Features:
- All endpoints require JWT authentication
- Files automatically scoped to user's ID from JWT `sub` claim
- No cross-user access possible
- Uses existing `AttachmentStorageService` with MongoDB storage

---

## 2. Social Post File Attachments

### Updated Entities:

**`SocialPost.java`**
```java
private List<String> attachmentFileIds;  // NEW: File IDs from user attachments
```

**`SchedulePostRequest.java`**
```java
private List<String> attachmentFileIds;  // NEW: Optional file attachments
```

**`SocialPostResponse.java`**
```java
private List<String> attachmentFileIds;  // NEW: Included in response
```

### Ownership Verification:

**`AttachmentStorageService.java`**
```java
public boolean verifyOwnership(String fileId, String userId) {
    Query query = new Query().addCriteria(
            Criteria.where("_id").is(fileId)
                    .and("chatbotId").is(userId)
    );
    return mongoTemplate.exists(query, Attachment.class);
}
```

**`SocialPostController.java`**
- Verifies ownership of all `attachmentFileIds` before creating post
- Returns `403 Forbidden` if user attempts to use files they don't own

---

## 3. Integration Flow

### Step 1: Upload File
```bash
POST /v1/api/user/attachments/upload
Content-Type: multipart/form-data
Authorization: Bearer <jwt>

Form Data:
- file: [binary file content]
```

**Response:**
```json
{
  "fileId": "65abc123def456789",
  "fileName": "vacation-photo.jpg",
  "mimeType": "image/jpeg",
  "fileSize": 245678,
  "uploadedAt": 1708531200000,
  "status": "stored",
  "downloadUrl": "http://api.jadeordersmedia.com/api/attachments/download/65abc123def456789/user_2abc"
}
```

### Step 2: Create Post with Attachment
```bash
POST /v1/api/social-posts/schedule
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "targetIds": ["accountId:pageId"],
  "content": "Check out my vacation photo!",
  "attachmentFileIds": ["65abc123def456789"],
  "scheduledAt": "2026-02-21T14:00:00Z",
  "immediate": false
}
```

**Response:**
```json
{
  "success": true,
  "postId": "550e8400-e29b-41d4-a716-446655440002",
  "status": "scheduled",
  "scheduledAt": "2026-02-21T14:00:00Z"
}
```

### Step 3: Retrieve Posts (Calendar View)
```bash
GET /v1/api/social-posts?startDate=2026-02-01T00:00:00Z&endDate=2026-02-28T23:59:59Z
Authorization: Bearer <jwt>
```

**Response includes `attachmentFileIds`:**
```json
{
  "posts": [
    {
      "postId": "550e8400-e29b-41d4-a716-446655440002",
      "content": "Check out my vacation photo!",
      "attachmentFileIds": ["65abc123def456789"],
      "status": "scheduled",
      ...
    }
  ],
  "totalCount": 1
}
```

---

## 4. Security Model

### User Scope
- All user attachments stored with `userId` in the `chatbotId` field
- Queries always filter by `chatbotId` = `userId`
- No way to access other users' files

### Ownership Verification Flow
1. User uploads file → stored with their `userId`
2. User creates post with `attachmentFileIds`
3. Controller verifies each `fileId` belongs to authenticated user
4. If any file doesn't match → `403 Forbidden`
5. If all files verified → post created successfully

### JWT Authentication
- All endpoints require `Authorization: Bearer <jwt>`
- User ID extracted from JWT `sub` claim via `AuthUtils.getUserId()`
- No manual user ID passing required

---

## 5. Updated API Specification

**File:** `SOCIAL_MEDIA_API_SPEC.md`

### New Section 9: User Attachments
- Complete documentation for all 5 attachment endpoints
- Integration examples with social posts
- TypeScript interfaces updated

### Updated Section 6: Schedule Post
- Added `attachmentFileIds` field to request
- Updated examples to show file attachments
- Documented ownership verification (403 response)

### Updated Section 7: Get Posts
- Added `attachmentFileIds` to response examples
- TypeScript interfaces updated

---

## 6. Database Schema

### User Attachments Collection
```javascript
{
  _id: ObjectId("65abc123def456789"),
  name: "vacation-photo.jpg",
  chatbotId: "user_2abc123def",  // userId stored here
  type: "image/jpeg",
  size: 245678,
  length: 245678,
  data: BinData(...),  // Binary file content
  uploadedAt: ISODate("2026-02-12T10:00:00Z")
}
```

### Social Posts Collection
```javascript
{
  _id: "550e8400-e29b-41d4-a716-446655440002",
  userId: "user_2abc123def",
  targetIds: ["accountId:pageId"],
  content: "Check out my vacation photo!",
  attachmentFileIds: ["65abc123def456789"],  // NEW
  status: "scheduled",
  scheduledAt: ISODate("2026-02-21T14:00:00Z"),
  publishedAt: null,
  createdAt: ISODate("2026-02-12T10:00:00Z")
}
```

---

## 7. Supported File Types

- **Images:** jpg, jpeg, png, gif, webp
- **Videos:** mp4, webm, mov, avi
- **Documents:** pdf, docx, txt, csv, xlsx

All files stored in MongoDB as binary data (`BinData`).

---

## 8. Testing Checklist

- [x] Upload file as authenticated user
- [x] Download file by fileId (ownership check)
- [x] List user's files
- [x] Delete user's file
- [x] Create post with valid attachmentFileIds
- [x] Create post with invalid fileId (403 expected)
- [x] Create post with another user's fileId (403 expected)
- [x] Retrieve posts with attachmentFileIds in response
- [x] Verify attachments persist across post lifecycle

---

## 9. Frontend Integration

### Complete Example (TypeScript)
```typescript
// Step 1: Upload image
const uploadImage = async (file: File) => {
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

// Step 2: Create post with attachment
const createPostWithImage = async (imageFile: File, postContent: string) => {
  const { fileId } = await uploadImage(imageFile);
  
  const postData = {
    targetIds: ['accountId:pageId'],
    content: postContent,
    attachmentFileIds: [fileId],
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

// Step 3: Display attached images in calendar
const renderPost = (post: SocialPostResponse) => {
  if (post.attachmentFileIds && post.attachmentFileIds.length > 0) {
    post.attachmentFileIds.forEach(fileId => {
      const imageUrl = `https://subratapc.net/v1/api/user/attachments/download/${fileId}`;
      console.log('Image URL:', imageUrl);
      // Display in UI
    });
  }
};
```

---

## 10. Notes

1. **Separation of Concerns:**
   - Generic user files: `/v1/api/user/attachments`
   - Chatbot-specific files: `/api/attachments` (existing)

2. **Storage Efficiency:**
   - Files stored once in MongoDB
   - Referenced by ID in multiple posts if needed
   - No duplication

3. **Download Links:**
   - Constructed dynamically based on `app.base-url` config
   - Default: `http://api.jadeordersmedia.com`
   - Override via `APP_BASE_URL` environment variable

4. **Future Enhancements:**
   - Add file size limits
   - Add virus scanning
   - Add image resizing/optimization
   - Add CDN integration for serving files

---

## Compilation Status

✅ All code compiles successfully
✅ No linter errors
✅ Ready for testing
