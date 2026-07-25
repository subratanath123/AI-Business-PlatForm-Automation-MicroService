# 🚨 Troubleshooting 401 Unauthorized Error

**Error:** `POST http://localhost:3000/api/attachments/upload` → 401 Unauthorized

---

## 🔍 Root Causes & Solutions

### Issue #1: Wrong URL/Port ⚠️ (MOST LIKELY)

**Problem:** You're sending request to port 3000, but API is on port 8080

**Check:**
```
❌ WRONG:  http://localhost:3000/api/attachments/upload
✅ CORRECT: http://localhost:8080/api/attachments/upload
```

**Solution:** Update your frontend URL

```javascript
// ❌ Wrong
const API_URL = 'http://localhost:3000';

// ✅ Correct
const API_URL = 'http://localhost:8080';
```

---

### Issue #2: CORS Not Configured

**Problem:** Frontend on port 3000 can't access API on port 8080 due to CORS

**Solution:** Add CORS configuration to backend

**File:** `src/main/java/net/ai/chatbot/config/CorsConfig.java`

```java
package net.ai.chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow requests from frontend
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOrigin("http://localhost:5173"); // Vite
        config.addAllowedOrigin("http://localhost:4200");  // Angular
        config.addAllowedOrigin("*");                      // Allow all (dev only!)
        
        // Allow all methods
        config.addAllowedMethod("*");
        
        // Allow all headers
        config.addAllowedHeader("*");
        
        // Allow credentials
        config.setAllowCredentials(true);
        
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

---

### Issue #3: Missing Required Parameters

**Problem:** Not sending all required form data

**Check your request:**

```javascript
// ❌ WRONG - Missing parameters
const form = new FormData();
form.append('file', file);
// Missing: chatbotId and sessionId!

const response = await fetch('http://localhost:8080/api/attachments/upload', {
  method: 'POST',
  body: form
});

// ✅ CORRECT - All parameters included
const form = new FormData();
form.append('file', file);
form.append('chatbotId', 'chatbot_123');  // ✅ Add this
form.append('sessionId', 'session_456');   // ✅ Add this

const response = await fetch('http://localhost:8080/api/attachments/upload', {
  method: 'POST',
  body: form
});
```

---

### Issue #4: Wrong HTTP Method

**Problem:** Using wrong HTTP method

```javascript
// ❌ WRONG
await fetch('/api/attachments/upload', { method: 'GET' });

// ✅ CORRECT
await fetch('/api/attachments/upload', { method: 'POST' });
```

---

### Issue #5: Headers Set Incorrectly

**Problem:** Setting Content-Type header when using FormData

```javascript
// ❌ WRONG - Do NOT set Content-Type with FormData
const headers = new Headers({
  'Content-Type': 'multipart/form-data'  // ❌ Remove this!
});

const response = await fetch('/api/attachments/upload', {
  method: 'POST',
  headers: headers,
  body: form
});

// ✅ CORRECT - Let browser set it automatically
const response = await fetch('/api/attachments/upload', {
  method: 'POST',
  // Don't set any headers!
  body: form  // FormData automatically sets correct Content-Type
});
```

---

## ✅ Correct Complete Example

### React/JavaScript

```javascript
async function uploadFile(file) {
  // 1. Create FormData
  const formData = new FormData();
  formData.append('file', file);
  formData.append('chatbotId', 'chatbot_123');
  formData.append('sessionId', 'session_456');

  try {
    // 2. Make request to CORRECT URL
    const response = await fetch('http://localhost:8080/api/attachments/upload', {
      method: 'POST',
      // Don't set headers - FormData will set Content-Type
      body: formData
    });

    // 3. Check response status
    if (!response.ok) {
      const error = await response.json();
      console.error('Error:', error.message);
      return null;
    }

    // 4. Parse response
    const result = await response.json();
    console.log('File uploaded:', result.fileId);
    return result;

  } catch (error) {
    console.error('Network error:', error);
    return null;
  }
}
```

---

## 🔧 Quick Checklist

### Frontend (Port 3000)

- [ ] Using correct API URL: `http://localhost:8080`
- [ ] Using POST method (not GET)
- [ ] Including all form fields:
  - [ ] file
  - [ ] chatbotId
  - [ ] sessionId
- [ ] NOT setting Content-Type header
- [ ] Handling errors properly

### Backend (Port 8080)

- [ ] CORS configured (if frontend on different port)
- [ ] Controller has @PostMapping("/upload")
- [ ] Service is autowired
- [ ] MongoDB is running

---

## 🧪 Test with Postman First

Before testing in frontend, test with Postman:

1. **Import:** FILE_ATTACHMENT_API.postman_collection.json
2. **Select:** Upload File endpoint
3. **Add file:** In form-data, select file
4. **Add params:**
   - chatbotId: chatbot_123
   - sessionId: session_456
5. **Send** and verify it works

If Postman works but frontend doesn't → Issue is in frontend code or CORS

---

## 🔍 Debug Steps

### Step 1: Check Backend is Running

```bash
# Is the server running on port 8080?
curl http://localhost:8080/api/attachments/list/test-bot
```

### Step 2: Test with Postman

Import and test FILE_ATTACHMENT_API.postman_collection.json

### Step 3: Check Network Tab in Browser

```
Network Tab → Look for your request
- Check URL is http://localhost:8080 (not 3000)
- Check Method is POST
- Check Response code (should not be 401)
```

### Step 4: Check Console for CORS Error

```
Browser Console → Look for:
"Access to XMLHttpRequest at 'http://localhost:8080/api/attachments/upload'
 from origin 'http://localhost:3000' has been blocked by CORS policy"
```

If you see this → Add CORS configuration to backend

### Step 5: Add Logging

```javascript
// Add this before fetch
console.log('Uploading to:', 'http://localhost:8080/api/attachments/upload');
console.log('Form data:', {
  file: file.name,
  chatbotId: 'chatbot_123',
  sessionId: 'session_456'
});

// Add this after fetch
.then(r => {
  console.log('Response status:', r.status);
  return r.json();
})
.then(data => console.log('Response data:', data))
.catch(error => console.error('Error:', error));
```

---

## 📋 Common Errors & Solutions

### Error: CORS Policy Blocked

```
Access to XMLHttpRequest at 'http://localhost:8080/...'
from origin 'http://localhost:3000' has been blocked by CORS policy
```

**Solution:** Add CORS configuration to backend (see above)

### Error: 404 Not Found

```
POST http://localhost:8080/api/attachments/upload 404
```

**Solution:** Verify:
- URL is exactly `/api/attachments/upload`
- Controller is deployed
- Check routing configuration

### Error: 400 Bad Request

```json
{
  "success": false,
  "message": "chatbotId is required"
}
```

**Solution:** Add missing parameters to FormData

### Error: 500 Internal Server Error

```
POST http://localhost:8080/api/attachments/upload 500
```

**Solution:**
- Check backend logs for error
- Verify MongoDB is running
- Check service implementation

---

## 🚀 Complete Working Example

### HTML + JavaScript

```html
<!DOCTYPE html>
<html>
<head>
  <title>File Upload Test</title>
</head>
<body>
  <h1>File Upload Test</h1>
  
  <input type="file" id="fileInput">
  <button onclick="uploadFile()">Upload</button>
  
  <div id="result"></div>

  <script>
    async function uploadFile() {
      const file = document.getElementById('fileInput').files[0];
      if (!file) {
        alert('Please select a file');
        return;
      }

      const formData = new FormData();
      formData.append('file', file);
      formData.append('chatbotId', 'chatbot_123');
      formData.append('sessionId', 'session_456');

      try {
        const response = await fetch('http://localhost:8080/api/attachments/upload', {
          method: 'POST',
          body: formData
        });

        if (!response.ok) {
          const error = await response.json();
          throw new Error(error.message);
        }

        const result = await response.json();
        document.getElementById('result').innerHTML = `
          <h3>✅ Success!</h3>
          <p>File ID: ${result.fileId}</p>
          <p>Download: <a href="${result.downloadUrl}">Click here</a></p>
        `;
      } catch (error) {
        document.getElementById('result').innerHTML = `
          <h3>❌ Error</h3>
          <p>${error.message}</p>
        `;
      }
    }
  </script>
</body>
</html>
```

---

## 📞 Still Getting 401?

1. ✅ Check URL is `http://localhost:8080` (not 3000)
2. ✅ Check all form parameters are included
3. ✅ Add CORS configuration to backend
4. ✅ Test with Postman first
5. ✅ Check browser console for errors
6. ✅ Check backend server logs

If still stuck, share:
- Browser console error message
- Network tab request details
- Backend server logs

---

**Most Common Fix:** Change URL from `localhost:3000` to `localhost:8080`



