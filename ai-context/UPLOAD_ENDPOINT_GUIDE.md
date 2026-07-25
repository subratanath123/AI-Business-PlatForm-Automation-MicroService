# File Upload Endpoint - Quick Guide

**Date:** February 10, 2026  
**Feature:** POST /api/attachments/upload - Upload file and get fileId  
**Status:** ✅ **LIVE & READY TO USE**

---

## 📝 Endpoint Details

### POST /api/attachments/upload

Upload a file to MongoDB and get back a `fileId` and `downloadUrl`.

---

## 📊 Request Parameters

**Content-Type:** `multipart/form-data`

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| **file** | File | ✅ Yes | The file to upload |
| **chatbotId** | String | ✅ Yes | Your chatbot ID |
| **sessionId** | String | ✅ Yes | Session ID for tracking |

---

## 📤 Request Example

### Using cURL

```bash
curl -X POST "http://localhost:8080/api/attachments/upload" \
  -F "file=@report.pdf" \
  -F "chatbotId=chatbot_123" \
  -F "sessionId=session_456"
```

### Using JavaScript/Fetch

```javascript
const file = document.getElementById('fileInput').files[0];
const formData = new FormData();
formData.append('file', file);
formData.append('chatbotId', 'chatbot_123');
formData.append('sessionId', 'session_456');

const response = await fetch('http://localhost:8080/api/attachments/upload', {
  method: 'POST',
  body: formData
});

const result = await response.json();
const fileId = result.fileId;
const downloadUrl = result.downloadUrl;
```

### Using Python/Requests

```python
import requests

files = {'file': open('report.pdf', 'rb')}
data = {
    'chatbotId': 'chatbot_123',
    'sessionId': 'session_456'
}

response = requests.post(
    'http://localhost:8080/api/attachments/upload',
    files=files,
    data=data
)

result = response.json()
file_id = result['fileId']
download_url = result['downloadUrl']
```

---

## 📥 Response

### Success (201 Created)

```json
{
  "fileId": "file_chatbot_123_session_456_report_1707385649123",
  "fileName": "report.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "downloadUrl": "http://localhost:8080/api/attachments/download/file_chatbot_123_session_456_report_1707385649123?chatbotId=chatbot_123",
  "uploadedAt": 1707385649000,
  "status": "stored"
}
```

### Error Cases

**400 Bad Request - Missing file:**
```json
{
  "success": false,
  "message": "File is empty",
  "timestamp": 1707385649000
}
```

**400 Bad Request - Missing chatbotId:**
```json
{
  "success": false,
  "message": "chatbotId is required",
  "timestamp": 1707385649000
}
```

**400 Bad Request - Missing sessionId:**
```json
{
  "success": false,
  "message": "sessionId is required",
  "timestamp": 1707385649000
}
```

**500 Internal Server Error:**
```json
{
  "success": false,
  "message": "Error uploading file: {error message}",
  "timestamp": 1707385649000
}
```

---

## 🔑 Key Response Fields

| Field | Description | Example |
|-------|-------------|---------|
| **fileId** | Unique file identifier | `file_chatbot_123_session_456_report_1707385649123` |
| **fileName** | Original file name | `report.pdf` |
| **mimeType** | File MIME type | `application/pdf` |
| **fileSize** | Size in bytes | `256000` |
| **downloadUrl** | ✨ **Direct download link** | `http://localhost:8080/api/attachments/download/...` |
| **uploadedAt** | Upload timestamp (ms) | `1707385649000` |
| **status** | File status | `stored` |

---

## 🚀 Complete Workflow

### Step 1: Upload File

```bash
curl -X POST "http://localhost:8080/api/attachments/upload" \
  -F "file=@image.jpg" \
  -F "chatbotId=chatbot_123" \
  -F "sessionId=session_456"

# Response:
# {
#   "fileId": "file_chatbot_123_session_456_image_1707385649123",
#   "downloadUrl": "http://localhost:8080/api/attachments/download/file_...",
#   "status": "stored"
# }
```

### Step 2: Use fileId Everywhere

```bash
# Store in your database
{
  "message": "Here's the image",
  "fileId": "file_chatbot_123_session_456_image_1707385649123"
}

# Or share the download URL
{
  "message": "Download the image",
  "downloadUrl": "http://localhost:8080/api/attachments/download/file_..."
}
```

### Step 3: Download File Anytime

```bash
curl "http://localhost:8080/api/attachments/download/file_chatbot_123_session_456_image_1707385649123?chatbotId=chatbot_123" \
  -o downloaded_image.jpg
```

### Step 4: N8N Can Process

```javascript
// N8N JavaScript Node
const fileId = 'file_chatbot_123_session_456_image_1707385649123';
const chatbotId = 'chatbot_123';

// Download using fileId
const downloadUrl = `http://localhost:8080/api/attachments/download/${fileId}?chatbotId=${chatbotId}`;
const response = await fetch(downloadUrl);
const buffer = await response.arrayBuffer();

// Analyze the image
const analysis = await analyzeImage(buffer);
```

---

## 💻 Integration Examples

### HTML Form Upload

```html
<form id="uploadForm">
  <input type="file" name="file" required>
  <input type="text" name="chatbotId" value="chatbot_123" required>
  <input type="text" name="sessionId" value="session_456" required>
  <button type="submit">Upload</button>
</form>

<script>
document.getElementById('uploadForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  
  const formData = new FormData(e.target);
  const response = await fetch('http://localhost:8080/api/attachments/upload', {
    method: 'POST',
    body: formData
  });
  
  const result = await response.json();
  console.log('File uploaded:', result.fileId);
  console.log('Download:', result.downloadUrl);
});
</script>
```

### React Upload Component

```jsx
import React, { useState } from 'react';

export function FileUploader() {
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState(null);
  
  const handleUpload = async (e) => {
    e.preventDefault();
    setUploading(true);
    
    const formData = new FormData();
    formData.append('file', file);
    formData.append('chatbotId', 'chatbot_123');
    formData.append('sessionId', 'session_456');
    
    try {
      const response = await fetch('http://localhost:8080/api/attachments/upload', {
        method: 'POST',
        body: formData
      });
      
      const data = await response.json();
      setResult(data);
    } catch (error) {
      console.error('Upload failed:', error);
    } finally {
      setUploading(false);
    }
  };
  
  return (
    <form onSubmit={handleUpload}>
      <input 
        type="file" 
        onChange={(e) => setFile(e.target.files[0])} 
        required 
      />
      <button type="submit" disabled={uploading}>
        {uploading ? 'Uploading...' : 'Upload'}
      </button>
      
      {result && (
        <div>
          <p>File ID: {result.fileId}</p>
          <p>Download: <a href={result.downloadUrl}>Click here</a></p>
        </div>
      )}
    </form>
  );
}
```

### N8N Integration

```javascript
// N8N HTTP Request Node
{
  "method": "POST",
  "url": "http://localhost:8080/api/attachments/upload",
  "headers": {
    "Content-Type": "multipart/form-data"
  },
  "formData": {
    "file": "{{$node['File Trigger'].data.binary.file}}",
    "chatbotId": "chatbot_123",
    "sessionId": "{{$node['Get Session'].data.sessionId}}"
  }
}

// Response in next node:
// {
//   "fileId": "file_...",
//   "downloadUrl": "http://localhost:8080/api/attachments/download/...",
//   "fileName": "document.pdf"
// }
```

---

## ✅ What Happens Behind the Scenes

1. **File Received**
   - Validate file is not empty
   - Validate chatbotId provided
   - Validate sessionId provided

2. **File Processing**
   - Read file bytes
   - Encode to Base64
   - Create Attachment DTO

3. **MongoDB Storage**
   - Store as BSON Binary
   - Create document with metadata
   - Generate unique fileId

4. **Download URL Generated**
   - Format: `/api/attachments/download/{fileId}?chatbotId={chatbotId}`
   - Ready to use immediately

5. **Response Returned**
   - fileId - for future reference
   - downloadUrl - for downloading
   - All metadata included

---

## 🔐 Security

✅ **chatbotId Required**
- All requests must include chatbotId
- Files isolated by chatbotId
- Prevents cross-chatbot access

✅ **sessionId Tracked**
- Files linked to sessions
- Can audit who uploaded what
- Enables session-based cleanup

✅ **File Validation**
- Empty file check
- Parameter validation
- Error logging

---

## 🎯 Common Use Cases

### 1. Chat with Attachment

```javascript
// User uploads file in chat
const uploadResult = await fetch('/api/attachments/upload', {
  method: 'POST',
  body: formData
});

const {fileId, downloadUrl} = await uploadResult.json();

// Send chat message with fileId
await fetch('/api/chat/message', {
  method: 'POST',
  body: JSON.stringify({
    message: "Analyze this document",
    attachmentFileId: fileId
  })
});
```

### 2. Document Management

```javascript
// Store document
const upload = await fetch('/api/attachments/upload', {...});
const {fileId} = await upload.json();

// Save metadata
database.save({
  documentId: 'doc_123',
  attachmentFileId: fileId,
  uploadedAt: new Date()
});

// Later: Download document
const download = await fetch(`/api/attachments/download/${fileId}?chatbotId=...`);
```

### 3. Image Analysis Pipeline

```javascript
// 1. Upload image
const upload = await fetch('/api/attachments/upload', {
  method: 'POST',
  body: formData
});
const {fileId, downloadUrl} = await upload.json();

// 2. Send to N8N for analysis
const analysis = await fetch('http://n8n:5678/webhook/analyze', {
  method: 'POST',
  body: JSON.stringify({fileId, downloadUrl})
});

// 3. N8N downloads and processes
// 4. Save results
```

---

## 📋 HTTP Status Codes

| Code | Meaning | Example |
|------|---------|---------|
| **201** | File uploaded successfully | Created with fileId |
| **400** | Bad request (missing params) | Missing chatbotId or sessionId |
| **500** | Server error | Database write failed |

---

## 🔗 Related Endpoints

| Endpoint | Purpose |
|----------|---------|
| **POST** `/api/attachments/upload` | Upload file ✨ |
| **GET** `/api/attachments/download/{fileId}` | Download file |
| **GET** `/api/attachments/metadata/{fileId}` | Get file info |
| **GET** `/api/attachments/list/{chatbotId}` | List all files |
| **DELETE** `/api/attachments/{fileId}` | Delete file |

---

## ✨ Summary

The upload endpoint makes it easy to:
1. Upload files via HTTP POST
2. Get unique fileId immediately
3. Use fileId to download anytime
4. Share downloadUrl with N8N
5. Process files locally

**Ready to use! Start uploading files now.**

---

**Status:** ✅ Complete & Production Ready  
**Last Updated:** February 10, 2026

