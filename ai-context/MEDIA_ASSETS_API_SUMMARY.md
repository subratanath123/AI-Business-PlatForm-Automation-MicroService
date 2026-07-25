# Media Assets API - Quick Summary

## Version 2.0: Backend Owns Supabase

**Change**: Backend now handles all Supabase operations. Frontend just sends raw files.

## Architecture

```
Frontend → POST /v1/api/assets/upload (multipart)
              ↓
          Backend uploads to Supabase
              ↓
          Backend saves to MongoDB
              ↓
          Returns public CDN URLs
```

## Why Backend Owns Supabase?

✅ Frontend never touches credentials  
✅ Service role key stays secure  
✅ Backend can enforce quotas, scan files  
✅ Simpler frontend (just one POST)

## API Endpoints

### POST `/v1/api/assets/upload`
Upload files (backend → Supabase → MongoDB)

**Request:**
```bash
curl -X POST "/v1/api/assets/upload" \
  -H "Authorization: Bearer JWT" \
  -F "files=@photo.jpg" \
  -F "files=@video.mp4"
```

**Response:**
```json
{
  "uploaded": [
    {
      "id": "550e8400...",
      "fileName": "photo.jpg",
      "mimeType": "image/jpeg",
      "sizeBytes": 204800,
      "supabaseUrl": "https://xxx.supabase.co/.../photo.jpg",
      "objectPath": "social-posts/user@example.com/123_photo.jpg",
      "createdAt": "2024-01-15T10:00:00Z",
      "tags": []
    }
  ],
  "failed": []
}
```

### GET `/v1/api/assets`
List assets with filtering

**Query params:**
- `type=image|video` - Filter by MIME type
- `search=keyword` - Search filename
- `limit=100` - Max results (max 200)
- `offset=0` - Pagination

**Response:**
```json
{
  "assets": [...],
  "total": 42
}
```

### GET `/v1/api/assets/{id}`
Get single asset

### DELETE `/v1/api/assets/{id}`
Delete from MongoDB + Supabase

**Response:**
```json
{ "success": true }
```

## Configuration

Add to your **Spring Cloud Config Server** properties:

```properties
# Config server: application-prod.properties or application-dev.properties
supabase.url=https://your-project.supabase.co
supabase.service-role-key=your_service_role_key
supabase.bucket=social-media-assets
```

Or in YAML:
```yaml
supabase:
  url: https://your-project.supabase.co
  service-role-key: your_service_role_key
  bucket: social-media-assets
```

**Get from Supabase Dashboard → Settings → API → service_role**

**⚠️ Use service_role key** (not anon key) - bypasses RLS, must stay secret

## Files Created

**Entity**: `MediaAsset.java`  
**Repository**: `MediaAssetDao.java`  
**DTOs**: `UploadResponse.java`, `ListAssetsResponse.java`, `DeleteResponse.java`  
**Services**: `SupabaseStorageService.java`, `MediaAssetService.java`  
**Controller**: `MediaAssetController.java`

## MongoDB Collection

```javascript
db.media_assets
{
  _id: "uuid",
  userEmail: "user@example.com",  // Indexed
  fileName: "photo.jpg",
  mimeType: "image/jpeg",
  sizeBytes: 204800,
  supabaseUrl: "https://...",
  objectPath: "social-posts/user@example.com/123_photo.jpg",
  createdAt: ISODate(...),
  tags: []
}
```

## Supabase Setup

1. Create bucket: `social-media-assets` (Public)
2. Get service role key: Dashboard → Settings → API → service_role
3. Add credentials to Spring Cloud Config Server
4. No RLS policies needed (service role bypasses RLS)

## Frontend Integration

```typescript
// Upload
const formData = new FormData();
files.forEach(f => formData.append("files", f));

const res = await fetch("/v1/api/assets/upload", {
  method: "POST",
  headers: { Authorization: `Bearer ${jwt}` },
  body: formData
});

const { uploaded, failed } = await res.json();

// List
const res = await fetch("/v1/api/assets?type=image&limit=20", {
  headers: { Authorization: `Bearer ${jwt}` }
});
const { assets, total } = await res.json();

// Delete
await fetch(`/v1/api/assets/${assetId}`, {
  method: "DELETE",
  headers: { Authorization: `Bearer ${jwt}` }
});
```

## Key Features

✅ **userEmail isolation** (from JWT)  
✅ **50 MB limit** per file  
✅ **Partial success** (some files can fail, others succeed)  
✅ **Filename sanitization** (removes unsafe chars)  
✅ **Pagination** (limit 100, max 200)  
✅ **Filtering** (by MIME type: image/video)  
✅ **Search** (by filename, case-insensitive)  
✅ **Secure deletion** (MongoDB + Supabase)

## Object Path Format

```
social-posts/{userEmail}/{timestamp}_{sanitized_filename}
```

Example:
```
social-posts/user@example.com/1709123456789_product-photo.jpg
```

## Testing

```bash
# Upload
curl -X POST "http://localhost:8080/v1/api/assets/upload" \
  -H "Authorization: Bearer JWT" \
  -F "files=@test.jpg"

# List
curl "http://localhost:8080/v1/api/assets?type=image" \
  -H "Authorization: Bearer JWT"

# Delete
curl -X DELETE "http://localhost:8080/v1/api/assets/ASSET_ID" \
  -H "Authorization: Bearer JWT"
```

## Error Handling

- **400**: No files provided
- **401**: Unauthorized (missing JWT)
- **404**: Asset not found or not owned
- **413**: File exceeds 50 MB

**Partial success example:**
```json
{
  "uploaded": [{ /* asset 1 */ }, { /* asset 2 */ }],
  "failed": [
    { "fileName": "too-big.mp4", "error": "File exceeds 50 MB limit" }
  ]
}
```

## Comparison: v1 vs v2

| Feature | v1 (Frontend Upload) | v2 (Backend Upload) |
|---------|---------------------|---------------------|
| **Who uploads?** | Frontend → Supabase | Backend → Supabase |
| **Frontend needs** | Supabase SDK + anon key | Just fetch() |
| **Security** | Client has keys | Keys server-side only |
| **File validation** | Client-side only | Server-side enforced |
| **Quotas** | Hard to enforce | Easy to enforce |

## Why v2 is Better

1. **No credentials in frontend** - Service role key stays secure
2. **Centralized control** - Backend can scan, resize, enforce quotas
3. **Simpler frontend** - No Supabase SDK needed
4. **Better security** - All operations authenticated server-side
5. **Consistent auth** - Same JWT used for all operations

## Next Steps

1. ✅ Backend implementation complete
2. ⏳ Configure Supabase (create bucket, get service role key)
3. ⏳ Add env vars to backend
4. ⏳ Test uploads with cURL/Postman
5. ⏳ Integrate with frontend
6. ⏳ Add MongoDB indexes for performance

See `MEDIA_ASSETS_BACKEND_API.md` for complete documentation.
