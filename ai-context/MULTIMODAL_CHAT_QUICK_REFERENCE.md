# Multimodal Chat API - Quick Reference
## One-Page Developer Guide

**Version:** 1.0 | **Date:** Feb 7, 2026 | **Status:** ✅ Production Ready

---

## 🚀 Quick Start

### 1. Install

```bash
npm install chat-widget-sdk
# or use CDN
<script src="https://cdn.example.com/chat-widget.min.js"></script>
```

### 2. Initialize

```javascript
const chat = new ChatWidget({
  apiBaseUrl: 'https://api.example.com/v1/api/n8n/multimodal',
  chatbotId: 'your-chatbot-id',
  sessionId: 'unique-session-id' // auto-generated if omitted
});
```

### 3. Send Message

```javascript
// Text only
const response = await chat.sendMessage({
  message: "What's in this document?"
});

// With attachments
const response = await chat.sendMessage({
  message: "Analyze this PDF",
  attachments: [
    new File(['...'], 'report.pdf', { type: 'application/pdf' })
  ]
});

// Get result
console.log(response.result);           // AI response
console.log(response.vectorIdMap);      // filename → vectorId mapping
console.log(response.vectorAttachments); // attachment metadata
```

---

## 📡 API Endpoints Cheat Sheet

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/anonymous/chat` | Send message (no auth) |
| `POST` | `/authenticated/chat` | Send message (with auth) |
| `GET` | `/attachments/{chatbotId}` | List all attachments |
| `GET` | `/attachments/{chatbotId}/{vectorId}` | Get attachment metadata |
| `DELETE` | `/attachments/{chatbotId}/{vectorId}` | Remove attachment |

---

## 📦 Request/Response

### Minimal Request
```json
{
  "message": "Hello",
  "chatbotId": "bot-123",
  "sessionId": "sess-456"
}
```

### With Files
```json
{
  "message": "Analyze this",
  "attachments": [
    {
      "name": "file.pdf",
      "type": "application/pdf",
      "size": 256000,
      "data": "JVBERi0xLjQK..."  // Base64
    }
  ],
  "chatbotId": "bot-123",
  "sessionId": "sess-456"
}
```

### Success Response
```json
{
  "success": true,
  "result": "Analysis complete...",
  "vectorIdMap": {
    "file.pdf": "attachment_bot_123_..."
  },
  "vectorAttachments": [{
    "vectorId": "attachment_bot_123_...",
    "fileName": "file.pdf",
    "mimeType": "application/pdf",
    "fileSize": 256000,
    "uploadedAt": 1707385649123
  }],
  "timestamp": 1707385650000
}
```

### Error Response
```json
{
  "success": false,
  "errorCode": "INVALID_REQUEST",
  "errorMessage": "ChatbotId required",
  "timestamp": 1707385650000
}
```

---

## ✅ Validation Checklist

Before sending request:
- [ ] `chatbotId` is set and valid
- [ ] `sessionId` is unique and consistent
- [ ] `message` OR `attachments` provided
- [ ] File size < 100 MB each
- [ ] Total size < 500 MB
- [ ] MIME type is supported
- [ ] `Content-Type: application/json`

---

## 🔧 Code Snippets

### Convert File to Base64
```javascript
async function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const base64 = reader.result.split(',')[1];
      resolve({
        name: file.name,
        type: file.type,
        size: file.size,
        data: base64
      });
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}
```

### Validate File
```javascript
function validateFile(file) {
  const MAX_SIZE = 100 * 1024 * 1024; // 100 MB
  const ALLOWED = ['application/pdf', 'image/jpeg', 'image/png', 'text/plain'];
  
  if (file.size > MAX_SIZE) return 'File too large';
  if (!ALLOWED.includes(file.type)) return 'Unsupported type';
  return null;
}
```

### React Hook
```javascript
import { useState } from 'react';

export function useChat(chatbotId) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const send = async (message, files) => {
    setLoading(true);
    try {
      const attachments = await Promise.all(
        (files || []).map(fileToBase64)
      );
      
      const res = await fetch(
        '/v1/api/n8n/multimodal/anonymous/chat',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message, attachments, chatbotId,
            sessionId: Math.random().toString(36).substr(2, 9)
          })
        }
      );
      
      const data = await res.json();
      if (!data.success) throw new Error(data.errorMessage);
      return data;
    } catch (e) {
      setError(e.message);
      throw e;
    } finally {
      setLoading(false);
    }
  };

  return { send, loading, error };
}
```

---

## 🎯 Supported File Types

| Type | Extensions | Max Size |
|------|-----------|----------|
| PDF | .pdf | 100 MB |
| Image | .jpg, .png, .gif, .webp | 50 MB |
| Document | .doc, .docx, .txt | 100 MB |
| Spreadsheet | .xls, .xlsx, .csv | 100 MB |
| Presentation | .ppt, .pptx | 100 MB |

---

## ⚠️ Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `INVALID_REQUEST` | Missing chatbotId | Add chatbotId to request |
| `CHATBOT_NOT_FOUND` | Bad chatbotId | Verify chatbot ID |
| `FILE_TOO_LARGE` | File > 100 MB | Compress or split file |
| `INVALID_ATTACHMENT_TYPE` | Unsupported type | Use supported format |
| `INTERNAL_ERROR` | Server error | Retry or contact support |
| `401 Unauthorized` | No/bad token | Add valid JWT token |

---

## 🧪 Test with cURL

```bash
# Simple message
curl -X POST http://localhost:8080/v1/api/n8n/multimodal/anonymous/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Hi","chatbotId":"bot-1","sessionId":"sess-1"}'

# With file
FILE=$(base64 -w 0 < file.pdf)
curl -X POST http://localhost:8080/v1/api/n8n/multimodal/anonymous/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message":"Analyze",
    "attachments":[{"name":"file.pdf","type":"application/pdf","size":256000,"data":"'$FILE'"}],
    "chatbotId":"bot-1",
    "sessionId":"sess-1"
  }'

# List attachments
curl http://localhost:8080/v1/api/n8n/multimodal/attachments/bot-1

# Delete attachment
curl -X DELETE http://localhost:8080/v1/api/n8n/multimodal/attachments/bot-1/vectorId-123
```

---

## 📊 Performance Tips

✅ **DO:**
- Compress images before upload
- Batch files into single request
- Reuse sessionId for multi-turn chat
- Cache results locally
- Show progress bar during upload

❌ **DON'T:**
- Send raw uncompressed files
- Send files > 100 MB
- Create new sessionId for each message
- Retry immediately on failure
- Block UI during upload

---

## 🔐 Security Notes

- ✅ HTTPS required for production
- ✅ MIME type validated server-side
- ✅ File size limits enforced
- ✅ JWT token support for auth endpoint
- ✅ CORS enabled (origins: *)
- ✅ Session isolation per chatbot

---

## 🔗 Full Documentation

- **Detailed Spec:** `MULTIMODAL_CHAT_FRONTEND_SPECIFICATION.md`
- **Architecture:** `MULTIMODAL_VECTOR_STORE_GUIDE.md`
- **N8N Integration:** `N8N_WEBHOOK_ATTACHMENT_PAYLOAD.md`
- **Examples:** See JavaScript/React examples above

---

## 📞 Support

| Channel | Contact |
|---------|---------|
| Email | `api-support@example.com` |
| Docs | `/docs/api/multimodal` |
| Issues | GitHub Issues |
| Status | `api-status.example.com` |

---

**Need Help?** Check the troubleshooting section in the full specification.  
**Ready to build?** Start with the Quick Start section above.  
**Questions?** Email support with endpoint and error code.

---

**Last Updated:** February 7, 2026 | **Version:** 1.0 | **Status:** ✅ Production Ready

