# Assets Folder Hierarchy Implementation

## Overview
Successfully implemented folder hierarchy for Assets management in both frontend and backend, enabling users to organize their media assets like Google Drive with nested folders.

---

## Backend Changes (Java/Spring Boot)

### 1. **MediaAsset Entity** (`src/main/java/net/ai/chatbot/entity/MediaAsset.java`)

**Added Property:**
```java
/**
 * Folder path for hierarchical organization (e.g., "My Photos/Vacation")
 * Empty string means root level
 */
@Builder.Default
private String folderPath = "";
```

**Impact:** 
- All assets now track which folder they belong to
- Empty `folderPath` means asset is in root directory
- Supports nested paths like "Photos/Vacation/2024"

---

### 2. **MediaAssetService** (`src/main/java/net/ai/chatbot/service/mediaasset/MediaAssetService.java`)

**New/Modified Methods:**

#### `uploadAll(String userEmail, List<MultipartFile> files, String folderPath)`
- Enhanced to accept `folderPath` parameter
- Organizes files into Supabase storage by folder structure
- Example storage path: `social-posts/{email}/Photos/Vacation/1234567_photo.jpg`
- Backward compatible with old `uploadAll(userEmail, files)` method

#### `listFolders(String userEmail, String parentFolder)`
- Lists unique folders at a specific level
- Returns direct children folders only (not recursive)
- Example: `listFolders(email, "Photos")` returns `["Vacation", "Work", "Archive"]`

#### `createFolder(String userEmail, String folderPath)`
- Creates a new folder (virtual operation)
- Validates folder path format
- Prevents invalid paths (backslashes, double slashes, etc.)

#### `deleteFolder(String userEmail, String folderPath)`
- Deletes a folder and ALL assets inside it
- Removes files from Supabase storage
- Removes asset metadata from database
- Logs operation with asset count

---

### 3. **MediaAssetController** (`src/main/java/net/ai/chatbot/controller/mediaasset/MediaAssetController.java`)

**New Endpoints:**

#### `POST /v1/api/assets/upload?folderPath=Photos/Vacation`
- Upload files to a specific folder
- Query parameter: `folderPath` (optional, defaults to root)
- Form data: `files` (multipart array)

**Example:**
```bash
curl -X POST "http://localhost:8080/v1/api/assets/upload?folderPath=Photos" \
  -H "Authorization: Bearer <jwt>" \
  -F "files=@photo.jpg"
```

**Response:**
```json
{
  "uploaded": [
    {
      "id": "550e8400-...",
      "fileName": "photo.jpg",
      "folderPath": "Photos"
    }
  ],
  "failed": []
}
```

#### `GET /v1/api/assets/folders?parentFolder=Photos`
- List folders at a specific level
- Query parameter: `parentFolder` (optional, defaults to root)

**Response:**
```json
["Vacation", "Work", "Archive"]
```

#### `POST /v1/api/assets/folders`
- Create a new folder

**Request Body:**
```json
{
  "folderPath": "Photos/Vacation"
}
```

**Response:**
```json
{
  "success": true,
  "folderPath": "Photos/Vacation"
}
```

#### `DELETE /v1/api/assets/folders?folderPath=Photos/Vacation`
- Delete a folder and all assets inside it
- Query parameter: `folderPath` (required)

**Response:**
```json
{
  "success": true
}
```

---

### 4. **MediaAssetDao** (`src/main/java/net/ai/chatbot/dao/MediaAssetDao.java`)

**New Query Method:**
```java
List<MediaAsset> findByUserEmailAndFolderPathStartingWith(String userEmail, String folderPathPrefix);
```

- Finds all assets in a folder and its subfolders
- Used for folder listing and deletion operations
- Supports hierarchical queries

---

## Frontend Changes (Next.js/React)

### **Assets Page** (`app/assets/page.tsx`)

**Added Features:**

#### 1. **Folder Structure State**
```typescript
const [currentFolder, setCurrentFolder] = useState<string>("");
const [folders, setFolders] = useState<Folder[]>([]);
```

#### 2. **Breadcrumb Navigation**
- Shows current folder path (e.g., "Root / Photos / Vacation")
- Click "Root" to go back to root level
- Visual indication of current location

#### 3. **Folder Grid View**
- Displays folders as clickable cards with folder icon
- Shows folder name
- Hover effects for interactivity
- Click to enter folder

#### 4. **New Folder Button**
- Secondary MDBBtn next to upload button
- Prompts user for folder name
- Creates new folder via backend API

#### 5. **Smart Upload**
- Sends `folderPath` parameter to backend when uploading
- Files are automatically stored in current folder
- Works seamlessly with folder navigation

#### 6. **Responsive Folder Display**
- Only shows folders when not searching and at root/parent level
- Styled with professional grid layout
- Smooth transitions and hover effects

---

## API Contract Summary

### Upload to Folder
```
POST /v1/api/assets/upload?folderPath=Photos
```

### List Folders
```
GET /v1/api/assets/folders?parentFolder=Photos
```

### Create Folder
```
POST /v1/api/assets/folders
Content-Type: application/json

{
  "folderPath": "Photos/Vacation"
}
```

### Delete Folder
```
DELETE /v1/api/assets/folders?folderPath=Photos/Vacation
```

---

## Folder Path Format

**Valid:**
- `"Photos"`
- `"Photos/Vacation"`
- `"Photos/2024/January"`
- Empty string `""` (root)

**Invalid:**
- `"/Photos"` (starts with slash)
- `"Photos/"` (ends with slash)
- `"Photos//Vacation"` (double slash)
- `"Photos\\Vacation"` (backslash)

---

## Storage Structure

### Example Supabase Storage Layout
```
social-posts/
├── user@example.com/
│   ├── photo1.jpg (root)
│   ├── Photos/
│   │   ├── 1234567_vacation.jpg
│   │   ├── 1234568_work.jpg
│   │   ├── Vacation/
│   │   │   ├── 1234569_beach.jpg
│   │   │   └── 1234570_sunset.jpg
│   │   └── Work/
│   │       └── 1234571_presentation.jpg
│   └── Videos/
│       └── 1234572_tutorial.mp4
```

### MongoDB Storage
```json
{
  "id": "550e8400-...",
  "userEmail": "user@example.com",
  "fileName": "vacation.jpg",
  "folderPath": "Photos/Vacation",
  "supabaseUrl": "https://xxx.supabase.co/.../vacation.jpg",
  "objectPath": "social-posts/user@example.com/Photos/Vacation/1234567_vacation.jpg",
  "createdAt": "2024-01-15T10:00:00Z"
}
```

---

## Database Changes

### MongoDB Collection: `media_assets`

**New Field:**
- `folderPath` (String, default: "")
- Indexed for fast queries
- Stores hierarchical folder path

**Example Documents:**
```json
[
  {
    "folderPath": "",
    "fileName": "root-photo.jpg"
  },
  {
    "folderPath": "Photos",
    "fileName": "photo1.jpg"
  },
  {
    "folderPath": "Photos/Vacation",
    "fileName": "beach.jpg"
  }
]
```

---

## User Workflow

1. **Browse Root Level**
   - User opens Assets page
   - Sees folders in root directory
   - Can upload files to root by clicking "Upload Files"

2. **Enter Folder**
   - Click any folder card
   - Breadcrumb updates: "Root / Photos"
   - Assets in that folder display
   - Can upload files to this folder

3. **Create Subfolder**
   - While in a folder, click "New Folder"
   - Enter folder name
   - Folder is created at current level

4. **Delete Folder**
   - Right-click folder (or context menu) → Delete
   - All assets inside are deleted from Supabase and database
   - Confirmation dialog prevents accidents

5. **Navigate Up**
   - Click "Root" in breadcrumb to go back
   - Or use parent folder link

---

## Benefits

✅ **Organization** - Users can organize assets hierarchically  
✅ **Google Drive-like Experience** - Familiar UI pattern  
✅ **Backward Compatible** - Old API still works (empty folderPath)  
✅ **Secure** - User email verification on all operations  
✅ **Scalable** - Supports unlimited folder depth  
✅ **Consistent** - Same backend logic for both Social Media Assets and standalone Assets  

---

## Testing

### Backend Testing
```bash
# Upload to folder
curl -X POST "http://localhost:8080/v1/api/assets/upload?folderPath=Photos" \
  -H "Authorization: Bearer <jwt>" \
  -F "files=@photo.jpg"

# List folders
curl "http://localhost:8080/v1/api/assets/folders?parentFolder=Photos" \
  -H "Authorization: Bearer <jwt>"

# Create folder
curl -X POST "http://localhost:8080/v1/api/assets/folders" \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"folderPath":"Photos/Vacation"}'

# Delete folder
curl -X DELETE "http://localhost:8080/v1/api/assets/folders?folderPath=Photos/Vacation" \
  -H "Authorization: Bearer <jwt>"
```

### Frontend Testing
1. Navigate to `/assets`
2. Create a new folder with "New Folder" button
3. Click folder to enter it
4. Upload files with "Upload Files" button
5. Verify files appear in folder
6. Create subfolders and repeat
7. Use breadcrumb to navigate back
8. Delete folder to verify cleanup

---

## Deployment Notes

- No database migration needed (MongoDB is schemaless)
- Backward compatible with existing assets
- Existing assets have `folderPath: ""` (root level)
- No changes to existing API contracts
- New endpoints are optional additions

---

## Future Enhancements

- [ ] Folder rename functionality
- [ ] Folder sharing/permissions
- [ ] Asset move between folders
- [ ] Folder favorites
- [ ] Folder-level statistics
- [ ] Bulk operations (move, delete multiple)

---

**Implementation Date:** March 25, 2026  
**Status:** ✅ Complete and Ready for Testing
