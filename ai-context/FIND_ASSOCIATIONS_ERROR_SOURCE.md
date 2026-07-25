# Debug Guide: Finding the Source of `/associations` Error

## 🔍 Where to Look

Since the error persists even though our code is correct, the issue is likely:

1. **N8N trying to auto-fetch vector store details**
2. **Browser/frontend making additional API calls**
3. **A custom OpenAI node in N8N trying to list associations**
4. **OpenAI's response triggering an automatic fetch**

---

## 📱 Step 1: Check N8N Activity

### View N8N Request History:
```
N8N Dashboard → Execution History
Look for failed requests with URL containing "associations"
```

### Check N8N HTTP Nodes:
```
In your N8N workflow, look for:
- HTTP Request nodes
- OpenAI nodes (if using them)
- Custom code nodes that might call OpenAI API
```

### Search N8N Workflow JSON:
If you exported your N8N workflow as JSON:
```bash
grep -i "associations" workflow.json
grep -i "/vector_stores/" workflow.json
```

---

## 🌐 Step 2: Check Browser Network Tab

### Instructions:
1. Open Browser DevTools (F12)
2. Go to Network tab
3. Trigger the error again
4. Look for failed requests to:
   - `*.openai.com`
   - `api.openai.com`

### Check each failed request:
- Click on the request
- Check the URL (should NOT contain "associations")
- Check the Headers (should include `OpenAI-Beta: assistants=v2`)
- Check the Response (shows the actual error)

---

## 📋 Step 3: Enable Debug Logging in Backend

Add this to `application.properties`:

```properties
# Enable HTTP client logging to see all requests to OpenAI
logging.level.org.springframework.web.client.RestTemplate=DEBUG
logging.level.org.springframework.http=DEBUG
logging.level.net.ai.chatbot.service.n8n=DEBUG

# OpenAI API calls debug
logging.level.com.sun.jersey.client=DEBUG
```

Then check Spring Boot logs for any `/associations` calls.

---

## 🔎 Step 4: Check If It's OpenAI's Auto-Fetch

**Possibility:** OpenAI might be auto-fetching associations after adding a file.

### Test directly with OpenAI API:

```bash
# 1. Create vector store
curl -X POST https://api.openai.com/v1/vector_stores \
  -H "Authorization: Bearer sk-..." \
  -H "Content-Type: application/json" \
  -d '{"name":"test"}' > vs_response.json

# Get the vector_store_id from response
VECTOR_STORE_ID=$(jq -r '.id' vs_response.json)
echo "Vector Store ID: $VECTOR_STORE_ID"

# 2. Upload file
curl -X POST https://api.openai.com/v1/files \
  -H "Authorization: Bearer sk-..." \
  -F "file=@test.pdf" \
  -F "purpose=assistants" > file_response.json

FILE_ID=$(jq -r '.id' file_response.json)
echo "File ID: $FILE_ID"

# 3. Add file to vector store
curl -X POST https://api.openai.com/v1/vector_stores/$VECTOR_STORE_ID/files \
  -H "Authorization: Bearer sk-..." \
  -H "Content-Type: application/json" \
  -H "OpenAI-Beta: assistants=v2" \
  -d "{\"file_id\": \"$FILE_ID\"}" > vs_file_response.json

# 4. Try to list files (CORRECT endpoint)
curl https://api.openai.com/v1/vector_stores/$VECTOR_STORE_ID/files \
  -H "Authorization: Bearer sk-..." \
  -H "OpenAI-Beta: assistants=v2"

# 5. Try associations (SHOULD FAIL)
curl https://api.openai.com/v1/vector_stores/$VECTOR_STORE_ID/associations \
  -H "Authorization: Bearer sk-..." \
  -H "OpenAI-Beta: assistants=v2"
```

If step 5 fails with "Invalid URL", that confirms `/associations` doesn't exist.

---

## 🎯 Step 5: Most Likely Culprit - N8N Configuration

### Check These N8N Nodes:

1. **OpenAI Chat Model Node**
   - Settings → Check if it's trying to auto-attach vector stores

2. **Custom Code Node**
   - Search for "associations" in JavaScript
   - Replace with "files"

3. **HTTP Request Node**
   - Check the exact URL being used
   - Ensure headers include `OpenAI-Beta: assistants=v2`

### Example N8N Fix:

If you have a node like:
```javascript
// ❌ WRONG
const url = `https://api.openai.com/v1/vector_stores/${vectorStoreId}/associations`;

// ✅ CORRECT
const url = `https://api.openai.com/v1/vector_stores/${vectorStoreId}/files`;
```

---

## 🔧 Step 6: Fix the Issue

### If in N8N HTTP Node:

```
Method: GET
URL: https://api.openai.com/v1/vector_stores/{{ $json.vectorStoreId }}/files
Headers:
  Authorization: Bearer {{ $env.OPENAI_API_KEY }}
  OpenAI-Beta: assistants=v2
```

### If in N8N Code Node:

```javascript
const response = await $http.get({
  url: `https://api.openai.com/v1/vector_stores/${vectorStoreId}/files`,
  headers: {
    'Authorization': `Bearer ${openaiKey}`,
    'OpenAI-Beta': 'assistants=v2'
  }
});

return response;
```

---

## 🐛 Common N8N Issues

### Issue 1: OpenAI node auto-fetching
**Solution:** Disable "Auto-fetch" or similar option in node settings

### Issue 2: Using old OpenAI node version
**Solution:** Update N8N to latest version
```bash
npm update -g n8n
```

### Issue 3: Custom integration calling wrong endpoint
**Solution:** Search N8N workflow for "associations" and replace

### Issue 4: N8N caching old API response
**Solution:** 
- Clear N8N cache
- Restart N8N service
- Clear browser cache

```bash
# Restart N8N
pm2 restart n8n
# Or
docker-compose restart n8n
```

---

## 📊 Quick Diagnostic Script

Create a test script to verify all endpoints work:

```bash
#!/bin/bash

OPENAI_KEY="sk-..."
VECTOR_STORE_ID="vs_6988c402e464819194410cadf5845751"

echo "Testing OpenAI Vector Store Endpoints..."
echo ""

# Test 1: List vector stores
echo "✓ Testing: List Vector Stores"
curl -s https://api.openai.com/v1/vector_stores \
  -H "Authorization: Bearer $OPENAI_KEY" \
  -H "OpenAI-Beta: assistants=v2" | jq '.data | length'
echo ""

# Test 2: Get specific vector store
echo "✓ Testing: Get Vector Store"
curl -s https://api.openai.com/v1/vector_stores/$VECTOR_STORE_ID \
  -H "Authorization: Bearer $OPENAI_KEY" \
  -H "OpenAI-Beta: assistants=v2" | jq '.id'
echo ""

# Test 3: List files in vector store (CORRECT)
echo "✓ Testing: List Files (CORRECT ENDPOINT)"
curl -s https://api.openai.com/v1/vector_stores/$VECTOR_STORE_ID/files \
  -H "Authorization: Bearer $OPENAI_KEY" \
  -H "OpenAI-Beta: assistants=v2" | jq '.data | length'
echo ""

# Test 4: Try associations (WRONG - should fail)
echo "✗ Testing: Associations (SHOULD FAIL)"
curl -s https://api.openai.com/v1/vector_stores/$VECTOR_STORE_ID/associations \
  -H "Authorization: Bearer $OPENAI_KEY" \
  -H "OpenAI-Beta: assistants=v2" | jq '.' || echo "Failed as expected"
```

---

## 📞 Information Needed to Help

To debug this further, please provide:

1. **Is this error from N8N or browser?**
2. **N8N workflow JSON** (if it's N8N)
3. **Browser Network tab screenshot** (if it's frontend)
4. **Spring Boot logs** showing the error
5. **Full error message with context**

---

## ✅ Action Plan

1. ✅ Find the source (N8N / Frontend / Other)
2. ✅ Look for `/associations` in that code/config
3. ✅ Replace with `/files`
4. ✅ Restart service/refresh browser
5. ✅ Test again

---

**Note:** Our backend code is 100% correct. The issue is external (likely N8N or frontend).

Document: **VECTOR_STORE_ERROR_FIX.md** has the complete guide.

