# 🔧 Fix: chatbotId Not Being Received

**Problem:** Upload endpoint returns "chatbotId is required" even though you're sending it

**Reason:** The parameters must be sent as form-data fields, not in the request body

---

## ✅ Correct Frontend Code

### JavaScript/React

```javascript
async function uploadFile(file, chatbotId, sessionId) {
  const formData = new FormData();
  
  // ✨ IMPORTANT: All three must be in FormData
  formData.append('file', file);
  formData.append('chatbotId', chatbotId);      // ✅ Add this
  formData.append('sessionId', sessionId);      // ✅ Add this

  const response = await fetch('http://localhost:8080/api/attachments/upload', {
    method: 'POST',
    body: formData
    // DO NOT set headers - let browser set Content-Type automatically
  });

  if (!response.ok) {
    const error = await response.json();
    console.error('Error:', error);
    return null;
  }

  const result = await response.json();
  console.log('Success:', result.fileId);
  return result;
}

// Usage
uploadFile(fileObject, 'chatbot_123', 'session_456');
```

### React Component Example

```jsx
import React, { useState } from 'react';

export function FileUploader() {
  const [file, setFile] = useState(null);
  const [chatbotId, setChatbotId] = useState('chatbot_123');
  const [sessionId, setSessionId] = useState('session_456');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const handleUpload = async () => {
    if (!file) {
      setError('Please select a file');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // Create FormData with ALL three fields
      const formData = new FormData();
      formData.append('file', file);
      formData.append('chatbotId', chatbotId);
      formData.append('sessionId', sessionId);

      console.log('Uploading with:');
      console.log('- File:', file.name);
      console.log('- chatbotId:', chatbotId);
      console.log('- sessionId:', sessionId);

      const response = await fetch('http://localhost:8080/api/attachments/upload', {
        method: 'POST',
        body: formData
        // Don't set Content-Type header!
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Upload failed');
      }

      const data = await response.json();
      setResult(data);
      console.log('✅ Upload successful:', data.fileId);

    } catch (err) {
      setError(err.message);
      console.error('❌ Upload error:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: '20px', border: '1px solid #ccc' }}>
      <h2>File Upload</h2>
      
      <div>
        <label>Chatbot ID:</label>
        <input
          type="text"
          value={chatbotId}
          onChange={(e) => setChatbotId(e.target.value)}
          placeholder="chatbot_123"
        />
      </div>

      <div>
        <label>Session ID:</label>
        <input
          type="text"
          value={sessionId}
          onChange={(e) => setSessionId(e.target.value)}
          placeholder="session_456"
        />
      </div>

      <div>
        <label>File:</label>
        <input
          type="file"
          onChange={(e) => setFile(e.target.files[0])}
        />
      </div>

      <button onClick={handleUpload} disabled={loading || !file}>
        {loading ? 'Uploading...' : 'Upload'}
      </button>

      {error && <div style={{ color: 'red' }}>Error: {error}</div>}
      
      {result && (
        <div style={{ color: 'green', marginTop: '10px' }}>
          <p>✅ Upload successful!</p>
          <p>File ID: {result.fileId}</p>
          <p>
            Download: <a href={result.downloadUrl}>Click here</a>
          </p>
        </div>
      )}
    </div>
  );
}
```

---

## 🧪 Test with cURL (Verify API Works)

```bash
curl -X POST "http://localhost:8080/api/attachments/upload" \
  -F "file=@/path/to/your/file.pdf" \
  -F "chatbotId=chatbot_123" \
  -F "sessionId=session_456"

# Response should be:
# {
#   "fileId": "file_chatbot_123_session_456_...",
#   "downloadUrl": "http://...",
#   "status": "stored"
# }
```

---

## 🧪 Test with Postman

1. **Method:** POST
2. **URL:** `http://localhost:8080/api/attachments/upload`
3. **Body:** Click "form-data"
4. **Add three fields:**
   - `file` (type: File) - Select your file
   - `chatbotId` (type: Text) - Enter: `chatbot_123`
   - `sessionId` (type: Text) - Enter: `session_456`
5. **Click Send**
6. **Should see fileId in response** ✅

---

## ❌ Common Mistakes

### Mistake 1: Sending in JSON Body

```javascript
// ❌ WRONG - This won't work
const response = await fetch('/api/attachments/upload', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    file: file,
    chatbotId: 'chatbot_123',
    sessionId: 'session_456'
  })
});
```

### Mistake 2: Setting Content-Type with FormData

```javascript
// ❌ WRONG - Don't set this with FormData
const headers = new Headers({
  'Content-Type': 'multipart/form-data'  // ❌ Remove this!
});

// ✅ CORRECT - Let browser set it
// Don't set any headers when using FormData
```

### Mistake 3: Missing FormData Parameters

```javascript
// ❌ WRONG - Missing chatbotId and sessionId
const formData = new FormData();
formData.append('file', file);  // Only this!

// ✅ CORRECT - Include all three
const formData = new FormData();
formData.append('file', file);
formData.append('chatbotId', chatbotId);
formData.append('sessionId', sessionId);
```

### Mistake 4: Incorrect Field Names

```javascript
// ❌ WRONG
formData.append('chatbot_id', 'chatbot_123');  // Underscore instead of camelCase
formData.append('session_id', 'session_456');  // Underscore instead of camelCase

// ✅ CORRECT - Use camelCase
formData.append('chatbotId', 'chatbot_123');
formData.append('sessionId', 'session_456');
```

---

## 🔍 Debug Checklist

- [ ] FormData has 'file' field
- [ ] FormData has 'chatbotId' field (camelCase, not underscore)
- [ ] FormData has 'sessionId' field (camelCase, not underscore)
- [ ] NOT setting Content-Type header manually
- [ ] Using method: 'POST'
- [ ] URL is correct: `http://localhost:8080/api/attachments/upload`
- [ ] File is not empty
- [ ] chatbotId value is not empty
- [ ] sessionId value is not empty

---

## 📝 Backend Endpoint Reference

```java
@PostMapping("/upload")
public ResponseEntity<?> uploadFile(
        @RequestParam("file") MultipartFile file,      // ✅ Expects form field "file"
        @RequestParam String chatbotId,                 // ✅ Expects form field "chatbotId"
        @RequestParam String sessionId) {               // ✅ Expects form field "sessionId"
```

**Key Points:**
- All parameters use `@RequestParam` which means they come from form-data
- Names must match exactly: `file`, `chatbotId`, `sessionId`
- All three are required (no defaults)
- Field must use `@RequestParam("file")` because the parameter name is "file"

---

## 🎯 HTML Form Test

```html
<!DOCTYPE html>
<html>
<body>
  <h1>Test Upload</h1>
  
  <form id="uploadForm">
    <input type="text" name="chatbotId" value="chatbot_123" required>
    <input type="text" name="sessionId" value="session_456" required>
    <input type="file" name="file" required>
    <button type="submit">Upload</button>
  </form>

  <div id="result"></div>

  <script>
    document.getElementById('uploadForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      
      const formData = new FormData(e.target);
      
      console.log('FormData fields:');
      for (let [key, value] of formData.entries()) {
        console.log(`  ${key}: ${value}`);
      }

      try {
        const response = await fetch('http://localhost:8080/api/attachments/upload', {
          method: 'POST',
          body: formData
        });

        const data = await response.json();
        document.getElementById('result').innerHTML = JSON.stringify(data, null, 2);
      } catch (error) {
        document.getElementById('result').innerHTML = 'Error: ' + error.message;
      }
    });
  </script>
</body>
</html>
```

---

## 📊 Expected Response (Success)

```json
{
  "fileId": "file_chatbot_123_session_456_document_1707385649123",
  "fileName": "document.pdf",
  "mimeType": "application/pdf",
  "fileSize": 256000,
  "downloadUrl": "http://localhost:8080/api/attachments/download/file_chatbot_123_session_456_document_1707385649123?chatbotId=chatbot_123",
  "uploadedAt": 1707385649000,
  "status": "stored"
}
```

## ❌ Expected Response (Error - Missing Parameter)

```json
{
  "success": false,
  "message": "chatbotId is required",
  "timestamp": 1707385649000
}
```

---

## ✅ Verification Steps

1. **Test with cURL:**
   ```bash
   curl -X POST "http://localhost:8080/api/attachments/upload" \
     -F "file=@test.pdf" \
     -F "chatbotId=chatbot_123" \
     -F "sessionId=session_456"
   ```

2. **If cURL works** → Your backend is fine
   - Then fix frontend FormData

3. **If cURL fails** → Backend issue
   - Check backend logs
   - Verify endpoint is deployed

---

## 🚀 Complete Working Example

```javascript
async function uploadFileCorrectly() {
  // 1. Get inputs
  const fileInput = document.getElementById('fileInput');
  const file = fileInput.files[0];
  const chatbotId = 'chatbot_123';
  const sessionId = 'session_456';

  // 2. Create FormData (the key!)
  const formData = new FormData();
  formData.append('file', file);
  formData.append('chatbotId', chatbotId);
  formData.append('sessionId', sessionId);

  // 3. Log for debugging
  console.log('Uploading:');
  console.log('- File:', file.name);
  console.log('- ChatbotId:', chatbotId);
  console.log('- SessionId:', sessionId);

  try {
    // 4. Send request
    const response = await fetch('http://localhost:8080/api/attachments/upload', {
      method: 'POST',
      body: formData
      // No headers needed - FormData handles it
    });

    // 5. Check response
    if (!response.ok) {
      const error = await response.json();
      console.error('Upload failed:', error.message);
      return null;
    }

    // 6. Parse response
    const result = await response.json();
    console.log('✅ Success:', result);
    console.log('File ID:', result.fileId);
    console.log('Download:', result.downloadUrl);
    
    return result;

  } catch (error) {
    console.error('❌ Network error:', error);
    return null;
  }
}

// Call it
uploadFileCorrectly();
```

---

**Key Takeaway:** Make sure ALL THREE parameters (file, chatbotId, sessionId) are in the FormData, with correct camelCase names!



