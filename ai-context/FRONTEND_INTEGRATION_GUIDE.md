# Frontend Integration Quick Reference

## Quick Start - 5 Minutes

### 1. Setup FormData
```javascript
const formData = new FormData();
formData.append('message', 'Your message here');
formData.append('chatbotId', 'your-chatbot-id');
formData.append('sessionId', `session-${Date.now()}`);
```

### 2. Add Files
```javascript
// Single file
formData.append('files', fileInput.files[0]);

// Multiple files
for (let file of fileInput.files) {
  formData.append('files', file);
}
```

### 3. Send Request
```javascript
const response = await fetch(
  '/v1/api/n8n/multimodal/authenticated/multipart/chat',
  {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${authToken}`
    },
    body: formData
  }
);

const data = await response.json();
```

### 4. Handle Response
```javascript
if (data.success) {
  console.log('Files uploaded:', data.vectorAttachments);
  console.log('AI Response:', data.result.response);
} else {
  console.error('Error:', data.errorMessage);
}
```

---

## Copy-Paste Ready Code

### Vue 3 Component
```vue
<template>
  <div class="upload-container">
    <form @submit.prevent="uploadFiles">
      <textarea 
        v-model="message" 
        placeholder="Enter message..."
        required
      />
      
      <input 
        type="file" 
        @change="onFileSelect" 
        multiple
        required
      />
      
      <button type="submit" :disabled="loading">
        {{ loading ? 'Uploading...' : 'Send' }}
      </button>
    </form>
    
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="response" class="success">
      <p>{{ response.result.response }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const message = ref('');
const files = ref([]);
const loading = ref(false);
const error = ref(null);
const response = ref(null);

const onFileSelect = (e) => {
  files.value = Array.from(e.target.files);
};

const uploadFiles = async () => {
  loading.value = true;
  error.value = null;

  try {
    const formData = new FormData();
    formData.append('message', message.value);
    formData.append('chatbotId', 'chatbot-123');
    formData.append('sessionId', `session-${Date.now()}`);
    
    files.value.forEach(file => {
      formData.append('files', file);
    });

    const res = await fetch(
      '/v1/api/n8n/multimodal/authenticated/multipart/chat',
      {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('authToken')}`
        },
        body: formData
      }
    );

    const data = await res.json();
    
    if (!data.success) {
      error.value = data.errorMessage;
      return;
    }

    response.value = data;
    message.value = '';
    files.value = [];
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
};
</script>
```

### React Hooks
```jsx
import { useState } from 'react';

export function FileUpload() {
  const [message, setMessage] = useState('');
  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState(null);
  const [error, setError] = useState(null);

  const handleUpload = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    const formData = new FormData();
    formData.append('message', message);
    formData.append('chatbotId', 'chatbot-123');
    formData.append('sessionId', `session-${Date.now()}`);
    
    files.forEach(file => formData.append('files', file));

    try {
      const res = await fetch(
        '/v1/api/n8n/multimodal/authenticated/multipart/chat',
        {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('authToken')}`
          },
          body: formData
        }
      );

      const data = await res.json();
      if (!data.success) throw new Error(data.errorMessage);
      
      setResponse(data);
      setMessage('');
      setFiles([]);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleUpload}>
      <textarea 
        value={message} 
        onChange={(e) => setMessage(e.target.value)}
        placeholder="Message..."
      />
      <input 
        type="file" 
        multiple 
        onChange={(e) => setFiles(Array.from(e.target.files))}
      />
      <button disabled={loading}>{loading ? 'Uploading...' : 'Send'}</button>
      {error && <p className="error">{error}</p>}
      {response && <p className="success">{response.result.response}</p>}
    </form>
  );
}
```

### Svelte Component
```svelte
<script>
  let message = '';
  let files = [];
  let loading = false;
  let response = null;
  let error = null;

  async function handleUpload(e) {
    e.preventDefault();
    loading = true;
    error = null;

    const formData = new FormData();
    formData.append('message', message);
    formData.append('chatbotId', 'chatbot-123');
    formData.append('sessionId', `session-${Date.now()}`);
    
    files.forEach(file => formData.append('files', file));

    try {
      const res = await fetch(
        '/v1/api/n8n/multimodal/authenticated/multipart/chat',
        {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('authToken')}`
          },
          body: formData
        }
      );

      const data = await res.json();
      if (!data.success) throw new Error(data.errorMessage);
      
      response = data;
      message = '';
      files = [];
    } catch (err) {
      error = err.message;
    } finally {
      loading = false;
    }
  }

  function handleFileSelect(e) {
    files = Array.from(e.target.files);
  }
</script>

<form on:submit={handleUpload}>
  <textarea bind:value={message} placeholder="Message..." required />
  <input type="file" multiple on:change={handleFileSelect} required />
  <button disabled={loading}>{loading ? 'Uploading...' : 'Send'}</button>
  {#if error}
    <p class="error">{error}</p>
  {/if}
  {#if response}
    <p class="success">{response.result.response}</p>
  {/if}
</form>
```

---

## API Endpoints Summary

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/authenticated/multipart/chat` | ✅ Required | Upload files (authenticated) |
| POST | `/anonymous/multipart/chat` | ❌ Not Required | Upload files (public) |
| GET | `/attachments/{chatbotId}` | ❌ Not Required | List attachments |

---

## Response Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Process response data |
| 400 | Bad Request | Check parameters/files |
| 401 | Unauthorized | Refresh auth token |
| 500 | Server Error | Retry or contact support |

---

## Common Errors & Solutions

### ❌ "ChatbotId is required"
**Solution:** Add `chatbotId` to FormData
```javascript
formData.append('chatbotId', 'your-chatbot-id');
```

### ❌ "MIME type 'application/exe' is not allowed"
**Solution:** Only use supported file types (PDF, DOCX, PNG, etc.)

### ❌ "File size exceeds 100MB limit"
**Solution:** Split files or compress before upload
```javascript
if (file.size > 100 * 1024 * 1024) {
  alert('File too large');
}
```

### ❌ "ChatBot not found"
**Solution:** Verify chatbotId exists in database
```javascript
// Wrong
formData.append('chatbotId', 'nonexistent-id');

// Correct
formData.append('chatbotId', 'chatbot-123');
```

### ❌ 401 Unauthorized
**Solution:** Check JWT token in localStorage
```javascript
const token = localStorage.getItem('authToken');
if (!token) {
  // Redirect to login
  window.location.href = '/login';
}
```

---

## File Validation Before Upload

```javascript
function validateFiles(files) {
  const MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
  const MAX_FILES = 20;
  const ALLOWED_TYPES = [
    'application/pdf',
    'text/plain',
    'text/csv',
    'application/json',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    'image/jpeg',
    'image/png',
    'image/gif',
    'image/webp',
    'image/svg+xml'
  ];

  if (files.length === 0) {
    throw new Error('Please select at least one file');
  }

  if (files.length > MAX_FILES) {
    throw new Error(`Maximum ${MAX_FILES} files allowed`);
  }

  for (let file of files) {
    if (file.size > MAX_FILE_SIZE) {
      throw new Error(`${file.name} exceeds 100MB limit`);
    }

    if (!ALLOWED_TYPES.includes(file.type)) {
      throw new Error(`${file.name} type not allowed`);
    }
  }

  return true;
}

// Usage
try {
  validateFiles(files);
  // Proceed with upload
} catch (error) {
  alert(error.message);
}
```

---

## TypeScript Types

```typescript
interface UploadRequest {
  message: string;
  files: File[];
  chatbotId: string;
  sessionId: string;
}

interface VectorAttachment {
  vectorId: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
  uploadedAt: number;
}

interface UploadResponse {
  success: boolean;
  result?: {
    response: string;
    metadata?: Record<string, any>;
  };
  vectorIdMap?: Record<string, string>;
  vectorAttachments?: VectorAttachment[];
  errorCode?: string;
  errorMessage?: string;
  timestamp: number;
}
```

---

## Axios Integration

```javascript
import axios from 'axios';

async function uploadFiles(message, files, chatbotId, sessionId, authToken) {
  const formData = new FormData();
  formData.append('message', message);
  formData.append('chatbotId', chatbotId);
  formData.append('sessionId', sessionId);
  
  files.forEach(file => formData.append('files', file));

  try {
    const response = await axios.post(
      '/v1/api/n8n/multimodal/authenticated/multipart/chat',
      formData,
      {
        headers: {
          'Authorization': `Bearer ${authToken}`,
          'Content-Type': 'multipart/form-data'
        }
      }
    );

    return response.data;
  } catch (error) {
    console.error('Upload failed:', error.response?.data);
    throw error;
  }
}
```

---

## Testing with Postman

1. **Create New Request**
   - Method: POST
   - URL: `{{BASE_URL}}/v1/api/n8n/multimodal/authenticated/multipart/chat`

2. **Headers Tab**
   - Authorization: `Bearer {{AUTH_TOKEN}}`

3. **Body Tab** → form-data
   - `message` (text): "Analyze these files"
   - `chatbotId` (text): "chatbot-123"
   - `sessionId` (text): "session-123"
   - `files` (file): Select multiple files

4. **Send**

---

## Performance Tips

1. **Batch Processing**: Upload up to 20 files per request
2. **Compression**: Compress images before upload
3. **Error Handling**: Implement retry logic for failed uploads
4. **Progress Tracking**: Show upload progress to users
5. **Caching**: Cache responses to reduce API calls

---

## Environment Variables

```bash
REACT_APP_API_BASE_URL=http://localhost:8080
REACT_APP_CHATBOT_ID=chatbot-123
REACT_APP_AUTH_TOKEN=${authToken}
```

---

## Troubleshooting Checklist

- [ ] API endpoint URL is correct
- [ ] Authentication token is valid
- [ ] Files are supported types
- [ ] Files are under 100MB each
- [ ] FormData has all required fields
- [ ] No CORS issues (check browser console)
- [ ] Network request shows in browser DevTools
- [ ] Response status is 200 or 400 (not 500)

---

**Version:** 1.0  
**Last Updated:** 2024-02-17

