# N8N Attachment Chat Implementation Guide

## Quick Start Guide

This guide provides step-by-step instructions for implementing and using attachment support in the N8N Chat Widget API.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Components](#components)
3. [Installation & Setup](#installation--setup)
4. [Configuration](#configuration)
5. [Integration Examples](#integration-examples)
6. [Testing](#testing)
7. [Deployment](#deployment)
8. [Monitoring & Maintenance](#monitoring--maintenance)

---

## Architecture Overview

### System Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend Application                      │
│  (Web, Mobile, Desktop - sends chat + attachments)          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ HTTP POST
                       │ JSON + Base64 files
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Chat API Endpoints                              │
│  /v1/api/n8n/anonymous/chat/with-attachments               │
│  /v1/api/n8n/authenticated/chat/with-attachments           │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│            N8N Attachment Service                            │
│  • Validates attachments                                     │
│  • Processes files                                           │
│  • Manages storage                                           │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
        ▼              ▼              ▼
   ┌────────┐   ┌───────────┐  ┌──────────────┐
   │ N8N    │   │ File      │  │ Attachment   │
   │Webhook │   │Storage    │  │Management    │
   │        │   │           │  │APIs          │
   └────────┘   └───────────┘  └──────────────┘
        │
        └──────────────────┬──────────────────┐
                           │                  │
                           ▼                  ▼
                      ┌─────────┐      ┌──────────────┐
                      │ N8N     │      │ Knowledge    │
                      │Workflow │      │ Base / AI    │
                      └─────────┘      └──────────────┘
```

### Data Flow

1. **Request Phase**
   - Client encodes files to Base64
   - Creates JSON request with message + attachments
   - Sends to API endpoint

2. **Processing Phase**
   - API validates request structure
   - Validates each attachment (type, size, encoding)
   - Decodes Base64 data
   - Saves files to disk

3. **Webhook Phase**
   - Sends request to N8N workflow
   - Includes attachment metadata in headers
   - N8N processes files and message
   - Returns response to client

4. **Response Phase**
   - API returns N8N response
   - Client displays result

---

## Components

### 1. DTOs (Data Transfer Objects)

#### Attachment.java
- Core attachment model
- Contains: name, size, type, data (Base64)
- Methods: getFileData(), getMimeType()

#### N8NChatRequest.java
- Request wrapper for N8N chat
- Validates required fields
- Enforces role == "user"
- Ensures message OR attachments present

#### AttachmentMetadata.java
- Metadata about saved attachments
- Contains: name, type, size, filePath, savedAt
- Used for attachment management APIs

#### StorageStats.java
- Session storage statistics
- Contains: fileCount, totalSize, formattedSize
- Tracks quota usage

### 2. Services

#### N8NAttachmentService
- Core service for attachment operations
- Methods:
  - `processAttachment()` - Save single attachment
  - `processAttachments()` - Save multiple attachments
  - `getAttachmentMetadata()` - Get file info
  - `deleteAttachment()` - Remove file
  - `listSessionAttachments()` - List files
  - `getSessionStorageStats()` - Get storage info

#### GenericN8NService (Updated)
- Extended with attachment support
- New methods:
  - `sendMessageWithAttachments()` - Send message with files
- Updated methods:
  - `executeWebhook()` - Now processes attachments

### 3. Controllers

#### AnonymousUserChatN8NController (Updated)
- New endpoint: `POST /v1/api/n8n/anonymous/chat/with-attachments`
- Handles anonymous user requests with attachments
- Validates N8NChatRequest
- Processes message and files

#### AuthenticatedUserChatN8NController (Updated)
- New endpoint: `POST /v1/api/n8n/authenticated/chat/with-attachments`
- Handles authenticated user requests with attachments
- Validates authentication + N8NChatRequest
- Processes message and files

#### N8NAttachmentController (New)
- Attachment management endpoints
- Methods:
  - GET: List, get metadata, get stats
  - DELETE: Remove files, cleanup session

### 4. Utilities

#### AttachmentUtils
- File validation
- Base64 encoding/decoding
- File storage operations
- MIME type management
- Filename sanitization
- File size formatting

---

## Installation & Setup

### Prerequisites

- Java 11+
- Spring Boot 2.7+
- MongoDB (for chat history)
- Disk space for file storage
- N8N instance configured

### Step 1: Add Dependencies

The following dependencies are required in `pom.xml`:

```xml
<!-- Already included in Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>

<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

### Step 2: Create Upload Directory

```bash
# Create uploads directory
mkdir -p /var/app/uploads

# Set permissions
chmod 755 /var/app/uploads
chown appuser:appgroup /var/app/uploads
```

### Step 3: Update Application Configuration

See [Configuration Section](#configuration) below.

---

## Configuration

### application.properties / application.yml

```yaml
# File Upload Configuration
file:
  upload:
    path: /var/app/uploads
  max:
    size: 104857600  # 100 MB

# N8N Webhook Configuration
n8n:
  webhook:
    knowledgebase:
      chat:
        url: https://your-n8n-instance.com/webhook/your-workflow

# Optional: Storage Cleanup
file:
  cleanup:
    enabled: true
    days: 30
    schedule: "0 2 * * *"  # Daily at 2 AM

# Optional: Security
security:
  allowed-mime-types:
    - application/pdf
    - image/jpeg
    - image/png
    - text/plain
    - text/csv
```

### Example application.properties

```properties
# File Configuration
file.upload.path=/var/app/uploads
file.max.size=104857600

# N8N Configuration
n8n.webhook.knowledgebase.chat.url=https://n8n.example.com/webhook/chatbot-workflow

# Server Configuration
server.port=8080
server.servlet.context-path=/
server.tomcat.max-http-post-size=104857600

# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/chatbot_db

# Logging
logging.level.net.ai.chatbot=INFO
```

---

## Integration Examples

### JavaScript/Node.js Example

```javascript
// Helper function to encode file
async function encodeFileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const base64String = reader.result.split(',')[1];
      resolve(base64String);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

// Send message with attachment
async function sendChatWithAttachment(message, file, chatbotId, sessionId) {
  const base64Data = await encodeFileToBase64(file);
  
  const payload = {
    role: 'user',
    message: message,
    attachments: [
      {
        name: file.name,
        type: file.type,
        size: file.size,
        data: base64Data
      }
    ],
    sessionId: sessionId,
    chatbotId: chatbotId
  };

  try {
    const response = await fetch(
      'https://api.example.com/v1/api/n8n/anonymous/chat/with-attachments',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      }
    );

    const data = await response.json();
    
    if (data.success) {
      console.log('Response:', data.result);
      return data.result;
    } else {
      console.error('Error:', data.errorMessage);
      throw new Error(data.errorMessage);
    }
  } catch (error) {
    console.error('Request failed:', error);
    throw error;
  }
}

// Usage
const fileInput = document.getElementById('fileInput');
const file = fileInput.files[0];
const message = 'Please analyze this document';
const chatbotId = 'chatbot_12345';
const sessionId = `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

sendChatWithAttachment(message, file, chatbotId, sessionId)
  .then(result => console.log('Chat response:', result))
  .catch(error => console.error('Chat error:', error));
```

### Python Example

```python
import requests
import base64
import json

def send_chat_with_attachment(message, file_path, chatbot_id, session_id):
    """Send chat message with file attachment"""
    
    # Read and encode file
    with open(file_path, 'rb') as f:
        file_data = f.read()
        base64_data = base64.b64encode(file_data).decode('utf-8')
    
    # Determine MIME type
    import mimetypes
    mime_type, _ = mimetypes.guess_type(file_path)
    if mime_type is None:
        mime_type = 'application/octet-stream'
    
    # Create payload
    payload = {
        'role': 'user',
        'message': message,
        'attachments': [
            {
                'name': file_path.split('/')[-1],
                'type': mime_type,
                'size': len(file_data),
                'data': base64_data
            }
        ],
        'sessionId': session_id,
        'chatbotId': chatbot_id
    }
    
    # Send request
    try:
        response = requests.post(
            'https://api.example.com/v1/api/n8n/anonymous/chat/with-attachments',
            headers={'Content-Type': 'application/json'},
            json=payload,
            timeout=60
        )
        
        result = response.json()
        
        if result.get('success'):
            print('Response:', result['result'])
            return result['result']
        else:
            print('Error:', result.get('errorMessage'))
            raise Exception(result.get('errorMessage'))
    
    except requests.RequestException as e:
        print(f'Request error: {e}')
        raise

# Usage
import time
session_id = f"session_{int(time.time())}_test"
send_chat_with_attachment(
    message='Analyze this PDF',
    file_path='./document.pdf',
    chatbot_id='chatbot_12345',
    session_id=session_id
)
```

### cURL Example

```bash
#!/bin/bash

# Encode file to base64
BASE64_DATA=$(base64 -w 0 < /path/to/file.pdf)

# Send request
curl -X POST https://api.example.com/v1/api/n8n/anonymous/chat/with-attachments \
  -H "Content-Type: application/json" \
  -d '{
    "role": "user",
    "message": "Please review this document",
    "attachments": [
      {
        "name": "file.pdf",
        "type": "application/pdf",
        "size": '$(stat -f%z /path/to/file.pdf)',
        "data": "'$BASE64_DATA'"
      }
    ],
    "sessionId": "session_'$(date +%s)'",
    "chatbotId": "chatbot_12345"
  }'
```

---

## Testing

### Unit Tests

Create `N8NAttachmentServiceTest.java`:

```java
@SpringBootTest
class N8NAttachmentServiceTest {
    
    @Autowired
    private N8NAttachmentService attachmentService;
    
    @Test
    void testProcessAttachment() throws IOException {
        // Create test attachment
        Attachment attachment = Attachment.builder()
            .name("test.txt")
            .type("text/plain")
            .size(100L)
            .data(Base64.getEncoder().encodeToString("test content".getBytes()))
            .build();
        
        // Process
        AttachmentMetadata metadata = attachmentService.processAttachment(
            attachment, "bot_test", "session_test"
        );
        
        // Verify
        assertNotNull(metadata);
        assertEquals("test.txt", metadata.getName());
        assertEquals(100L, metadata.getSize());
    }
    
    @Test
    void testValidateAttachment() {
        // Valid attachment
        Attachment valid = Attachment.builder()
            .name("doc.pdf")
            .type("application/pdf")
            .size(1000L)
            .data("base64data")
            .build();
        
        // Should pass validation
        assertDoesNotThrow(() -> {
            attachmentService.processAttachment(valid, "bot", "session");
        });
    }
}
```

### Integration Tests

Test the full endpoint:

```bash
# Test anonymous endpoint
curl -X POST http://localhost:8080/v1/api/n8n/anonymous/chat/with-attachments \
  -H "Content-Type: application/json" \
  -d '{
    "role": "user",
    "message": "Test",
    "attachments": [],
    "sessionId": "test_session",
    "chatbotId": "test_bot"
  }'

# Expected response
# {"success": true, "result": "..."}
```

---

## Deployment

### Docker Deployment

```dockerfile
FROM openjdk:11-jre-slim

WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy application
COPY target/chatbot-api.jar app.jar

# Create upload directory
RUN mkdir -p /app/uploads && chmod 755 /app/uploads

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose

```yaml
version: '3.8'

services:
  chatbot-api:
    build: .
    ports:
      - "8080:8080"
    environment:
      - FILE_UPLOAD_PATH=/app/uploads
      - FILE_MAX_SIZE=104857600
      - N8N_WEBHOOK_KNOWLEDGEBASE_CHAT_URL=http://n8n:5678/webhook/chat
      - SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/chatbot_db
    volumes:
      - ./uploads:/app/uploads
      - ./logs:/app/logs
    depends_on:
      - mongo
      - n8n

  mongo:
    image: mongo:5.0
    volumes:
      - mongo_data:/data/db
    ports:
      - "27017:27017"

  n8n:
    image: n8nio/n8n:latest
    ports:
      - "5678:5678"
    environment:
      - N8N_BASIC_AUTH_ACTIVE=true
      - N8N_BASIC_AUTH_USER=admin
      - N8N_BASIC_AUTH_PASSWORD=admin
    volumes:
      - n8n_data:/home/node/.n8n

volumes:
  mongo_data:
  n8n_data:
```

---

## Monitoring & Maintenance

### Health Check Endpoint

```bash
curl http://api.example.com/actuator/health
```

### Storage Monitoring

```bash
# Check disk usage
df -h /var/app/uploads

# Find large files
find /var/app/uploads -type f -size +10M

# Calculate total size
du -sh /var/app/uploads
```

### Log Monitoring

```bash
# View logs
tail -f /var/log/app/chatbot-api.log

# Filter attachment logs
grep "attachment" /var/log/app/chatbot-api.log

# View errors
grep "ERROR" /var/log/app/chatbot-api.log
```

### Cleanup Tasks

```bash
# Clean old files (older than 30 days)
find /var/app/uploads -type f -mtime +30 -delete

# Clean empty directories
find /var/app/uploads -type d -empty -delete
```

---

## Performance Optimization

### 1. Request Batching

```javascript
// Send multiple files in one request
const payload = {
  role: 'user',
  message: 'Analyze these files',
  attachments: [
    // Multiple files
    { name: 'file1.pdf', ... },
    { name: 'file2.pdf', ... },
    { name: 'file3.pdf', ... }
  ],
  sessionId, chatbotId
};
```

### 2. Compression

```javascript
// Use pako library for compression
import pako from 'pako';

const compressed = pako.gzip(fileData);
const base64 = btoa(String.fromCharCode(...compressed));
```

### 3. Caching

```javascript
// Cache session metadata
const sessionMetadata = await fetch(`/api/attachments/stats/${botId}/${sessionId}`);
```

---

## Troubleshooting Checklist

- [ ] Verify upload directory exists and is writable
- [ ] Check file size limit in configuration
- [ ] Verify N8N webhook URL is correct
- [ ] Check Base64 encoding on client
- [ ] Review server logs for errors
- [ ] Verify disk space available
- [ ] Check file permissions
- [ ] Test with curl before integrating

---

## Next Steps

1. Configure environment variables
2. Set up file storage directory
3. Configure N8N webhook
4. Test with sample files
5. Deploy to staging environment
6. Monitor and optimize
7. Deploy to production

---

**Document Date:** February 6, 2026  
**Last Updated:** February 6, 2026

