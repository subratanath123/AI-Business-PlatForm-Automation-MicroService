# N8N Webhook Attachment Payload Documentation

## Overview

When attachments are included in a chat request, the Chat API sends them to the N8N webhook with full attachment data (Base64 encoded) so that N8N can process the files.

**Updated:** February 6, 2026

---

## Webhook Request with Attachments

### Request Format

When attachments are present, the API sends a **JSON request** (instead of form data) to the N8N webhook with the following structure:

```json
{
  "message": "Please analyze this document",
  "instructions": "Your chatbot instructions...",
  "attachments": [
    {
      "name": "report.pdf",
      "type": "application/pdf",
      "size": 102400,
      "data": "JVBERi0xLjQKJeLjz9MNCjEgMCBvYmoKPDwKL1R5cGUgL0NhdGFsb2cKL1BhZ2VzIDIgMCBSCj4+CmVuZG9iag=="
    },
    {
      "name": "spreadsheet.xlsx",
      "type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "size": 256000,
      "data": "UEsDBBQABgAIAAAAIQA..."
    }
  ]
}
```

### Key Points

- **Content-Type:** `application/json` (instead of form data)
- **Data Included:** Full Base64 encoded file content
- **Metadata:** Each attachment includes name, type (MIME), and size
- **Multiple Files:** Supports arrays of attachments

---

## HTTP Headers

When sending attachments to N8N webhook, the following headers are added:

```
Content-Type: application/json
attachment-count: 2
has-attachments: true
email: user@example.com
fallbackmessage: Sorry, I couldn't understand that
greetingmessage: Hello! How can I help?
chatbotid: chatbot_12345
sessionid: session_123456
```

---

## Request Without Attachments

If no attachments are provided, the API falls back to **form data**:

```
Content-Type: application/x-www-form-urlencoded

message=Hello&instructions=Your%20instructions
```

---

## Implementation Details

### 1. Attachment Processing Flow

```
Client Request (with Base64 attachments)
    ↓
Controller receives request
    ↓
Validate request structure
    ↓
N8NAttachmentService processes attachments
    ├→ Save to disk (uploads/{chatbotId}/{sessionId}/)
    └→ Store metadata
    ↓
Build webhook payload (JSON with attachment data)
    ↓
Send to N8N webhook
    ↓
N8N receives and processes files
    ↓
Response returned to client
```

### 2. Data Flow in Code

**GenericN8NService.java** now handles attachments as follows:

```java
if (attachments != null && !attachments.isEmpty()) {
    // 1. Save attachments to disk
    attachmentService.processAttachments(attachments, chatbotId, conversationId);
    
    // 2. Add metadata headers
    headers.put("attachment-count", String.valueOf(attachments.size()));
    headers.put("has-attachments", "true");
    headers.put("Content-Type", "application/json");
    
    // 3. Build JSON body with attachment data
    Map<String, Object> jsonBody = buildJsonBodyWithAttachments(
        chatBot, messageContent, attachments, extraFormFields
    );
    
    // 4. Send to N8N webhook as JSON
    GenericWebClientResponse<String> responseEntity = 
        genericWebClient.postWithResponse(
            webhookUrl,
            () -> BodyInserters.fromValue(jsonBody),
            String.class,
            headers
        );
}
```

---

## JSON Body Structure

### With Attachments

```json
{
  "message": "User message text",
  "instructions": "Chatbot instructions from configuration",
  "attachments": [
    {
      "name": "filename.pdf",
      "type": "application/pdf",
      "size": 102400,
      "data": "BASE64_ENCODED_FILE_CONTENT"
    }
  ]
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `message` | string | The user's message text (can be empty) |
| `instructions` | string | Chatbot-specific instructions |
| `attachments` | array | Array of attachment objects |
| `attachments[].name` | string | Original filename |
| `attachments[].type` | string | MIME type (e.g., "application/pdf") |
| `attachments[].size` | number | File size in bytes |
| `attachments[].data` | string | Base64 encoded file content |

---

## N8N Workflow Configuration

### Expected Input in N8N

Your N8N workflow should expect a webhook request with the following JSON structure:

```json
{
  "message": string,
  "instructions": string,
  "attachments": [
    {
      "name": string,
      "type": string,
      "size": number,
      "data": string (base64)
    }
  ]
}
```

### Processing Attachments in N8N

To process attachments in your N8N workflow:

1. **Extract attachment data:**
   ```javascript
   // In N8N JavaScript node
   const attachments = $input.all()[0].json.attachments;
   const base64Data = attachments[0].data;
   const filename = attachments[0].name;
   const mimeType = attachments[0].type;
   ```

2. **Decode Base64:**
   ```javascript
   // Decode base64 to binary
   const buffer = Buffer.from(base64Data, 'base64');
   ```

3. **Save or process file:**
   ```javascript
   // Use HTTP node to process file with external service
   // Or save to file system
   // Or send to document processing API
   ```

### Example N8N Workflow Steps

```
1. Webhook trigger
   ↓
2. Parse JSON body
   ↓
3. Extract attachments array
   ↓
4. For each attachment:
   a. Decode base64 data
   b. Save to file system OR
   c. Send to processing service
   ↓
5. Process message + file content
   ↓
6. Return response
```

---

## Error Handling

### If Attachment Processing Fails

If the attachment processing fails for any reason:

1. **Files saved to disk** - These can be accessed via attachment management APIs
2. **Fallback to form data** - Message is still sent to N8N without attachment data
3. **Error message included** - Response includes attachment error details

Example fallback response:

```json
{
  "success": true,
  "result": "Response from N8N\n[Note: Attachment processing failed: Permission denied]"
}
```

---

## Testing Webhook Payload

### cURL with Attachment Data

```bash
curl -X POST http://your-n8n-instance.com/webhook/chat \
  -H "Content-Type: application/json" \
  -H "attachment-count: 1" \
  -H "has-attachments: true" \
  -d '{
    "message": "Analyze this PDF",
    "instructions": "Review document quality",
    "attachments": [
      {
        "name": "document.pdf",
        "type": "application/pdf",
        "size": 102400,
        "data": "'$(base64 -w 0 < document.pdf)'"
      }
    ]
  }'
```

### Node.js Test

```javascript
const axios = require('axios');
const fs = require('fs');

const file = fs.readFileSync('document.pdf');
const base64Data = file.toString('base64');

const payload = {
  message: 'Process this file',
  instructions: 'Extract text content',
  attachments: [{
    name: 'document.pdf',
    type: 'application/pdf',
    size: file.length,
    data: base64Data
  }]
};

axios.post('http://your-n8n-instance.com/webhook/chat', payload, {
  headers: {
    'Content-Type': 'application/json',
    'attachment-count': '1',
    'has-attachments': 'true'
  }
})
.then(res => console.log('Response:', res.data))
.catch(err => console.error('Error:', err.message));
```

---

## Performance Considerations

### Base64 Encoding Size

- **Original file:** 100 KB
- **Base64 encoded:** ~133 KB (33% larger)
- **JSON wrapper:** +50-100 bytes
- **Total payload:** ~134 KB

### Webhook Timeout

- **Default:** 30 seconds
- **For large files:** Consider increasing timeout
- **Recommendation:** Monitor N8N processing time

### Memory Usage

- **In-memory:** Attachment data held in JSON payload
- **On disk:** Files saved to local storage for persistence
- **Total:** Original file size × 1.33 (base64) + disk space

---

## Comparison: With vs Without Attachments

### Without Attachments

```
Request Format:  application/x-www-form-urlencoded
Content Size:    Small
Processing:      Fast
Headers:         attachment-count: 0, has-attachments: false
```

### With Attachments

```
Request Format:  application/json
Content Size:    Large (file size × 1.33)
Processing:      Slower (encode/decode time)
Headers:         attachment-count: N, has-attachments: true
Payload:         Full attachment data included
```

---

## Troubleshooting

### Attachment Not Reaching N8N

**Problem:** Files aren't being processed in N8N  
**Solution:**
1. Check N8N webhook logs
2. Verify `has-attachments: true` header received
3. Confirm `attachments` array in JSON body
4. Test webhook with cURL

### Base64 Decoding Error in N8N

**Problem:** Error decoding base64 data  
**Solution:**
1. Verify base64 string is not truncated
2. Check for extra whitespace
3. Use proper base64 decoding library
4. Test base64 data with online decoder

### Large File Timeout

**Problem:** Request times out with large files  
**Solution:**
1. Increase webhook timeout in N8N
2. Increase API timeout on client side
3. Compress files before upload
4. Use chunked/streaming upload

### Headers Not Received

**Problem:** N8N not receiving attachment headers  
**Solution:**
1. Check N8N webhook configuration
2. Verify headers are being sent (check logs)
3. Ensure N8N processes custom headers
4. Review proxy/gateway configuration

---

## Security Notes

1. **Files saved to disk** with session isolation
2. **Base64 data** includes full file content in webhook
3. **Webhook URL must be secure** (HTTPS recommended)
4. **Access control** should be implemented in N8N
5. **File cleanup** recommended for old sessions

---

## API Reference

See also:
- `N8N_ATTACHMENT_API_DOCUMENTATION.md` - Client API reference
- `N8N_ATTACHMENT_IMPLEMENTATION_GUIDE.md` - Full implementation details
- `QUICK_START_ATTACHMENTS.md` - Quick reference

---

**Last Updated:** February 6, 2026  
**Status:** ✅ Complete Implementation

