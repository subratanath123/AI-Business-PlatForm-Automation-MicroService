# File Attachment API Documentation

**Date:** February 10, 2026  
**Version:** 1.0  
**Status:** Production Ready  
**Base URL:** `http://localhost:8080` (or your server URL)

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Authentication](#authentication)
3. [Endpoints](#endpoints)
4. [Request/Response Examples](#requestresponse-examples)
5. [Error Handling](#error-handling)
6. [Integration Examples](#integration-examples)
7. [Data Models](#data-models)

---

## Overview

The File Attachment API allows you to:
- **Upload** files and get a unique fileId
- **Download** files using fileId
- **List** all uploaded files
- **Delete** files
- **Query** file metadata

### Key Features

✅ Store files in MongoDB (not cloud)  
✅ Get download links immediately  
✅ Files tracked by chatbot and session  
✅ Simple REST API  
✅ Supports all file types  

---

## Authentication

Currently, no authentication is required. All endpoints accept requests with:
- **chatbotId** - Required for all endpoints
- **sessionId** - Required for upload endpoint

### Future Enhancement
Authentication will be added using Bearer tokens in the Authorization header.

---

## Endpoints

### 1. Upload File

#### POST /api/attachments/upload

Upload a file and receive a fileId and download URL.

**Request:**
- **Method:** POST
- **Content-Type:** multipart/form-data
- **URL:** `/api/attachments/upload`

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| file | File | ✅ Yes | The file to upload |
| chatbotId | String | ✅ Yes | Unique chatbot identifier |
| sessionId | String | ✅ Yes | Unique session identifier |

**Example Request (cURL):**

```bash
curl -X POST "http://localhost:8080/api/attachments/upload" \
  -F "file=@document.pdf" \
  -F "chatbotId=chatbot_123" \
  -F "sessionId=session_456"
```

**Example Request (JavaScript/Fetch):**

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
console.log('File ID:', result.fileId);
console.log('Download URL:', result.downloadUrl);
```

**Success Response (HTTP 201):**

```json
{
  "fileId": "file_chatbot_123_session_456_document_1707385649123",
  "fileName": "document.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "downloadUrl": "http://localhost:8080/api/attachments/download/file_chatbot_123_session_456_document_1707385649123?chatbotId=chatbot_123",
  "uploadedAt": 1707385649000,
  "status": "stored"
}
```

**Error Response (HTTP 400):**

```json
{
  "success": false,
  "message": "chatbotId is required",
  "timestamp": 1707385649000
}
```

---

### 2. Download File

#### GET /api/attachments/download/{fileId}

Download a file using its fileId.

**Request:**
- **Method:** GET
- **URL:** `/api/attachments/download/{fileId}?chatbotId={chatbotId}`
- **Query Parameters:** chatbotId (required)

**Parameters:**

| Parameter | Type | Location | Required | Description |
|-----------|------|----------|----------|-------------|
| fileId | String | Path | ✅ Yes | File identifier from upload response |
| chatbotId | String | Query | ✅ Yes | Must match the chatbot that uploaded |

**Example Request (cURL):**

```bash
curl "http://localhost:8080/api/attachments/download/file_chatbot_123_session_456_document_1707385649123?chatbotId=chatbot_123" \
  -o downloaded_document.pdf
```

**Example Request (JavaScript/Fetch):**

```javascript
const fileId = 'file_chatbot_123_session_456_document_1707385649123';
const chatbotId = 'chatbot_123';

const response = await fetch(
  `http://localhost:8080/api/attachments/download/${fileId}?chatbotId=${chatbotId}`
);

const blob = await response.blob();
// Create download link
const url = window.URL.createObjectURL(blob);
const link = document.createElement('a');
link.href = url;
link.download = 'document.pdf';
link.click();
```

**Success Response (HTTP 200):**
- Returns binary file content
- Headers include:
  - `Content-Type`: File MIME type (e.g., application/pdf)
  - `Content-Disposition`: attachment; filename="document.pdf"
  - `Content-Length`: File size in bytes

**Error Response (HTTP 404):**

```json
{
  "success": false,
  "message": "File not found: file_...",
  "timestamp": 1707385649000
}
```

---

### 3. Get File Metadata

#### GET /api/attachments/metadata/{fileId}

Get file information without downloading the content.

**Request:**
- **Method:** GET
- **URL:** `/api/attachments/metadata/{fileId}?chatbotId={chatbotId}`
- **Query Parameters:** chatbotId (required)

**Parameters:**

| Parameter | Type | Location | Required | Description |
|-----------|------|----------|----------|-------------|
| fileId | String | Path | ✅ Yes | File identifier |
| chatbotId | String | Query | ✅ Yes | Chatbot identifier |

**Example Request (cURL):**

```bash
curl "http://localhost:8080/api/attachments/metadata/file_chatbot_123_session_456_document_1707385649123?chatbotId=chatbot_123"
```

**Example Request (JavaScript/Fetch):**

```javascript
const fileId = 'file_chatbot_123_session_456_document_1707385649123';
const chatbotId = 'chatbot_123';

const response = await fetch(
  `http://localhost:8080/api/attachments/metadata/${fileId}?chatbotId=${chatbotId}`
);

const metadata = await response.json();
console.log('File Name:', metadata.fileName);
console.log('File Size:', metadata.fileSize);
console.log('MIME Type:', metadata.mimeType);
```

**Success Response (HTTP 200):**

```json
{
  "fileId": "file_chatbot_123_session_456_document_1707385649123",
  "fileName": "document.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "uploadedAt": 1707385649000,
  "status": "stored",
  "formattedFileSize": "250.0 KB"
}
```

---

### 4. List All Files

#### GET /api/attachments/list/{chatbotId}

List all uploaded files for a chatbot.

**Request:**
- **Method:** GET
- **URL:** `/api/attachments/list/{chatbotId}`

**Parameters:**

| Parameter | Type | Location | Required | Description |
|-----------|------|----------|----------|-------------|
| chatbotId | String | Path | ✅ Yes | Chatbot identifier |

**Example Request (cURL):**

```bash
curl "http://localhost:8080/api/attachments/list/chatbot_123"
```

**Example Request (JavaScript/Fetch):**

```javascript
const chatbotId = 'chatbot_123';

const response = await fetch(
  `http://localhost:8080/api/attachments/list/${chatbotId}`
);

const data = await response.json();
console.log('Total Files:', data.totalFiles);
console.log('Files:', data.files);

// Iterate through files
data.files.forEach(file => {
  console.log(`${file.fileName} (${file.fileSize} bytes)`);
});
```

**Success Response (HTTP 200):**

```json
{
  "chatbotId": "chatbot_123",
  "totalFiles": 3,
  "files": [
    {
      "fileId": "file_chatbot_123_session_456_document_1707385649123",
      "fileName": "document.pdf",
      "mimeType": "application/pdf",
      "fileSize": 256000,
      "uploadedAt": 1707385649000,
      "status": "stored",
      "formattedFileSize": "250.0 KB"
    },
    {
      "fileId": "file_chatbot_123_session_789_image_1707385649124",
      "fileName": "screenshot.png",
      "mimeType": "image/png",
      "fileSize": 512000,
      "uploadedAt": 1707385649000,
      "status": "stored",
      "formattedFileSize": "500.0 KB"
    }
  ]
}
```

---

### 5. Delete File

#### DELETE /api/attachments/{fileId}

Delete a file from the system.

**Request:**
- **Method:** DELETE
- **URL:** `/api/attachments/{fileId}?chatbotId={chatbotId}`
- **Query Parameters:** chatbotId (required)

**Parameters:**

| Parameter | Type | Location | Required | Description |
|-----------|------|----------|----------|-------------|
| fileId | String | Path | ✅ Yes | File identifier |
| chatbotId | String | Query | ✅ Yes | Chatbot identifier |

**Example Request (cURL):**

```bash
curl -X DELETE "http://localhost:8080/api/attachments/file_chatbot_123_session_456_document_1707385649123?chatbotId=chatbot_123"
```

**Example Request (JavaScript/Fetch):**

```javascript
const fileId = 'file_chatbot_123_session_456_document_1707385649123';
const chatbotId = 'chatbot_123';

const response = await fetch(
  `http://localhost:8080/api/attachments/${fileId}?chatbotId=${chatbotId}`,
  { method: 'DELETE' }
);

const result = await response.json();
console.log(result.message); // "File deleted successfully"
```

**Success Response (HTTP 200):**

```json
{
  "success": true,
  "message": "File deleted successfully",
  "timestamp": 1707385649000
}
```

**Error Response (HTTP 404):**

```json
{
  "success": false,
  "message": "File not found: file_...",
  "timestamp": 1707385649000
}
```

---

## Request/Response Examples

### Complete Upload and Download Flow

#### Step 1: Upload File

```javascript
// 1. Select file from input
const fileInput = document.getElementById('fileInput');
const file = fileInput.files[0];

// 2. Create form data
const formData = new FormData();
formData.append('file', file);
formData.append('chatbotId', 'my_chatbot');
formData.append('sessionId', 'session_' + Date.now());

// 3. Upload
const uploadResponse = await fetch('http://localhost:8080/api/attachments/upload', {
  method: 'POST',
  body: formData
});

const uploadResult = await uploadResponse.json();
const fileId = uploadResult.fileId;
const downloadUrl = uploadResult.downloadUrl;

console.log('File uploaded:', fileId);
```

#### Step 2: Use fileId

```javascript
// Store fileId in your database or state
const attachmentData = {
  messageId: 'msg_123',
  fileId: uploadResult.fileId,
  fileName: uploadResult.fileName,
  downloadUrl: uploadResult.downloadUrl
};

// Send to backend/save to database
await saveAttachmentData(attachmentData);
```

#### Step 3: Display Download Link

```javascript
// Later: Display link to user
const element = document.getElementById('attachment-link');
element.innerHTML = `
  <a href="${downloadUrl}" download>
    Download ${uploadResult.fileName}
  </a>
`;
```

#### Step 4: Download When Needed

```javascript
// When user clicks download or you need the file
const response = await fetch(downloadUrl);
const blob = await response.blob();

// Option 1: Trigger browser download
const url = window.URL.createObjectURL(blob);
const a = document.createElement('a');
a.href = url;
a.download = uploadResult.fileName;
a.click();

// Option 2: Process the file
const arrayBuffer = await blob.arrayBuffer();
// Send to AI API, analyze, etc.
```

---

## Error Handling

### HTTP Status Codes

| Code | Meaning | When |
|------|---------|------|
| 201 | Created | File uploaded successfully |
| 200 | OK | File downloaded, metadata retrieved, list retrieved |
| 400 | Bad Request | Missing parameters, empty file, validation error |
| 404 | Not Found | File not found |
| 500 | Internal Server Error | Server error during processing |

### Common Errors

**Error: chatbotId is required**
```json
{
  "success": false,
  "message": "chatbotId is required",
  "timestamp": 1707385649000
}
```
**Solution:** Include chatbotId in request parameters

**Error: sessionId is required**
```json
{
  "success": false,
  "message": "sessionId is required",
  "timestamp": 1707385649000
}
```
**Solution:** Include sessionId in form data for upload

**Error: File is empty**
```json
{
  "success": false,
  "message": "File is empty",
  "timestamp": 1707385649000
}
```
**Solution:** Select a valid file before uploading

**Error: File not found**
```json
{
  "success": false,
  "message": "File not found: file_...",
  "timestamp": 1707385649000
}
```
**Solution:** Verify fileId is correct and file hasn't been deleted

---

## Integration Examples

### React Component - File Upload

```jsx
import React, { useState } from 'react';

export function FileUploader({ chatbotId, sessionId, onUploadSuccess }) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState(null);

  const handleFileChange = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    setUploading(true);
    setError(null);

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('chatbotId', chatbotId);
      formData.append('sessionId', sessionId);

      const response = await fetch('http://localhost:8080/api/attachments/upload', {
        method: 'POST',
        body: formData
      });

      if (!response.ok) {
        throw new Error('Upload failed');
      }

      const result = await response.json();
      onUploadSuccess(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <input
        type="file"
        onChange={handleFileChange}
        disabled={uploading}
      />
      {uploading && <p>Uploading...</p>}
      {error && <p style={{ color: 'red' }}>{error}</p>}
    </div>
  );
}
```

### React Component - File List

```jsx
export function FileList({ chatbotId }) {
  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchFiles = async () => {
      try {
        const response = await fetch(
          `http://localhost:8080/api/attachments/list/${chatbotId}`
        );
        const data = await response.json();
        setFiles(data.files);
      } catch (error) {
        console.error('Failed to load files:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchFiles();
  }, [chatbotId]);

  if (loading) return <p>Loading...</p>;

  return (
    <div>
      <h3>Files ({files.length})</h3>
      <ul>
        {files.map(file => (
          <li key={file.fileId}>
            <a href={`/api/attachments/download/${file.fileId}?chatbotId=${chatbotId}`} download>
              {file.fileName}
            </a>
            <span> ({file.formattedFileSize})</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
```

### Vue.js Component - Upload

```vue
<template>
  <div>
    <input type="file" @change="handleFileUpload" :disabled="uploading" />
    <div v-if="uploading">Uploading...</div>
    <div v-if="error" style="color: red">{{ error }}</div>
    <div v-if="uploadResult">
      File ID: {{ uploadResult.fileId }}
      <a :href="uploadResult.downloadUrl" download>Download</a>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      uploading: false,
      error: null,
      uploadResult: null
    };
  },
  methods: {
    async handleFileUpload(event) {
      const file = event.target.files[0];
      if (!file) return;

      this.uploading = true;
      this.error = null;

      try {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('chatbotId', 'my_chatbot');
        formData.append('sessionId', 'session_' + Date.now());

        const response = await fetch('http://localhost:8080/api/attachments/upload', {
          method: 'POST',
          body: formData
        });

        const result = await response.json();
        this.uploadResult = result;
      } catch (err) {
        this.error = err.message;
      } finally {
        this.uploading = false;
      }
    }
  }
};
</script>
```

---

## Data Models

### AttachmentStorageResult (Upload Response)

```json
{
  "fileId": "file_chatbot_123_session_456_document_1707385649123",
  "fileName": "document.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "downloadUrl": "http://localhost:8080/api/attachments/download/file_...",
  "uploadedAt": 1707385649000,
  "status": "stored"
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| fileId | String | Unique identifier for the file |
| fileName | String | Original file name |
| mimeType | String | File MIME type |
| fileSize | Number | File size in bytes |
| downloadUrl | String | URL to download the file |
| uploadedAt | Number | Upload timestamp (milliseconds) |
| status | String | Always "stored" on success |

### FileMetadata (List/Metadata Response)

```json
{
  "fileId": "file_chatbot_123_session_456_document_1707385649123",
  "fileName": "document.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "uploadedAt": 1707385649000,
  "status": "stored",
  "formattedFileSize": "250.0 KB"
}
```

### Error Response

```json
{
  "success": false,
  "message": "Error description",
  "timestamp": 1707385649000
}
```

---

## Best Practices

### 1. Always Include chatbotId

```javascript
// ✅ Correct
fetch(`/api/attachments/download/${fileId}?chatbotId=my_bot`)

// ❌ Wrong
fetch(`/api/attachments/download/${fileId}`)
```

### 2. Store fileId, Not downloadUrl

```javascript
// ✅ Correct - Store fileId for future use
database.save({
  messageId: msg.id,
  attachmentFileId: uploadResult.fileId
});

// ❌ Wrong - URL might change
database.save({
  attachmentUrl: uploadResult.downloadUrl
});
```

### 3. Handle Errors Properly

```javascript
// ✅ Correct
try {
  const response = await fetch(url);
  if (!response.ok) {
    const error = await response.json();
    console.error(error.message);
  }
  const data = await response.json();
} catch (error) {
  console.error('Network error:', error);
}

// ❌ Wrong - No error handling
const data = await fetch(url).then(r => r.json());
```

### 4. Show Upload Progress

```javascript
// Use XMLHttpRequest or Fetch API with progress tracking
const xhr = new XMLHttpRequest();
xhr.upload.addEventListener('progress', (e) => {
  const percentComplete = (e.loaded / e.total) * 100;
  console.log('Upload progress:', percentComplete + '%');
});
```

---

## Environment Variables

Configure these in your frontend environment:

```javascript
// .env
REACT_APP_API_URL=http://localhost:8080
REACT_APP_CHATBOT_ID=chatbot_123
```

Usage:

```javascript
const API_URL = process.env.REACT_APP_API_URL;
const CHATBOT_ID = process.env.REACT_APP_CHATBOT_ID;

const response = await fetch(`${API_URL}/api/attachments/upload`, {
  method: 'POST',
  body: formData
});
```

---

## Support & Troubleshooting

### Q: File upload hangs
**A:** Check network connection and file size. Ensure chatbotId and sessionId are provided.

### Q: Download link doesn't work
**A:** Verify fileId and chatbotId are correct. Check if file hasn't been deleted.

### Q: CORS issues
**A:** Backend CORS is configured. Ensure requests come from allowed origins.

### Q: File size too large
**A:** Maximum file size is 15 MB. Split larger files or compress before upload.

---

## API Endpoints Summary

| Method | Endpoint | Purpose | Returns |
|--------|----------|---------|---------|
| POST | /api/attachments/upload | Upload file | fileId, downloadUrl |
| GET | /api/attachments/download/{fileId} | Download file | Binary file |
| GET | /api/attachments/metadata/{fileId} | Get metadata | FileMetadata |
| GET | /api/attachments/list/{chatbotId} | List files | FileMetadata[] |
| DELETE | /api/attachments/{fileId} | Delete file | Success message |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | Feb 10, 2026 | Initial release |

---

**Last Updated:** February 10, 2026  
**Status:** Production Ready  
**Support:** For issues, contact development team


