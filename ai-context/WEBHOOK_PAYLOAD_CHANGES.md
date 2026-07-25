# N8N Webhook Payload Changes - Summary

**Change Date:** February 6, 2026  
**Status:** ✅ Implementation Complete

---

## 🎯 What Changed

The `GenericN8NService` has been updated to **send attachment data to the N8N webhook** so that N8N can process files.

---

## 📋 Key Changes

### 1. **Request Format Change**

**Before (without attachments):**
- Format: `application/x-www-form-urlencoded`
- Method: Form data in body
- Attachments: Not sent to N8N

**After (with attachments):**
- Format: `application/json`
- Method: JSON body with full attachment data
- Attachments: **Included in webhook request with Base64 content**

---

### 2. **Webhook Payload Structure**

#### With Attachments (NEW)

```json
{
  "message": "Please analyze this document",
  "instructions": "Chatbot instructions...",
  "attachments": [
    {
      "name": "report.pdf",
      "type": "application/pdf",
      "size": 102400,
      "data": "JVBERi0xLjQKJeLjz9MNCjEgMCBvYmoKPDwKL1R5cGUgL0NhdGFsb2cK..."
    }
  ]
}
```

#### Without Attachments (Backward Compatible)

```
message=Text&instructions=...
```

---

### 3. **New Headers Added**

When attachments are present:

```
Content-Type: application/json
attachment-count: 2
has-attachments: true
```

---

## 🔧 Implementation Details

### Modified File
**`src/main/java/net/ai/chatbot/service/n8n/GenericN8NService.java`**

### New Methods Added

1. **`buildJsonBodyWithAttachments()`**
   - Creates JSON payload with attachment data
   - Includes Base64 encoded file content
   - Preserves message and instructions

2. **`sendWithoutAttachments()`**
   - Fallback method if attachment processing fails
   - Sends message without file data
   - Logs error for diagnostics

### Modified Method

**`executeWebhook()`**
- Now checks if attachments are present
- Saves files to disk
- Sends JSON with attachment data to N8N
- Falls back to form data if attachment processing fails

---

## 📊 Data Flow

```
1. Client sends request
   ├─ Message: "text"
   ├─ Attachments: [file data]
   ├─ SessionId: "session_123"
   └─ ChatbotId: "bot_456"
      ↓
2. API validates request
      ↓
3. N8NAttachmentService processes files
   ├─ Saves to disk
   └─ Tracks metadata
      ↓
4. GenericN8NService sends to N8N
   ├─ Build JSON body
   ├─ Include Base64 attachment data
   ├─ Add attachment headers
   └─ POST to webhook
      ↓
5. N8N receives request
   ├─ Message text
   ├─ Instructions
   ├─ Attachment array with data
   └─ Metadata headers
      ↓
6. N8N processes files
   ├─ Decode Base64
   ├─ Extract content
   └─ Generate response
      ↓
7. Response returned to client
```

---

## ✅ Features

✅ **Full attachment data sent to N8N**  
✅ **Base64 encoded for safe transmission**  
✅ **Multiple files supported**  
✅ **Metadata included (name, type, size)**  
✅ **Error handling with fallback**  
✅ **Backward compatible** (no attachments still works)  
✅ **File storage on disk** for persistence  

---

## 📝 N8N Workflow Integration

### What N8N Receives

```json
{
  "message": "user text",
  "instructions": "chatbot instructions",
  "attachments": [
    {
      "name": "filename",
      "type": "mime/type",
      "size": 12345,
      "data": "base64content..."
    }
  ]
}
```

### How to Process in N8N

```javascript
// Extract attachment
const attachment = body.attachments[0];
const base64Data = attachment.data;

// Decode
const buffer = Buffer.from(base64Data, 'base64');

// Process file
// ... your file processing logic ...
```

---

## 🔄 Backward Compatibility

✅ **Existing endpoints still work:**
- `POST /v1/api/n8n/anonymous/chat` - Still accepts messages without attachments
- `POST /v1/api/n8n/authenticated/chat` - Still accepts messages without attachments

✅ **Form data still used:**
- When no attachments are provided, form data is used as before
- N8N webhook receives same format as before

✅ **No breaking changes:**
- Existing clients continue to work unchanged
- New attachment support is opt-in

---

## 🚀 New Endpoints for Attachments

Two new endpoints now support attachments:

```
POST /v1/api/n8n/anonymous/chat/with-attachments
POST /v1/api/n8n/authenticated/chat/with-attachments
```

---

## 📊 Example Webhook Call

### What Chat API Sends to N8N

```bash
POST https://your-n8n-instance.com/webhook/your-workflow
Content-Type: application/json
attachment-count: 1
has-attachments: true

{
  "message": "Please analyze this report",
  "instructions": "Extract key metrics from PDF",
  "attachments": [
    {
      "name": "quarterly_report.pdf",
      "type": "application/pdf",
      "size": 256000,
      "data": "JVBERi0xLjQK..."
    }
  ]
}
```

### N8N Response

```json
{
  "success": true,
  "analysis": "Key findings: Revenue up 15%, Expenses down 8%..."
}
```

---

## 🧪 Testing

### Test with cURL

```bash
# Encode file
BASE64=$(base64 -w 0 < document.pdf)
SIZE=$(stat -f%z document.pdf)

# Send request
curl -X POST http://localhost:8080/v1/api/n8n/anonymous/chat/with-attachments \
  -H "Content-Type: application/json" \
  -d '{
    "role": "user",
    "message": "Analyze this",
    "attachments": [{
      "name": "document.pdf",
      "type": "application/pdf",
      "size": '$SIZE',
      "data": "'$BASE64'"
    }],
    "sessionId": "test_session",
    "chatbotId": "test_bot"
  }'
```

### Test with JavaScript

```javascript
const file = document.getElementById('file').files[0];
const reader = new FileReader();

reader.onload = async (e) => {
  const base64 = e.target.result.split(',')[1];
  
  const response = await fetch(
    '/v1/api/n8n/anonymous/chat/with-attachments',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        role: 'user',
        message: 'Process this',
        attachments: [{
          name: file.name,
          type: file.type,
          size: file.size,
          data: base64
        }],
        sessionId: 'session_123',
        chatbotId: 'bot_456'
      })
    }
  );
  
  const result = await response.json();
  console.log(result);
};

reader.readAsDataURL(file);
```

---

## ⚙️ Configuration

No special configuration needed! The webhook will receive attachment data automatically when files are included in the request.

### Optional: Update N8N Webhook

If your N8N webhook expects a specific format, you can configure it to:

1. Accept JSON requests (instead of form data)
2. Parse `attachments` array
3. Decode Base64 data
4. Process files

---

## 📚 Documentation

For more details, see:

- **`N8N_WEBHOOK_ATTACHMENT_PAYLOAD.md`** - Detailed payload documentation
- **`N8N_ATTACHMENT_API_DOCUMENTATION.md`** - Complete API reference
- **`N8N_ATTACHMENT_IMPLEMENTATION_GUIDE.md`** - Implementation details

---

## ✅ Summary

| Aspect | Change |
|--------|--------|
| **Webhook Format** | Form data → JSON (with attachments) |
| **Attachment Data** | Not sent → **Sent as Base64** |
| **Request Type** | Form data → JSON |
| **Backward Compatible** | ✅ Yes |
| **Breaking Changes** | ❌ None |
| **New Endpoints** | ✅ `/chat/with-attachments` |
| **File Processing** | ✅ N8N receives full data |

---

## 🎯 Impact

### For Users
- Can now send files to N8N for processing
- Attachments are included in webhook request
- N8N can access full file content

### For Developers
- N8N workflows can now process files
- Files available as Base64 in webhook
- Metadata provided (name, type, size)

### For Operations
- Files stored locally on disk
- Webhook payload includes all data
- Error handling with fallback

---

**Implementation Date:** February 6, 2026  
**Status:** ✅ **COMPLETE AND TESTED**

All attachment data is now sent to N8N webhook for processing! 🚀

