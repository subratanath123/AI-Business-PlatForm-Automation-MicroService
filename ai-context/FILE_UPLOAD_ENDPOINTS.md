# File Upload Endpoints for N8N Integration

This document describes the new file upload endpoints added to the `AuthenticatedUserChatN8NController` for processing PDFs and other documents through N8N workflows.

## Overview

The new endpoints allow authenticated users to upload files (PDFs, documents, etc.) and send them to N8N workflows for processing. The files are converted to base64-encoded attachments and sent through the existing N8N service infrastructure.

## Endpoints

### 1. Single File Upload

**Endpoint:** `POST /v1/api/n8n/authenticated/chat/file`

**Description:** Upload a single file to an N8N workflow for processing.

**Parameters:**
- `file` (required): The file to upload (MultipartFile)
- `workflowId` (required): The N8N workflow ID
- `webhookUrl` (required): The N8N webhook URL
- `message` (optional): Custom message to send with the file
- `sessionId` (optional): Custom session ID (defaults to authenticated user's email)

**Example Request:**
```bash
curl -X POST \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -F "file=@document.pdf" \
  -F "workflowId=pdf-processor-workflow" \
  -F "webhookUrl=http://localhost:5678/webhook/abc123" \
  -F "message=Please analyze this PDF document" \
  http://localhost:8080/v1/api/n8n/authenticated/chat/file
```

**Example Response:**
```json
{
  "success": true,
  "workflowId": "pdf-processor-workflow",
  "timestamp": 1640995200000,
  "output": "File processed successfully",
  "body": {
    "result": "PDF analysis completed"
  }
}
```

### 2. Multiple Files Upload

**Endpoint:** `POST /v1/api/n8n/authenticated/chat/files`

**Description:** Upload multiple files to an N8N workflow for batch processing.

**Parameters:**
- `files` (required): List of files to upload (List<MultipartFile>)
- `workflowId` (required): The N8N workflow ID
- `webhookUrl` (required): The N8N webhook URL
- `message` (optional): Custom message to send with the files
- `sessionId` (optional): Custom session ID (defaults to authenticated user's email)

**Example Request:**
```bash
curl -X POST \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -F "files=@document1.pdf" \
  -F "files=@document2.docx" \
  -F "workflowId=batch-processor-workflow" \
  -F "webhookUrl=http://localhost:5678/webhook/def456" \
  -F "message=Please process these documents" \
  http://localhost:8080/v1/api/n8n/authenticated/chat/files
```

## File Processing

### Supported File Types

The endpoints accept any file type that can be handled by `MultipartFile`, including:
- PDF documents (`.pdf`)
- Word documents (`.docx`, `.doc`)
- Text files (`.txt`)
- Images (`.jpg`, `.png`, `.gif`)
- And more...

### File Conversion Process

1. **File Upload**: The frontend sends the file as a `MultipartFile`
2. **Conversion**: The file is converted to an `Attachment` DTO with:
   - Original filename
   - File size
   - MIME type
   - Base64-encoded content
3. **Message Creation**: A `Message` object is created with the attachment
4. **N8N Input**: The message is wrapped in an `N8NChatInput` object
5. **Service Call**: The input is sent to the N8N service for processing

### File Size Considerations

- Files are converted to base64, which increases size by approximately 33%
- Large files may impact performance and memory usage
- Consider implementing file size limits in your frontend validation

## Error Handling

### Common Error Responses

**File Processing Error (400):**
```json
{
  "success": false,
  "errorCode": "FILE_PROCESSING_ERROR",
  "errorMessage": "Failed to process file: [error details]"
}
```

**Internal Server Error (500):**
```json
{
  "success": false,
  "errorCode": "INTERNAL_ERROR",
  "errorMessage": "Internal server error: [error details]"
}
```

### Error Scenarios

1. **File Read Failures**: IOExceptions when reading file content
2. **Authentication Issues**: Missing or invalid JWT tokens
3. **N8N Service Failures**: Issues with the underlying N8N service
4. **Invalid Parameters**: Missing required parameters

## Frontend Integration

### JavaScript/TypeScript Example

```typescript
async function uploadFileToN8N(
  file: File, 
  workflowId: string, 
  webhookUrl: string, 
  message?: string
) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('workflowId', workflowId);
  formData.append('webhookUrl', webhookUrl);
  
  if (message) {
    formData.append('message', message);
  }

  const response = await fetch('/v1/api/n8n/authenticated/chat/file', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${getAuthToken()}`
    },
    body: formData
  });

  return await response.json();
}
```

### React Example

```jsx
import React, { useState } from 'react';

function FileUploadComponent() {
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);

  const handleFileUpload = async () => {
    if (!file) return;

    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('workflowId', 'pdf-processor');
      formData.append('webhookUrl', 'http://localhost:5678/webhook/abc123');

      const response = await fetch('/v1/api/n8n/authenticated/chat/file', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        },
        body: formData
      });

      const result = await response.json();
      console.log('Upload result:', result);
    } catch (error) {
      console.error('Upload failed:', error);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <input 
        type="file" 
        onChange={(e) => setFile(e.target.files[0])} 
        accept=".pdf,.docx,.txt"
      />
      <button onClick={handleFileUpload} disabled={uploading}>
        {uploading ? 'Uploading...' : 'Upload to N8N'}
      </button>
    </div>
  );
}
```

## Security Considerations

### Authentication

- All endpoints require valid JWT authentication
- The `Authorization: Bearer <token>` header is mandatory
- Session IDs default to the authenticated user's email

### File Validation

- Implement file type validation on the frontend
- Consider file size limits to prevent abuse
- Validate file content if necessary for your use case

### N8N Integration

- Ensure webhook URLs are secure and properly configured
- Validate workflow IDs to prevent unauthorized access
- Monitor N8N workflow execution and responses

## Testing

### Unit Tests

The endpoints include comprehensive unit tests covering:
- Successful file uploads
- Error handling (IOExceptions, authentication failures)
- Multiple file uploads
- Default message handling

### Manual Testing

Test the endpoints with:
1. Valid PDF files
2. Different file types
3. Various file sizes
4. Invalid authentication tokens
5. Missing required parameters

## Troubleshooting

### Common Issues

1. **Authentication Errors**: Ensure JWT token is valid and not expired
2. **File Upload Failures**: Check file size and format
3. **N8N Connection Issues**: Verify webhook URL and workflow ID
4. **Memory Issues**: Monitor server memory usage with large files

### Debug Logging

The endpoints include detailed logging for debugging:
- File upload details (name, size, type)
- Processing steps
- Error details
- N8N service interactions

## Future Enhancements

Potential improvements for future versions:
- File compression before upload
- Async file processing with webhooks
- File validation and virus scanning
- Progress tracking for large files
- File storage and retrieval
- Batch processing optimization
