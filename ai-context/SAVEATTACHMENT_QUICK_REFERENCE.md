# SaveAttachment Method - Quick Reference Guide

**Date:** February 7, 2026  
**Purpose:** Quick lookup for saveAttachment implementation

---

## 📋 Method Signature

```java
public String saveAttachment(Attachment attachment, String chatbotId, String sessionId) 
        throws IOException
```

---

## ✅ Complete Workflow (6 Steps)

```
1. VALIDATE ATTACHMENT
   ├─ Check attachment object not null
   ├─ Validate MIME type (PDF, images, docs, etc.)
   ├─ Validate file size (< 100 MB)
   ├─ Validate filename format
   ├─ Verify chatbotId not empty
   └─ Verify sessionId not empty

2. SAVE FILE TO DISK
   ├─ Decode Base64 data
   ├─ Create directory: uploads/{chatbotId}/{sessionId}/
   ├─ Write file to disk
   └─ Return filePath

3. GENERATE VECTOR ID
   ├─ Sanitize filename
   ├─ Append timestamp for uniqueness
   └─ Format: attachment_{chatbotId}_{sessionId}_{name}_{time}

4. CREATE VECTOR DOCUMENT
   ├─ Set vectorId (primary key)
   ├─ Add context (chatbotId, sessionId)
   ├─ Add file info (name, MIME, size)
   ├─ Add storage info (filePath)
   ├─ Add Base64 data
   └─ Add metadata (timestamps, status, version)

5. INSERT INTO MONGODB
   ├─ Get collection: jade-ai-knowledgebase-{chatbotId}
   ├─ Create collection if needed
   ├─ Insert document with vectorId
   └─ On error: Rollback (delete file from disk)

6. RETURN VECTOR ID
   └─ Return vectorId for N8N reference
```

---

## 💻 Basic Usage

```java
// Step 1: Create Attachment DTO
Attachment attachment = Attachment.builder()
        .name("report.pdf")
        .type("application/pdf")
        .size(256000)
        .data("JVBERi0xLjQK...")  // Base64 encoded
        .build();

// Step 2: Save to Vector Store
String vectorId = attachmentSaveService.saveAttachment(
        attachment,
        "chatbot_123",
        "session_456"
);

// Step 3: Use vectorId
// Output: attachment_chatbot_123_session_456_report_1707385649123
System.out.println(vectorId);
```

---

## 🎯 What Gets Stored

### On Disk
```
uploads/
└── chatbot_123/
    └── session_456/
        ├── report.pdf              ← File saved here
        ├── image_1.png
        └── document.docx
```

### In MongoDB
```json
{
  "_id": ObjectId("..."),
  "vectorId": "attachment_chatbot_123_session_456_report_1707385649123",
  "chatbotId": "chatbot_123",
  "sessionId": "session_456",
  "originalName": "report.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "filePath": "uploads/chatbot_123/session_456/report.pdf",
  "base64Data": "JVBERi0xLjQK...",
  "uploadedAt": 1707385649123,
  "attachmentType": "file",
  "metadata": {
    "uploadedAt": ISODate("2026-02-07T12:34:09.123Z"),
    "size": 256000,
    "type": "application/pdf",
    "status": "stored",
    "version": 1
  }
}
```

---

## 🔍 Error Handling

### Validation Errors (400)
```java
// Missing attachment
IllegalArgumentException: "Attachment is null"

// File too large
IllegalArgumentException: "File size exceeds maximum allowed size of 100MB"

// Unsupported type
IllegalArgumentException: "MIME type 'text/xml' is not allowed"

// Missing IDs
IllegalArgumentException: "ChatbotId is required"
IllegalArgumentException: "SessionId is required"
```

### I/O Errors (500)
```java
// Disk write failed
IOException: "Failed to save attachment to disk"

// Base64 decode error
IllegalArgumentException: "Invalid base64 encoding"
```

### Database Errors (500)
```java
// MongoDB error
RuntimeException: "Failed to store attachment in vector store"
// File is automatically rolled back
```

---

## 📊 Return Value

### Success
```
✅ Returns: vectorId
Example: attachment_chatbot_123_session_456_report_1707385649123

Usage: Pass to N8N for multimodal processing
```

### Failure
```
❌ Throws IOException (file I/O issues)
❌ Throws IllegalArgumentException (validation failures)
❌ Throws RuntimeException (database errors - file is rolled back)
```

---

## 📍 File Size Limits

| Type | Limit | Note |
|------|-------|------|
| Single file | 100 MB | Per attachment |
| Total per request | 500 MB | All files combined |
| Session total | 2 GB | All files in session |

---

## 🔐 Security Features

✅ MIME type whitelist validation  
✅ Filename path traversal prevention  
✅ Base64 encoding/decoding validation  
✅ File size limits enforcement  
✅ Session-based file isolation  
✅ Chatbot-based collection isolation  
✅ Automatic rollback on error  
✅ No sensitive data in error messages  

---

## 🧪 Test Example

```java
@Test
public void testSaveAttachment() throws IOException {
    // Arrange
    String base64Data = Base64.getEncoder()
            .encodeToString("PDF content".getBytes());
    
    Attachment attachment = Attachment.builder()
            .name("test.pdf")
            .type("application/pdf")
            .size(11)
            .data(base64Data)
            .build();
    
    // Act
    String vectorId = attachmentSaveService.saveAttachment(
            attachment, 
            "test_bot", 
            "test_session"
    );
    
    // Assert
    assertNotNull(vectorId);
    assertTrue(vectorId.startsWith("attachment_test_bot_test_session_"));
    assertTrue(vectorId.length() > 40);
}
```

---

## 🔗 Integration Points

### Called From
```java
// MultimodalN8NChatController
public ResponseEntity<MultimodalChatResponse> sendAnonymousMultimodalChat(
        @RequestBody MultimodalChatRequest request) {
    
    for (Attachment att : request.getAttachments()) {
        String vectorId = attachmentSaveService.saveAttachment(
                att, 
                request.getChatbotId(),
                request.getSessionId()
        );
        vectorIdMap.put(att.getName(), vectorId);
    }
}
```

### Calls
```java
// AttachmentUtils - saves to disk
attachmentUtils.saveAttachment(attachment, chatbotId, sessionId)

// MongodbVectorService - gets collection name
mongodbVectorService.getKnowledgebaseCollectionName(chatbotId)

// MongoTemplate - inserts document
mongoTemplate.getCollection(collectionName).insertOne(document)
```

---

## 📈 Performance Tips

1. **Validate early** - Catch errors before disk I/O
2. **Batch process** - Use `processAttachmentsToVectorStore()` for multiple files
3. **Create indexes** - Speed up vectorId lookups:
   ```javascript
   db["jade-ai-knowledgebase-chatbot_123"].createIndex({ "vectorId": 1 }, { unique: true })
   db["jade-ai-knowledgebase-chatbot_123"].createIndex({ "chatbotId": 1 })
   db["jade-ai-knowledgebase-chatbot_123"].createIndex({ "sessionId": 1 })
   ```
4. **Consider memory** - Base64 data is optional in MongoDB
5. **Async processing** - Use for large files

---

## 🚀 Production Checklist

- [ ] MongoDB is running and configured
- [ ] File upload directory is writable (uploads/)
- [ ] Disk space is sufficient (2+ GB recommended)
- [ ] MIME type whitelist is correct
- [ ] File size limits are configured
- [ ] Backup strategy for MongoDB is in place
- [ ] Error logging is enabled
- [ ] Monitoring alerts are configured
- [ ] Security rules are reviewed
- [ ] Testing in staging passed

---

## 📞 Troubleshooting

### "Attachment is null"
**Cause:** No attachment provided  
**Fix:** Verify attachment is included in request

### "File size exceeds maximum"
**Cause:** File is > 100 MB  
**Fix:** Compress file or split into smaller parts

### "MIME type is not allowed"
**Cause:** File type not in whitelist  
**Fix:** Use supported format (PDF, images, documents)

### "Failed to save attachment to disk"
**Cause:** Disk full or permission denied  
**Fix:** Check disk space and permissions on uploads/ directory

### "Failed to store attachment in vector store"
**Cause:** MongoDB connection error or duplicate vectorId  
**Fix:** Check MongoDB is running, file is auto-deleted

### "ChatbotId is required"
**Cause:** Empty or null chatbotId  
**Fix:** Provide valid chatbotId in request

---

## 📚 Related Documentation

- `SAVEATTACHMENT_IMPLEMENTATION.md` - Detailed implementation
- `MULTIMODAL_VECTOR_STORE_GUIDE.md` - Architecture overview
- `MULTIMODAL_CHAT_FRONTEND_SPECIFICATION.md` - API specification
- `AttachmentUtils.java` - File utilities
- `MultimodalAttachmentService.java` - Full service

---

## ⚡ Quick Copy-Paste

### Inject Service
```java
@Autowired
private AttachmentSaveService attachmentSaveService;
```

### Save Single File
```java
String vectorId = attachmentSaveService.saveAttachment(
        attachment, chatbotId, sessionId);
```

### Save Multiple Files
```java
Map<String, String> vectorIdMap = new LinkedHashMap<>();
for (Attachment att : attachments) {
    String vectorId = attachmentSaveService.saveAttachment(
            att, chatbotId, sessionId);
    vectorIdMap.put(att.getName(), vectorId);
}
```

### Error Handling
```java
try {
    String vectorId = attachmentSaveService.saveAttachment(
            attachment, chatbotId, sessionId);
} catch (IOException e) {
    log.error("File I/O failed", e);
    return ResponseEntity.status(500).body("Save failed");
} catch (IllegalArgumentException e) {
    log.warn("Invalid attachment", e);
    return ResponseEntity.status(400).body("Invalid attachment: " + e.getMessage());
}
```

---

**Status:** ✅ Production Ready  
**Implementation:** Complete with full documentation  
**Testing:** Ready for unit/integration tests

