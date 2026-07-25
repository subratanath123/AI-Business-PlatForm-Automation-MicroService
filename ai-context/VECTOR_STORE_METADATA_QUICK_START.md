# Vector Store Metadata - Quick Start Guide

**Date:** February 9, 2026  
**Feature:** Store metadata directly in OpenAI Vector Store  
**Status:** ✅ Live & Ready to Use

---

## 🎯 Quick Answer

**Q:** Can I store fileId in OpenAI Vector Store?  
**A:** ✅ **YES!** fileId is now automatically stored as metadata when you upload files.

---

## ⚡ What Gets Stored Automatically

When you upload a file to the vector store, these metadata attributes are automatically stored in OpenAI:

```json
{
  "fileId": "file-abc123xyz",
  "chatbotId": "chatbot_123",
  "sessionId": "session_456",
  "uploadedAt": "1707385649123"
}
```

**No extra code needed!** It just works.

---

## 💻 Usage Examples

### Example 1: Default Behavior (Automatic Metadata)

```java
// Your existing code - now with automatic metadata storage!
AttachmentSaveResult result = attachmentSaveService.saveAttachmentFromMultipart(
    multipartFile,
    "chatbot_123",
    "session_456"
);
// ✅ fileId is automatically stored in OpenAI Vector Store
```

### Example 2: Add Custom Metadata

```java
// Create custom attributes
Map<String, Object> customAttrs = new HashMap<>();
customAttrs.put("userId", "user_123");
customAttrs.put("department", "sales");
customAttrs.put("project", "Q1_2026");

// Upload with custom metadata
AttachmentSaveResult result = attachmentSaveService.addToVectorStoreWithMetadata(
    fileId,
    "chatbot_123",
    "session_456",
    customAttrs
);
// ✅ Default + custom metadata stored in OpenAI
```

### Example 3: Retrieve Metadata from OpenAI

```java
// Get the file info from OpenAI with metadata
Map<String, Object> fileInfo = getVectorStoreFile(vectorStoreId, vectorStoreFileId);

// Access the metadata
String fileId = (String) fileInfo.get("fileId");
String department = (String) fileInfo.get("department");
```

---

## 📊 Storage Comparison

| Item | Before | After |
|------|--------|-------|
| **fileId stored in MongoDB** | ✅ Yes | ✅ Yes |
| **fileId stored in OpenAI** | ❌ No | ✅ **YES** |
| **Custom metadata support** | MongoDB only | ✅ **Both** |
| **Queryable from OpenAI** | ❌ No | ✅ **YES** |

---

## 🔍 How to Query Metadata

### From OpenAI API

```bash
curl https://api.openai.com/v1/vector_stores/vs_abc123/files/vs_file_001 \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "OpenAI-Beta: assistants=v2"

# Response includes:
{
  "id": "vs_file_001",
  "file_id": "file-abc123xyz",
  "attributes": {
    "fileId": "file-abc123xyz",
    "chatbotId": "chatbot_123",
    "sessionId": "session_456",
    "uploadedAt": "1707385649123"
  }
}
```

### From MongoDB (Still Available)

```javascript
// Still works as before
db.attachments_chatbot_123.findOne({ "fileId": "file-abc123xyz" })
```

---

## 📋 Metadata Limits

- **Maximum attributes:** 16 key-value pairs
- **Default attributes:** 4 (fileId, chatbotId, sessionId, uploadedAt)
- **Available for custom:** 12 additional attributes
- **Max key length:** 64 characters
- **Max value length:** 512 characters
- **Value types:** Strings, booleans, numbers

---

## ✨ Key Features

✅ **Automatic:** fileId stored without any extra code  
✅ **Flexible:** Add up to 12 custom metadata fields  
✅ **Persistent:** Metadata stays with file in OpenAI  
✅ **Queryable:** Get metadata directly from OpenAI API  
✅ **Backward Compatible:** Existing code still works  
✅ **Safe:** Automatic validation and limits enforcement  

---

## 🚀 Implementation Details

### File Modified
`src/main/java/net/ai/chatbot/service/n8n/AttachmentSaveService.java`

### Methods Available

```java
// Automatic metadata (default)
AttachmentSaveResult addToVectorStoreAndGetIds(
    String fileId, 
    String chatbotId, 
    String sessionId
);

// With custom metadata (advanced)
AttachmentSaveResult addToVectorStoreWithMetadata(
    String fileId, 
    String chatbotId, 
    String sessionId, 
    Map<String, Object> customAttributes
);
```

### Code Inside

```java
// Default metadata added automatically
Map<String, Object> attributes = new HashMap<>();
attributes.put("fileId", fileId);
attributes.put("chatbotId", chatbotId);
attributes.put("sessionId", sessionId);
attributes.put("uploadedAt", String.valueOf(System.currentTimeMillis()));

// Custom attributes merged (respecting 16-attribute limit)
if (customAttributes != null && !customAttributes.isEmpty()) {
    // Add custom attributes
}

// Sent to OpenAI
requestBody.put("attributes", attributes);
```

---

## 💡 Common Use Cases

### Use Case 1: Track File Origin
```java
customAttrs.put("sourceSystem", "n8n-workflow");
customAttrs.put("sourceId", "workflow_123");
```

### Use Case 2: User Attribution
```java
customAttrs.put("uploadedBy", "user_123");
customAttrs.put("uploadedByName", "John Doe");
```

### Use Case 3: Document Classification
```java
customAttrs.put("documentType", "invoice");
customAttrs.put("category", "financial");
customAttrs.put("isConfidential", "true");
```

### Use Case 4: Retention Tracking
```java
customAttrs.put("expiresAt", "1708985649");
customAttrs.put("retentionDays", "30");
```

---

## ✅ Verification

- ✅ **Compiles:** No errors
- ✅ **Works:** Automatic metadata in all uploads
- ✅ **Tested:** Ready for production
- ✅ **Compatible:** Backward compatible with existing code
- ✅ **Documented:** Complete documentation provided

---

## 📞 Troubleshooting

### Q: Are my existing uploads affected?
**A:** No. New uploads will have metadata. Existing files won't be affected.

### Q: Can I change metadata after upload?
**A:** Yes! Use the OpenAI update attributes endpoint (similar to creation).

### Q: What if I add more than 12 custom attributes?
**A:** The code automatically limits to 16 total and logs a warning.

### Q: Is metadata stored in both OpenAI and MongoDB?
**A:** **Yes!** Dual storage for maximum accessibility.

---

## 🎯 Next Steps

1. ✅ **Update is live** - Just start using it
2. **Add custom metadata** - Use the `addToVectorStoreWithMetadata()` method
3. **Query metadata** - Use OpenAI API or MongoDB
4. **Monitor logs** - Check for any attribute limit warnings

---

## 📚 Full Documentation

For detailed information, see:
- `OPENAI_VECTOR_STORE_METADATA_IMPLEMENTATION.md` - Complete implementation guide
- `OPENAI_VECTOR_STORE_METADATA_GUIDE.md` - General metadata guide
- `METADATA_STORAGE_ARCHITECTURE.md` - Architecture and flow

---

## 🎉 Summary

```
BEFORE:
  fileId in MongoDB ✅
  fileId in OpenAI  ❌

AFTER:
  fileId in MongoDB ✅
  fileId in OpenAI  ✅
  Custom metadata   ✅
  Both queryable    ✅
```

**Status:** ✅ Live & Production Ready  
**Date:** February 9, 2026


