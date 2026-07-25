# Social Asset Frontend Integration Guide

## Overview

The backend has been refactored to use a new `SocialAsset` collection for social media post file uploads. This guide documents all required frontend changes.

**Status:** Backend ready ✅ | Frontend changes required ⚠️

---

## Key Changes Summary

| Aspect | Old API | New API | Action |
|--------|---------|---------|--------|
| **Upload Endpoint** | `POST /v1/api/social-media/upload` | Same (unchanged) | No change |
| **Response mediaId** | `attachmentId` | `socialAssetId` | Update storage |
| **Media Object** | Referenced by `attachmentId` | Referenced by `mediaId` from SocialAsset | Update tracking |
| **Schedule Endpoint** | Same | Same | No change |
| **Media Property** | `media[].attachmentId` | `media[].mediaId` from upload response | Update payload |

---

## API Changes

### Upload Media Endpoint

**Endpoint:** `POST /v1/api/social-media/upload` ✅

**Request:**
```http
POST /v1/api/social-media/upload HTTP/1.1
Content-Type: multipart/form-data
Authorization: Bearer <jwt_token>

[file data]
```

**Response - BEFORE (Old):**
```json
{
  "items": [
    {
      "mediaId": "attachment_id_123",  // ← OLD: This was Attachment ID
      "mediaUrl": "https://api.../download/attachment_id_123",
      "mimeType": "image/jpeg",
      "fileName": "campaign.jpg",
      "sizeBytes": 3072000,
      "width": 1920,
      "height": 1080,
      "durationMs": null,
      "thumbnailUrl": null
    }
  ]
}
```

**Response - AFTER (New) ✅:**
```json
{
  "items": [
    {
      "mediaId": "social_asset_id_456",  // ✅ NEW: This is SocialAsset ID
      "mediaUrl": "https://api.../download/attachment_id_123",  // Still same URL
      "mimeType": "image/jpeg",
      "fileName": "campaign.jpg",
      "sizeBytes": 3072000,
      "width": 1920,
      "height": 1080,
      "durationMs": null,
      "thumbnailUrl": null
    }
  ]
}
```

**Key Difference:**
- `mediaId` value changed from `attachmentId` to `socialAssetId`
- `mediaUrl` remains the same (both resolve to attachment)
- Frontend must use `mediaId` from upload response

---

## API Endpoints Reference

| Operation | Endpoint | Method | Purpose |
|-----------|----------|--------|---------|
| Upload Media | `/v1/api/social-media/upload` | POST | Upload files for social posts |
| Schedule Post | `/v1/api/social-posts/schedule` | POST | Schedule post with media |
| Get Posts | `/v1/api/social-posts` | GET | Get posts by date range |

---

## Frontend Implementation Guide

### 1. Upload Media Files

#### TypeScript/React Example

**Before:**
```typescript
// Old code
const uploadSocialMedia = async (files: File[]): Promise<void> => {
  const formData = new FormData();
  files.forEach((file, index) => {
    formData.append('files', file);
  });

  try {
    const response = await fetch('/v1/api/social-media/upload', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
      body: formData,
    });

    const data = await response.json();
    
    // ❌ OLD: Used attachmentId directly
    setUploadedMedia(data.items.map(item => ({
      id: item.mediaId,  // This was attachmentId
      url: item.mediaUrl,
      type: item.mimeType,
      fileName: item.fileName,
    })));
  } catch (error) {
    console.error('Upload failed:', error);
  }
};
```

**After (Updated) ✅:**
```typescript
// New code - works with SocialAsset
const uploadSocialMedia = async (files: File[]): Promise<void> => {
  const formData = new FormData();
  files.forEach((file) => {
    formData.append('files', file);
  });

  try {
    const response = await fetch('/v1/api/social-media/upload', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
      body: formData,
    });

    const data: MediaUploadResponse = await response.json();
    
    // ✅ NEW: mediaId is now SocialAsset ID (same usage)
    setUploadedMedia(data.items.map(item => ({
      mediaId: item.mediaId,      // ✅ Use as-is (now SocialAsset ID)
      mediaUrl: item.mediaUrl,    // URL still works
      mimeType: item.mimeType,
      fileName: item.fileName,
      sizeBytes: item.sizeBytes,
      width: item.width,
      height: item.height,
    })));
  } catch (error) {
    console.error('Upload failed:', error);
  }
};
```

**TypeScript Types - Updated:**
```typescript
// Media item from upload response
interface MediaItem {
  mediaId: string;          // ✅ SocialAsset ID (was: attachmentId)
  mediaUrl: string;
  mimeType: string;
  fileName: string;
  sizeBytes: number;
  width?: number;
  height?: number;
  durationMs?: number;
  thumbnailUrl?: string;
}

// Response from upload endpoint
interface MediaUploadResponse {
  items: MediaItem[];
}

// Local storage for drafted posts
interface DraftPost {
  content: string;
  media: MediaItem[];  // ✅ Use MediaItem type
  scheduledAt?: string;
}
```

---

### 2. Schedule Social Post

**Before:**
```typescript
const schedulePost = async (
  content: string,
  media: MediaItem[],
  platforms: string[]
): Promise<void> => {
  const payload = {
    content,
    media: media.map(item => ({
      mediaId: item.mediaId,      // ❌ Was Attachment ID
      mediaUrl: item.mediaUrl,
      mimeType: item.mimeType,
      fileName: item.fileName,
    })),
    scheduledAt: new Date().toISOString(),
    platforms,
  };

  const response = await fetch('/v1/api/social-media/schedule', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (response.ok) {
    clearUploadedMedia();
  }
};
```

**After (No Changes! ✅):**
```typescript
// No changes needed - same structure!
// mediaId from upload response is already SocialAsset ID
const schedulePost = async (
  content: string,
  media: MediaItem[],
  platforms: string[]
): Promise<void> => {
  const payload = {
    content,
    media: media.map(item => ({
      mediaId: item.mediaId,      // ✅ Already SocialAsset ID from upload
      mediaUrl: item.mediaUrl,
      mimeType: item.mimeType,
      fileName: item.fileName,
    })),
    scheduledAt: new Date().toISOString(),
    platforms,
  };

  const response = await fetch('/v1/api/social-media/schedule', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (response.ok) {
    clearUploadedMedia();
  }
};
```

**Key Point:** ✅ The schedule endpoint payload structure is unchanged. You just use `mediaId` from the upload response (which is now SocialAsset ID).

---

### 3. Media Display Component

**Before:**
```typescript
const MediaPreview: React.FC<{ media: MediaItem }> = ({ media }) => {
  return (
    <div className="media-preview">
      <img 
        key={media.mediaId}  // ❌ Was Attachment ID
        src={media.mediaUrl}
        alt={media.fileName}
      />
      <p>{media.fileName}</p>
    </div>
  );
};
```

**After (No Changes! ✅):**
```typescript
// Same component works
const MediaPreview: React.FC<{ media: MediaItem }> = ({ media }) => {
  return (
    <div className="media-preview">
      <img 
        key={media.mediaId}  // ✅ Works with SocialAsset ID
        src={media.mediaUrl}  // URL works same way
        alt={media.fileName}
      />
      <p>{media.fileName}</p>
    </div>
  );
};
```

---

### 4. Draft Post Storage

**Before:**
```typescript
// Store draft with uploaded media
const saveDraft = (content: string, mediaItems: MediaItem[]) => {
  const draft = {
    id: generateId(),
    content,
    media: mediaItems.map(m => ({
      attachmentId: m.mediaId,  // ❌ Stored attachmentId
      url: m.mediaUrl,
    })),
    createdAt: new Date(),
  };
  localStorage.setItem('draft', JSON.stringify(draft));
};
```

**After (Updated) ✅:**
```typescript
// Store draft with SocialAsset ID
const saveDraft = (content: string, mediaItems: MediaItem[]) => {
  const draft = {
    id: generateId(),
    content,
    media: mediaItems.map(m => ({
      mediaId: m.mediaId,        // ✅ Store SocialAsset ID
      mediaUrl: m.mediaUrl,
      mimeType: m.mimeType,
      fileName: m.fileName,
      sizeBytes: m.sizeBytes,
    })),
    createdAt: new Date(),
  };
  localStorage.setItem('draft', JSON.stringify(draft));
};
```

---

### 5. Error Handling

**Before & After (No Changes):**
```typescript
const handleUploadError = (error: Error): string => {
  if (error.message.includes('413')) {
    return 'File size exceeds 50MB limit';  // ✅ Increased from 12MB
  }
  if (error.message.includes('415')) {
    return 'Unsupported file type';
  }
  if (error.message.includes('401')) {
    return 'Authentication required. Please log in.';
  }
  return 'Upload failed. Please try again.';
};
```

---

## React Hook Example

**Custom Hook - Updated:**
```typescript
import { useState, useCallback } from 'react';

interface MediaItem {
  mediaId: string;      // ✅ SocialAsset ID
  mediaUrl: string;
  mimeType: string;
  fileName: string;
  sizeBytes: number;
  width?: number;
  height?: number;
}

export const useSocialMediaUpload = (authToken: string) => {
  const [uploadedMedia, setUploadedMedia] = useState<MediaItem[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const uploadMedia = useCallback(
    async (files: File[]): Promise<void> => {
      setIsUploading(true);
      setError(null);

      const formData = new FormData();
      files.forEach((file) => {
        formData.append('files', file);
      });

      try {
        const response = await fetch('/v1/api/social-media/upload', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${authToken}`,
          },
          body: formData,
        });

        if (!response.ok) {
          throw new Error(`Upload failed: ${response.statusText}`);
        }

        const data = await response.json();
        
        // ✅ mediaId is SocialAsset ID
        setUploadedMedia(prev => [
          ...prev,
          ...data.items.map((item: any) => ({
            mediaId: item.mediaId,      // ✅ SocialAsset ID
            mediaUrl: item.mediaUrl,
            mimeType: item.mimeType,
            fileName: item.fileName,
            sizeBytes: item.sizeBytes,
            width: item.width,
            height: item.height,
          })),
        ]);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Upload failed');
      } finally {
        setIsUploading(false);
      }
    },
    [authToken]
  );

  const removeMedia = useCallback((mediaId: string) => {
    setUploadedMedia(prev => prev.filter(m => m.mediaId !== mediaId));
  }, []);

  const clearAll = useCallback(() => {
    setUploadedMedia([]);
  }, []);

  return {
    uploadedMedia,
    isUploading,
    error,
    uploadMedia,
    removeMedia,
    clearAll,
  };
};
```

**Usage:**
```typescript
const SocialPostComposer = () => {
  const { uploadedMedia, isUploading, uploadMedia, removeMedia } = 
    useSocialMediaUpload(authToken);

  const handleSchedule = async () => {
    await schedulePost(
      content,
      uploadedMedia,  // ✅ Use mediaId from hook (SocialAsset ID)
      selectedPlatforms
    );
  };

  return (
    <div className="composer">
      <textarea value={content} onChange={e => setContent(e.target.value)} />
      
      <input
        type="file"
        multiple
        onChange={e => uploadMedia(Array.from(e.target.files || []))}
        disabled={isUploading}
      />

      <div className="media-gallery">
        {uploadedMedia.map(media => (
          <div key={media.mediaId}>  {/* ✅ Use mediaId */}
            <img src={media.mediaUrl} alt={media.fileName} />
            <button onClick={() => removeMedia(media.mediaId)}>Remove</button>
          </div>
        ))}
      </div>

      <button onClick={handleSchedule}>Schedule Post</button>
    </div>
  );
};
```

---

## Migration Checklist

### For Existing Frontend Code

- [ ] Update MediaItem type definition (if custom)
  ```typescript
  // Change: attachmentId → mediaId (same usage)
  ```

- [ ] Upload response parsing
  ```typescript
  // No change needed - just use item.mediaId
  // It's now SocialAsset ID instead of Attachment ID
  ```

- [ ] Draft post storage
  ```typescript
  // Update stored field from attachmentId → mediaId
  ```

- [ ] Media display/preview
  ```typescript
  // Use media.mediaId as key/identifier (works same)
  ```

- [ ] Schedule post payload
  ```typescript
  // No change needed - pass mediaId as-is
  ```

- [ ] API error handling
  ```typescript
  // 50MB limit now supported (was 12MB)
  // Update any UI messages if needed
  ```

---

## Testing Checklist

### Unit Tests
- [ ] Upload with single file → receives SocialAsset ID
- [ ] Upload with multiple files → all have unique SocialAsset IDs
- [ ] Remove media → works with SocialAsset ID
- [ ] Draft saved → mediaId field present and correct
- [ ] Draft loaded → mediaId used correctly

### Integration Tests
- [ ] Upload → Schedule post flow works end-to-end
- [ ] Media displayed → Shows correct filename/dimensions
- [ ] Error handling → 50MB limit properly shown
- [ ] Offline → Draft with media persists correctly

### Manual Testing
- [ ] Upload JPEG → displays thumbnail
- [ ] Upload video → shows duration (if available)
- [ ] Upload multiple → all appear in gallery
- [ ] Delete media → removed from UI and draft
- [ ] Schedule → post appears on social platform

---

## Backward Compatibility

✅ **100% Compatible** - No breaking changes!

- `mediaUrl` endpoint unchanged
- Response structure same (just different ID)
- API payload structure same
- Error codes same
- File size limits improved (50MB now supported)

**No migration needed for existing users** - they'll just get SocialAsset IDs instead of Attachment IDs, but everything works the same.

---

## API Request/Response Examples

### Request: Upload Multiple Files
```bash
curl -X POST https://api.jadeordersmedia.com/v1/api/social-media/upload \
  -H "Authorization: Bearer eyJhbGc..." \
  -F "files=@image1.jpg" \
  -F "files=@image2.jpg" \
  -F "files=@video.mp4"
```

### Response: Upload Success
```json
{
  "items": [
    {
      "mediaId": "507f1f77bcf86cd799439011",
      "mediaUrl": "https://api.jadeordersmedia.com/v1/api/user/attachments/download/attachment_123",
      "mimeType": "image/jpeg",
      "fileName": "image1.jpg",
      "sizeBytes": 2048000,
      "width": 1920,
      "height": 1080,
      "durationMs": null,
      "thumbnailUrl": null
    },
    {
      "mediaId": "507f1f77bcf86cd799439012",
      "mediaUrl": "https://api.jadeordersmedia.com/v1/api/user/attachments/download/attachment_124",
      "mimeType": "image/jpeg",
      "fileName": "image2.jpg",
      "sizeBytes": 1536000,
      "width": 1280,
      "height": 720,
      "durationMs": null,
      "thumbnailUrl": null
    }
  ]
}
```

### Request: Schedule Post with Media
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
        "mimeType": "image/jpeg",
        "fileName": "image1.jpg"
      }
    ],
    "scheduledAt": "2026-03-20T15:00:00Z",
    "platforms": ["facebook", "twitter"]
  }'
```

---

## Summary of Changes

| Area | Change | Impact |
|------|--------|--------|
| Upload Response | `mediaId` = SocialAsset ID | Store this ID for posts |
| API Payload | No change | Send mediaId as-is |
| Storage Key | attachmentId → mediaId | Update draft storage |
| Display | No change | Works with mediaId |
| Error Handling | No change | Same error codes |
| File Size Limit | 12MB → 50MB | Can upload larger files |

---

## Need More Help?

**Reference Documentation:**
- Backend: `COLLECTION_SEPARATION_ARCHITECTURE.md`
- Implementation: `COLLECTION_SEPARATION_IMPLEMENTATION.md`
- Quick Summary: `SOCIAL_ASSET_SEPARATION_SUMMARY.md`

**Key Points:**
- ✅ `mediaId` from upload response is now SocialAsset ID
- ✅ Use it same way as before (it's just a different ID value)
- ✅ No breaking changes to API endpoints
- ✅ No changes needed for schedule post flow
