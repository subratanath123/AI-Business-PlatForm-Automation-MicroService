# Asset Management API - Quick Summary

## What Was Built

Complete backend API for managing Supabase CDN assets with MongoDB metadata storage.

## Architecture

```
Browser → Supabase Storage (direct upload)
    ↓
Backend → MongoDB (register metadata)
    ↓
Frontend → Asset Grid (list/delete)
```

## API Endpoints

### POST `/v1/api/assets`
Register asset uploaded to Supabase

**Request:**
```json
{
  "fileName": "photo.jpg",
  "url": "https://xxx.supabase.co/.../photo.jpg",
  "path": "assets/user123/photo.jpg",
  "size": 1024000,
  "mimeType": "image/jpeg"
}
```

**Response:**
```json
{
  "success": true,
  "assetId": "550e8400...",
  "type": "image",
  "uploadedAt": "2024-01-15T10:30:00Z"
}
```

### GET `/v1/api/assets?page=0&pageSize=20&type=image&search=photo`
List assets with pagination, filtering, and search

**Response:**
```json
{
  "assets": [...],
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

### DELETE `/v1/api/assets/{assetId}`
Delete asset from MongoDB + Supabase

**Response:**
```json
{
  "success": true,
  "message": "Asset deleted successfully"
}
```

## Key Features

✅ **userEmail-based isolation** (from JWT)  
✅ **Direct Supabase uploads** (fast CDN)  
✅ **Pagination** (20 per page, max 100)  
✅ **Filtering** (by type: image/video/document/all)  
✅ **Search** (by filename, case-insensitive)  
✅ **Statistics** (total files/images/videos/size)  
✅ **Secure deletion** (MongoDB + Supabase using service role key)  
✅ **Auto type detection** (from MIME type)

## Files Created

**Entity**: `Asset.java`  
**Repository**: `AssetDao.java`  
**DTOs**: `RegisterAssetRequest.java`, `RegisterAssetResponse.java`, `AssetResponse.java`, `ListAssetsResponse.java`, `DeleteAssetResponse.java`  
**Service**: `AssetService.java`  
**Controller**: `AssetController.java`

## Configuration

Add to `.env`:
```bash
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key
```

## Security

- All endpoints require JWT: `Authorization: Bearer <clerk_jwt>`
- `userEmail` extracted from JWT automatically
- Users can only access their own assets
- Service role key used for Supabase deletion (bypasses RLS)

## MongoDB Collection

```javascript
db.assets
{
  _id: "uuid",
  userEmail: "user@example.com",  // Indexed
  fileName: "photo.jpg",
  url: "https://supabase.co/.../photo.jpg",
  path: "assets/user123/photo.jpg",
  size: 1024000,
  mimeType: "image/jpeg",
  type: "image",
  uploadedAt: ISODate(...)
}
```

## Frontend Integration

```typescript
// 1. Upload to Supabase (direct)
const { data } = await supabase.storage
  .from('assets')
  .upload(`${userId}/${file.name}`, file);

const publicUrl = supabase.storage
  .from('assets')
  .getPublicUrl(data.path).data.publicUrl;

// 2. Register with backend
const response = await fetch('/v1/api/assets', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${jwt}` },
  body: JSON.stringify({
    fileName: file.name,
    url: publicUrl,
    path: `assets/${data.path}`,
    size: file.size,
    mimeType: file.type
  })
});

// 3. List assets
const { assets, stats } = await fetch('/v1/api/assets?page=0').then(r => r.json());

// 4. Delete asset
await fetch(`/v1/api/assets/${assetId}`, { method: 'DELETE' });
```

## Testing

```bash
# Register
curl -X POST 'http://localhost:8080/v1/api/assets' \
  -H 'Authorization: Bearer JWT' \
  -d '{"fileName":"test.jpg","url":"...","path":"...","size":1024,"mimeType":"image/jpeg"}'

# List
curl 'http://localhost:8080/v1/api/assets?page=0' \
  -H 'Authorization: Bearer JWT'

# Delete
curl -X DELETE 'http://localhost:8080/v1/api/assets/ASSET_ID' \
  -H 'Authorization: Bearer JWT'
```

## Statistics

- **Total Files**: Count of all assets
- **Total Images**: Count of image/* assets
- **Total Videos**: Count of video/* assets
- **Total Size**: Sum of all file sizes (bytes)

Calculated in real-time using MongoDB aggregation.

## Asset Types

- `image` - image/*
- `video` - video/*
- `document` - PDF, Word, Excel
- `other` - everything else

Auto-determined from MIME type.

## Supabase Setup

1. Create `assets` bucket (public)
2. Set RLS policies for upload/read/delete
3. Get service role key from Settings → API
4. Add to backend environment variables

## Why This Architecture?

1. **Fast Uploads**: Direct browser → Supabase bypasses backend
2. **CDN Performance**: Global CDN for asset delivery
3. **Backend Tracking**: MongoDB stores user's asset library
4. **Secure Deletion**: Backend uses service role key
5. **User Isolation**: All operations scoped to `userEmail`

## Next Steps

- ✅ Backend implementation complete
- ⏳ Frontend integration (use ASSETS_BACKEND_API.md)
- ⏳ Test with real Supabase project
- ⏳ Add MongoDB indexes for performance
- ⏳ Configure Supabase RLS policies

See `ASSETS_BACKEND_API.md` for complete documentation.
