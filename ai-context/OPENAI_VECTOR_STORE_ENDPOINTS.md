# OpenAI Vector Store API - Correct Endpoints Guide

## 🔴 Issue: Invalid URL Error

**Error:** `Invalid URL (GET /v1/vector_stores/vs_6988c402e464819194410cadf5845751/associations)`

**Cause:** The `/associations` endpoint doesn't exist in OpenAI's Vector Store API

---

## ✅ Correct OpenAI Vector Store Endpoints

### 1. Create Vector Store
```
POST /v1/vector_stores
Content-Type: application/json

{
  "name": "My Vector Store",
  "metadata": {
    "chatbotId": "chatbot-123"
  }
}

Response: { "id": "vs_abc123", ... }
```

### 2. Upload File to OpenAI Files API
```
POST /v1/files
Content-Type: multipart/form-data

file: <binary>
purpose: assistants

Response: { "id": "file-abc123", ... }
```

### 3. Add File to Vector Store
```
POST /v1/vector_stores/{vector_store_id}/files
Content-Type: application/json

{
  "file_id": "file-abc123",
  "chunking_strategy": {
    "type": "auto"
  }
}

Response: { "id": "vs_abc123_file_001", ... }
```

### 4. ✅ List Files in Vector Store (CORRECT ENDPOINT)
```
GET /v1/vector_stores/{vector_store_id}/files
Headers: Authorization: Bearer {API_KEY}

Response: {
  "object": "list",
  "data": [
    {
      "id": "vs_abc123_file_001",
      "created_at": 1708123456,
      "vector_store_id": "vs_abc123",
      "status": "completed",
      "file_id": "file-abc123"
    }
  ]
}
```

### 5. ❌ DO NOT USE: Associations Endpoint
This endpoint does NOT exist:
```
❌ GET /v1/vector_stores/{vector_store_id}/associations  <- INVALID
```

---

## 🔧 How to Fix This in N8N or Frontend

### If Error is from N8N:
Make sure N8N is calling the correct endpoint:
```javascript
// ❌ WRONG
GET /v1/vector_stores/vs_6988c402e464819194410cadf5845751/associations

// ✅ CORRECT
GET /v1/vector_stores/vs_6988c402e464819194410cadf5845751/files
```

### If Error is from Frontend:
Update your API call:
```javascript
// ❌ WRONG
const url = `https://api.openai.com/v1/vector_stores/${vectorStoreId}/associations`;

// ✅ CORRECT
const url = `https://api.openai.com/v1/vector_stores/${vectorStoreId}/files`;

const response = await fetch(url, {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${OPENAI_API_KEY}`,
    'OpenAI-Beta': 'assistants=v2'
  }
});

const files = await response.json();
console.log(files.data); // Array of files in vector store
```

---

## 📋 Complete Vector Store Operations

### List All Files in a Vector Store
```bash
curl https://api.openai.com/v1/vector_stores/vs_6988c402e464819194410cadf5845751/files \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "OpenAI-Beta: assistants=v2"
```

### Get Specific File in Vector Store
```bash
curl https://api.openai.com/v1/vector_stores/vs_6988c402e464819194410cadf5845751/files/vs_abc123_file_001 \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "OpenAI-Beta: assistants=v2"
```

### Delete File from Vector Store
```bash
curl -X DELETE https://api.openai.com/v1/vector_stores/vs_6988c402e464819194410cadf5845751/files/vs_abc123_file_001 \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "OpenAI-Beta: assistants=v2"
```

### Delete Vector Store
```bash
curl -X DELETE https://api.openai.com/v1/vector_stores/vs_6988c402e464819194410cadf5845751 \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "OpenAI-Beta: assistants=v2"
```

---

## 🚀 Creating a Helper Service to List Vector Store Files

### Java Implementation
```java
@Service
@Slf4j
public class VectorStoreFileService {
    
    @Value("${openai.api.key}")
    private String openaiApiKey;
    
    @Value("${openai.api.base.url:https://api.openai.com/v1}")
    private String openaiBaseUrl;
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * List all files in a vector store
     * 
     * @param vectorStoreId the vector store ID (vs_*)
     * @return list of files in the vector store
     */
    public List<VectorStoreFile> listVectorStoreFiles(String vectorStoreId) {
        try {
            String url = String.format("%s/vector_stores/%s/files", openaiBaseUrl, vectorStoreId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openaiApiKey);
            headers.set("OpenAI-Beta", "assistants=v2");
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                return data.stream()
                    .map(this::mapToVectorStoreFile)
                    .collect(Collectors.toList());
            }
            
            log.warn("No files found in vector store: {}", vectorStoreId);
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Failed to list vector store files: {}", vectorStoreId, e);
            throw new RuntimeException("Failed to list files: " + e.getMessage());
        }
    }
    
    /**
     * Get specific file from vector store
     * 
     * @param vectorStoreId the vector store ID
     * @param fileId the file ID within the vector store
     * @return file details
     */
    public VectorStoreFile getVectorStoreFile(String vectorStoreId, String fileId) {
        try {
            String url = String.format("%s/vector_stores/%s/files/%s", 
                openaiBaseUrl, vectorStoreId, fileId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openaiApiKey);
            headers.set("OpenAI-Beta", "assistants=v2");
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            return mapToVectorStoreFile(response.getBody());
            
        } catch (Exception e) {
            log.error("Failed to get vector store file: {}/{}", vectorStoreId, fileId, e);
            throw new RuntimeException("Failed to get file: " + e.getMessage());
        }
    }
    
    /**
     * Delete file from vector store
     * 
     * @param vectorStoreId the vector store ID
     * @param fileId the file ID within the vector store
     * @return success status
     */
    public boolean deleteVectorStoreFile(String vectorStoreId, String fileId) {
        try {
            String url = String.format("%s/vector_stores/%s/files/%s", 
                openaiBaseUrl, vectorStoreId, fileId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openaiApiKey);
            headers.set("OpenAI-Beta", "assistants=v2");
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                entity,
                Void.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.error("Failed to delete vector store file: {}/{}", vectorStoreId, fileId, e);
            return false;
        }
    }
    
    private VectorStoreFile mapToVectorStoreFile(Map<String, Object> data) {
        return VectorStoreFile.builder()
            .id((String) data.get("id"))
            .createdAt(((Number) data.get("created_at")).longValue())
            .vectorStoreId((String) data.get("vector_store_id"))
            .status((String) data.get("status"))
            .fileId((String) data.get("file_id"))
            .build();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class VectorStoreFile {
    private String id;
    private long createdAt;
    private String vectorStoreId;
    private String status;
    private String fileId;
}
```

### Controller Endpoint
```java
@GetMapping("/vector-stores/{vectorStoreId}/files")
public ResponseEntity<List<VectorStoreFile>> listVectorStoreFiles(
        @PathVariable String vectorStoreId) {
    try {
        List<VectorStoreFile> files = vectorStoreFileService.listVectorStoreFiles(vectorStoreId);
        return ResponseEntity.ok(files);
    } catch (Exception e) {
        return ResponseEntity.status(500).build();
    }
}
```

---

## 📝 Summary of Changes

| Item | Wrong ❌ | Correct ✅ |
|------|---------|----------|
| List Files Endpoint | `/vector_stores/{id}/associations` | `/vector_stores/{id}/files` |
| Get File Endpoint | `/vector_stores/{id}/association/{fileId}` | `/vector_stores/{id}/files/{fileId}` |
| HTTP Method | POST | GET |
| Headers | `Content-Type: application/json` | `OpenAI-Beta: assistants=v2` |

---

## 🔗 Reference

**OpenAI Official Docs:**
- Vector Stores: https://platform.openai.com/docs/api-reference/vector-stores
- Vector Store Files: https://platform.openai.com/docs/api-reference/vector-store-files
- Files: https://platform.openai.com/docs/api-reference/files

---

**Status:** ✅ Production Ready  
**Updated:** 2024-02-17

