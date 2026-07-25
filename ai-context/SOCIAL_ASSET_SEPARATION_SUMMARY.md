# Social Asset Separation - Quick Summary

## Problem Fixed

Previously, social media uploads were being stored in the `Attachment` collection using `chatbotId` to store the user ID. This mixed concerns:
- Chatbot knowledge files (PDFs for RAG) 
- User social media post files
- Both in the same collection with inconsistent ownership models

## Solution

**Created a new `SocialAsset` collection** to cleanly separate concerns:

### Before ❌
```
SocialMediaUploadController
    ↓
Attachment{chatbotId = userId}  ← Wrong! chatbotId meant for chatbots, not users
    ↓
Mixed with knowledge base files
```

### After ✅
```
SocialMediaUploadController
    ↓
Attachment{chatbotId = userId}  ← Still stores file content (backward compatible)
    ↓
SocialAsset{userEmail = user@example.com, attachmentId = "..."}  ← NEW! Tracks ownership
    ↓
Separated concerns, clear ownership
```

## Changes Made

### New Files
1. **`SocialAsset.java`** - Entity with `userEmail` ownership
2. **`SocialAssetDao.java`** - Repository for SocialAsset queries
3. **`COLLECTION_SEPARATION_ARCHITECTURE.md`** - Detailed architecture doc

### Updated Files
1. **`SocialMediaUploadController.java`**
   - Now saves to both Attachment (file content) and SocialAsset (metadata)
   - Uses `userEmail` from JWT for ownership verification
   - Returns `mediaId` from SocialAsset (not attachmentId)

## Ownership Model

| Collection | Owner Field | Purpose |
|-----------|------------|---------|
| `attachments` | `chatbotId` | Chatbot knowledge base files |
| `media_assets` | `userEmail` | Personal user files (in Supabase) |
| `social_assets` | `userEmail` | Social media post file metadata |

## API Flow

**Upload to Social Media:**
```
POST /v1/api/social-media/upload
Content-Type: multipart/form-data

↓

1. Store file bytes in Attachment collection
2. Save metadata in SocialAsset collection
3. Return MediaItem with SocialAsset ID (for frontend)

↓

{
  "items": [
    {
      "mediaId": "social_asset_id_123",  ← Use this in posts
      "mediaUrl": "https://api.../download/attachment_id",
      "mimeType": "image/jpeg",
      ...
    }
  ]
}
```

**Schedule Social Post (uses mediaId from SocialAsset):**
```
POST /v1/api/social-media/schedule
{
  "content": "Check this out!",
  "media": [
    {
      "mediaId": "social_asset_id_123",  ← Reference from upload
      "mediaUrl": "https://...",
      ...
    }
  ]
}
```

## Access Control

**SocialAsset queries are always filtered by userEmail:**
```java
// Only get social assets owned by this user
findByUserEmailAndIdAndUserEmail(id, userEmail)

// List all social assets for user
findByUserEmailOrderByCreatedAtDesc(userEmail, pageable)
```

## Benefits

✅ **Clear ownership** - `userEmail` field makes it obvious who owns what  
✅ **Separated concerns** - Chatbot files vs social media files  
✅ **Scalability** - Each collection can be indexed/optimized independently  
✅ **Security** - Ownership verification is straightforward  
✅ **Maintainability** - Code is clearer about what each collection stores  

## Backward Compatibility

✅ Attachment collection structure unchanged  
✅ Existing fileId references still work  
✅ Download endpoint unchanged  
✅ New SocialAsset is additive, no breaking changes
