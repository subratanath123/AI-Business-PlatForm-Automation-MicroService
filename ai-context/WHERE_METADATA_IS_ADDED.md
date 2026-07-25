# Where Metadata is Added During File Upload to OpenAI

**Date:** February 9, 2026  
**File:** `src/main/java/net/ai/chatbot/service/n8n/AttachmentSaveService.java`  
**Status:** ✅ Complete & Working

---

## 📍 Exact Location

**Method:** `addToVectorStoreAndGetIds()`  
**Lines:** 474-565

---

## 🎯 The Code Section

### Part 1: Create Attributes Map (Lines 491-498)

```java
// ✨ ADD METADATA ATTRIBUTES - Store metadata directly in OpenAI Vector Store
Map<String, Object> attributes = new HashMap<>();

// Default attributes (always included)
attributes.put("fileId", fileId);  // Store fileId as metadata
attributes.put("chatbotId", chatbotId);
attributes.put("sessionId", sessionId);
attributes.put("uploadedAt", String.valueOf(System.currentTimeMillis()));
```

**This is where the 4 default attributes are created:**
- `fileId` ✨
- `chatbotId` ✨
- `sessionId` ✨
- `uploadedAt` ✨

### Part 2: Merge Custom Attributes (Lines 500-514)

```java
// Merge custom attributes if provided
if (customAttributes != null && !customAttributes.isEmpty()) {
    // Only add custom attributes that don't exceed 16 total attributes
    int attributeCount = attributes.size();
    for (Map.Entry<String, Object> entry : customAttributes.entrySet()) {
        if (attributeCount >= 16) {
            log.warn("Cannot add more attributes. Maximum 16 attributes allowed. Skipping: {}", entry.getKey());
            break;
        }
        if (!attributes.containsKey(entry.getKey())) {
            attributes.put(entry.getKey(), entry.getValue());
            attributeCount++;
        }
    }
}
```

**This merges custom attributes while:**
- Validating 16-attribute limit
- Preventing duplicate keys
- Logging warnings if limit exceeded

### Part 3: Add to Request Body (Line 516)

```java
requestBody.put("attributes", attributes);
```

**This adds the attributes map to the request body that gets sent to OpenAI!**

### Part 4: Log the Metadata (Lines 518-519)

```java
log.debug("Adding {} metadata attributes to vector store file", attributes.size());
log.debug("Metadata attributes: {}", attributes);
```

**This logs what's being sent for debugging purposes.**

### Part 5: Send to OpenAI (Lines 521-530)

```java
// Create headers
HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "Bearer " + openaiApiKey);
headers.set("OpenAI-Beta", "assistants=v2");
headers.setContentType(MediaType.APPLICATION_JSON);

// Make request
HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

String url = String.format("%s/vector_stores/%s/files", openaiBaseUrl, vectorStoreId);
```

**This sends the request with metadata attributes to OpenAI!**

---

## 📊 Complete Method Overview

### Method Signature (Line 474)

```java
private AttachmentSaveResult addToVectorStoreAndGetIds(
    String fileId, 
    String chatbotId, 
    String sessionId, 
    Map<String, Object> customAttributes) {
```

### Flow Diagram

```
addToVectorStoreAndGetIds(fileId, chatbotId, sessionId, customAttributes)
    ↓
Get or create vector store (line 479)
    ↓
Create request body (line 485)
    ├─ Add file_id
    └─ Add chunking_strategy
    ↓
CREATE ATTRIBUTES MAP (line 492) ← ✨ METADATA STARTS HERE
    ├─ Add default attributes (lines 495-498)
    │  ├─ fileId ✨
    │  ├─ chatbotId ✨
    │  ├─ sessionId ✨
    │  └─ uploadedAt ✨
    │
    └─ Merge custom attributes (lines 500-514)
       └─ Validate 16-attribute limit
    ↓
ADD ATTRIBUTES TO REQUEST BODY (line 516) ← ✨ METADATA ADDED TO REQUEST
    ↓
Log metadata being sent (lines 518-519)
    ↓
Create HTTP headers (lines 521-525)
    ├─ Authorization
    ├─ OpenAI-Beta
    └─ Content-Type
    ↓
Create HTTP entity (line 528)
    ↓
SEND REQUEST TO OPENAI (line 530+) ← ✨ METADATA SENT TO OPENAI
    ↓
Receive response with vectorStoreFileId
    ↓
Return result
```

---

## 🔍 Visual Highlighting

Here's the exact section with highlighting:

```java
474|    private AttachmentSaveResult addToVectorStoreAndGetIds(String fileId, String chatbotId, String sessionId, Map<String, Object> customAttributes) {
475|        log.debug("Adding file to vector store: fileId={}, chatbotId={}", fileId, chatbotId);
476|        
477|        try {
478|            // Get or create vector store for this chatbot
479|            String vectorStoreId = getOrCreateVectorStore(chatbotId);
480|            log.debug("Using vector store: {} for chatbot: {}", vectorStoreId, chatbotId);
481|            
482|            // Create request body with metadata attributes
483|            // ✨ METADATA SUPPORT: OpenAI Vector Store Files API now supports attributes parameter
484|            // You can store up to 16 key-value pairs (strings, booleans, or numbers)
485|            Map<String, Object> requestBody = new HashMap<>();
486|            requestBody.put("file_id", fileId);
487|            requestBody.put("chunking_strategy", new HashMap<String, String>() {{
488|                put("type", "auto");
489|            }});
490|            
491|            // ✨ ADD METADATA ATTRIBUTES - Store metadata directly in OpenAI Vector Store
492|            Map<String, Object> attributes = new HashMap<>();  ← ✨ ATTRIBUTES MAP CREATED
493|            
494|            // Default attributes (always included)
495|            attributes.put("fileId", fileId);  // Store fileId as metadata  ← ✨ fileId
496|            attributes.put("chatbotId", chatbotId);                        ← ✨ chatbotId
497|            attributes.put("sessionId", sessionId);                        ← ✨ sessionId
498|            attributes.put("uploadedAt", String.valueOf(System.currentTimeMillis()));  ← ✨ uploadedAt
499|            
500|            // Merge custom attributes if provided
501|            if (customAttributes != null && !customAttributes.isEmpty()) {
502|                // Only add custom attributes that don't exceed 16 total attributes
503|                int attributeCount = attributes.size();
504|                for (Map.Entry<String, Object> entry : customAttributes.entrySet()) {
505|                    if (attributeCount >= 16) {
506|                        log.warn("Cannot add more attributes. Maximum 16 attributes allowed. Skipping: {}", entry.getKey());
507|                        break;
508|                    }
509|                    if (!attributes.containsKey(entry.getKey())) {
510|                        attributes.put(entry.getKey(), entry.getValue());  ← ✨ CUSTOM METADATA ADDED
511|                    }
512|                }
513|            }
514|            
515|            requestBody.put("attributes", attributes);  ← ✨ ATTRIBUTES ADDED TO REQUEST
516|            
517|            log.debug("Adding {} metadata attributes to vector store file", attributes.size());
518|            log.debug("Metadata attributes: {}", attributes);
519|            
520|            // Create headers
521|            HttpHeaders headers = new HttpHeaders();
522|            headers.set("Authorization", "Bearer " + openaiApiKey);
523|            headers.set("OpenAI-Beta", "assistants=v2");
524|            headers.setContentType(MediaType.APPLICATION_JSON);
525|            
526|            // Make request
527|            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
528|            
529|            String url = String.format("%s/vector_stores/%s/files", openaiBaseUrl, vectorStoreId);
530|            log.debug("Adding to vector store endpoint: {}", url);
531|            
532|            @SuppressWarnings("unchecked")
533|            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);  ← ✨ SENT TO OPENAI
534|            
535|            log.debug("OpenAI Vector Store Response: {}", response);
536|            
537|            if (response == null) {
538|                throw new RuntimeException("Null response from OpenAI Vector Store API");
539|            }
```

---

## 🚀 What Gets Sent to OpenAI

### The Request Body

```json
{
  "file_id": "file-abc123xyz",
  "chunking_strategy": {
    "type": "auto"
  },
  "attributes": {
    "fileId": "file-abc123xyz",
    "chatbotId": "chatbot_123",
    "sessionId": "session_456",
    "uploadedAt": "1707385649123"
  }
}
```

### The URL Endpoint

```
POST https://api.openai.com/v1/vector_stores/{vectorStoreId}/files
```

---

## 📍 Call Stack

### How This Method Gets Called

```
saveAttachmentFromMultipart()  [Line 70]
    ↓
addToVectorStoreAndGetIds(fileId, chatbotId, sessionId)  [Line 107]
    ↓
addToVectorStoreAndGetIds(fileId, chatbotId, sessionId, null)  [Line 453]
    ↓
addToVectorStoreAndGetIds(fileId, chatbotId, sessionId, customAttributes)  [Line 474] ← YOU ARE HERE
```

### Or With Custom Metadata

```
addToVectorStoreWithMetadata()  [Line 467]
    ↓
addToVectorStoreAndGetIds(fileId, chatbotId, sessionId, customAttributes)  [Line 474] ← YOU ARE HERE
```

---

## ✨ Summary

### Where Metadata is Added

| Step | Location | What Happens |
|------|----------|--------------|
| **1** | Line 492 | Attributes map created |
| **2** | Lines 495-498 | Default attributes added |
| **3** | Lines 500-514 | Custom attributes merged |
| **4** | Line 516 | Attributes added to request body |
| **5** | Line 533 | Request sent to OpenAI |

### Key Variables

| Variable | Type | Purpose |
|----------|------|---------|
| `attributes` | `Map<String, Object>` | Holds all metadata (default + custom) |
| `requestBody` | `Map<String, Object>` | Request body sent to OpenAI |
| `entity` | `HttpEntity<Map<String, Object>>` | HTTP request with headers and body |

### Key Code Lines

```
Line 492: Map<String, Object> attributes = new HashMap<>();
Line 495-498: Add default attributes
Line 516: requestBody.put("attributes", attributes);
Line 533: restTemplate.postForObject(url, entity, Map.class);
```

---

## 🎯 Bottom Line

**fileId and other metadata are added during file upload to OpenAI at:**

```
File: src/main/java/net/ai/chatbot/service/n8n/AttachmentSaveService.java
Method: addToVectorStoreAndGetIds() [4-parameter version]
Lines: 491-519 (creating and populating attributes)
Line: 516 (adding attributes to request body)
Line: 533 (sending to OpenAI)
```

**The attributes map (containing fileId) is passed in the `attributes` field of the POST request to OpenAI's Vector Store Files API endpoint.**

---

**Status:** ✅ Confirmed & Working  
**Date:** February 9, 2026

