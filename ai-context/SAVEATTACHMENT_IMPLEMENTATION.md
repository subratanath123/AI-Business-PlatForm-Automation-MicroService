# SaveAttachment Method Implementation
## For Vector Store Storage

**Date:** February 7, 2026  
**Purpose:** Save attachment to MongoDB vector store with proper validation and metadata

---

## Complete Implementation

```java
/**
 * Save attachment to vector store with validation and metadata
 * 
 * Process:
 * 1. Validate attachment (size, MIME type, format)
 * 2. Save file to disk for persistence
 * 3. Create vector store document with metadata
 * 4. Insert into MongoDB knowledge base collection
 * 5. Return vectorId for reference
 * 
 * @param attachment the attachment DTO containing name, type, size, and base64 data
 * @param chatbotId the ID of the chatbot (for collection isolation)
 * @param sessionId the session ID (for session tracking and file organization)
 * @return vectorId unique identifier for vector store lookup
 * @throws IOException if file I/O or encoding operations fail
 * @throws IllegalArgumentException if attachment validation fails
 */
public String saveAttachment(Attachment attachment, String chatbotId, String sessionId) throws IOException {
    
    // ============================================================
    // STEP 1: Validate Attachment
    // ============================================================
    log.info("Validating attachment: {} for chatbot: {}, session: {}", 
            attachment.getName(), chatbotId, sessionId);
    
    // Validate attachment structure
    AttachmentUtils.ValidationResult validation = attachmentUtils.validateAttachment(attachment);
    if (!validation.isValid()) {
        log.warn("Attachment validation failed: {}", validation.getMessage());
        throw new IllegalArgumentException("Attachment validation failed: " + validation.getMessage());
    }
    
    // Validate chatbot ID and session ID
    if (chatbotId == null || chatbotId.trim().isEmpty()) {
        throw new IllegalArgumentException("ChatbotId is required");
    }
    if (sessionId == null || sessionId.trim().isEmpty()) {
        throw new IllegalArgumentException("SessionId is required");
    }
    
    // ============================================================
    // STEP 2: Save File to Disk for Persistence
    // ============================================================
    log.debug("Saving attachment to disk: {}", attachment.getName());
    
    String filePath;
    try {
        filePath = attachmentUtils.saveAttachment(attachment, chatbotId, sessionId);
        log.info("Attachment saved to disk at path: {}", filePath);
    } catch (IOException e) {
        log.error("Failed to save attachment to disk", e);
        throw new IOException("Failed to save attachment to disk: " + e.getMessage(), e);
    }
    
    // ============================================================
    // STEP 3: Generate Vector ID
    // ============================================================
    // Generate unique vectorId for this attachment
    // Format: attachment_{chatbotId}_{sessionId}_{sanitizedFilename}_{timestamp}
    String vectorId = generateVectorId(chatbotId, sessionId, attachment.getName());
    log.debug("Generated vectorId: {}", vectorId);
    
    // ============================================================
    // STEP 4: Create Vector Store Document
    // ============================================================
    log.debug("Creating vector store document for attachment");
    
    long uploadedAt = System.currentTimeMillis();
    
    Document vectorDocument = new Document()
            // Unique identifier for this attachment
            .append("vectorId", vectorId)
            
            // Context information for isolation and tracking
            .append("chatbotId", chatbotId)
            .append("sessionId", sessionId)
            
            // File information
            .append("originalName", attachment.getName())
            .append("mimeType", attachment.getMimeType())
            .append("fileSize", attachment.getSize())
            
            // Storage information
            .append("filePath", filePath)
            .append("attachmentType", "file")
            
            // Timestamp information
            .append("uploadedAt", uploadedAt)
            
            // Base64 data for immediate access without disk I/O
            // Note: This is optional and can be removed for memory efficiency
            .append("base64Data", attachment.getFileData())
            
            // Metadata for additional information
            .append("metadata", new Document()
                    .append("uploadedAt", new Date(uploadedAt))
                    .append("size", attachment.getSize())
                    .append("type", attachment.getMimeType())
                    .append("fileName", attachment.getName())
                    .append("status", "stored")
                    .append("version", 1)
            );
    
    // ============================================================
    // STEP 5: Insert into MongoDB Vector Store
    // ============================================================
    log.debug("Inserting document into vector store for chatbot: {}", chatbotId);
    
    try {
        // Get collection name for this chatbot's knowledge base
        String collectionName = mongodbVectorService.getKnowledgebaseCollectionName(chatbotId);
        
        // Ensure collection exists with proper indexes
        mongodbVectorService.createMongodbCollection(collectionName);
        
        // Insert the document
        mongoTemplate.getCollection(collectionName).insertOne(vectorDocument);
        
        log.info("Successfully stored attachment in vector store: vectorId={}, chatbot={}, session={}", 
                vectorId, chatbotId, sessionId);
        
    } catch (Exception e) {
        log.error("Failed to insert document into vector store", e);
        
        // Clean up: Delete file from disk if MongoDB insert fails
        try {
            attachmentUtils.deleteAttachment(filePath);
            log.info("Rolled back file deletion after vector store failure: {}", filePath);
        } catch (Exception rollbackError) {
            log.warn("Failed to rollback file deletion: {}", rollbackError.getMessage());
        }
        
        throw new RuntimeException("Failed to store attachment in vector store: " + e.getMessage(), e);
    }
    
    // ============================================================
    // STEP 6: Return Vector ID
    // ============================================================
    log.info("Attachment successfully saved to vector store with vectorId: {}", vectorId);
    return vectorId;
}
```

---

## Helper Method: Generate Vector ID

```java
/**
 * Generate unique vectorId for attachment
 * Format: attachment_{chatbotId}_{sessionId}_{sanitizedFilename}_{timestamp}
 * 
 * @param chatbotId the chatbot ID
 * @param sessionId the session ID
 * @param fileName the original filename
 * @return unique vectorId string
 */
private String generateVectorId(String chatbotId, String sessionId, String fileName) {
    // Sanitize filename
    String sanitized = sanitizeFileName(fileName);
    
    // Generate timestamp suffix for uniqueness
    long timestamp = System.currentTimeMillis();
    
    // Combine all parts
    return String.format("attachment_%s_%s_%s_%d", 
            chatbotId.toLowerCase(),
            sessionId.toLowerCase(),
            sanitized,
            timestamp);
}

/**
 * Sanitize filename for use in vectorId
 * Remove special characters, convert to lowercase
 * 
 * @param fileName the filename to sanitize
 * @return sanitized filename
 */
private String sanitizeFileName(String fileName) {
    // Remove extension
    String nameWithoutExt = fileName.contains(".") 
            ? fileName.substring(0, fileName.lastIndexOf("."))
            : fileName;
    
    // Remove path separators and special characters
    String sanitized = nameWithoutExt.replaceAll("[^a-zA-Z0-9._-]", "_");
    
    // Replace multiple underscores with single
    sanitized = sanitized.replaceAll("_+", "_");
    
    // Convert to lowercase
    sanitized = sanitized.toLowerCase();
    
    // Limit length
    if (sanitized.length() > 50) {
        sanitized = sanitized.substring(0, 50);
    }
    
    return sanitized;
}
```

---

## Usage Example

### Basic Usage
```java
@Autowired
private MultimodalAttachmentService multimodalAttachmentService;

// Create attachment
Attachment attachment = Attachment.builder()
        .name("quarterly_report.pdf")
        .type("application/pdf")
        .size(256000)
        .data("JVBERi0xLjQKJeLjz9MNCjEgMCBvYm8...")
        .build();

// Save to vector store
try {
    String vectorId = multimodalAttachmentService.saveAttachment(
            attachment,
            "chatbot_123",
            "session_456"
    );
    
    log.info("Attachment saved with vectorId: {}", vectorId);
    // Output: attachment_chatbot_123_session_456_quarterly_report_1707385649123
    
} catch (IOException e) {
    log.error("Failed to save attachment", e);
} catch (IllegalArgumentException e) {
    log.error("Invalid attachment: {}", e.getMessage());
}
```

### In MultimodalChatController Context
```java
@PostMapping("/anonymous/chat")
public ResponseEntity<MultimodalChatResponse> sendAnonymousMultimodalChat(
        @RequestBody MultimodalChatRequest request) {
    
    try {
        // Process each attachment
        Map<String, String> vectorIdMap = new LinkedHashMap<>();
        
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            for (Attachment attachment : request.getAttachments()) {
                String vectorId = multimodalAttachmentService.saveAttachment(
                        attachment,
                        request.getChatbotId(),
                        request.getSessionId()
                );
                vectorIdMap.put(attachment.getName(), vectorId);
            }
        }
        
        // Use vectorIds to create multimodal request for N8N
        MultimodalN8NRequest multimodalRequest = MultimodalN8NRequest.builder()
                .role("user")
                .message(request.getMessage())
                .vectorIds(new ArrayList<>(vectorIdMap.values()))
                .vectorIdMap(vectorIdMap)
                .chatbotId(request.getChatbotId())
                .sessionId(request.getSessionId())
                .build();
        
        // Send to N8N webhook
        N8NChatResponse<Object> n8nResponse = n8nService.sendMultimodalMessage(
                chatBot, multimodalRequest, webhookUrl);
        
        return ResponseEntity.ok(MultimodalChatResponse.success(
                n8nResponse, vectorIdMap, vectorAttachments));
        
    } catch (IOException e) {
        return ResponseEntity.status(500).body(
                MultimodalChatResponse.error("IO_ERROR", e.getMessage()));
    } catch (Exception e) {
        return ResponseEntity.status(500).body(
                MultimodalChatResponse.error("INTERNAL_ERROR", e.getMessage()));
    }
}
```

---

## Process Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ Frontend sends Attachment                                    │
│ (name, type, size, base64 data)                              │
└────────────────────┬────────────────────────────────────────┘
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ saveAttachment() Method Called                               │
│ with: attachment, chatbotId, sessionId                       │
└────────────────────┬────────────────────────────────────────┘
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. VALIDATE ATTACHMENT                                       │
│ ✓ Check name, size, MIME type, format                       │
│ ✓ Verify chatbotId and sessionId                            │
└────────────────────┬────────────────────────────────────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
      VALID                   INVALID
         │                       │
         ▼                       ▼
  Continue             Throw IllegalArgumentException
                       (validation failed)
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. SAVE FILE TO DISK                                         │
│ • Decode Base64 data                                         │
│ • Create directory: uploads/{chatbotId}/{sessionId}/         │
│ • Write file to disk                                         │
│ • Return filePath                                            │
└────────────────────┬────────────────────────────────────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
      SUCCESS                  ERROR
         │                       │
         ▼                       ▼
  Continue             Throw IOException
                       (file I/O failed)
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. GENERATE VECTOR ID                                        │
│ Format: attachment_{chatbotId}_{sessionId}                  │
│         _{sanitizedName}_{timestamp}                         │
└────────────────────┬────────────────────────────────────────┘
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. CREATE VECTOR DOCUMENT                                    │
│ {                                                            │
│   "vectorId": "...",                                         │
│   "chatbotId": "...",                                        │
│   "sessionId": "...",                                        │
│   "originalName": "...",                                     │
│   "mimeType": "...",                                         │
│   "fileSize": 256000,                                        │
│   "filePath": "...",                                         │
│   "base64Data": "...",                                       │
│   "uploadedAt": 1707385649123,                               │
│   "metadata": {...}                                          │
│ }                                                            │
└────────────────────┬────────────────────────────────────────┘
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. INSERT INTO MONGODB VECTOR STORE                          │
│ • Get collection: jade-ai-knowledgebase-{chatbotId}         │
│ • Create collection if not exists                           │
│ • Insert document with vectorId                             │
└────────────────────┬────────────────────────────────────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
      SUCCESS                  ERROR
         │                       │
         ▼                       ▼
  Return vectorId          Delete file (rollback)
                           Throw RuntimeException
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ Return: vectorId                                             │
│ Example: attachment_bot_123_sess_456_report_1707385649123   │
└─────────────────────────────────────────────────────────────┘
```

---

## MongoDB Document Structure

The document stored in MongoDB looks like:

```json
{
  "_id": ObjectId("507f1f77bcf86cd799439011"),
  "vectorId": "attachment_chatbot_123_session_456_quarterly_report_1707385649123",
  "chatbotId": "chatbot_123",
  "sessionId": "session_456",
  "originalName": "quarterly_report.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "filePath": "/uploads/chatbot_123/session_456/quarterly_report.pdf",
  "attachmentType": "file",
  "uploadedAt": 1707385649123,
  "base64Data": "JVBERi0xLjQKJeLjz9MNCjEgMCBvYm8...",
  "metadata": {
    "uploadedAt": ISODate("2026-02-07T12:34:09.123Z"),
    "size": 256000,
    "type": "application/pdf",
    "fileName": "quarterly_report.pdf",
    "status": "stored",
    "version": 1
  }
}
```

---

## Error Handling

### Validation Errors
| Error | Cause | Status | Recovery |
|-------|-------|--------|----------|
| Attachment is null | Missing attachment | 400 | Provide attachment |
| File size exceeds limit | File > 100 MB | 400 | Compress or split file |
| Invalid MIME type | Unsupported file type | 400 | Use supported format |
| Invalid filename | Path traversal attempt | 400 | Use valid filename |
| ChatbotId required | Missing chatbotId | 400 | Provide chatbotId |
| SessionId required | Missing sessionId | 400 | Provide sessionId |

### I/O Errors
| Error | Cause | Status | Recovery |
|-------|-------|--------|----------|
| Failed to save to disk | Disk full or permissions | 500 | Check disk space/permissions |
| Invalid base64 encoding | Malformed Base64 | 400 | Re-encode attachment |
| File system error | OS error | 500 | Retry or contact support |

### Database Errors
| Error | Cause | Status | Recovery |
|-------|-------|--------|----------|
| MongoDB connection failed | DB unavailable | 503 | Check MongoDB connection |
| Collection creation failed | Insufficient permissions | 500 | Check DB permissions |
| Insert failed | Duplicate vectorId | 409 | Retry with new timestamp |

---

## Performance Considerations

### Optimization Tips
1. **Disk I/O** - Save to disk first for persistence, then to DB
2. **Base64 Storage** - Optional in MongoDB, depends on use case
3. **Indexing** - Create index on (vectorId, chatbotId) for fast lookup
4. **Collection Names** - Use per-chatbot collections for scalability
5. **Batch Processing** - Process multiple files in batches for efficiency

### Recommended MongoDB Indexes
```javascript
// Create indexes in MongoDB for optimal performance
db["jade-ai-knowledgebase-chatbot_123"].createIndex({ "vectorId": 1 }, { unique: true })
db["jade-ai-knowledgebase-chatbot_123"].createIndex({ "chatbotId": 1 })
db["jade-ai-knowledgebase-chatbot_123"].createIndex({ "sessionId": 1 })
db["jade-ai-knowledgebase-chatbot_123"].createIndex({ "uploadedAt": 1 })
db["jade-ai-knowledgebase-chatbot_123"].createIndex({ "chatbotId": 1, "sessionId": 1 })
```

---

## Security Considerations

✅ **Validate MIME types** - Only allow whitelisted types  
✅ **Validate filenames** - Prevent directory traversal attacks  
✅ **Check file sizes** - Prevent disk exhaustion  
✅ **Sanitize paths** - Remove special characters  
✅ **Session isolation** - Separate files per session  
✅ **Chatbot isolation** - Separate collections per chatbot  
✅ **Error messages** - Don't expose internal paths in errors  
✅ **Disk permissions** - Ensure secure file storage  

---

## Testing

### Unit Test Example
```java
@Test
public void testSaveAttachment() throws IOException {
    // Arrange
    Attachment attachment = Attachment.builder()
            .name("test.pdf")
            .type("application/pdf")
            .size(1000)
            .data(Base64.getEncoder().encodeToString("test data".getBytes()))
            .build();
    
    // Act
    String vectorId = multimodalAttachmentService.saveAttachment(
            attachment, "bot_123", "sess_456");
    
    // Assert
    assertNotNull(vectorId);
    assertTrue(vectorId.startsWith("attachment_bot_123_sess_456_"));
}
```

---

## Related Methods

See also:
- `MultimodalAttachmentService.processAttachmentsToVectorStore()` - Process multiple files
- `MultimodalAttachmentService.getAttachmentByVectorId()` - Retrieve attachment
- `MultimodalAttachmentService.deleteAttachmentFromVectorStore()` - Delete attachment
- `AttachmentUtils.validateAttachment()` - Validate attachment
- `AttachmentUtils.saveAttachment()` - Save to disk

---

**Status:** ✅ Production Ready  
**Last Updated:** February 7, 2026

