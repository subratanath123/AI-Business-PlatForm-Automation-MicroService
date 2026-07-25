# OpenAI Vector Store Metadata Storage Guide

**Date:** February 9, 2026  
**Topic:** Storing Metadata (like fileId) in OpenAI Vector Store  
**Status:** ✅ Production Ready

---

## 🎯 Overview

You can store metadata in OpenAI Vector Store in **two ways**:

### 1. **Vector Store Level Metadata** (Recommended)
Store metadata when creating the Vector Store - this applies to the entire store

### 2. **MongoDB Metadata Mapping** (Flexible)
Store file-specific metadata in MongoDB and link it via `fileId` and `vectorStoreFileId`

---

## ✨ Approach 1: Vector Store Metadata (on Creation)

OpenAI's Vector Store API **supports metadata at the Vector Store level** when creating a new store.

### Current Implementation (Already in Your Code)

```java
private String createNewVectorStore(String chatbotId) {
    // Create request body with metadata
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("name", "vector_store_" + chatbotId);
    
    // ✨ ADD METADATA HERE
    Map<String, String> metadata = new HashMap<>();
    metadata.put("chatbotId", chatbotId);
    metadata.put("createdAt", String.valueOf(System.currentTimeMillis()));
    metadata.put("environment", "production");
    requestBody.put("metadata", metadata);
    
    // Send to OpenAI
    // POST /v1/vector_stores
}
```

### What Gets Stored in OpenAI

When you create a vector store with metadata, OpenAI stores it and returns it:

```json
{
  "id": "vs_abc123",
  "object": "vector_store",
  "created_at": 1707385649,
  "name": "vector_store_chatbot_123",
  "metadata": {
    "chatbotId": "chatbot_123",
    "createdAt": "1707385649123",
    "environment": "production"
  },
  "file_counts": {
    "in_progress": 0,
    "completed": 5,
    "failed": 0,
    "cancelled": 0,
    "total": 5
  }
}
```

### Query Vector Store Metadata from OpenAI

To retrieve the metadata later:

```java
/**
 * Get vector store including metadata
 */
public Map<String, Object> getVectorStoreWithMetadata(String vectorStoreId) {
    try {
        String url = String.format("%s/vector_stores/%s", openaiBaseUrl, vectorStoreId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + openaiApiKey);
        headers.set("OpenAI-Beta", "assistants=v2");
        
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, Map.class
        ).getBody();
        
        log.info("Vector Store metadata: {}", response);
        return response;
        
    } catch (Exception e) {
        log.error("Failed to get vector store: {}", vectorStoreId, e);
        throw new RuntimeException("Failed to get vector store: " + e.getMessage());
    }
}
```

### Add More Metadata

You can enhance metadata during vector store creation:

```java
private String createNewVectorStore(String chatbotId) {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("name", "vector_store_" + chatbotId);
    
    // ✨ EXTENDED METADATA
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("chatbotId", chatbotId);
    metadata.put("createdAt", String.valueOf(System.currentTimeMillis()));
    metadata.put("version", "1.0");
    metadata.put("environment", "production");
    metadata.put("owner", "chatbot_team");
    metadata.put("tags", Arrays.asList("important", "customer-data"));
    metadata.put("retention", "30days");
    requestBody.put("metadata", metadata);
    
    // ... rest of implementation
}
```

---

## 📁 Approach 2: File-Level Metadata in MongoDB

Since **OpenAI Vector Store Files API doesn't support metadata**, we store file-specific metadata in MongoDB.

### Current Implementation (Already Enhanced)

```java
private void saveAttachmentMetadata(Attachment attachment, String fileId, 
                                   String vectorStoreFileId, String chatbotId, String sessionId,
                                   String vectorStoreId) {
    
    Document metadata = new Document()
            // OpenAI identifiers (LINK TO OPENAI)
            .append("fileId", fileId)
            .append("vectorStoreId", vectorStoreId)
            .append("vectorStoreFileId", vectorStoreFileId)
            
            // Context
            .append("chatbotId", chatbotId)
            .append("sessionId", sessionId)
            
            // File information
            .append("originalName", attachment.getName())
            .append("mimeType", attachment.getMimeType())
            .append("fileSize", attachment.getSize())
            
            // ✨ CUSTOM METADATA
            .append("metadata", new Document()
                    .append("uploadedAt", new Date())
                    .append("fileName", attachment.getName())
                    .append("fileType", attachment.getMimeType())
                    .append("status", "stored")
                    .append("customFields", attachment.getMetadata())
            )
            
            .append("uploadedAt", System.currentTimeMillis())
            .append("createdAt", new Date())
            .append("status", "stored")
            .append("source", "openai_vector_store")
            .append("version", 1);
    
    // Save to MongoDB collection
    mongoTemplate.getCollection("attachments_" + chatbotId).insertOne(metadata);
}
```

### MongoDB Storage Structure

**Collection:** `attachments_{chatbotId}`

**Document Example:**
```json
{
  "_id": ObjectId("..."),
  "fileId": "file-abc123xyz",
  "vectorStoreFileId": "vs_file_001",
  "vectorStoreId": "vs_abc123",
  "chatbotId": "chatbot_123",
  "sessionId": "session_456",
  "originalName": "report.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "metadata": {
    "uploadedAt": ISODate("2026-02-09T10:30:00Z"),
    "fileName": "report.pdf",
    "fileType": "application/pdf",
    "status": "stored",
    "customFields": {
      "userId": "user_123",
      "department": "sales",
      "project": "Q1_2026"
    }
  },
  "createdAt": ISODate("2026-02-09T10:30:00Z"),
  "status": "stored",
  "source": "openai_vector_store",
  "version": 1
}
```

---

## 🔄 Query Metadata by fileId

### From MongoDB

```java
/**
 * Retrieve file metadata from MongoDB using fileId
 */
public Document getFileMetadata(String chatbotId, String fileId) {
    try {
        String collectionName = "attachments_" + chatbotId;
        Document fileMetadata = mongoTemplate.getCollection(collectionName)
            .find(new Document("fileId", fileId))
            .first();
        
        if (fileMetadata != null) {
            log.info("Found metadata for fileId: {}", fileId);
            return fileMetadata;
        }
        
        log.warn("No metadata found for fileId: {}", fileId);
        return null;
        
    } catch (Exception e) {
        log.error("Failed to get file metadata: {}", fileId, e);
        return null;
    }
}

// Usage
Document metadata = getFileMetadata("chatbot_123", "file-abc123xyz");
String fileName = metadata.getString("originalName");
Document customFields = (Document) metadata.get("metadata");
```

### From MongoDB (Query by vectorStoreFileId)

```javascript
// MongoDB Shell
db.attachments_chatbot_123.findOne(
    { "vectorStoreFileId": "vs_file_001" }
)
```

---

## 🛠️ Creating a Metadata Retrieval Service

Here's a complete service to manage metadata:

```java
@Service
@Slf4j
public class AttachmentMetadataService {
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    /**
     * Get file metadata by fileId
     */
    public Document getMetadataByFileId(String chatbotId, String fileId) {
        return mongoTemplate.getCollection("attachments_" + chatbotId)
            .find(new Document("fileId", fileId))
            .first();
    }
    
    /**
     * Get file metadata by vectorStoreFileId
     */
    public Document getMetadataByVectorStoreFileId(String chatbotId, String vectorStoreFileId) {
        return mongoTemplate.getCollection("attachments_" + chatbotId)
            .find(new Document("vectorStoreFileId", vectorStoreFileId))
            .first();
    }
    
    /**
     * Get all files for a chatbot
     */
    public List<Document> getAllFiles(String chatbotId) {
        return mongoTemplate.getCollection("attachments_" + chatbotId)
            .find()
            .into(new ArrayList<>());
    }
    
    /**
     * Update metadata for a file
     */
    public void updateMetadata(String chatbotId, String fileId, Document newMetadata) {
        mongoTemplate.getCollection("attachments_" + chatbotId)
            .updateOne(
                new Document("fileId", fileId),
                new Document("$set", new Document("metadata", newMetadata))
            );
        
        log.info("Updated metadata for fileId: {}", fileId);
    }
    
    /**
     * Add a custom metadata field
     */
    public void addCustomField(String chatbotId, String fileId, String key, Object value) {
        mongoTemplate.getCollection("attachments_" + chatbotId)
            .updateOne(
                new Document("fileId", fileId),
                new Document("$set", new Document("metadata." + key, value))
            );
        
        log.info("Added custom field {} for fileId: {}", key, fileId);
    }
    
    /**
     * Delete file metadata
     */
    public void deleteMetadata(String chatbotId, String fileId) {
        mongoTemplate.getCollection("attachments_" + chatbotId)
            .deleteOne(new Document("fileId", fileId));
        
        log.info("Deleted metadata for fileId: {}", fileId);
    }
}
```

---

## 📊 Comparison: Vector Store vs MongoDB Metadata

| Feature | OpenAI Vector Store | MongoDB |
|---------|---------------------|---------|
| **Where** | Stored in OpenAI | Stored in MongoDB |
| **When** | Vector Store creation time | File upload time |
| **Scope** | Entire vector store | Per file |
| **Mutable** | No (fixed on creation) | Yes (can update) |
| **Query** | GET `/vector_stores/{id}` | MongoDB queries |
| **Use Case** | Store immutable info | Store dynamic metadata |

---

## 🚀 Complete Example Workflow

### Step 1: Create Vector Store with Metadata

```java
// When first file is uploaded for a chatbot
String vectorStoreId = createNewVectorStore("chatbot_123");
// Metadata is automatically added:
// {
//   "chatbotId": "chatbot_123",
//   "createdAt": "1707385649123"
// }
```

### Step 2: Upload File and Save Metadata

```java
// Upload file to OpenAI
String fileId = uploadToOpenAIFilesAPI(tempFilePath, attachment);
// Returns: "file-abc123"

// Add to vector store
String vectorStoreFileId = addToVectorStore(fileId, vectorStoreId);
// Returns: "vs_file_001"

// Save file metadata to MongoDB
saveAttachmentMetadata(
    attachment,           // originalName, mimeType, size
    fileId,              // "file-abc123"
    vectorStoreFileId,   // "vs_file_001"
    chatbotId,           // "chatbot_123"
    sessionId,           // "session_456"
    vectorStoreId        // "vs_abc123"
);
// Stored in: db.attachments_chatbot_123
```

### Step 3: Retrieve Metadata

```java
// Get metadata from MongoDB by fileId
Document metadata = getFileMetadata("chatbot_123", "file-abc123");

// Access the data
String fileName = metadata.getString("originalName");
String mimeType = metadata.getString("mimeType");
long fileSize = metadata.getLong("fileSize");
Document customData = (Document) metadata.get("metadata");
```

---

## 💡 Best Practices

### 1. **Use Vector Store Metadata for Immutable Data**
```java
// Store once at vector store creation
metadata.put("chatbotId", chatbotId);        // ✅ Immutable
metadata.put("createdAt", currentTime);      // ✅ Immutable
metadata.put("owner", "team_name");          // ✅ Immutable
```

### 2. **Use MongoDB for Dynamic/File-Specific Data**
```java
// Store in MongoDB for each file
metadata.put("userId", userId);              // ✅ Can change
metadata.put("status", "active");            // ✅ Can update
metadata.put("accessCount", 42);             // ✅ Can increment
metadata.put("lastAccessedAt", timestamp);   // ✅ Can update
```

### 3. **Maintain Backward Compatibility**
Always store `fileId` and `vectorStoreFileId` in MongoDB to link back to OpenAI:
```java
.append("fileId", fileId)                    // ✅ Link to OpenAI
.append("vectorStoreFileId", vectorStoreFileId)  // ✅ Link to OpenAI
```

### 4. **Create Indexes for Performance**
```javascript
// Create index for faster lookups
db.attachments_chatbot_123.createIndex(
    { "fileId": 1 }, 
    { unique: true }
);

db.attachments_chatbot_123.createIndex(
    { "vectorStoreFileId": 1 }
);

db.attachments_chatbot_123.createIndex(
    { "uploadedAt": 1 }
);
```

---

## 🔗 OpenAI API References

- **Create Vector Store:** https://platform.openai.com/docs/api-reference/vector-stores/create
- **Get Vector Store:** https://platform.openai.com/docs/api-reference/vector-stores/retrieve
- **Vector Store Files:** https://platform.openai.com/docs/api-reference/vector-store-files

---

## ✅ Summary

**✨ Store metadata in OpenAI Vector Store:**
- Use `metadata` field when creating a vector store
- Limited to immutable, vector-store-level data

**✨ Store metadata for individual files:**
- Use MongoDB with `fileId` and `vectorStoreFileId` links
- Store file-specific, dynamic metadata
- Full flexibility for custom fields

**✨ Your Current Implementation:**
- ✅ Already creates vector stores with `chatbotId` and `createdAt`
- ✅ Already stores file metadata in MongoDB
- ✅ Already links both with `fileId` and `vectorStoreFileId`

---

**Status:** ✅ Production Ready  
**Last Updated:** February 9, 2026

