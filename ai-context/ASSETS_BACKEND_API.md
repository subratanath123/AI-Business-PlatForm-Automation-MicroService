# Asset Management API - Backend Implementation

## Overview

This document describes the complete backend implementation for the Asset Management system that integrates with Supabase Storage CDN. The backend handles asset registration, listing with pagination/filtering/search, statistics, and deletion (both from MongoDB and Supabase).

## Architecture

```
User picks file
    │
    ▼
Browser → Supabase Storage (direct, fast CDN upload)
    │
    │ gets back publicUrl
    ▼
Browser → POST /v1/api/assets  (register with backend)
    │
    │ backend saves: userEmail, fileName, url, size, path
    ▼
GET /v1/api/assets  →  asset grid shows library
DELETE /v1/api/assets/{id}  →  removes from DB + Supabase
```

### Why This Architecture?

1. **Fast Uploads**: Direct browser → Supabase upload bypasses backend
2. **CDN Performance**: Supabase provides global CDN for fast asset delivery
3. **Backend Tracking**: MongoDB stores metadata for user's asset library
4. **Secure Deletion**: Backend uses service role key to delete from Supabase
5. **User Isolation**: All operations scoped to `userEmail` from JWT

## API Endpoints

### 1. Register Asset

**POST** `/v1/api/assets`

Register an asset that was uploaded to Supabase.

**Headers:**
```
Authorization: Bearer <clerk_jwt>
Content-Type: application/json
```

**Request Body:**
```json
{
  "fileName": "photo.jpg",
  "url": "https://xxx.supabase.co/storage/v1/object/public/assets/user123/photo.jpg",
  "path": "assets/user123/photo.jpg",
  "size": 1024000,
  "mimeType": "image/jpeg"
}
```

**Response (200):**
```json
{
  "success": true,
  "assetId": "550e8400-e29b-41d4-a716-446655440002",
  "type": "image",
  "uploadedAt": "2024-01-15T10:30:00Z"
}
```

**Asset Types** (auto-determined from MIME type):
- `image` - image/*
- `video` - video/*
- `document` - application/pdf, application/msword, etc.
- `other` - everything else

### 2. List Assets

**GET** `/v1/api/assets?page=0&pageSize=20&type=image&search=photo`

List assets with pagination, filtering, and search.

**Headers:**
```
Authorization: Bearer <clerk_jwt>
```

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | integer | 0 | Page number (0-indexed) |
| `pageSize` | integer | 20 | Items per page (max 100) |
| `type` | string | all | Filter: `all`, `image`, `video`, `document` |
| `search` | string | - | Search by filename (case-insensitive) |

**Response (200):**
```json
{
  "assets": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "fileName": "photo.jpg",
      "url": "https://xxx.supabase.co/storage/v1/object/public/assets/user123/photo.jpg",
      "path": "assets/user123/photo.jpg",
      "size": 1024000,
      "mimeType": "image/jpeg",
      "type": "image",
      "uploadedAt": "2024-01-15T10:30:00Z"
    }
  ],
  "page": 0,
  "pageSize": 20,
  "totalAssets": 50,
  "totalPages": 3,
  "stats": {
    "totalFiles": 50,
    "totalImages": 30,
    "totalVideos": 15,
    "totalSize": 52428800
  }
}
```

**Statistics Breakdown:**
- `totalFiles`: Total number of assets for the user
- `totalImages`: Count of image assets
- `totalVideos`: Count of video assets
- `totalSize`: Sum of all file sizes in bytes

### 3. Delete Asset

**DELETE** `/v1/api/assets/{assetId}`

Delete asset from both MongoDB and Supabase storage.

**Headers:**
```
Authorization: Bearer <clerk_jwt>
```

**Response (200):**
```json
{
  "success": true,
  "message": "Asset deleted successfully"
}
```

**Response (404):**
```json
{
  "success": false,
  "message": "Asset not found or you don't have permission to delete it"
}
```

**Deletion Process:**
1. Verify asset exists and belongs to user (by `userEmail`)
2. Delete from Supabase storage using service role key
3. Delete from MongoDB
4. Return success response

## Data Model

### MongoDB Collection: `assets`

```java
{
  "_id": "550e8400-e29b-41d4-a716-446655440002",
  "userEmail": "user@example.com",  // Indexed
  "fileName": "photo.jpg",
  "url": "https://xxx.supabase.co/storage/v1/object/public/assets/user123/photo.jpg",
  "path": "assets/user123/photo.jpg",
  "size": 1024000,
  "mimeType": "image/jpeg",
  "type": "image",
  "uploadedAt": ISODate("2024-01-15T10:30:00Z")
}
```

**Indexes:**
- `userEmail` (ascending) - For fast user-specific queries

## Configuration

### Environment Variables

Add to your `.env` or environment:

```bash
# Supabase Configuration
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key_here
```

### application.yml

```yaml
# Supabase Configuration (for asset storage deletion)
supabase:
  url: ${SUPABASE_URL:https://your-project.supabase.co}
  service-role-key: ${SUPABASE_SERVICE_ROLE_KEY:your_service_role_key}
```

**⚠️ Important**: Use the **service role key**, not the anon key, for deletion operations. The service role key bypasses Row Level Security (RLS).

## Security

### Authentication

All endpoints require JWT authentication via Clerk:
```
Authorization: Bearer <clerk_jwt>
```

### User Isolation

- `userEmail` extracted from JWT automatically
- All database queries filtered by `userEmail`
- Ownership verified before deletion
- Users can only see/delete their own assets

### Token Requirements

- Frontend uses anon key for direct uploads
- Backend uses service role key for deletions
- Service role key stored securely in environment variables

## Implementation Details

### Files Created

**Entity:**
- `src/main/java/net/ai/chatbot/entity/Asset.java`

**Repository:**
- `src/main/java/net/ai/chatbot/dao/AssetDao.java`

**DTOs:**
- `src/main/java/net/ai/chatbot/dto/asset/RegisterAssetRequest.java`
- `src/main/java/net/ai/chatbot/dto/asset/RegisterAssetResponse.java`
- `src/main/java/net/ai/chatbot/dto/asset/AssetResponse.java`
- `src/main/java/net/ai/chatbot/dto/asset/ListAssetsResponse.java`
- `src/main/java/net/ai/chatbot/dto/asset/DeleteAssetResponse.java`

**Service:**
- `src/main/java/net/ai/chatbot/service/asset/AssetService.java`

**Controller:**
- `src/main/java/net/ai/chatbot/controller/asset/AssetController.java`

**Utilities:**
- Updated `src/main/java/net/ai/chatbot/utils/AuthUtils.java` (added `getUserEmail()`)

### Key Features

#### 1. Asset Registration
- Saves metadata for Supabase-uploaded files
- Auto-determines asset type from MIME type
- Returns UUID for frontend reference

#### 2. Pagination
- Uses Spring Data `Pageable` for efficient pagination
- Default 20 items per page, max 100
- Returns page metadata (current page, total pages, total items)

#### 3. Filtering & Search
- Filter by type: `image`, `video`, `document`, `all`
- Search by filename (case-insensitive, partial match)
- Combines with pagination seamlessly

#### 4. Statistics
- Uses MongoDB aggregation for accurate totals
- Counts by type (`image`, `video`)
- Sums file sizes efficiently
- Calculated in real-time with each list request

#### 5. Supabase Deletion
- Extracts bucket and path from stored path
- Uses Supabase Storage API: `DELETE /storage/v1/object/{bucket}/{path}`
- Authenticates with service role key
- Gracefully handles Supabase failures (still removes from MongoDB)

### Error Handling

**401 Unauthorized**: Missing or invalid JWT
**404 Not Found**: Asset doesn't exist or doesn't belong to user
**500 Internal Server Error**: Database or Supabase API errors

All errors logged with detailed context for debugging.

## Frontend Integration

### Upload Flow

```typescript
// 1. Upload to Supabase (frontend)
const { data, error } = await supabase.storage
  .from('assets')
  .upload(`${userId}/${file.name}`, file, {
    cacheControl: '3600',
    upsert: false
  });

const publicUrl = supabase.storage
  .from('assets')
  .getPublicUrl(data.path).data.publicUrl;

// 2. Register with backend
const response = await fetch('/v1/api/assets', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${jwtToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    fileName: file.name,
    url: publicUrl,
    path: `assets/${data.path}`,
    size: file.size,
    mimeType: file.type
  })
});

const { assetId } = await response.json();
```

### List Assets

```typescript
const response = await fetch('/v1/api/assets?page=0&pageSize=20&type=image', {
  headers: {
    'Authorization': `Bearer ${jwtToken}`
  }
});

const { assets, stats, totalPages } = await response.json();
```

### Delete Asset

```typescript
await fetch(`/v1/api/assets/${assetId}`, {
  method: 'DELETE',
  headers: {
    'Authorization': `Bearer ${jwtToken}`
  }
});
```

## Testing

### Manual Testing with cURL

**Register Asset:**
```bash
curl -X POST 'http://localhost:8080/v1/api/assets' \
  -H 'Authorization: Bearer YOUR_JWT_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{
    "fileName": "test.jpg",
    "url": "https://xxx.supabase.co/storage/v1/object/public/assets/test.jpg",
    "path": "assets/user123/test.jpg",
    "size": 1024000,
    "mimeType": "image/jpeg"
  }'
```

**List Assets:**
```bash
curl 'http://localhost:8080/v1/api/assets?page=0&pageSize=20' \
  -H 'Authorization: Bearer YOUR_JWT_TOKEN'
```

**Delete Asset:**
```bash
curl -X DELETE 'http://localhost:8080/v1/api/assets/ASSET_ID' \
  -H 'Authorization: Bearer YOUR_JWT_TOKEN'
```

## Supabase Setup

### 1. Create Storage Bucket

```sql
-- In Supabase SQL Editor
INSERT INTO storage.buckets (id, name, public)
VALUES ('assets', 'assets', true);
```

### 2. Set RLS Policies

```sql
-- Allow authenticated users to upload
CREATE POLICY "Users can upload assets"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'assets');

-- Allow public read access
CREATE POLICY "Public read access"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'assets');

-- Allow authenticated users to delete their own files
CREATE POLICY "Users can delete own assets"
ON storage.objects FOR DELETE
TO authenticated
USING (bucket_id = 'assets' AND owner = auth.uid());
```

### 3. Get Service Role Key

1. Go to Supabase Dashboard
2. Settings → API
3. Copy "service_role" key (keep secret!)
4. Add to backend environment variables

## Performance Considerations

### Database Indexes

```javascript
// MongoDB shell
db.assets.createIndex({ "userEmail": 1 });
db.assets.createIndex({ "userEmail": 1, "type": 1 });
db.assets.createIndex({ "userEmail": 1, "uploadedAt": -1 });
```

### Pagination Best Practices

- Default page size: 20
- Maximum page size: 100
- Always sorted by `uploadedAt` descending (newest first)

### Statistics Caching (Optional)

For high-traffic applications, consider caching stats:
```java
@Cacheable(value = "assetStats", key = "#userEmail")
private ListAssetsResponse.AssetStats calculateStats(String userEmail) {
    // ...
}
```

## Troubleshooting

### Asset Not Deleted from Supabase

**Symptoms**: MongoDB record deleted, but file still exists in Supabase

**Possible Causes:**
1. Invalid service role key
2. Incorrect path format
3. Supabase RLS blocking deletion

**Solution**: Check logs for Supabase API errors, verify service role key

### Statistics Show Zero

**Symptoms**: `totalFiles`, `totalImages`, etc. return 0

**Possible Causes:**
1. No assets registered yet
2. Wrong `userEmail` in JWT
3. MongoDB aggregation issue

**Solution**: Verify assets exist in MongoDB, check JWT email claim

### 401 Unauthorized Errors

**Symptoms**: All requests return 401

**Possible Causes:**
1. Missing JWT in Authorization header
2. JWT expired
3. JWT not properly configured in Spring Security

**Solution**: Verify JWT in request headers, check token expiry

## Next Steps

### Recommended Enhancements

1. **Bulk Delete**: Delete multiple assets at once
2. **Folders**: Organize assets into folders
3. **Tags**: Add tagging system for better organization
4. **Thumbnails**: Generate and store thumbnail URLs
5. **Usage Tracking**: Track which assets are used where
6. **Storage Limits**: Enforce per-user storage quotas
7. **Batch Upload**: Register multiple assets in one request

### Monitoring

- Track upload/delete success rates
- Monitor Supabase API response times
- Alert on failed deletions
- Track storage usage per user

## Summary

The Asset Management API provides a complete solution for managing Supabase CDN assets with:

- ✅ Fast direct uploads to Supabase
- ✅ Backend tracking in MongoDB
- ✅ Pagination, filtering, and search
- ✅ Real-time statistics
- ✅ Secure deletion from both MongoDB and Supabase
- ✅ User isolation via JWT authentication
- ✅ Comprehensive error handling

All operations use `userEmail` from JWT for consistency with your existing authentication system.
