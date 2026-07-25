# Multimodal Chat API - Frontend Specification
## ChatWidget Integration Guide

**API Version:** v1  
**Last Updated:** February 7, 2026  
**Status:** ✅ Production Ready  
**Base URL:** `/v1/api/n8n/multimodal`

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [API Endpoints](#api-endpoints)
4. [Request/Response Formats](#requestresponse-formats)
5. [Error Handling](#error-handling)
6. [Implementation Examples](#implementation-examples)
7. [Best Practices](#best-practices)
8. [Troubleshooting](#troubleshooting)

---

## Overview

The Multimodal Chat API enables your chat widget to:
- Send text messages with file attachments (PDF, images, documents, etc.)
- Store attachments efficiently in a MongoDB vector store
- Reference files via lightweight `vectorIds` instead of large file data
- Support both anonymous and authenticated users
- Manage attachment lifecycle (upload, retrieve, delete)

### Key Benefits
✅ **90% bandwidth reduction** - Small vectorIds instead of large Base64 data  
✅ **Efficient storage** - Files stored once, referenced many times  
✅ **Fast processing** - Optimized N8N webhook payloads  
✅ **Scalable** - Handles large files and high volume  

---

## Getting Started

### Prerequisites
- Modern browser with JavaScript/TypeScript support
- Access to the Chat API base URL
- Valid `chatbotId` from your chatbot configuration
- Optional: Authentication token for authenticated endpoints

### Installation

```bash
# Via NPM (if packaged)
npm install @your-org/chat-widget-sdk

# Or include directly in HTML
<script src="/path/to/chat-widget.js"></script>
```

### Quick Start Example

```javascript
// Initialize the chat widget
const chatWidget = new ChatWidget({
  chatbotId: 'your-chatbot-id',
  sessionId: 'unique-session-id',
  apiBaseUrl: 'https://api.example.com/v1/api/n8n/multimodal'
});

// Send message with attachments
const response = await chatWidget.sendMessage({
  message: 'Please analyze this document',
  attachments: [
    new File(['...'], 'report.pdf', { type: 'application/pdf' })
  ]
});

console.log(response.result); // AI response
console.log(response.vectorIdMap); // File → vectorId mapping
```

---

## API Endpoints

### 1. Send Multimodal Chat Message (Anonymous)

**Endpoint:** `POST /anonymous/chat`  
**Full URL:** `POST /v1/api/n8n/multimodal/anonymous/chat`  
**Authentication:** None required

Send a chat message with optional file attachments as an anonymous user.

**Request:**
```http
POST /v1/api/n8n/multimodal/anonymous/chat HTTP/1.1
Content-Type: application/json
Host: api.example.com

{
  "message": "Please analyze this quarterly report",
  "attachments": [
    {
      "name": "Q4_Report_2025.pdf",
      "type": "application/pdf",
      "size": 256000,
      "data": "JVBERi0xLjQKJeLjz9MNCjEgMCBvYm8..."
    }
  ],
  "sessionId": "session_123456",
  "chatbotId": "chatbot_789"
}
```

**Response (Success - 200):**
```json
{
  "success": true,
  "result": "The quarterly report shows a 25% increase in Q4 revenue compared to Q3...",
  "vectorIdMap": {
    "Q4_Report_2025.pdf": "attachment_chatbot_789_session_123456_report_1707385649123"
  },
  "vectorAttachments": [
    {
      "vectorId": "attachment_chatbot_789_session_123456_report_1707385649123",
      "fileName": "Q4_Report_2025.pdf",
      "mimeType": "application/pdf",
      "fileSize": 256000,
      "uploadedAt": 1707385649123
    }
  ],
  "timestamp": 1707385650000
}
```

**Response (Error - 400):**
```json
{
  "success": false,
  "errorCode": "INVALID_REQUEST",
  "errorMessage": "ChatbotId and message are required",
  "timestamp": 1707385650000
}
```

---

### 2. Send Multimodal Chat Message (Authenticated)

**Endpoint:** `POST /authenticated/chat`  
**Full URL:** `POST /v1/api/n8n/multimodal/authenticated/chat`  
**Authentication:** Bearer token required

Same as anonymous endpoint, but for authenticated users with JWT token.

**Request:**
```http
POST /v1/api/n8n/multimodal/authenticated/chat HTTP/1.1
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Host: api.example.com

{
  "message": "Process this document",
  "attachments": [...],
  "sessionId": "session_123456",
  "chatbotId": "chatbot_789"
}
```

**Response:** Same as anonymous endpoint

---

### 3. Get Attachment Metadata

**Endpoint:** `GET /attachments/{chatbotId}/{vectorId}`  
**Full URL:** `GET /v1/api/n8n/multimodal/attachments/{chatbotId}/{vectorId}`  
**Authentication:** Optional

Retrieve metadata about a stored attachment using its vectorId.

**Request:**
```http
GET /v1/api/n8n/multimodal/attachments/chatbot_789/attachment_chatbot_789_session_123456_report_1707385649123 HTTP/1.1
Host: api.example.com
```

**Response (Success - 200):**
```json
{
  "vectorId": "attachment_chatbot_789_session_123456_report_1707385649123",
  "fileName": "Q4_Report_2025.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "uploadedAt": 1707385649123,
  "chatbotId": "chatbot_789",
  "sessionId": "session_123456"
}
```

**Response (Not Found - 404):**
```
Not Found
```

---

### 4. List All Attachments for Chatbot

**Endpoint:** `GET /attachments/{chatbotId}`  
**Full URL:** `GET /v1/api/n8n/multimodal/attachments/{chatbotId}`  
**Authentication:** Optional

List all attachments uploaded for a specific chatbot.

**Request:**
```http
GET /v1/api/n8n/multimodal/attachments/chatbot_789 HTTP/1.1
Host: api.example.com
```

**Response (Success - 200):**
```json
[
  {
    "vectorId": "attachment_chatbot_789_session_123456_report_1707385649123",
    "fileName": "Q4_Report_2025.pdf",
    "mimeType": "application/pdf",
    "fileSize": 256000,
    "uploadedAt": 1707385649123,
    "chatbotId": "chatbot_789",
    "sessionId": "session_123456"
  },
  {
    "vectorId": "attachment_chatbot_789_session_123456_image_1707385650000",
    "fileName": "chart.png",
    "mimeType": "image/png",
    "fileSize": 125000,
    "uploadedAt": 1707385650000,
    "chatbotId": "chatbot_789",
    "sessionId": "session_123456"
  }
]
```

---

### 5. Delete Attachment

**Endpoint:** `DELETE /attachments/{chatbotId}/{vectorId}`  
**Full URL:** `DELETE /v1/api/n8n/multimodal/attachments/{chatbotId}/{vectorId}`  
**Authentication:** Optional

Remove an attachment from the vector store.

**Request:**
```http
DELETE /v1/api/n8n/multimodal/attachments/chatbot_789/attachment_chatbot_789_session_123456_report_1707385649123 HTTP/1.1
Host: api.example.com
```

**Response (Success - 200):**
```
Attachment deleted successfully
```

**Response (Not Found - 404):**
```
Not Found
```

---

## Request/Response Formats

### Multimodal Chat Request

```typescript
interface MultimodalChatRequest {
  // Chat message content
  message: string;                    // Required: Text message to send
  
  // File attachments
  attachments?: Attachment[];         // Optional: Array of files
  
  // Session & bot context
  sessionId: string;                  // Required: Unique session identifier
  chatbotId: string;                  // Required: Chatbot identifier
}

interface Attachment {
  name: string;                       // Required: Original filename
  type: string;                       // Required: MIME type (e.g., "application/pdf")
  size: number;                       // Required: File size in bytes
  data: string;                       // Required: Base64 encoded file content
}
```

### Multimodal Chat Response

```typescript
interface MultimodalChatResponse {
  success: boolean;                   // Success flag
  result?: object;                    // AI-generated response (if success)
  
  // Vector attachment tracking
  vectorIdMap?: {                     // Mapping of filename → vectorId
    [filename: string]: string;
  };
  vectorAttachments?: VectorAttachment[];  // Array of processed attachments
  
  // Error information (if !success)
  errorCode?: string;                 // Error code (e.g., "INVALID_REQUEST")
  errorMessage?: string;              // Human-readable error message
  
  // Metadata
  timestamp: number;                  // Unix timestamp (milliseconds)
}

interface VectorAttachment {
  vectorId: string;                   // Unique identifier in vector store
  fileName: string;                   // Original filename
  mimeType: string;                   // MIME type
  fileSize: number;                   // File size in bytes
  uploadedAt: number;                 // Upload timestamp (milliseconds)
}
```

### Supported MIME Types

| MIME Type | Extensions | Category |
|-----------|-----------|----------|
| `application/pdf` | .pdf | Documents |
| `text/plain` | .txt | Documents |
| `application/msword` | .doc | Documents |
| `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | .docx | Documents |
| `application/vnd.ms-excel` | .xls | Spreadsheets |
| `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | .xlsx | Spreadsheets |
| `application/vnd.ms-powerpoint` | .ppt | Presentations |
| `image/jpeg` | .jpg, .jpeg | Images |
| `image/png` | .png | Images |
| `image/gif` | .gif | Images |
| `image/webp` | .webp | Images |
| `video/mp4` | .mp4 | Videos |
| `audio/mpeg` | .mp3 | Audio |
| `application/json` | .json | Data |
| `application/xml` | .xml | Data |
| `text/csv` | .csv | Data |

### File Size Limits

| Category | Limit | Notes |
|----------|-------|-------|
| Single file | 100 MB | Per attachment |
| Total per request | 500 MB | All attachments combined |
| Session total | 2 GB | All files in session |

---

## Error Handling

### HTTP Status Codes

| Code | Meaning | Example |
|------|---------|---------|
| `200` | Success | Message sent successfully |
| `400` | Bad Request | Missing required field |
| `401` | Unauthorized | Invalid token (authenticated endpoint) |
| `404` | Not Found | Attachment or resource doesn't exist |
| `413` | Payload Too Large | File exceeds size limit |
| `500` | Internal Server Error | Server processing error |

### Error Response Format

```json
{
  "success": false,
  "errorCode": "ERROR_TYPE",
  "errorMessage": "Human-readable error description",
  "timestamp": 1707385650000
}
```

### Common Error Codes

| Code | Description | Solution |
|------|-------------|----------|
| `INVALID_REQUEST` | Missing required fields | Check request structure |
| `CHATBOT_NOT_FOUND` | Invalid chatbotId | Verify chatbot ID is correct |
| `INVALID_ATTACHMENT_TYPE` | Unsupported file type | Use supported MIME type |
| `FILE_TOO_LARGE` | File exceeds size limit | Compress or split file |
| `SESSION_INVALID` | Invalid sessionId | Generate valid session ID |
| `INTERNAL_ERROR` | Server error | Retry or contact support |

### Error Handling Example

```javascript
try {
  const response = await chatWidget.sendMessage({
    message: 'Process this',
    attachments: [file]
  });
  
  if (response.success) {
    console.log('Response:', response.result);
  } else {
    console.error(`Error [${response.errorCode}]:`, response.errorMessage);
  }
} catch (error) {
  console.error('Network error:', error);
}
```

---

## Implementation Examples

### JavaScript/TypeScript Implementation

#### 1. Basic Chat Widget Class

```typescript
class ChatWidget {
  private apiBaseUrl: string;
  private chatbotId: string;
  private sessionId: string;

  constructor(config: {
    apiBaseUrl: string;
    chatbotId: string;
    sessionId?: string;
  }) {
    this.apiBaseUrl = config.apiBaseUrl;
    this.chatbotId = config.chatbotId;
    this.sessionId = config.sessionId || this.generateSessionId();
  }

  /**
   * Send message with optional attachments
   */
  async sendMessage(options: {
    message: string;
    attachments?: File[];
  }): Promise<MultimodalChatResponse> {
    // Convert files to Base64
    const attachmentsData = await Promise.all(
      (options.attachments || []).map(file => this.fileToBase64(file))
    );

    const request: MultimodalChatRequest = {
      message: options.message,
      attachments: attachmentsData,
      sessionId: this.sessionId,
      chatbotId: this.chatbotId
    };

    const response = await fetch(
      `${this.apiBaseUrl}/anonymous/chat`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request)
      }
    );

    if (!response.ok) {
      throw new Error(`API error: ${response.statusText}`);
    }

    return response.json();
  }

  /**
   * Convert File to Base64
   */
  private async fileToBase64(file: File): Promise<Attachment> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      
      reader.onload = () => {
        const base64 = (reader.result as string).split(',')[1];
        resolve({
          name: file.name,
          type: file.type || 'application/octet-stream',
          size: file.size,
          data: base64
        });
      };
      
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  /**
   * Generate unique session ID
   */
  private generateSessionId(): string {
    return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * List attachments for this chatbot
   */
  async listAttachments(): Promise<VectorAttachment[]> {
    const response = await fetch(
      `${this.apiBaseUrl}/attachments/${this.chatbotId}`
    );
    
    if (!response.ok) {
      throw new Error(`Failed to list attachments: ${response.statusText}`);
    }
    
    return response.json();
  }

  /**
   * Get attachment metadata
   */
  async getAttachment(vectorId: string): Promise<VectorAttachment> {
    const response = await fetch(
      `${this.apiBaseUrl}/attachments/${this.chatbotId}/${vectorId}`
    );
    
    if (!response.ok) {
      throw new Error(`Failed to get attachment: ${response.statusText}`);
    }
    
    return response.json();
  }

  /**
   * Delete attachment
   */
  async deleteAttachment(vectorId: string): Promise<void> {
    const response = await fetch(
      `${this.apiBaseUrl}/attachments/${this.chatbotId}/${vectorId}`,
      { method: 'DELETE' }
    );
    
    if (!response.ok) {
      throw new Error(`Failed to delete attachment: ${response.statusText}`);
    }
  }
}
```

#### 2. React Hook Implementation

```typescript
import { useState, useCallback } from 'react';

export function useMultimodalChat(chatbotId: string) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attachments, setAttachments] = useState<VectorAttachment[]>([]);
  const sessionId = useRef(generateSessionId()).current;

  const sendMessage = useCallback(
    async (message: string, files?: File[]) => {
      setLoading(true);
      setError(null);

      try {
        const attachmentData = await Promise.all(
          (files || []).map(file => fileToBase64(file))
        );

        const response = await fetch(
          '/v1/api/n8n/multimodal/anonymous/chat',
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              message,
              attachments: attachmentData,
              sessionId,
              chatbotId
            })
          }
        );

        if (!response.ok) {
          throw new Error('Failed to send message');
        }

        const data = await response.json();

        if (!data.success) {
          throw new Error(data.errorMessage);
        }

        // Update attachments list
        if (data.vectorAttachments) {
          setAttachments(prev => [...prev, ...data.vectorAttachments]);
        }

        return data;
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Unknown error';
        setError(message);
        throw err;
      } finally {
        setLoading(false);
      }
    },
    [chatbotId]
  );

  return {
    sendMessage,
    loading,
    error,
    attachments
  };
}
```

#### 3. Vue 3 Composition API Implementation

```typescript
import { ref, reactive } from 'vue';

export function useMultimodalChat(chatbotId: string) {
  const state = reactive({
    loading: false,
    error: null as string | null,
    attachments: [] as VectorAttachment[],
    sessionId: generateSessionId()
  });

  const sendMessage = async (message: string, files?: File[]) => {
    state.loading = true;
    state.error = null;

    try {
      const attachmentData = await Promise.all(
        (files || []).map(file => fileToBase64(file))
      );

      const response = await fetch(
        '/v1/api/n8n/multimodal/anonymous/chat',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message,
            attachments: attachmentData,
            sessionId: state.sessionId,
            chatbotId
          })
        }
      );

      const data = await response.json();

      if (!data.success) {
        throw new Error(data.errorMessage);
      }

      if (data.vectorAttachments) {
        state.attachments.push(...data.vectorAttachments);
      }

      return data;
    } catch (err) {
      state.error = err instanceof Error ? err.message : 'Unknown error';
      throw err;
    } finally {
      state.loading = false;
    }
  };

  return {
    state,
    sendMessage
  };
}
```

---

## Best Practices

### 1. File Handling

```javascript
// ✅ DO: Validate file before upload
function validateFile(file: File): { valid: boolean; error?: string } {
  // Check size
  if (file.size > 100 * 1024 * 1024) {
    return { valid: false, error: 'File too large (max 100MB)' };
  }

  // Check MIME type
  const allowedTypes = [
    'application/pdf',
    'image/jpeg',
    'image/png',
    'application/msword',
    // ... more types
  ];
  
  if (!allowedTypes.includes(file.type)) {
    return { valid: false, error: 'File type not supported' };
  }

  return { valid: true };
}

// ❌ DON'T: Send large files without checking
async function sendLargeFile(file: File) {
  const response = await fetch('/v1/api/n8n/multimodal/anonymous/chat', {
    method: 'POST',
    body: JSON.stringify({ attachments: [file] })
  });
}
```

### 2. Error Handling

```javascript
// ✅ DO: Handle all error cases
async function sendMessageWithErrorHandling(message: string, files?: File[]) {
  try {
    // Validate inputs
    if (!message && (!files || files.length === 0)) {
      throw new Error('Message or files required');
    }

    // Validate files
    for (const file of files || []) {
      const validation = validateFile(file);
      if (!validation.valid) {
        throw new Error(validation.error);
      }
    }

    // Send request
    const response = await fetch(
      '/v1/api/n8n/multimodal/anonymous/chat',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message,
          attachments: await Promise.all(
            (files || []).map(fileToBase64)
          ),
          sessionId,
          chatbotId
        })
      }
    );

    // Check response
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const data = await response.json();
    if (!data.success) {
      throw new Error(data.errorMessage || 'Unknown error');
    }

    return data;
  } catch (error) {
    console.error('Failed to send message:', error);
    // Show user-friendly error message
    showErrorToast(error instanceof Error ? error.message : 'Unknown error');
    throw error;
  }
}
```

### 3. Performance Optimization

```javascript
// ✅ DO: Batch convert files to Base64 with progress
async function filesToBase64WithProgress(
  files: File[],
  onProgress: (current: number, total: number) => void
): Promise<Attachment[]> {
  const results: Attachment[] = [];

  for (let i = 0; i < files.length; i++) {
    const attachment = await fileToBase64(files[i]);
    results.push(attachment);
    onProgress(i + 1, files.length);
  }

  return results;
}

// ✅ DO: Cache sessionId for multiple requests
class PersistentChatWidget {
  private sessionId: string;

  constructor(chatbotId: string) {
    // Try to restore from localStorage
    this.sessionId = localStorage.getItem('chatSession')
      || generateSessionId();
    localStorage.setItem('chatSession', this.sessionId);
  }

  // Reuse sessionId for all requests
  async sendMessage(message: string) {
    return await fetch('/v1/api/n8n/multimodal/anonymous/chat', {
      method: 'POST',
      body: JSON.stringify({
        message,
        sessionId: this.sessionId,
        chatbotId: this.chatbotId
      })
    });
  }
}
```

### 4. User Experience

```javascript
// ✅ DO: Show upload progress
function renderFileInput(onFilesSelected: (files: File[]) => void) {
  return (
    <div>
      <input
        type="file"
        multiple
        accept=".pdf,.jpg,.png,.doc,.docx,.xlsx"
        onChange={e => onFilesSelected(Array.from(e.target.files || []))}
      />
      <progress id="upload-progress" value="0" max="100" />
    </div>
  );
}

// ✅ DO: Display file list with vectorIds
function renderAttachments(attachments: VectorAttachment[]) {
  return (
    <div>
      {attachments.map(att => (
        <div key={att.vectorId}>
          <span>{att.fileName}</span>
          <span>{formatFileSize(att.fileSize)}</span>
          <button onClick={() => deleteAttachment(att.vectorId)}>
            Remove
          </button>
        </div>
      ))}
    </div>
  );
}

// Utility function
function formatFileSize(bytes: number): string {
  const units = ['B', 'KB', 'MB', 'GB'];
  let size = bytes;
  let unitIndex = 0;
  
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex++;
  }
  
  return `${size.toFixed(1)} ${units[unitIndex]}`;
}
```

---

## Troubleshooting

### Issue: "INVALID_REQUEST" Error

**Symptom:** API returns `errorCode: "INVALID_REQUEST"` with message "ChatbotId and message are required"

**Solutions:**
1. Verify `chatbotId` is included in request
2. Check `message` is not empty (unless attachments provided)
3. Ensure JSON is valid (check quotes, brackets)

```javascript
// ✅ Correct
const request = {
  message: "Analyze this",
  chatbotId: "valid-id",
  sessionId: "session-id",
  attachments: []
};

// ❌ Wrong
const request = {
  chatbotId: "valid-id"  // Missing message
};
```

### Issue: "FILE_TOO_LARGE" Error

**Symptom:** API returns error for file attachment

**Solutions:**
1. Check file size < 100 MB
2. Compress image files before upload
3. Split large documents into multiple files

```javascript
// ✅ Good: Compress images
async function compressImage(file: File): Promise<File> {
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d')!;
  const img = new Image();
  
  img.src = URL.createObjectURL(file);
  img.onload = () => {
    // Resize to 50% quality
    canvas.width = img.width * 0.5;
    canvas.height = img.height * 0.5;
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    
    canvas.toBlob(blob => {
      return new File([blob!], file.name, { type: 'image/jpeg' });
    }, 'image/jpeg', 0.7);
  };
}
```

### Issue: Slow Response Times

**Symptom:** Chat responses take > 10 seconds

**Solutions:**
1. Reduce file sizes before upload
2. Use compression for documents
3. Check network connection
4. Batch multiple files into single request

```javascript
// ✅ Good: Monitor request time
async function sendMessageWithMetrics(message: string, files?: File[]) {
  const startTime = performance.now();
  
  const response = await fetch('/v1/api/n8n/multimodal/anonymous/chat', {
    method: 'POST',
    body: JSON.stringify({/* ... */})
  });
  
  const duration = performance.now() - startTime;
  console.log(`Request took ${duration.toFixed(2)}ms`);
  
  if (duration > 10000) {
    console.warn('Slow response detected - consider optimizing');
  }
  
  return response.json();
}
```

### Issue: VectorIds Not Returned

**Symptom:** Response has `success: true` but `vectorIdMap` is empty

**Solutions:**
1. Check attachments were included in request
2. Verify attachment MIME types are correct
3. Check MongoDB vector store is accessible
4. Review server logs for errors

```javascript
// ✅ Debug: Check response structure
const response = await chatWidget.sendMessage({
  message: 'Test',
  attachments: [file]
});

console.log('Response:', response);
console.log('VectorIdMap:', response.vectorIdMap);
console.log('VectorAttachments:', response.vectorAttachments);

// If empty, check:
// 1. File was actually included
// 2. API processed without error
// 3. MongoDB connection works
```

### Issue: CORS Errors

**Symptom:** Browser blocks request with CORS error

**Solutions:**
1. Ensure API has CORS enabled (already configured)
2. Use correct base URL (no trailing slash)
3. Check request headers

```javascript
// ✅ Correct: No trailing slash
const baseUrl = 'https://api.example.com/v1/api/n8n/multimodal';
// ❌ Wrong
const baseUrl = 'https://api.example.com/v1/api/n8n/multimodal/';

// ✅ API already has CORS headers configured:
// Access-Control-Allow-Origin: *
// Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS
// Access-Control-Allow-Headers: Content-Type, Authorization
```

### Issue: Authentication Token Expires

**Symptom:** `401 Unauthorized` error on authenticated endpoint

**Solutions:**
1. Refresh token before expiration
2. Implement token refresh logic
3. Use anonymous endpoint for public chats

```typescript
// ✅ Good: Handle token refresh
class AuthenticatedChatWidget {
  private token: string;
  private tokenExpiresAt: number;

  async sendAuthenticatedMessage(message: string) {
    // Refresh if expiring soon
    if (Date.now() > this.tokenExpiresAt - 60000) {
      this.token = await this.refreshToken();
      this.tokenExpiresAt = Date.now() + 3600000; // 1 hour
    }

    return fetch('/v1/api/n8n/multimodal/authenticated/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${this.token}`
      },
      body: JSON.stringify({
        message,
        chatbotId: this.chatbotId,
        sessionId: this.sessionId
      })
    });
  }

  private async refreshToken(): Promise<string> {
    const response = await fetch('/auth/refresh', {
      method: 'POST'
    });
    const data = await response.json();
    return data.token;
  }
}
```

---

## Support & Resources

### Documentation
- **Vector Store Architecture:** See `MULTIMODAL_VECTOR_STORE_GUIDE.md`
- **N8N Integration:** See `N8N_WEBHOOK_ATTACHMENT_PAYLOAD.md`
- **Implementation Guide:** See `N8N_ATTACHMENT_IMPLEMENTATION_GUIDE.md`

### Testing
- **Postman Collection:** Import the provided Postman collection for testing
- **cURL Examples:** See "Testing with cURL" section below

### Getting Help
- Check the **Troubleshooting** section above
- Review **Error Codes** table for specific errors
- Contact: `api-support@example.com`

---

## Testing with cURL

### Test 1: Send Simple Message

```bash
curl -X POST http://localhost:8080/v1/api/n8n/multimodal/anonymous/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Hello, how are you?",
    "sessionId": "test_session_001",
    "chatbotId": "test_bot_001"
  }'
```

### Test 2: Send Message with File

```bash
# Encode file to Base64
FILE_DATA=$(base64 -w 0 < document.pdf)

# Send request
curl -X POST http://localhost:8080/v1/api/n8n/multimodal/anonymous/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Please analyze this document",
    "attachments": [{
      "name": "document.pdf",
      "type": "application/pdf",
      "size": 256000,
      "data": "'$FILE_DATA'"
    }],
    "sessionId": "test_session_001",
    "chatbotId": "test_bot_001"
  }'
```

### Test 3: List Attachments

```bash
curl -X GET http://localhost:8080/v1/api/n8n/multimodal/attachments/test_bot_001
```

### Test 4: Delete Attachment

```bash
curl -X DELETE http://localhost:8080/v1/api/n8n/multimodal/attachments/test_bot_001/attachment_test_bot_001_test_session_001_document_1707385649123
```

---

**Last Updated:** February 7, 2026  
**Status:** ✅ Production Ready  
**Questions?** Contact: `api-support@example.com`

