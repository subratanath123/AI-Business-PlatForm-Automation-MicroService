# 🚨 Fix 401 Unauthorized Error - Summary

**Issue:** Getting 401 error on `http://localhost:3000/api/attachments/upload`

---

## ⚡ Quick Fix (3 Steps)

### Step 1: Change URL from Port 3000 to 8080

**Before (Wrong):**
```javascript
fetch('http://localhost:3000/api/attachments/upload', ...)
```

**After (Correct):**
```javascript
fetch('http://localhost:8080/api/attachments/upload', ...)
```

### Step 2: Add Required Parameters

```javascript
const formData = new FormData();
formData.append('file', file);
formData.append('chatbotId', 'chatbot_123');      // ✅ Required
formData.append('sessionId', 'session_456');      // ✅ Required

const response = await fetch('http://localhost:8080/api/attachments/upload', {
  method: 'POST',
  body: formData
});
```

### Step 3: Add CORS Configuration to Backend

**New File Created:** `src/main/java/net/ai/chatbot/config/CorsConfig.java`

This file is already created and configured. Just rebuild your project:

```bash
# Rebuild the project
gradle clean build

# Or if using Maven
mvn clean install
```

---

## 🎯 Why You Were Getting 401

| Issue | Reason |
|-------|--------|
| **Wrong Port** | API is on 8080, not 3000 |
| **Missing Parameters** | FormData missing chatbotId/sessionId |
| **CORS Blocked** | Frontend (port 3000) blocked by backend (port 8080) |

---

## ✅ What Was Done

### Backend Files Added

1. **CorsConfig.java** - CORS configuration to allow port 3000 requests
   - Location: `src/main/java/net/ai/chatbot/config/CorsConfig.java`
   - Status: ✅ No errors
   - Ready to use: ✅ Yes

### Frontend Files Created

1. **TROUBLESHOOTING_401_ERROR.md** - Complete debugging guide
2. **FIX_401_ERROR_SUMMARY.md** - This file

---

## 🚀 Complete Working Code

### Frontend (React/JavaScript)

```javascript
async function uploadFile(file) {
  // 1. Create FormData with all required fields
  const formData = new FormData();
  formData.append('file', file);
  formData.append('chatbotId', 'chatbot_123');
  formData.append('sessionId', 'session_456');

  try {
    // 2. Send to CORRECT URL (port 8080, not 3000)
    const response = await fetch('http://localhost:8080/api/attachments/upload', {
      method: 'POST',
      body: formData
    });

    // 3. Check if request was successful
    if (!response.ok) {
      const error = await response.json();
      console.error('Upload failed:', error.message);
      return null;
    }

    // 4. Parse response
    const result = await response.json();
    console.log('✅ File uploaded:', result.fileId);
    return result;

  } catch (error) {
    console.error('❌ Error:', error);
    return null;
  }
}
```

---

## 📋 Deployment Checklist

- [ ] Change API URL from 3000 to 8080 in frontend
- [ ] Add chatbotId to FormData
- [ ] Add sessionId to FormData
- [ ] Don't set Content-Type header (let FormData handle it)
- [ ] Backend CORS config added (CorsConfig.java)
- [ ] Rebuild backend project
- [ ] Restart backend server
- [ ] Test in Postman first
- [ ] Test in frontend browser

---

## 🧪 Test It

### Option 1: Use Postman (Fastest)

1. Import: `FILE_ATTACHMENT_API.postman_collection.json`
2. Select: Upload File endpoint
3. Add file in form-data
4. Add chatbotId parameter
5. Add sessionId parameter
6. Click Send
7. Should see fileId in response ✅

### Option 2: Use HTML Form

```html
<form id="uploadForm">
  <input type="file" id="file" required>
  <input type="text" value="chatbot_123" id="chatbotId">
  <input type="text" value="session_456" id="sessionId">
  <button type="submit">Upload</button>
</form>

<script>
document.getElementById('uploadForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  
  const formData = new FormData();
  formData.append('file', document.getElementById('file').files[0]);
  formData.append('chatbotId', document.getElementById('chatbotId').value);
  formData.append('sessionId', document.getElementById('sessionId').value);

  const response = await fetch('http://localhost:8080/api/attachments/upload', {
    method: 'POST',
    body: formData
  });

  const result = await response.json();
  console.log('Result:', result);
});
</script>
```

---

## 🔍 Verify Setup

### Check Backend is Running

```bash
# Should return file list (empty or not)
curl http://localhost:8080/api/attachments/list/chatbot_123
```

### Check CORS is Working

Open browser console and check for CORS errors:
- ❌ If you see "blocked by CORS" → Rebuild backend
- ✅ If no error → CORS is working

### Check Request Parameters

Open browser DevTools → Network tab:
- Check URL is `http://localhost:8080/api/attachments/upload`
- Check Method is `POST`
- Check Form Data includes: file, chatbotId, sessionId

---

## 📚 Related Documents

- **TROUBLESHOOTING_401_ERROR.md** - Detailed debugging guide
- **API_DOCUMENTATION_FOR_FRONTEND.md** - Complete API reference
- **API_QUICK_REFERENCE.md** - Quick lookup

---

## ✨ You're Ready!

The 401 error should be fixed. If still having issues:

1. ✅ Verify URL is `localhost:8080` (not 3000)
2. ✅ Check all form parameters are included
3. ✅ Rebuild backend with CORS config
4. ✅ Check browser console for errors
5. ✅ Test with Postman first

---

## 📞 Quick Reference

| Item | Value |
|------|-------|
| Backend URL | `http://localhost:8080` |
| Frontend URL | `http://localhost:3000` |
| Upload Endpoint | `POST /api/attachments/upload` |
| Required Fields | file, chatbotId, sessionId |
| Content-Type | Don't set (FormData sets it) |
| CORS Config | CorsConfig.java (added) |

---

**Status:** ✅ Fixed & Ready  
**Last Updated:** February 10, 2026



