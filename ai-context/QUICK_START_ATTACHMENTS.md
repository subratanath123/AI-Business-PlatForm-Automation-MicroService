# Quick Start: N8N Chat with Attachments

Get started with file attachment support in 5 minutes!

---

## 1️⃣ Basic Setup

### Create Upload Directory
```bash
mkdir -p /var/app/uploads
chmod 755 /var/app/uploads
```

### Configure Application (application.properties)
```properties
file.upload.path=/var/app/uploads
file.max.size=104857600
n8n.webhook.knowledgebase.chat.url=https://your-n8n-instance.com/webhook/chat
server.tomcat.max-http-post-size=104857600
```

---

## 2️⃣ Simple JavaScript Example

```javascript
// Send a message with a file attachment
async function sendChatWithFile(message, file, chatbotId, sessionId) {
  // Read file and convert to Base64
  const base64 = await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result.split(',')[1]);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });

  // Send to API
  const response = await fetch(
    'https://api.example.com/v1/api/n8n/anonymous/chat/with-attachments',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        role: 'user',
        message: message,
        attachments: [{
          name: file.name,
          type: file.type,
          size: file.size,
          data: base64
        }],
        sessionId: sessionId,
        chatbotId: chatbotId
      })
    }
  );

  const result = await response.json();
  console.log(result.result);
  return result.result;
}

// Usage
const fileInput = document.getElementById('fileInput');
sendChatWithFile('Review this', fileInput.files[0], 'bot_123', 'session_123');
```

---

## 3️⃣ cURL Test

### With Attachment
```bash
curl -X POST http://localhost:8080/v1/api/n8n/anonymous/chat/with-attachments \
  -H "Content-Type: application/json" \
  -d '{
    "role": "user",
    "message": "Analyze this",
    "attachments": [
      {
        "name": "file.pdf",
        "type": "application/pdf",
        "size": 100000,
        "data": "'$(base64 -w 0 < file.pdf)'"
      }
    ],
    "sessionId": "test_session_123",
    "chatbotId": "test_bot_456"
  }'
```

### Text Only
```bash
curl -X POST http://localhost:8080/v1/api/n8n/anonymous/chat/with-attachments \
  -H "Content-Type: application/json" \
  -d '{
    "role": "user",
    "message": "Hello!",
    "attachments": [],
    "sessionId": "test_session_123",
    "chatbotId": "test_bot_456"
  }'
```

---

## 4️⃣ Python Quick Example

```python
import requests
import base64

# Encode file
with open('document.pdf', 'rb') as f:
    file_data = f.read()
    base64_data = base64.b64encode(file_data).decode()

# Send request
response = requests.post(
    'http://localhost:8080/v1/api/n8n/anonymous/chat/with-attachments',
    json={
        'role': 'user',
        'message': 'Please analyze this PDF',
        'attachments': [{
            'name': 'document.pdf',
            'type': 'application/pdf',
            'size': len(file_data),
            'data': base64_data
        }],
        'sessionId': 'session_123',
        'chatbotId': 'bot_456'
    }
)

print(response.json()['result'])
```

---

## 5️⃣ Key Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/v1/api/n8n/anonymous/chat/with-attachments` | POST | Anonymous chat with files |
| `/v1/api/n8n/authenticated/chat/with-attachments` | POST | Authenticated chat with files |
| `/v1/api/n8n/attachments/{botId}/{sessionId}` | GET | List session files |
| `/v1/api/n8n/attachments/stats/{botId}/{sessionId}` | GET | Storage stats |
| `/v1/api/n8n/attachments/{botId}/{sessionId}/{fileName}` | DELETE | Delete file |

---

## 6️⃣ Supported File Types

### Documents
- PDF, TXT, CSV, JSON
- Word (.docx), Excel (.xlsx), PowerPoint (.pptx)

### Images
- JPEG, PNG, GIF, WebP, SVG

---

## 7️⃣ Troubleshooting

### File Not Accepted
```javascript
// Error: INVALID_MIME_TYPE
// Solution: Use supported MIME types above
```

### File Too Large
```javascript
// Error: FILE_TOO_LARGE
// Solution: File size > 100MB, compress or split
```

### Base64 Error
```javascript
// Error: INVALID_BASE64
// Solution: Ensure proper Base64 encoding
```

---

## 8️⃣ Test API Keys

| Field | Value |
|-------|-------|
| `chatbotId` | `test_bot_123` |
| `sessionId` | `session_test_123` |
| `role` | `user` (required) |

---

## 9️⃣ Response Format

### Success (200)
```json
{
  "success": true,
  "result": "Analysis complete...",
  "status": "200",
  "timestamp": 1707385700000
}
```

### Error (400+)
```json
{
  "success": false,
  "errorCode": "INVALID_REQUEST",
  "errorMessage": "Attachment validation failed",
  "timestamp": 1707385700000
}
```

---

## 🔟 Common MIME Types

```
PDF: application/pdf
Text: text/plain
CSV: text/csv
JSON: application/json
JPEG: image/jpeg
PNG: image/png
Word: application/vnd.openxmlformats-officedocument.wordprocessingml.document
Excel: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

---

## Full Documentation

📖 **API Docs:** See `N8N_ATTACHMENT_API_DOCUMENTATION.md`  
🛠️ **Implementation Guide:** See `N8N_ATTACHMENT_IMPLEMENTATION_GUIDE.md`  
📋 **Summary:** See `ATTACHMENT_IMPLEMENTATION_SUMMARY.md`

---

## Next: Full Integration

1. Read `N8N_ATTACHMENT_API_DOCUMENTATION.md` for complete API reference
2. Review `N8N_ATTACHMENT_IMPLEMENTATION_GUIDE.md` for production setup
3. Test with provided examples
4. Configure your N8N workflow
5. Deploy to production

---

**Version:** 1.0  
**Last Updated:** February 6, 2026  
**Status:** ✅ Ready to Use

