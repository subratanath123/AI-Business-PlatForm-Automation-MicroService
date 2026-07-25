# 🚀 Frontend Implementation Guide - Updated API

**Last Updated:** February 10, 2026  
**Status:** ✅ Ready for Integration

---

## Quick Summary

The multimodal chat API now uses a **two-step process**:

1. **Upload file** → Get `fileId`
2. **Send chat message** → Include `fileId` in request

No more raw file binary data in chat requests!

---

## Step 1: Upload File

### Endpoint
```
POST /api/attachments/upload
```

### Request
```javascript
const formData = new FormData();
formData.append('file', file);  // File object from input
formData.append('chatbotId', 'your-chatbot-id');
formData.append('sessionId', 'session-id-here');

const response = await fetch('http://localhost:8080/api/attachments/upload', {
  method: 'POST',
  body: formData
});

const result = await response.json();
```

### Response
```json
{
  "fileId": "file_698576e4d5fd040c84aed7d8_session_...",
  "fileName": "image.png",
  "mimeType": "image/png",
  "fileSize": 226585,
  "downloadUrl": "http://localhost:8080/api/attachments/download/..."
}
```

**Save the `fileId` for the next step!**

---

## Step 2: Send Chat Message with File Reference

### Endpoint (NEW!)
```
POST /v1/api/n8n/multimodal/anonymous/chat
```

### Request Format
```javascript
const chatRequest = {
  "role": "user",
  "message": "Analyze this image",
  "chatbotId": "698576e4d5fd040c84aed7d8",
  "sessionId": "session_1770743703337_lax2egqzx",
  "fileAttachments": [
    {
      "fileId": "file_698576e4d5fd040c84aed7d8_...",  // ← Use fileId from Step 1
      "fileName": "image.png",
      "mimeType": "image/png",
      "fileSize": 226585,
      "downloadUrl": "http://localhost:8080/api/attachments/download/..."
    }
  ]
};

const response = await fetch('http://localhost:8080/v1/api/n8n/multimodal/anonymous/chat', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(chatRequest)
});

const result = await response.json();
```

### Response
```json
{
  "success": true,
  "result": "I can see a screenshot showing...",
  "vectorIdMap": {
    "image.png": "file_698576e4d5fd040c84aed7d8_..."
  },
  "vectorAttachments": [
    {
      "vectorId": "file_698576e4d5fd040c84aed7d8_...",
      "fileName": "image.png",
      "mimeType": "image/png",
      "fileSize": 226585,
      "uploadedAt": 1770745423910
    }
  ]
}
```

---

## Complete JavaScript Example

```javascript
// Step 1: Upload file
async function uploadFile(file, chatbotId, sessionId) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('chatbotId', chatbotId);
  formData.append('sessionId', sessionId);

  const response = await fetch('http://localhost:8080/api/attachments/upload', {
    method: 'POST',
    body: formData
  });

  return await response.json();  // Returns { fileId, fileName, ... }
}

// Step 2: Send chat message with file
async function sendChatWithFile(message, fileAttachment, chatbotId, sessionId) {
  const chatRequest = {
    role: "user",
    message: message,
    chatbotId: chatbotId,
    sessionId: sessionId,
    fileAttachments: [fileAttachment]  // Use response from Step 1
  };

  const response = await fetch('http://localhost:8080/v1/api/n8n/multimodal/anonymous/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(chatRequest)
  });

  return await response.json();
}

// Usage
const file = document.getElementById('fileInput').files[0];
const uploadResult = await uploadFile(file, 'bot-123', 'session-456');
const chatResult = await sendChatWithFile('Analyze this', uploadResult, 'bot-123', 'session-456');
console.log('AI Response:', chatResult.result);
```

---

## React Example

```jsx
import { useState } from 'react';

export function ChatWithAttachments() {
  const [message, setMessage] = useState('');
  const [fileAttachments, setFileAttachments] = useState([]);
  const [response, setResponse] = useState(null);

  const handleFileUpload = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    // Step 1: Upload file
    const formData = new FormData();
    formData.append('file', file);
    formData.append('chatbotId', 'bot-123');
    formData.append('sessionId', 'session-456');

    const uploadRes = await fetch('http://localhost:8080/api/attachments/upload', {
      method: 'POST',
      body: formData
    });

    const uploadResult = await uploadRes.json();
    setFileAttachments([...fileAttachments, uploadResult]);
  };

  const handleSendMessage = async () => {
    // Step 2: Send message with attachments
    const chatRequest = {
      role: 'user',
      message: message,
      chatbotId: 'bot-123',
      sessionId: 'session-456',
      fileAttachments: fileAttachments
    };

    const chatRes = await fetch('http://localhost:8080/v1/api/n8n/multimodal/anonymous/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(chatRequest)
    });

    const chatResult = await chatRes.json();
    setResponse(chatResult);
    setMessage('');
    setFileAttachments([]);
  };

  return (
    <div>
      <input type="file" onChange={handleFileUpload} />
      <textarea value={message} onChange={(e) => setMessage(e.target.value)} />
      <button onClick={handleSendMessage}>Send</button>
      {response && <p>AI: {response.result}</p>}
    </div>
  );
}
```

---

## Key Points

✅ **Upload First** - Always upload file before sending chat
✅ **Use fileId** - Pass fileId, not raw binary
✅ **Smaller Requests** - ~99% bandwidth reduction
✅ **Async Processing** - Parallel file handling
✅ **Better Performance** - Faster responses

---

## Endpoint Reference

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/attachments/upload` | Upload file, get fileId |
| `POST` | `/v1/api/n8n/multimodal/anonymous/chat` | Send message with fileId |
| `POST` | `/v1/api/n8n/multimodal/authenticated/chat` | Authenticated version |
| `GET` | `/api/attachments/download/{fileId}` | Download file |
| `GET` | `/api/attachments/metadata/{fileId}` | Get file metadata |
| `DELETE` | `/api/attachments/{fileId}` | Delete file |

---

## Error Handling

```javascript
try {
  const result = await fetch(url, options);
  
  if (!result.ok) {
    if (result.status === 401) {
      console.error('Unauthorized - check chatbotId and sessionId');
    } else if (result.status === 400) {
      console.error('Invalid request - check parameters');
    } else if (result.status === 500) {
      console.error('Server error - check backend logs');
    }
  }
  
  const data = await result.json();
  if (!data.success) {
    console.error('Error:', data.error?.message);
  }
} catch (error) {
  console.error('Network error:', error);
}
```

---

## Testing with cURL

### Upload
```bash
curl -X POST http://localhost:8080/api/attachments/upload \
  -F "file=@image.png" \
  -F "chatbotId=bot-123" \
  -F "sessionId=session-456"
```

### Chat with File
```bash
curl -X POST http://localhost:8080/v1/api/n8n/multimodal/anonymous/chat \
  -H "Content-Type: application/json" \
  -d '{
    "role": "user",
    "message": "Analyze this",
    "chatbotId": "bot-123",
    "sessionId": "session-456",
    "fileAttachments": [{
      "fileId": "file_abc123_...",
      "fileName": "image.png",
      "mimeType": "image/png",
      "fileSize": 226585
    }]
  }'
```

---

## Important Notes

⚠️ **Always include chatbotId and sessionId** - Both are required  
⚠️ **Upload before sending chat** - fileId must exist first  
⚠️ **Use correct MIME type** - Affects processing  
⚠️ **Handle errors gracefully** - Network issues can occur  

---

## Support

For issues or questions, check:
- Backend logs: `gradle bootRun`
- API Documentation: `MULTIMODAL_CHAT_FRONTEND_SPECIFICATION.md`
- Controller Code: `MultimodalN8NChatController.java`


