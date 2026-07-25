# Vector Store File ID Flow - Verification

**Date:** February 7, 2026  
**Issue:** Confirming that `vs_*` IDs are returned, not file IDs

---

## 📊 Expected Flow

```
1. Upload File to OpenAI Files API
   POST /files
   Response: { "id": "file-abc123xyz789", "object": "file", ... }
   ↓ Store as: fileId (file-abc123xyz789)

2. Add File to Vector Store
   POST /vector_stores/{vs_id}/files
   Request: { "file_id": "file-abc123xyz789", "chunking_strategy": {...} }
   Response: { "id": "vs_69875f8ecf988191aa944a15519a2904", "object": "vector_store.file", ... }
   ↓ Store as: vectorStoreFileId (vs_...)

3. Return to Controller
   vectorStoreFileId = "vs_69875f8ecf988191aa944a15519a2904" ✅

4. Add to vectorStoreFileIdMap
   { "filename.pdf": "vs_69875f8ecf988191aa944a15519a2904" } ✅

5. Return in Response
   {
     "success": true,
     "vectorIdMap": {
       "filename.pdf": "vs_69875f8ecf988191aa944a15519a2904"
     },
     "vectorAttachments": [
       {
         "vectorId": "vs_69875f8ecf988191aa944a15519a2904",
         "fileName": "filename.pdf",
         ...
       }
     ]
   } ✅
```

---

## 🔍 Code Verification

### AttachmentSaveService.saveAttachment() - Line 112

```java
vectorStoreFileId = addToVectorStore(fileId, chatbotId, sessionId);
//                   ↑ Returns vs_* ID
```

### AttachmentSaveService.addToVectorStore() - Line 309-315

```java
String vectorStoreFileId = (String) response.get("id");
// The "id" field from OpenAI's response is the vector store file ID
// Format: "vs_69875f8ecf988191aa944a15519a2904"

if (vectorStoreFileId == null) {
    log.error("No 'id' field in response. Available keys: {}", response.keySet());
    throw new RuntimeException("No 'id' field in OpenAI Vector Store API response");
}
```

### AttachmentSaveService.saveAttachment() - Line 143

```java
return vectorStoreFileId;  // ✅ Returns vs_* ID to controller
```

### MultimodalN8NChatController.sendAnonymousMultimodalChat() - Line 111-117

```java
String vectorStoreFileId = attachmentSaveService.saveAttachment(...);
// vectorStoreFileId = "vs_69875f8ecf988191aa944a15519a2904"

vectorStoreFileIdMap.put(attachment.getName(), vectorStoreFileId);
// vectorStoreFileIdMap = {"test.pdf": "vs_69875f8ecf988191aa944a15519a2904"}
```

### Response - Line 171

```java
return ResponseEntity.ok(MultimodalChatResponse.success(
        n8nResponse,
        vectorStoreFileIdMap,  // ✅ Contains vs_* IDs
        vectorAttachments      // ✅ Contains vs_* IDs in vectorId field
));
```

---

## ✅ Verification Checklist

- [x] AttachmentSaveService.saveAttachment() returns vectorStoreFileId
- [x] addToVectorStore() extracts "id" field from OpenAI response
- [x] "id" field is the vector store file ID (vs_*)
- [x] Controller receives vectorStoreFileId
- [x] vectorStoreFileIdMap populated with vs_* IDs
- [x] Response includes vectorIdMap with vs_* IDs
- [x] Response includes vectorAttachments with vectorId as vs_* ID

---

## 🧪 What You Should See in Response

```json
{
  "success": true,
  "result": { ... },
  "vectorIdMap": {
    "document.pdf": "vs_69875f8ecf988191aa944a15519a2904",
    "image.png": "vs_73b5e9c2d1e4f6a9b3c7d8e1f2a3b4c5"
  },
  "vectorAttachments": [
    {
      "vectorId": "vs_69875f8ecf988191aa944a15519a2904",
      "fileName": "document.pdf",
      "mimeType": "application/pdf",
      "fileSize": 52000,
      "uploadedAt": 1707385649000
    },
    {
      "vectorId": "vs_73b5e9c2d1e4f6a9b3c7d8e1f2a3b4c5",
      "fileName": "image.png",
      "mimeType": "image/png",
      "fileSize": 28000,
      "uploadedAt": 1707385650000
    }
  ]
}
```

---

## 📋 Debugging Tips

If you're seeing file IDs instead of vector store IDs:

1. **Check logs for:** `File added to vector store: vectorStoreFileId=vs_...`
2. **Verify OpenAI response** includes "id" field with vs_* format
3. **Check AttachmentSaveService.addToVectorStore()** is extracting the right field

The code is already correct and should return vs_* IDs! ✅

