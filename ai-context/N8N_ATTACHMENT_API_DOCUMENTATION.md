# N8N Chat Widget API - Attachment Support Documentation

## Overview

This document provides comprehensive documentation for the N8N Chat Widget API with support for file attachments. The API allows users to send chat messages with optional file attachments to N8N workflows.

**Last Updated:** February 6, 2026  
**API Version:** 1.0  
**Status:** Production Ready

---

## Table of Contents

1. [Endpoints](#endpoints)
2. [Request/Response Format](#requestresponse-format)
3. [Field Reference](#field-reference)
4. [Supported MIME Types](#supported-mime-types)
5. [Examples](#examples)
6. [Error Handling](#error-handling)
7. [Attachment Management](#attachment-management)
8. [Best Practices](#best-practices)
9. [Troubleshooting](#troubleshooting)

---

## Endpoints

### 1. Anonymous Chat Endpoint

**Endpoint:** `POST /v1/api/n8n/anonymous/chat`

**Description:** Send a chat message without authentication. Supports optional file attachments.

**Headers:**
```
Content-Type: application/json
```

**Use Case:** Public chatbots, guest interactions, demo environments

---

### 2. Authenticated Chat Endpoint

**Endpoint:** `POST /v1/api/n8n/authenticated/chat`

**Description:** Send a chat message with authentication. Supports optional file attachments.

**Headers:**
```
Content-Type: application/json
Authorization: Bearer {token}
```

**Use Case:** Authenticated users, premium features, session tracking

---

### 3. Attachment Management Endpoints

#### List Attachments
```
GET /v1/api/n8n/attachments/{chatbotId}/{sessionId}
```

#### Get Attachment Metadata
```
GET /v1/api/n8n/attachments/{chatbotId}/{sessionId}/{fileName}
```

#### Delete Attachment
```
DELETE /v1/api/n8n/attachments/{chatbotId}/{sessionId}/{fileName}
```

#### Delete All Session Attachments
```
DELETE /v1/api/n8n/attachments/{chatbotId}/{sessionId}
```

#### Get Storage Statistics
```
GET /v1/api/n8n/attachments/stats/{chatbotId}/{sessionId}
```

---

## Request/Response Format

### Complete Request Example

```json
{
  "role": "user",
  "message": "Please analyze this document",
  "attachments": [
    {
      "name": "report.pdf",
      "type": "application/pdf",
      "size": 102400,
      "data": "JVBERi0xLjQKJeLjz9MNCjEgMCBvYmoKPDwKL1R5cGUgL0NhdGFsb2cKL1BhZ2VzIDIgMCBSCj4+CmVuZG9iag=="
    }
  ],
  "sessionId": "session_1707385649123_abc123xyz",
  "chatbotId": "chatbot_12345"
}
```

### Complete Response Example

```json
{
  "success": true,
  "result": "I've analyzed your PDF. Here are the key findings...",
  "status": "200",
  "timestamp": 1707385700000,
  "headers": {
    "content-type": "application/json",
    "attachment-count": "1",
    "has-attachments": "true"
  }
}
```

---

## Field Reference

### Request Fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `role` | string | ✓ | Must be `"user"` |
| `message` | string | ◐ | Chat message. Can be empty if attachments present |
| `attachments` | array | ◐ | Array of file objects. Can be empty array |
| `attachments[].name` | string | ✓ | Original filename (e.g., "document.pdf") |
| `attachments[].type` | string | ✓ | MIME type (e.g., "application/pdf") |
| `attachments[].size` | number | ✓ | File size in bytes (1 to 104,857,600) |
| `attachments[].data` | string | ✓ | Base64 encoded file content |
| `sessionId` | string | ✓ | Unique session identifier |
| `chatbotId` | string | ✓ | Unique chatbot identifier |
| `googleTokens` | object | ✗ | Optional OAuth tokens for Google services |

**Legend:**
- ✓ = Required
- ◐ = At least one required (message OR attachments)
- ✗ = Optional

### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | Request success status |
| `result` | string | Chat response from N8N workflow |
| `status` | string | HTTP status code |
| `timestamp` | number | Server timestamp (milliseconds) |
| `headers` | object | Response headers |
| `errorCode` | string | Error code (on failure) |
| `errorMessage` | string | Error message (on failure) |

---

## Supported MIME Types

The API supports the following MIME types for security:

### Documents
| MIME Type | Extension | Description |
|-----------|-----------|-------------|
| `text/plain` | .txt | Plain text files |
| `text/csv` | .csv | CSV spreadsheets |
| `application/json` | .json | JSON documents |
| `application/pdf` | .pdf | PDF documents |
| `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | .docx | Microsoft Word |
| `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | .xlsx | Microsoft Excel |
| `application/vnd.openxmlformats-officedocument.presentationml.presentation` | .pptx | Microsoft PowerPoint |

### Images
| MIME Type | Extension | Description |
|-----------|-----------|-------------|
| `image/jpeg` | .jpg, .jpeg | JPEG images |
| `image/png` | .png | PNG images |
| `image/gif` | .gif | GIF images |
| `image/webp` | .webp | WebP images |
| `image/svg+xml` | .svg | SVG vector images |

---

## Examples

### Example 1: Text-Only Message

**Request:**
```json
{
  "role": "user",
  "message": "What is the weather today?",
  "attachments": [],
  "sessionId": "session_123",
  "chatbotId": "bot_456"
}
```

**Response:**
```json
{
  "success": true,
  "result": "The weather today is sunny with a high of 75°F.",
  "status": "200",
  "timestamp": 1707385700000
}
```

---

### Example 2: Message with Single Attachment

**Request:**
```json
{
  "role": "user",
  "message": "Please review this document",
  "attachments": [
    {
      "name": "quarterly_report.pdf",
      "type": "application/pdf",
      "size": 256000,
      "data": "JVBERi0xLjQKJeLjz9MNCjEgMCBvYmoKPDwKL1R5cGUgL0NhdGFsb2cKL1BhZ2VzIDIgMCBSCj4+CmVuZG9iag=="
    }
  ],
  "sessionId": "session_user_12345",
  "chatbotId": "chatbot_finance_001"
}
```

**Response:**
```json
{
  "success": true,
  "result": "I've reviewed the quarterly report. Key findings: Revenue increased by 15%, Operating expenses decreased by 8%...",
  "status": "200",
  "timestamp": 1707385700000,
  "headers": {
    "attachment-count": "1",
    "has-attachments": "true"
  }
}
```

---

### Example 3: Message with Multiple Attachments

**Request:**
```json
{
  "role": "user",
  "message": "Compare these two spreadsheets",
  "attachments": [
    {
      "name": "Q1_Sales_2024.xlsx",
      "type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "size": 45000,
      "data": "UEsDBBQABgAIAAAAIQA..."
    },
    {
      "name": "Q2_Sales_2024.xlsx",
      "type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "size": 48000,
      "data": "UEsDBBQABgAIAAAAIQB..."
    }
  ],
  "sessionId": "session_analyst_789",
  "chatbotId": "chatbot_sales_analytics"
}
```

**Response:**
```json
{
  "success": true,
  "result": "Q1 vs Q2 Comparison: Q2 showed 12% growth in total sales...",
  "status": "200",
  "timestamp": 1707385700000,
  "headers": {
    "attachment-count": "2",
    "has-attachments": "true"
  }
}
```

---

### Example 4: File-Only Message (No Text)

**Request:**
```json
{
  "role": "user",
  "message": "",
  "attachments": [
    {
      "name": "document.pdf",
      "type": "application/pdf",
      "size": 100000,
      "data": "JVBERi0xLjQKJeLjz9MNCjEgMCBvYmoKPDwKL1R5cGUgL0NhdGFsb2cKL1BhZ2VzIDIgMCBSCj4+CmVuZG9iag=="
    }
  ],
  "sessionId": "session_456",
  "chatbotId": "bot_789"
}
```

**Response:**
```json
{
  "success": true,
  "result": "Document analyzed. I found 5 key sections...",
  "status": "200",
  "timestamp": 1707385700000
}
```

---

### Example 5: Image Attachment

**Request:**
```json
{
  "role": "user",
  "message": "What's in this image?",
  "attachments": [
    {
      "name": "screenshot.png",
      "type": "image/png",
      "size": 65000,
      "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    }
  ],
  "sessionId": "session_visual_001",
  "chatbotId": "chatbot_visual"
}
```

**Response:**
```json
{
  "success": true,
  "result": "This appears to be a screenshot showing a dashboard with...",
  "status": "200",
  "timestamp": 1707385700000
}
```

---

## cURL Examples

### Basic Text Message
```bash
curl -X POST http://api.example.com/v1/api/n8n/anonymous/chat \
  -H "Content-Type: application/json" \
  -d '{
    "role": "user",
    "message": "Hello!",
    "attachments": [],
    "sessionId": "session_123",
    "chatbotId": "bot_456"
  }'
```

### Message with Attachment
```bash
curl -X POST http://api.example.com/v1/api/n8n/anonymous/chat \
  -H "Content-Type: application/json" \
  -d '{
    "role": "user",
    "message": "Analyze this report",
    "attachments": [
      {
        "name": "report.pdf",
        "type": "application/pdf",
        "size": 102400,
        "data": "'$(base64 < report.pdf)'"
      }
    ],
    "sessionId": "session_123",
    "chatbotId": "bot_456"
  }'
```

### With Authentication
```bash
curl -X POST http://api.example.com/v1/api/n8n/authenticated/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "role": "user",
    "message": "Process this document",
    "attachments": [...],
    "sessionId": "session_123",
    "chatbotId": "bot_456"
  }'
```

---

## Error Handling

### HTTP Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Request processed successfully |
| 400 | Bad Request | Check request format and required fields |
| 401 | Unauthorized | Invalid or missing authentication token |
| 413 | Payload Too Large | File size exceeds maximum allowed |
| 500 | Internal Server Error | Server-side error, retry with exponential backoff |

### Error Response Format

```json
{
  "success": false,
  "errorCode": "INVALID_REQUEST",
  "errorMessage": "Attachment name is required",
  "timestamp": 1707385700000
}
```

### Common Error Codes

| Error Code | Meaning | Solution |
|-----------|---------|----------|
| `INVALID_REQUEST` | Malformed request | Check JSON syntax and required fields |
| `INVALID_MESSAGE` | Missing message or attachments | Provide at least message or attachments |
| `CHATBOT_NOT_FOUND` | ChatBot ID doesn't exist | Verify chatbotId is correct |
| `FILE_TOO_LARGE` | Exceeds max file size (100MB) | Compress or split the file |
| `INVALID_MIME_TYPE` | Unsupported file type | Use supported MIME types only |
| `INVALID_BASE64` | Base64 encoding error | Verify base64 data is properly encoded |
| `HTTP_ERROR` | Webhook communication failed | Check N8N webhook URL and status |
| `INTERNAL_ERROR` | Server error | Retry request, contact support if persists |

---

## Attachment Management

### List Attachments for a Session

**Request:**
```bash
curl -X GET http://api.example.com/v1/api/n8n/attachments/bot_123/session_456
```

**Response:**
```json
[
  {
    "name": "document.pdf",
    "type": "application/pdf",
    "size": 102400,
    "filePath": "/uploads/bot_123/session_456/document.pdf",
    "savedAt": 1707385600000,
    "formattedSize": "100.0 KB"
  },
  {
    "name": "spreadsheet.xlsx",
    "type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "size": 256000,
    "filePath": "/uploads/bot_123/session_456/spreadsheet.xlsx",
    "savedAt": 1707385605000,
    "formattedSize": "250.0 KB"
  }
]
```

### Get Storage Statistics

**Request:**
```bash
curl -X GET http://api.example.com/v1/api/n8n/attachments/stats/bot_123/session_456
```

**Response:**
```json
{
  "chatbotId": "bot_123",
  "sessionId": "session_456",
  "fileCount": 2,
  "totalSize": 358400,
  "formattedSize": "350.0 KB"
}
```

### Delete Attachment

**Request:**
```bash
curl -X DELETE http://api.example.com/v1/api/n8n/attachments/bot_123/session_456/document.pdf
```

**Response:**
```json
"Attachment deleted successfully"
```

### Delete All Session Attachments

**Request:**
```bash
curl -X DELETE http://api.example.com/v1/api/n8n/attachments/bot_123/session_456
```

**Response:**
```json
"Deleted 2 attachment(s)"
```

---

## Best Practices

### 1. Base64 Encoding

**Client-side encoding (Node.js):**
```javascript
const fs = require('fs');
const fileBuffer = fs.readFileSync('document.pdf');
const base64Data = fileBuffer.toString('base64');

const request = {
  role: 'user',
  message: 'Review this PDF',
  attachments: [{
    name: 'document.pdf',
    type: 'application/pdf',
    size: fileBuffer.length,
    data: base64Data
  }],
  sessionId: 'session_123',
  chatbotId: 'bot_456'
};
```

**Python encoding:**
```python
import base64

with open('document.pdf', 'rb') as f:
    file_data = f.read()
    base64_data = base64.b64encode(file_data).decode('utf-8')

payload = {
    'role': 'user',
    'message': 'Review this PDF',
    'attachments': [{
        'name': 'document.pdf',
        'type': 'application/pdf',
        'size': len(file_data),
        'data': base64_data
    }],
    'sessionId': 'session_123',
    'chatbotId': 'bot_456'
}
```

### 2. File Size Optimization

- **Before encoding:** Maximum 100 MB
- **After base64:** ~33% larger (100 MB → ~133 MB)
- **Recommendation:** Compress large files before uploading
  - PDF: Use compression tools
  - Images: Convert to WebP or compress with ImageMagick
  - Documents: Archive with ZIP

### 3. Session Management

```javascript
// Generate unique session IDs
const sessionId = 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);

// Example: session_1707385649123_abc123xyz
```

### 4. Error Handling

```javascript
const response = await fetch('/v1/api/n8n/anonymous/chat', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(request)
});

const data = await response.json();

if (!data.success) {
  console.error('Error:', data.errorCode, data.errorMessage);
  // Handle error appropriately
}
```

### 5. Performance Optimization

- Batch process files when possible
- Implement request timeouts (30-60 seconds)
- Use connection pooling for multiple requests
- Monitor storage usage regularly

---

## Troubleshooting

### Issue: Base64 Encoding Error
**Symptom:** `INVALID_BASE64` error  
**Solution:**
- Verify file is readable
- Check base64 encoding tool
- Ensure no trailing whitespace
- Test with online base64 validator

### Issue: File Not Stored
**Symptom:** Attachment not found in session  
**Solution:**
- Check file permissions
- Verify disk space available
- Check `file.upload.path` configuration
- Review server logs

### Issue: Large File Timeout
**Symptom:** Request timeout with large files  
**Solution:**
- Compress file before uploading
- Increase request timeout (client-side)
- Split into multiple requests
- Use chunked upload (if available)

### Issue: Unsupported MIME Type
**Symptom:** `INVALID_MIME_TYPE` error  
**Solution:**
- Verify correct MIME type in request
- Use supported types only
- Contact support for new MIME types
- Convert file to supported format

---

## Security Considerations

1. **File Validation**
   - All files are validated for MIME type
   - Filenames are sanitized to prevent directory traversal
   - Files are stored separately by chatbot and session

2. **Access Control**
   - Session-based isolation
   - Chatbot ownership validation
   - Optional authentication layer

3. **Data Protection**
   - Base64 encoding in transit
   - Secure storage on server
   - Automatic cleanup policies (configurable)

---

## Rate Limiting (Recommended)

- **10 requests per second** per session
- **100 MB total files** per session per day
- **5 concurrent uploads** per session
- **404 file size limit** per request

---

## Storage Configuration

### Environment Variables

```properties
# File upload configuration
file.upload.path=uploads
file.max.size=104857600  # 100 MB in bytes

# N8N webhook configuration
n8n.webhook.knowledgebase.chat.url=https://your-n8n-instance.com/webhook/chat

# Optional: Storage cleanup
file.cleanup.enabled=true
file.cleanup.days=30  # Delete files older than 30 days
```

### Storage Directory Structure

```
uploads/
  {chatbotId}/
    {sessionId}/
      file1.pdf
      file2.txt
      file3.xlsx
      ...
```

---

## Support & Resources

- **Documentation:** See `API_REQUEST_STRUCTURE.md`
- **Examples:** See `BACKEND_IMPLEMENTATION_EXAMPLE.md`
- **Integration Guide:** See `CHATWIDGET_INTEGRATION_GUIDE.md`
- **Issues:** Contact API support team
- **Status Page:** https://status.example.com

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-06 | Initial release with attachment support |

---

**Document Date:** February 6, 2026  
**Last Updated:** February 6, 2026

For the most up-to-date information, visit the API documentation portal.

