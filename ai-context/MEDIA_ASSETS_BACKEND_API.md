# Media Assets — Backend API Implementation

**Version:** 2.0  
**Architecture:** Backend owns all Supabase operations

## Overview

This implementation gives the backend complete control over Supabase Storage operations. The frontend sends raw files to the backend via multipart/form-data, and the backend handles uploading to Supabase, tracking metadata in MongoDB, and returning public CDN URLs.

## Why Backend Owns Supabase?

✅ **Security**: Frontend never touches Supabase credentials  
✅ **Control**: Backend can enforce quotas, virus scan, image resize, etc.  
✅ **Simplicity**: Frontend just does one multipart POST  
✅ **Server-side Key**: Supabase service role key stays secure  

## Architecture Flow

```
User selects file(s)
        │
        ▼
Frontend  ──  POST /v1/api/assets/upload  (multipart/form-data)
                        │
                        ▼  (backend does both)
                  Upload ──► Supabase Storage
                        │    bucket: social-media-assets
                        │    path: social-posts/{userEmail}/{timestamp}_{filename}
                        ▼
                  Save ──► MongoDB (media_assets collection)
                        │  {userEmail, fileName, mimeType, supabaseUrl, objectPath}
                        ▼
              Return asset records with public URLs
                        │
        ◄───────────────┘
Frontend displays asset grid

GET  /v1/api/assets        ◄── list user's assets
DELETE /v1/api/assets/{id} ◄── delete asset (MongoDB + Supabase)
```

## API Endpoints

### 1. Upload Assets

**POST** `/v1/api/assets/upload`

Upload one or more files. Backend uploads each to Supabase, saves metadata to MongoDB, returns all uploaded assets.

**Headers:**
```
Authorization: Bearer <clerk_jwt>
Content-Type: multipart/form-data
```

**Form Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `files` | `File[]` | Yes | One or more files (repeat field for multiple) |

**Example Request (JavaScript):**
```javascript
const formData = new FormData();
files.forEach(f => formData.append("files", f));

const response = await fetch("/v1/api/assets/upload", {
  method: "POST",
  headers: { Authorization: `Bearer ${clerkToken}` },
  body: formData
});

const { uploaded, failed } = await response.json();
```

**Example cURL:**
```bash
curl -X POST "http://localhost:8080/v1/api/assets/upload" \
  -H "Authorization: Bearer <clerk_jwt>" \
  -F "files=@product-photo.jpg" \
  -F "files=@promo-video.mp4"
```

**Response (200):**
```json
{
  "uploaded": [
    {
      "id":          "550e8400-e29b-41d4-a716-446655440001",
      "fileName":    "product-photo.jpg",
      "mimeType":    "image/jpeg",
      "sizeBytes":   204800,
      "supabaseUrl": "https://xxx.supabase.co/storage/v1/object/public/social-media-assets/social-posts/user@example.com/1709123456789_product-photo.jpg",
      "objectPath":  "social-posts/user@example.com/1709123456789_product-photo.jpg",
      "createdAt":   "2024-01-15T10:00:00Z",
      "tags":        []
    }
  ],
  "failed": []
}
```

**Response Fields:**

| Field | Description |
|-------|-------------|
| `uploaded` | Array of successfully uploaded assets |
| `failed` | Array of `{ fileName, error }` for any files that failed |

**Allows partial success**: If 3 files are uploaded and 1 fails, the 2 successful uploads still return in `uploaded[]`.

**Error Responses:**
- **400**: No files provided
- **401**: Missing or invalid JWT
- **413**: File exceeds 50 MB limit

**Backend Processing:**
1. Extract `userEmail` from JWT
2. Validate file size (<= 50 MB)
3. Sanitize filename (remove unsafe characters)
4. Generate `objectPath`: `social-posts/{userEmail}/{timestamp}_{filename}`
5. Upload to Supabase Storage
6. Get public CDN URL
7. Save metadata to MongoDB
8. Return asset record

---

### 2. List Assets

**GET** `/v1/api/assets`

List all assets for the authenticated user, newest first.

**Headers:**
```
Authorization: Bearer <clerk_jwt>
```

**Query Parameters (all optional):**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `type` | `image` \| `video` | — | Filter by MIME type prefix |
| `search` | string | — | Case-insensitive filename contains |
| `limit` | number | `100` | Max items to return (max 200) |
| `offset` | number | `0` | Pagination offset |

**Example Request:**
```bash
curl "http://localhost:8080/v1/api/assets?type=image&limit=20&offset=0" \
  -H "Authorization: Bearer <clerk_jwt>"
```

**Response (200):**
```json
{
  "assets": [
    {
      "id":          "550e8400-e29b-41d4-a716-446655440001",
      "fileName":    "product-photo.jpg",
      "mimeType":    "image/jpeg",
      "sizeBytes":   204800,
      "supabaseUrl": "https://xxx.supabase.co/storage/v1/object/public/social-media-assets/...",
      "objectPath":  "social-posts/user@example.com/1709123456789_product-photo.jpg",
      "createdAt":   "2024-01-15T10:00:00Z",
      "tags":        []
    }
  ],
  "total": 42
}
```

**Filtering Examples:**
- `?type=image` - Only image/* files
- `?type=video` - Only video/* files  
- `?search=product` - Filenames containing "product" (case-insensitive)
- `?limit=20&offset=20` - Second page of 20 items

---

### 3. Get Single Asset

**GET** `/v1/api/assets/{id}`

Get details of a single asset (with ownership verification).

**Headers:**
```
Authorization: Bearer <clerk_jwt>
```

**Response (200):** Same shape as a single item from list endpoint

**Response (404):** Asset not found or not owned by this user

---

### 4. Delete Asset

**DELETE** `/v1/api/assets/{id}`

Delete asset from **both** Supabase Storage and MongoDB.

**Headers:**
```
Authorization: Bearer <clerk_jwt>
```

**Backend Steps:**
1. Extract `userEmail` from JWT
2. Find asset by `id` where `userEmail` matches
3. Delete from Supabase Storage using service role key
4. Delete from MongoDB
5. Return success response

**Response (200):**
```json
{
  "success": true
}
```

**Response (404):** Asset not found or not owned by this user

---

## Data Model

### MongoDB Collection: `media_assets`

```javascript
{
  "_id": "550e8400-e29b-41d4-a716-446655440001",
  "userEmail": "user@example.com",   // Indexed
  "fileName": "product-photo.jpg",
  "mimeType": "image/jpeg",
  "sizeBytes": 204800,
  "supabaseUrl": "https://xxx.supabase.co/storage/v1/object/public/social-media-assets/...",
  "objectPath": "social-posts/user@example.com/1709123456789_product-photo.jpg",
  "createdAt": ISODate("2024-01-15T10:00:00Z"),
  "tags": []
}
```

**Index:**
```javascript
db.media_assets.createIndex({ "userEmail": 1 });
```

### MongoDB Entity (Java)

```java
@Document(collection = "media_assets")
public class MediaAsset {
    @Id
    private String id;
    
    @Indexed
    private String userEmail;      // From JWT
    
    private String fileName;       // Original filename
    private String mimeType;       // image/jpeg, video/mp4
    private Long sizeBytes;
    private String supabaseUrl;    // Public CDN URL
    private String objectPath;     // For deletion
    private Instant createdAt;
    private List<String> tags;
}
```

---

## Configuration

### Supabase Config from Config Server

The Supabase configuration is provided by a Spring Cloud Config Server. The backend expects:

```properties
supabase.url=https://your-project.supabase.co
supabase.service-role-key=your_service_role_key_here
supabase.bucket=social-media-assets
```

### application.yml (Local Defaults)

Local `application.yml` has empty defaults (expects values from config server):

```yaml
# Supabase Configuration (from config server)
supabase:
  url: ${supabase.url:}
  service-role-key: ${supabase.service-role-key:}
  bucket: ${supabase.bucket:social-media-assets}
```

### Config Server Setup

Add these properties to your Spring Cloud Config Server:

**In your config server repository:**
```properties
# application-prod.properties or application-dev.properties

# Supabase Config
supabase.url=https://your-project.supabase.co
supabase.service-role-key=<get_from_supabase_dashboard>
supabase.bucket=social-media-assets
```

Or in YAML format:
```yaml
supabase:
  url: https://your-project.supabase.co
  service-role-key: <get_from_supabase_dashboard>
  bucket: social-media-assets
```

### Getting Supabase Credentials

1. Go to [Supabase Dashboard](https://supabase.com/dashboard)
2. Select your project
3. Settings → API
4. Copy:
   - **Project URL** → `supabase.url`
   - **service_role** → `supabase.service-role-key` (⚠️ Keep this secret!)

**⚠️ Important**: Use the **service role key** (not anon/public key). The service role key:
- Bypasses Row Level Security (RLS)
- Allows server-side uploads/deletes
- Must be kept secret (server-side only)
- Never expose to frontend

### Error Handling

If Supabase configuration is missing from config server, the service will throw clear errors:
```
IllegalStateException: Supabase URL is not configured. Check config server settings.
IllegalStateException: Supabase service role key is not configured. Check config server settings.
```

This ensures configuration issues are caught early and not silently ignored.

---

## Supabase Setup

### 1. Create Storage Bucket

Go to Supabase Dashboard → Storage → Create Bucket:

- **Name**: `social-media-assets`
- **Public**: Yes (allows CDN access)
- **File Size Limit**: 50 MB (optional)

### 2. No RLS Policies Needed

Since the backend uses the service role key, Row Level Security (RLS) is bypassed. No policies are needed for this bucket.

**Why this works:**
- Anon key: Subject to RLS policies
- Service role key: Bypasses RLS entirely

### 3. CORS Configuration (if needed)

If your frontend needs to directly access the CDN URLs, ensure CORS is configured:

Settings → API → CORS Configuration:
```
https://your-frontend-domain.com
```

---

## Security

### Authentication

All endpoints require Clerk JWT:
```
Authorization: Bearer <clerk_jwt>
```

### User Isolation

- `userEmail` extracted from JWT `email` claim automatically
- All database queries filtered by `userEmail`
- Ownership verified before deletion
- Users can only access their own assets

### File Validation

- **Max size**: 50 MB per file (configurable)
- **Filename sanitization**: Removes unsafe characters
- **MIME type**: Stored and returned for frontend validation

### Secure Token Storage

- Service role key stored in environment variables
- Never exposed to frontend
- Only backend can upload/delete from Supabase

---

## Implementation Files

### Created Files

**Entity:**
- `MediaAsset.java` - MongoDB document

**Repository:**
- `MediaAssetDao.java` - Spring Data MongoDB

**DTOs:**
- `UploadResponse.java` (with nested `AssetDto` and `FailedUpload`)
- `ListAssetsResponse.java` (with nested `AssetDto`)
- `DeleteResponse.java`

**Services:**
- `SupabaseStorageService.java` - Supabase API integration
- `MediaAssetService.java` - Business logic

**Controller:**
- `MediaAssetController.java` - REST endpoints

**Configuration:**
- Updated `application.yml` - Supabase config

### Modified Files

- `AuthUtils.java` - Already has `getUserEmail()` method

---

## Testing

### Manual Testing with cURL

**Upload Files:**
```bash
curl -X POST "http://localhost:8080/v1/api/assets/upload" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -F "files=@test-photo.jpg" \
  -F "files=@test-video.mp4"
```

**List Assets:**
```bash
curl "http://localhost:8080/v1/api/assets?limit=20" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Filter by Type:**
```bash
curl "http://localhost:8080/v1/api/assets?type=image&limit=10" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Search by Filename:**
```bash
curl "http://localhost:8080/v1/api/assets?search=product" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Get Single Asset:**
```bash
curl "http://localhost:8080/v1/api/assets/ASSET_ID" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Delete Asset:**
```bash
curl -X DELETE "http://localhost:8080/v1/api/assets/ASSET_ID" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Postman Testing

1. **Upload Endpoint**:
   - Method: POST
   - URL: `http://localhost:8080/v1/api/assets/upload`
   - Headers: `Authorization: Bearer <jwt>`
   - Body: form-data
   - Add file field: `files` (select file)
   - Can add multiple `files` fields for batch upload

2. **List Endpoint**:
   - Method: GET
   - URL: `http://localhost:8080/v1/api/assets`
   - Headers: `Authorization: Bearer <jwt>`

3. **Delete Endpoint**:
   - Method: DELETE
   - URL: `http://localhost:8080/v1/api/assets/{id}`
   - Headers: `Authorization: Bearer <jwt>`

---

## Frontend Integration

### Upload Flow

```typescript
async function uploadFiles(files: File[]) {
  const formData = new FormData();
  files.forEach(file => formData.append("files", file));

  const response = await fetch("/v1/api/assets/upload", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${clerkToken}`
    },
    body: formData
  });

  const { uploaded, failed } = await response.json();
  
  // Handle successful uploads
  uploaded.forEach(asset => {
    console.log(`Uploaded: ${asset.fileName}`);
    console.log(`URL: ${asset.supabaseUrl}`);
  });

  // Handle failures
  if (failed.length > 0) {
    failed.forEach(fail => {
      console.error(`Failed: ${fail.fileName} - ${fail.error}`);
    });
  }
}
```

### List Assets

```typescript
async function listAssets(type?: string, search?: string) {
  const params = new URLSearchParams();
  if (type) params.append("type", type);
  if (search) params.append("search", search);
  params.append("limit", "20");

  const response = await fetch(`/v1/api/assets?${params}`, {
    headers: {
      Authorization: `Bearer ${clerkToken}`
    }
  });

  const { assets, total } = await response.json();
  return { assets, total };
}
```

### Delete Asset

```typescript
async function deleteAsset(assetId: string) {
  await fetch(`/v1/api/assets/${assetId}`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${clerkToken}`
    }
  });
}
```

---

## Error Handling

### Common Errors

**400 Bad Request**: No files provided
```json
"No files provided"
```

**401 Unauthorized**: Missing or invalid JWT
```json
"Unauthorized"
```

**404 Not Found**: Asset doesn't exist or not owned
```json
{
  "timestamp": "2024-01-15T10:00:00.000+00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Asset not found or you don't have permission to delete it",
  "path": "/v1/api/assets/abc123"
}
```

**413 Payload Too Large**: File exceeds 50 MB
```json
{
  "uploaded": [],
  "failed": [
    {
      "fileName": "large-video.mp4",
      "error": "File exceeds 50 MB limit"
    }
  ]
}
```

### Partial Success Handling

The upload endpoint supports partial success. If uploading 5 files and 2 fail:

```json
{
  "uploaded": [
    { /* asset 1 */ },
    { /* asset 2 */ },
    { /* asset 3 */ }
  ],
  "failed": [
    { "fileName": "corrupted.jpg", "error": "Failed to read file" },
    { "fileName": "too-big.mp4", "error": "File exceeds 50 MB limit" }
  ]
}
```

This allows the frontend to show both successes and failures to the user.

---

## Performance Considerations

### File Size Limits

- **Per file**: 50 MB (configurable in code)
- **Total request**: Limited by server's max request size

### Pagination

- Default limit: 100
- Maximum limit: 200
- Use `offset` for pagination

### Database Indexes

```javascript
// MongoDB shell
db.media_assets.createIndex({ "userEmail": 1 });
db.media_assets.createIndex({ "userEmail": 1, "createdAt": -1 });
db.media_assets.createIndex({ "userEmail": 1, "mimeType": 1 });
```

### Supabase Storage

- Global CDN for fast delivery
- Automatic image optimization (optional, configure in Supabase)
- No bandwidth limits on most plans

---

## Troubleshooting

### Upload Fails with 500 Error

**Check:**
1. Supabase URL and service role key are correct
2. Bucket `social-media-assets` exists
3. Bucket is set to public
4. Service role key has upload permissions

**Debug logs**: Check backend logs for Supabase API errors

### Files Upload But CDN URL Returns 404

**Check:**
1. Bucket is set to **Public**
2. Object path matches what's stored in database
3. Supabase project is active (not paused)

### Delete Succeeds But File Still in Supabase

**Check:**
1. Service role key has delete permissions
2. Object path format is correct
3. Backend logs for Supabase API errors

**Note**: Deletion from MongoDB always happens even if Supabase deletion fails. This prevents orphaned database records.

---

## Summary

✅ **Backend-owned architecture**: Frontend sends files, backend handles all Supabase operations  
✅ **Secure**: Service role key stays server-side  
✅ **Simple frontend**: Just multipart POST, no Supabase SDK needed  
✅ **Robust**: Partial success support, ownership verification  
✅ **Scalable**: Pagination, filtering, search  
✅ **MongoDB integration**: Full Spring Data MongoDB support  

**Key Endpoints:**
- `POST /v1/api/assets/upload` - Upload files
- `GET /v1/api/assets` - List assets
- `DELETE /v1/api/assets/{id}` - Delete asset

All operations use `userEmail` from JWT for user isolation and security.
