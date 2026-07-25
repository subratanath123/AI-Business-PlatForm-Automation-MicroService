# Quick Start: Store Metadata (fileId) in OpenAI Vector Store

**Date:** February 9, 2026  
**Quick Reference:** How to store and retrieve fileId metadata

---

## 📌 TL;DR

### ✅ Store fileId in OpenAI Vector Store

OpenAI Vector Store **DOES NOT support file-level metadata** in the Vector Store Files API, BUT you can:

1. **Store at Vector Store creation** (for immutable data like `chatbotId`)
2. **Store in MongoDB** (for file-specific data like `fileId`)
3. **Link them together** using unique identifiers

---

## 🎯 Your Scenario

**Question:** Can I store `fileId` when saving attachments to vector store?

**Answer:** Yes! Using a two-part approach:

```
┌─────────────────────────────────────────┐
│  OpenAI Vector Store (Immutable)        │
│  ├─ vectorStoreId: "vs_abc123"          │
│  ├─ metadata: {                         │
│  │   ├─ chatbotId: "bot_123"            │
│  │   └─ createdAt: "2026-02-09"         │
│  └─ }                                   │
└─────────────────────────────────────────┘
            ↕ Links via chatbotId
┌─────────────────────────────────────────┐
│  MongoDB (Dynamic)                      │
│  ├─ fileId: "file-xyz"              ✨  │
│  ├─ vectorStoreFileId: "vs_file_001" ✨ │
│  ├─ vectorStoreId: "vs_abc123"       ✨  │
│  ├─ fileName: "report.pdf"           ✨  │
│  ├─ mimeType: "application/pdf"      ✨  │
│  └─ custom metadata...               ✨  │
└─────────────────────────────────────────┘
```

---

## ⚡ Quick Implementation

### Step 1: Vector Store is Created with Metadata

**File:** `AttachmentSaveService.java` (Line 558-600)

```java
private String createNewVectorStore(String chatbotId) {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("name", "vector_store_" + chatbotId);
    
    // ✨ Metadata at vector store level
    Map<String, String> metadata = new HashMap<>();
    metadata.put("chatbotId", chatbotId);
    metadata.put("createdAt", String.valueOf(System.currentTimeMillis()));
    requestBody.put("metadata", metadata);
    
    // Send to OpenAI...
}
```

### Step 2: File Metadata is Saved to MongoDB

**File:** `AttachmentSaveService.java` (Line 731-773)

```java
private void saveAttachmentMetadata(Attachment attachment, String fileId, 
                                   String vectorStoreFileId, String chatbotId, 
                                   String sessionId, String vectorStoreId) {
    
    Document metadata = new Document()
            // ✨ CRITICAL LINKS TO OPENAI
            .append("fileId", fileId)                          // Link to OpenAI file
            .append("vectorStoreFileId", vectorStoreFileId)    // Link to OpenAI vector store file
            .append("vectorStoreId", vectorStoreId)            // Link to OpenAI vector store
            
            // Context
            .append("chatbotId", chatbotId)
            .append("sessionId", sessionId)
            
            // ✨ FILE METADATA
            .append("originalName", attachment.getName())
            .append("mimeType", attachment.getMimeType())
            .append("fileSize", attachment.getSize())
            
            // Timestamps
            .append("uploadedAt", System.currentTimeMillis())
            .append("createdAt", new Date());
    
    // Save to MongoDB
    String collectionName = "attachments_" + chatbotId;
    mongoTemplate.getCollection(collectionName).insertOne(metadata);
}
```

---

## 🔍 How to Query fileId

### From MongoDB

```java
// Get metadata by fileId
Document metadata = mongoTemplate.getCollection("attachments_chatbot_123")
    .find(new Document("fileId", "file-xyz"))
    .first();

// Access the fields
String fileName = metadata.getString("originalName");
String vectorStoreFileId = metadata.getString("vectorStoreFileId");
String vectorStoreId = metadata.getString("vectorStoreId");
```

### Using MongoDB Shell

```javascript
// List all files with their fileId
db.attachments_chatbot_123.find({}, {
    fileId: 1,
    vectorStoreFileId: 1,
    originalName: 1,
    uploadedAt: 1
}).pretty()

// Find by fileId
db.attachments_chatbot_123.findOne({ "fileId": "file-xyz" })

// Count files
db.attachments_chatbot_123.find({ "fileId": { $exists: true } }).count()
```

---

## 🛠️ Add Custom Metadata Fields

### To Store Additional Data in MongoDB:

```java
private void saveAttachmentMetadata(Attachment attachment, String fileId, 
                                   String vectorStoreFileId, String chatbotId, 
                                   String sessionId, String vectorStoreId) {
    
    Document metadata = new Document()
            .append("fileId", fileId)
            .append("vectorStoreFileId", vectorStoreFileId)
            .append("vectorStoreId", vectorStoreId)
            .append("chatbotId", chatbotId)
            .append("sessionId", sessionId)
            .append("originalName", attachment.getName())
            .append("mimeType", attachment.getMimeType())
            .append("fileSize", attachment.getSize())
            
            // ✨ ADD YOUR CUSTOM METADATA HERE
            .append("userId", "user_123")
            .append("department", "sales")
            .append("project", "Q1_2026")
            .append("description", "Quarterly report")
            .append("isConfidential", true)
            .append("expiresAt", new Date(System.currentTimeMillis() + 30*24*60*60*1000))
            
            .append("uploadedAt", System.currentTimeMillis())
            .append("createdAt", new Date());
    
    mongoTemplate.getCollection("attachments_" + chatbotId).insertOne(metadata);
}
```

### Query by Custom Field:

```javascript
// Find files uploaded by a specific user
db.attachments_chatbot_123.find({ "userId": "user_123" })

// Find confidential files
db.attachments_chatbot_123.find({ "isConfidential": true })

// Find files expiring soon
db.attachments_chatbot_123.find({ 
    "expiresAt": { $lt: new Date() }
})
```

---

## 🚀 Complete Workflow

### 1. Upload File to OpenAI
```
POST /v1/files
→ Returns: fileId = "file-xyz123"
```

### 2. Add to Vector Store
```
POST /v1/vector_stores/vs_abc123/files
Body: { "file_id": "file-xyz123" }
→ Returns: vectorStoreFileId = "vs_file_001"
```

### 3. Save Metadata to MongoDB
```
Collection: attachments_chatbot_123
Document: {
  fileId: "file-xyz123",
  vectorStoreFileId: "vs_file_001",
  vectorStoreId: "vs_abc123",
  originalName: "report.pdf",
  mimeType: "application/pdf",
  fileSize: 256000,
  uploadedAt: ISODate("2026-02-09T...")
}
```

### 4. Retrieve by fileId
```java
Document doc = mongoTemplate.getCollection("attachments_chatbot_123")
    .find(new Document("fileId", "file-xyz123"))
    .first();
```

---

## 📋 MongoDB Collection Structure

**Collection Name:** `attachments_{chatbotId}`

**Example Document:**
```json
{
  "_id": ObjectId("..."),
  "fileId": "file-xyz123",
  "vectorStoreFileId": "vs_file_001",
  "vectorStoreId": "vs_abc123",
  "chatbotId": "chatbot_123",
  "sessionId": "session_456",
  "originalName": "report.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "uploadedAt": 1707385649000,
  "createdAt": ISODate("2026-02-09T10:30:00Z"),
  "status": "stored",
  "source": "openai_vector_store",
  "version": 1
}
```

---

## ✅ Current Implementation Status

| Component | Status | Notes |
|-----------|--------|-------|
| Vector Store Creation | ✅ Done | Includes `chatbotId` metadata |
| File Upload to OpenAI | ✅ Done | Returns `fileId` |
| Add to Vector Store | ✅ Done | Returns `vectorStoreFileId` |
| MongoDB Metadata Save | ✅ Done | Stores all three IDs |
| Link fileId → MongoDB | ✅ Done | Via "fileId" field |
| Link vectorStoreFileId → MongoDB | ✅ Done | Via "vectorStoreFileId" field |

---

## 🎓 Key Concepts

### OpenAI IDs

| ID | Example | Purpose |
|----|---------|---------|
| **fileId** | `file-xyz123` | Identifies file in OpenAI Files API |
| **vectorStoreId** | `vs_abc123` | Identifies vector store |
| **vectorStoreFileId** | `vs_file_001` | Identifies file within vector store |

### Metadata Storage

| Location | Mutable | Use Case |
|----------|---------|----------|
| **OpenAI Vector Store** | ❌ No | Store immutable data (chatbotId, createdAt) |
| **MongoDB** | ✅ Yes | Store file metadata (fileId, custom fields) |

### Query Flow

```
Query by fileId
    ↓
db.attachments_chatbot_123.findOne({ "fileId": "file-xyz" })
    ↓
Returns: fileId, vectorStoreFileId, vectorStoreId, custom metadata
    ↓
Can use these IDs to query OpenAI if needed
```

---

## 💡 Pro Tips

### 1. Create Indexes for Performance
```javascript
db.attachments_chatbot_123.createIndex({ "fileId": 1 }, { unique: true })
db.attachments_chatbot_123.createIndex({ "vectorStoreFileId": 1 })
db.attachments_chatbot_123.createIndex({ "uploadedAt": -1 })
```

### 2. Store Related Data Together
```java
.append("fileId", fileId)                    // Always store all three IDs
.append("vectorStoreFileId", vectorStoreFileId)
.append("vectorStoreId", vectorStoreId)
```

### 3. Use Consistent Naming
```java
.append("fileId", fileId)                    // OpenAI file ID
.append("originalName", attachment.getName())  // Original filename
.append("uploadedAt", timestamp)             // When uploaded
```

---

## 📞 Support

### If You Want to...

**Query files by chatbot:**
```javascript
db.attachments_chatbot_123.find()
```

**Get file info by fileId:**
```java
Document doc = mongoTemplate.getCollection("attachments_chatbot_123")
    .find(new Document("fileId", "file-xyz"))
    .first();
```

**Update metadata for a file:**
```javascript
db.attachments_chatbot_123.updateOne(
    { "fileId": "file-xyz" },
    { $set: { "userId": "user_456" } }
)
```

**Delete file metadata:**
```javascript
db.attachments_chatbot_123.deleteOne({ "fileId": "file-xyz" })
```

---

**✅ Status:** Production Ready  
**Last Updated:** February 9, 2026

