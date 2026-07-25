# 🔧 Vector Store API Error - Diagnosis & Fix Guide

## 🔴 Your Error
```
An unexpected error occurred: Invalid URL (GET /v1/vector_stores/vs_6988c402e464819194410cadf5845751/associations)
```

---

## ✅ Step-by-Step Diagnosis

### Step 1: Identify the Source
- [ ] **Is this from N8N?** (n8n.io logs or webhook response)
- [ ] **Is this from frontend code?** (browser console)
- [ ] **Is this from backend logs?** (Spring Boot application logs)

### Step 2: Where is `/associations` Being Called?
```
❌ FIND AND REPLACE:
  /vector_stores/{id}/associations
  
✅ WITH:
  /vector_stores/{id}/files
```

### Step 3: Check These Locations

**Location 1: N8N Workflow**
- [ ] Check N8N HTTP node
- [ ] Look for "associations" in URL
- [ ] Change to use `/files` endpoint

**Location 2: Frontend Code**
- [ ] Search for "associations" in JavaScript/React code
- [ ] Check API calls to OpenAI
- [ ] Replace with `/files` endpoint

**Location 3: Backend Code**
- [ ] Already correct in `AttachmentSaveService.java` line 468
- [ ] Check if there are custom implementations elsewhere
- [ ] Verify no new endpoints added

---

## 🎯 Common Places to Check

### 1. N8N HTTP Request Node
**If you have an N8N workflow:**
```javascript
// ❌ WRONG
const url = `https://api.openai.com/v1/vector_stores/${vectorStoreId}/associations`;

// ✅ CORRECT
const url = `https://api.openai.com/v1/vector_stores/${vectorStoreId}/files`;
```

### 2. Frontend Fetch Call
```javascript
// ❌ WRONG
fetch(`/v1/vector_stores/${vectorStoreId}/associations`)

// ✅ CORRECT
fetch(`/v1/vector_stores/${vectorStoreId}/files`)
```

### 3. Browser DevTools
**Steps:**
1. Open DevTools → Network tab
2. Look for failed requests to OpenAI
3. Check the URL in the request
4. Should end with `/files`, not `/associations`

### 4. Spring Boot Logs
```bash
# Search for "associations" in logs
grep -r "associations" /path/to/logs/

# Should return: (nothing - it's not in backend code)
```

---

## 🚀 Quick Fix

### If in N8N:
1. Open your N8N workflow
2. Find the HTTP node calling OpenAI
3. Edit the URL:
   ```
   FROM: https://api.openai.com/v1/vector_stores/{{ $json.vectorStoreId }}/associations
   TO:   https://api.openai.com/v1/vector_stores/{{ $json.vectorStoreId }}/files
   ```
4. Save and re-deploy

### If in Frontend:
1. Search codebase: `grep -r "associations" ./src/`
2. Replace in each file:
   - Old: `.../associations`
   - New: `.../files`
3. Restart dev server
4. Test again

### If in Custom Backend Code:
1. Search: `grep -r "associations" ./src/main/java/`
2. Check if new endpoint was added
3. Verify it uses `/files` instead
4. Rebuild and restart

---

## 📋 Verification Checklist

After fixing, verify:
- [ ] No more "associations" in network requests
- [ ] Vector store files can be listed
- [ ] Response includes `data` array with files
- [ ] Each file has `id`, `status`, `file_id`, `vector_store_id`

---

## 🧪 Test the Correct Endpoint

### cURL Test
```bash
# Make sure this works
curl https://api.openai.com/v1/vector_stores/vs_6988c402e464819194410cadf5845751/files \
  -H "Authorization: Bearer sk-..." \
  -H "OpenAI-Beta: assistants=v2"

# Expected Response:
# {
#   "object": "list",
#   "data": [
#     {
#       "id": "vs_abc123_file_001",
#       "created_at": 1708123456,
#       "vector_store_id": "vs_6988c402e464819194410cadf5845751",
#       "status": "completed",
#       "file_id": "file-xyz789"
#     }
#   ]
# }
```

### JavaScript Test
```javascript
const vectorStoreId = 'vs_6988c402e464819194410cadf5845751';
const apiKey = 'sk-...';

const response = await fetch(
  `https://api.openai.com/v1/vector_stores/${vectorStoreId}/files`,
  {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${apiKey}`,
      'OpenAI-Beta': 'assistants=v2'
    }
  }
);

const data = await response.json();
console.log(data.data); // Array of files
```

---

## 📍 Quick Reference: Correct OpenAI Endpoints

| Operation | Endpoint | Method | Status |
|-----------|----------|--------|--------|
| List Vector Stores | `/vector_stores` | GET | ✅ Works |
| Create Vector Store | `/vector_stores` | POST | ✅ Works |
| Get Vector Store | `/vector_stores/{id}` | GET | ✅ Works |
| List Files in Store | `/vector_stores/{id}/files` | GET | ✅ Works |
| Get File in Store | `/vector_stores/{id}/files/{fileId}` | GET | ✅ Works |
| Add File to Store | `/vector_stores/{id}/files` | POST | ✅ Works |
| Delete File from Store | `/vector_stores/{id}/files/{fileId}` | DELETE | ✅ Works |
| ❌ Associations (DOESN'T EXIST) | `/vector_stores/{id}/associations` | GET | ❌ Invalid |

---

## 🎓 Understanding the Flow

```
1. User uploads file via UI
   ↓
2. Backend uploads to OpenAI Files API
   - Endpoint: POST /v1/files
   - Returns: file_id (file-xyz789)
   ↓
3. Backend adds file to Vector Store
   - Endpoint: POST /v1/vector_stores/{id}/files
   - Returns: vs_id (vs_abc123_file_001)
   ↓
4. N8N/Frontend wants to list files
   - ✅ Endpoint: GET /v1/vector_stores/{id}/files  (CORRECT)
   - ❌ Endpoint: GET /v1/vector_stores/{id}/associations  (WRONG - DOESN'T EXIST)
```

---

## 📞 Need Help?

1. Check the source of the error (N8N logs, browser console, Spring logs)
2. Search for "associations" in that location
3. Replace with "files"
4. Test with cURL first
5. Then test in application

---

**Version:** 1.0  
**Last Updated:** 2024-02-17  
**Status:** Ready to Debug

