# ✅ Vector Store Fixed - Now Fixing File Upload Status

## 🎉 Good News!
Your Vector Store is now working! The `OpenAI-Beta: assistants=v2` header fix worked.

## 🔴 Current Issue: File Upload Status = "Failed"

The file attached shows status **"Failed"** in OpenAI Platform.

---

## 🔍 Root Cause

The file `kafka-logo-png_seeklogo-27-...png` (an image file) was added to the vector store, but Vector Stores are optimized for **text-based files**, not images.

**Vector Store Supported Files:**
- ✅ PDF
- ✅ DOCX (Word documents)
- ✅ PPTX (PowerPoint)
- ✅ XLSX (Excel)
- ✅ TXT (Plain text)
- ✅ CSV
- ✅ JSON
- ✅ Markdown
- ❌ PNG (Images - not recommended)
- ❌ JPG (Images - not recommended)

---

## 🔧 Solution: Update File Validation

The issue is that we're allowing **image files** to be uploaded to Vector Store, but they should either be:
1. **Blocked from Vector Store** (use regular Files API instead)
2. **Converted to text** before uploading
3. **Used with Vision API** instead of Vector Store

### Fix: Update AttachmentSaveService to validate file types for Vector Store

```java
private boolean isValidForVectorStore(String mimeType) {
    Set<String> vectorStoreSupported = new HashSet<>(Arrays.asList(
        // Documents
        "application/pdf",
        "text/plain",
        "text/csv",
        "application/json",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",  // .docx
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",         // .xlsx
        "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx
        
        // Markdown
        "text/markdown",
        "text/x-markdown"
    ));
    return vectorStoreSupported.contains(mimeType);
}

private boolean isValidForRegularFiles(String mimeType) {
    Set<String> allSupported = new HashSet<>(Arrays.asList(
        // Documents
        "application/pdf",
        "text/plain",
        "text/csv",
        "application/json",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        
        // Images
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/svg+xml"
    ));
    return allSupported.contains(mimeType);
}
```

---

## 📋 Complete Fix Implementation

### Update AttachmentSaveService.java

Add this method after `isAllowedMimeType()`:

```java
/**
 * Check if file type is supported for Vector Store
 * Vector Store only supports text-based documents
 */
private boolean isVectorStoreSupportedType(String mimeType) {
    Set<String> vectorStoreSupported = new HashSet<>(Arrays.asList(
        // Documents only - Vector Store doesn't support images
        "application/pdf",
        "text/plain",
        "text/csv",
        "application/json",
        "text/markdown",
        "text/x-markdown",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",  // .docx
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",         // .xlsx
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"  // .pptx
    ));
    return vectorStoreSupported.contains(mimeType);
}
```

### Update the main saveAttachmentFromMultipart method

In the file upload loop, add a check:

```java
for (MultipartFile file : files) {
    try {
        log.debug("Processing multipart file: {}", file.getOriginalFilename());
        
        // NEW: Check if file is supported for Vector Store
        if (!isVectorStoreSupportedType(file.getContentType())) {
            log.warn("File type not supported for Vector Store: {}", file.getContentType());
            return ResponseEntity.badRequest().body(
                MultimodalChatResponse.error("INVALID_ATTACHMENT",
                    "File type '" + file.getContentType() + "' is not supported for Vector Store. " +
                    "Only text-based documents (PDF, DOCX, TXT, CSV, etc.) are supported.")
            );
        }
        
        // Upload to OpenAI Vector Store using MultipartFile
        AttachmentSaveService.AttachmentSaveResult saveResult = attachmentSaveService.saveAttachmentFromMultipart(
                file,
                chatbotId,
                sessionId
        );
        // ... rest of code
    }
}
```

---

## 🚀 Better Approach: Dual-Purpose Storage

Create separate flows for different file types:

### Option 1: Store images in regular Files API (not Vector Store)

```java
private AttachmentSaveResult saveAttachmentFromMultipart(MultipartFile file, 
                                                         String chatbotId, 
                                                         String sessionId,
                                                         boolean useVectorStore) {
    // Check file type
    if (isImageFile(file.getContentType())) {
        // Images: Use regular Files API only (no Vector Store)
        String fileId = uploadToOpenAIFilesAPI(tempFilePath, file.getOriginalFilename(), file.getContentType());
        
        return AttachmentSaveResult.builder()
                .vectorStoreId(null)  // No vector store for images
                .vectorStoreFileId(null)
                .fileId(fileId)  // Store the regular file ID instead
                .build();
    } else {
        // Documents: Use Vector Store
        String fileId = uploadToOpenAIFilesAPI(tempFilePath, file.getOriginalFilename(), file.getContentType());
        
        AttachmentSaveResult result = addToVectorStoreAndGetIds(fileId, chatbotId, sessionId);
        return result;
    }
}

private boolean isImageFile(String mimeType) {
    return mimeType != null && mimeType.startsWith("image/");
}
```

---

## 📊 Recommended Approach

### Keep Vector Store Only for Documents

**Update the MIME type validation:**

```java
private void validateMultipartFile(MultipartFile file) throws IOException {
    if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
        throw new IllegalArgumentException("File name is required");
    }
    
    if (file.getSize() <= 0) {
        throw new IllegalArgumentException("File is empty");
    }
    
    // 100MB limit
    if (file.getSize() > 100 * 1024 * 1024) {
        throw new IllegalArgumentException("File size exceeds 100MB limit. Size: " + file.getSize() + " bytes");
    }
    
    String mimeType = file.getContentType();
    if (mimeType == null || mimeType.isEmpty()) {
        throw new IllegalArgumentException("MIME type is required");
    }
    
    // NEW: Check if it's a supported type for Vector Store
    if (!isVectorStoreSupportedType(mimeType)) {
        throw new IllegalArgumentException(
            "File type '" + mimeType + "' is not supported for Vector Store. " +
            "Only text-based documents (PDF, DOCX, TXT, CSV, JSON, PPTX, XLSX) are supported."
        );
    }
}

private boolean isVectorStoreSupportedType(String mimeType) {
    Set<String> vectorStoreTypes = new HashSet<>(Arrays.asList(
        "application/pdf",
        "text/plain",
        "text/csv",
        "application/json",
        "text/markdown",
        "text/x-markdown",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    ));
    return vectorStoreTypes.contains(mimeType);
}
```

---

## ✅ Updated isAllowedMimeType() Method

Keep general file type validation but update Vector Store check:

```java
private boolean isAllowedMimeType(String mimeType) {
    Set<String> allowed = new HashSet<>(Arrays.asList(
        // Documents - FOR VECTOR STORE
        "application/pdf",
        "text/plain",
        "text/csv",
        "application/json",
        "text/markdown",
        "text/x-markdown",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        
        // Images - FOR REGULAR FILES API (not Vector Store)
        // Keep but mark as "images only - no vector store"
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/svg+xml"
    ));
    return allowed.contains(mimeType);
}
```

---

## 📝 Error Messages to Display

### For Vector Store Upload:
```
"File type 'image/png' is not supported for Vector Store. 
Only text-based documents are supported:
- PDF (.pdf)
- Word (.docx)
- Excel (.xlsx)
- PowerPoint (.pptx)
- Text (.txt)
- CSV (.csv)
- JSON (.json)"
```

---

## 🧪 Test the Fix

1. Try uploading a **PNG/JPG** → Should get error message
2. Try uploading a **PDF/DOCX/TXT** → Should succeed with status "completed"

---

## 🎯 Next Steps

1. **Option A (Recommended):** Only allow text documents in Vector Store (reject images with clear error)
2. **Option B:** Support both - images go to Files API, documents go to Vector Store
3. **Option C:** Keep current flow but document the limitation

Which approach would you like to implement?

---

**Status:** Vector Store working ✅ | File type validation needed ⏳
**Updated:** 2024-02-17





