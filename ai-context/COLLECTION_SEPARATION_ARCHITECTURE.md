# Collection Separation Architecture

## Overview

This document outlines the separation of concerns for file storage collections in the Chat API. We have three distinct use cases, each with its own collection, ownership model, and access patterns.

---

## Collections Breakdown

### 1. **Attachment Collection** (Chatbot Knowledge Base)

**Purpose:** Store files uploaded to chatbot knowledge bases (PDFs, texts, images for RAG).

**Collection Name:** `attachments` (or `attachments_{chatbotId}`)

**Ownership Model:** `chatbotId` (chatbot-specific, not user-specific)

**Fields:**
```
{
  "_id": "ObjectId",
  "chatbotId": "support-bot",  // Chatbot this file belongs to
  "name": "document.pdf",
  "type": "application/pdf",
  "size": 1024000,
  "length": 1024000,
  "uploadedAt": "2026-03-19T10:00:00Z",
  "data": <binary>
}
```

**Used By:**
- `AttachmentStorageService` - stores/retrieves file content
- `AttachmentDownloadController` - downloads files by fileId
- Knowledge base processing (N8N, vector search)

**Access Control:**
- Any authenticated user can read if they have access to the chatbot
- Files are tied to chatbots, not individual users

---

### 2. **MediaAsset Collection** (Personal User Files)

**Purpose:** Store files uploaded by users for personal use (profile pictures, documents, media library).

**Collection Name:** `media_assets`

**Ownership Model:** `userEmail` (user-specific, from JWT)

**Fields:**
```
{
  "_id": "ObjectId",
  "userEmail": "user@example.com",  // User who uploaded
  "fileName": "profile.jpg",
  "mimeType": "image/jpeg",
  "sizeBytes": 2048000,
  "supabaseUrl": "https://bucket.supabase.co/...",  // Public CDN URL
  "objectPath": "personal/{userEmail}/...",  // Path in Supabase bucket
  "createdAt": "2026-03-19T10:00:00Z",
  "tags": ["profile", "personal"]
}
```

**Used By:**
- `MediaAssetController` - uploads/lists/deletes personal files
- `MediaAssetService` - business logic for asset management
- `SupabaseStorageService` - uploads to Supabase Storage (backend-owned)
- Frontend media library, profile settings

**Access Control:**
- Files stored in Supabase Storage (not MongoDB)
- Only the user who uploaded can access/delete
- Verified by `userEmail` from JWT
- Metadata tracked in MongoDB

---

### 3. **SocialAsset Collection** (Social Media Post Attachments) ⭐ NEW

**Purpose:** Store metadata for files uploaded for social media posts (Facebook, Twitter, LinkedIn).

**Collection Name:** `social_assets`

**Ownership Model:** `userEmail` (user-specific, from JWT)

**Fields:**
```
{
  "_id": "ObjectId",
  "userEmail": "user@example.com",  // User who uploaded
  "attachmentId": "attachment_id_123",  // Reference to file in Attachment
  "fileName": "campaign.jpg",
  "mimeType": "image/jpeg",
  "sizeBytes": 3072000,
  "downloadUrl": "https://api.jadeordersmedia.com/v1/api/user/attachments/download/attachment_id_123",
  "width": 1920,
  "height": 1080,
  "durationMs": null,  // For video files
  "createdAt": "2026-03-19T10:00:00Z"
}
```

**Used By:**
- `SocialMediaUploadController` - uploads media for posts
- `SocialPostController` - associates media with scheduled posts
- Facebook/Twitter/LinkedIn publishers

**Access Control:**
- File content stored in `Attachment` collection (reference: `attachmentId`)
- Metadata in `SocialAsset` (indexed by `userEmail`)
- Only the user who uploaded can use in their posts

---

## Data Flow Comparison

### Chatbot Knowledge Upload
```
User Upload
    ↓
AttachmentDownloadController
    ↓
AttachmentStorageService.storeAttachmentInMongoDB()
    ↓
Attachment{chatbotId="support-bot", data=<bytes>}
    ↓
Knowledge Base Processing (RAG, Vector Search)
```

### Personal Media Upload
```
User Upload
    ↓
MediaAssetController
    ↓
MediaAssetService.uploadAll()
    ↓
SupabaseStorageService.upload() → Supabase Storage
    ↓
MediaAsset{userEmail="user@example.com", supabaseUrl="https://..."}
    ↓
Frontend Media Library
```

### Social Media Post Upload (NEW)
```
User Upload
    ↓
SocialMediaUploadController
    ↓
Step 1: AttachmentStorageService → Attachment{chatbotId=userId, data=<bytes>}
    ↓
Step 2: SocialAssetDao.save() → SocialAsset{userEmail="user@example.com", attachmentId="..."}
    ↓
SocialPostController (includes mediaId in post)
    ↓
FacebookPublisher/TwitterPublisher
    ↓
Social Media Platforms
```

---

## Key Differences Summary

| Aspect | Attachment | MediaAsset | SocialAsset |
|--------|-----------|-----------|-----------|
| **Collection** | `attachments` | `media_assets` | `social_assets` |
| **Owner Field** | `chatbotId` | `userEmail` | `userEmail` |
| **Storage** | MongoDB (binary) | Supabase Storage | MongoDB (metadata only) |
| **Access** | Chatbot-based | User-based | User-based |
| **Use Case** | Knowledge base | Personal files | Social posts |
| **File Content** | Stored | Stored remotely | Referenced |
| **Typical Size** | Small-Medium | Large (images, videos) | Medium |
| **Deletion** | Delete from MongoDB | Delete from Supabase + MongoDB | Delete metadata, keep Attachment |

---

## Ownership & Access Control

### Attachment (Chatbot)
```java
// No direct user ownership
// Access: user must have permission to chatbot
chatbotId = "support-bot"
```

### MediaAsset (Personal)
```java
// User-owned, verified by email
@Indexed private String userEmail;
// Query: findByUserEmailAndFileNameContaining(userEmail, ...)
```

### SocialAsset (Social Posts)
```java
// User-owned, verified by email
@Indexed private String userEmail;
// Query: findByUserEmailAndIdAndUserEmail(id, userEmail)
// Cross-reference: SocialAsset.attachmentId → Attachment
```

---

## Future Considerations

1. **Cleanup Jobs:** Remove orphaned Attachment records when SocialAsset is deleted
2. **Media Conversion:** Convert SocialAsset files to platform-specific formats
3. **Tracking:** Log which social platforms a media item was posted to
4. **CDN:** Consider moving Attachment files to Supabase for consistency
5. **Versioning:** Track different versions of media assets

---

## Summary

✅ **Attachment** = Chatbot knowledge (chatbotId-based)  
✅ **MediaAsset** = Personal files in Supabase (userEmail-based)  
✅ **SocialAsset** = Social media post metadata (userEmail-based, references Attachment)

This separation ensures:
- Clear ownership boundaries
- No user email leakage to chatbot collections
- Proper access control verification
- Scalability and maintainability
