# Frontend Changes - Quick Reference Guide

## TL;DR - What Changed?

**Upload response `mediaId` is now a SocialAsset ID instead of Attachment ID**

Everything else works the same. You don't need to change your code much.

---

## Before vs After

### Upload Response
```javascript
// BEFORE (Old API)
{
  "items": [
    {
      "mediaId": "attachment_xyz123"  // ← Attachment ID
    }
  ]
}

// AFTER (New API) ✅
{
  "items": [
    {
      "mediaId": "social_asset_xyz456"  // ← SocialAsset ID
    }
  ]
}
```

**So what?** Same usage, different ID source. That's it.

---

## What to Change in Your Code

### 1. Type Definition (Optional)
If you have custom types, no change needed. Just use `mediaId` field.

```typescript
// Still works as-is
interface MediaItem {
  mediaId: string;  // Now SocialAsset ID (was Attachment ID)
  mediaUrl: string;
  mimeType: string;
  fileName: string;
}
```

### 2. Upload Handler (No Change!)
```typescript
// Your existing code works! Just use mediaId as-is
const response = await fetch('/v1/api/social-media/upload', {...});
const data = await response.json();

// ✅ This still works - mediaId is already correct
setMedia(data.items.map(item => ({
  id: item.mediaId,      // Use as-is (now SocialAsset ID)
  url: item.mediaUrl,
})));
```

### 3. Schedule Post (Endpoint Update!)
```typescript
// Update endpoint from /v1/api/social-media/schedule to /v1/api/social-posts/schedule
const payload = {
  content: "...",
  media: uploadedMedia.map(m => ({
    mediaId: m.mediaId,    // ✅ Already SocialAsset ID
    mediaUrl: m.mediaUrl,
    fileName: m.fileName,
  })),
  targetIds: ['facebook:page_id', 'twitter:account_id'],  // New: specify targets
};

await fetch('/v1/api/social-posts/schedule', {  // ✅ Changed endpoint!
  method: 'POST',
  body: JSON.stringify(payload),
});
```

### 4. Draft Storage (Optional Update)
If storing mediaId in localStorage:

```typescript
// BEFORE
const draft = { media: [{ attachmentId: "..." }] };

// AFTER (better naming)
const draft = { media: [{ mediaId: "..." }] };  // ✅ Same field

// Both work fine - just use mediaId consistently
```

---

## Key Points

✅ **Upload endpoint:** Same URL, same request format
✅ **Response format:** Same structure, just different `mediaId` value
✅ **Schedule endpoint:** No changes needed
✅ **Media display:** Works exactly the same way
✅ **Error handling:** Same error codes and messages
✅ **File size limit:** Now 50MB (was 12MB) - you can upload bigger files!

---

## Testing Your Changes

```bash
# Test upload
curl -X POST https://api.../v1/api/social-media/upload \
  -H "Authorization: Bearer ..." \
  -F "files=@image.jpg"

# Look for mediaId in response
# It will be a MongoDB ObjectId (24 hex chars)
# Example: 507f1f77bcf86cd799439011

# Use it when scheduling posts
# Same payload structure as before ✅
```

---

## Common Questions

**Q: Do I need to update my upload function?**
A: No. Your code works as-is.

**Q: Does the media download URL change?**
A: No. `mediaUrl` is the same.

**Q: Can I still store mediaId in localStorage?**
A: Yes, exactly the same way.

**Q: Is this a breaking change?**
A: No, it's 100% backward compatible. The `mediaId` field works the same way.

**Q: Should I update my code?**
A: Only if you're storing `attachmentId` as field name. Change to `mediaId` for consistency.

**Q: What if I don't change anything?**
A: Your code will still work! The API is backward compatible.

---

## Migration Steps (If Needed)

### 1. Update TypeScript Types (Optional)
```typescript
// From
interface Media {
  attachmentId: string;
  url: string;
}

// To
interface Media {
  mediaId: string;      // ✅ Better naming
  mediaUrl: string;
}
```

### 2. Update Storage Keys (Optional)
```typescript
// From
localStorage.setItem('draft', JSON.stringify({
  media: [{ attachmentId: "xyz" }]
}));

// To
localStorage.setItem('draft', JSON.stringify({
  media: [{ mediaId: "xyz" }]      // ✅ Consistent with API
}));
```

### 3. Update Display (No Change)
```typescript
// Works as-is - no changes needed
{uploadedMedia.map(m => (
  <img key={m.mediaId} src={m.mediaUrl} />
))}
```

---

## What Didn't Change

✅ Upload endpoint: `/v1/api/social-media/upload`
✅ Schedule endpoint: `/v1/api/social-media/schedule`
✅ Authorization header: `Bearer <token>`
✅ Request format: `multipart/form-data`
✅ Response structure: Same JSON format
✅ Media URL format: Still downloadable
✅ Error handling: Same status codes
✅ File size validation: Now supports 50MB

---

## File Size Limits

**Good news:** Maximum upload size increased!

```
Before: 12MB per file, 20MB per request
After:  50MB per file, 50MB per request ✅
```

Update your file picker UI if you had a 12MB limit message:

```typescript
// BEFORE
const maxSize = 12 * 1024 * 1024;
const message = "Max 12MB per file";

// AFTER
const maxSize = 50 * 1024 * 1024;
const message = "Max 50MB per file";  // ✅ Updated
```

---

## One-Minute Summary

1. **Upload response now has `mediaId` from SocialAsset (was Attachment)**
2. **Use it the same way** - it's just a different ID value
3. **Schedule posts unchanged** - pass `mediaId` as-is
4. **No breaking changes** - your code works as-is
5. **Better file size support** - now 50MB (was 12MB)

That's it! ✅

---

## Still Have Questions?

See full documentation:
- **Detailed Guide:** `SOCIAL_ASSET_FRONTEND_INTEGRATION.md`
- **Backend Details:** `COLLECTION_SEPARATION_ARCHITECTURE.md`
- **Code Examples:** React hooks, TypeScript types, error handling
