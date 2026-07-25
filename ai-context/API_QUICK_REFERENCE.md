# API Quick Reference - File Attachment API

**Base URL:** `http://localhost:8080`

---

## 5 Main Endpoints

### 1️⃣ Upload File
```
POST /api/attachments/upload
Content-Type: multipart/form-data

Parameters:
- file (required)
- chatbotId (required)
- sessionId (required)

Response (201):
{
  "fileId": "file_chatbot_123_session_456_doc_1707385649123",
  "fileName": "document.pdf",
  "downloadUrl": "http://...",
  "fileSize": 256000,
  "status": "stored"
}
```

### 2️⃣ Download File
```
GET /api/attachments/download/{fileId}?chatbotId={chatbotId}

Response: Binary file content
Headers:
- Content-Type: application/pdf (or file type)
- Content-Disposition: attachment; filename="document.pdf"
```

### 3️⃣ Get File Metadata
```
GET /api/attachments/metadata/{fileId}?chatbotId={chatbotId}

Response (200):
{
  "fileId": "file_...",
  "fileName": "document.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "uploadedAt": 1707385649000,
  "status": "stored"
}
```

### 4️⃣ List All Files
```
GET /api/attachments/list/{chatbotId}

Response (200):
{
  "chatbotId": "chatbot_123",
  "totalFiles": 3,
  "files": [...]
}
```

### 5️⃣ Delete File
```
DELETE /api/attachments/{fileId}?chatbotId={chatbotId}

Response (200):
{
  "success": true,
  "message": "File deleted successfully"
}
```

---

## Quick Examples

### JavaScript
```javascript
// Upload
const form = new FormData();
form.append('file', fileInput.files[0]);
form.append('chatbotId', 'bot_123');
form.append('sessionId', 'sess_456');

const res = await fetch('/api/attachments/upload', {
  method: 'POST',
  body: form
});
const {fileId, downloadUrl} = await res.json();

// Download
const download = await fetch(
  `/api/attachments/download/${fileId}?chatbotId=bot_123`
);
const blob = await download.blob();
```

### cURL
```bash
# Upload
curl -X POST "http://localhost:8080/api/attachments/upload" \
  -F "file=@document.pdf" \
  -F "chatbotId=bot_123" \
  -F "sessionId=sess_456"

# Download
curl "http://localhost:8080/api/attachments/download/file_...?chatbotId=bot_123" \
  -o document.pdf

# List
curl "http://localhost:8080/api/attachments/list/bot_123"

# Delete
curl -X DELETE "http://localhost:8080/api/attachments/file_...?chatbotId=bot_123"
```

### Python
```python
import requests

# Upload
files = {'file': open('document.pdf', 'rb')}
data = {'chatbotId': 'bot_123', 'sessionId': 'sess_456'}
response = requests.post('http://localhost:8080/api/attachments/upload',
                         files=files, data=data)
file_id = response.json()['fileId']

# Download
response = requests.get(f'http://localhost:8080/api/attachments/download/{file_id}',
                        params={'chatbotId': 'bot_123'})
with open('document.pdf', 'wb') as f:
    f.write(response.content)

# List
response = requests.get('http://localhost:8080/api/attachments/list/bot_123')
files = response.json()['files']
```

---

## Status Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created (upload successful) |
| 400 | Bad request (missing params, empty file) |
| 404 | Not found (file doesn't exist) |
| 500 | Server error |

---

## Response Fields

### Upload Response
- `fileId` - Use to download later
- `fileName` - Original filename
- `downloadUrl` - Ready to use
- `fileSize` - In bytes
- `status` - "stored"

### Metadata Response
- `fileId` - File identifier
- `fileName` - Original name
- `mimeType` - File type
- `fileSize` - In bytes
- `uploadedAt` - Timestamp
- `formattedFileSize` - Human readable

---

## Error Format

```json
{
  "success": false,
  "message": "Error description",
  "timestamp": 1707385649000
}
```

---

## Important Notes

✅ **Always include chatbotId** - Required for all endpoints  
✅ **Store fileId, not URL** - Use fileId for future downloads  
✅ **Max file size: 15 MB** - Larger files won't upload  
✅ **All file types** - PDF, images, documents, etc.  
✅ **Session tracking** - Files linked to sessions  

---

## Workflow

```
1. Upload file
   ↓ Get fileId
2. Store fileId in database
   ↓
3. Use fileId to download later
   ↓
4. Or share fileId with backend
```

---

That's it! 🚀 Ready to integrate with your frontend.


