# Frontend Code Migration Examples

Complete before/after code examples for common use cases.

---

## Example 1: React Component - Simple Upload

### BEFORE (Old Code)
```typescript
import React, { useState } from 'react';

interface UploadedMedia {
  attachmentId: string;
  url: string;
  fileName: string;
}

export const SocialMediaUpload: React.FC = () => {
  const [media, setMedia] = useState<UploadedMedia[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const handleUpload = async (files: FileList) => {
    setIsLoading(true);
    const formData = new FormData();
    Array.from(files).forEach(file => {
      formData.append('files', file);
    });

    try {
      const response = await fetch('/v1/api/social-media/upload', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        body: formData,
      });

      const data = await response.json();
      
      // ❌ OLD: Used attachmentId field
      setMedia(data.items.map((item: any) => ({
        attachmentId: item.mediaId,  // ← Different naming
        url: item.mediaUrl,
        fileName: item.fileName,
      })));
    } catch (error) {
      console.error('Upload failed:', error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div>
      <input
        type="file"
        multiple
        onChange={e => handleUpload(e.target.files!)}
        disabled={isLoading}
      />
      
      <div className="media-list">
        {media.map(m => (
          <div key={m.attachmentId}>  {/* ❌ Used attachmentId */}
            <img src={m.url} alt={m.fileName} />
            <p>{m.fileName}</p>
          </div>
        ))}
      </div>
    </div>
  );
};
```

### AFTER (Updated Code) ✅
```typescript
import React, { useState } from 'react';

interface UploadedMedia {
  mediaId: string;      // ✅ Changed: attachmentId → mediaId
  mediaUrl: string;     // ✅ Changed: url → mediaUrl (matches API)
  fileName: string;
}

export const SocialMediaUpload: React.FC = () => {
  const [media, setMedia] = useState<UploadedMedia[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const handleUpload = async (files: FileList) => {
    setIsLoading(true);
    const formData = new FormData();
    Array.from(files).forEach(file => {
      formData.append('files', file);
    });

    try {
      const response = await fetch('/v1/api/social-media/upload', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        body: formData,
      });

      const data = await response.json();
      
      // ✅ NEW: Use mediaId directly (it's SocialAsset ID now)
      setMedia(data.items.map((item: any) => ({
        mediaId: item.mediaId,        // ✅ Use as-is
        mediaUrl: item.mediaUrl,      // ✅ Matches API field name
        fileName: item.fileName,
      })));
    } catch (error) {
      console.error('Upload failed:', error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div>
      <input
        type="file"
        multiple
        onChange={e => handleUpload(e.target.files!)}
        disabled={isLoading}
      />
      
      <div className="media-list">
        {media.map(m => (
          <div key={m.mediaId}>  {/* ✅ Use mediaId */}
            <img src={m.mediaUrl} alt={m.fileName} />
            <p>{m.fileName}</p>
          </div>
        ))}
      </div>
    </div>
  );
};
```

---

## Example 2: Custom Hook

### BEFORE (Old Code)
```typescript
// hooks/useMediaUpload.ts
import { useState, useCallback } from 'react';

export const useMediaUpload = (token: string) => {
  const [uploads, setUploads] = useState<{
    attachmentId: string;  // ❌ Not consistent with API
    fileName: string;
    url: string;
  }[]>([]);

  const upload = useCallback(async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch('/v1/api/social-media/upload', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` },
      body: formData,
    });

    const { items } = await response.json();
    
    // ❌ Rename field for storage
    const result = items[0];
    setUploads(prev => [...prev, {
      attachmentId: result.mediaId,  // ❌ Confusing naming
      fileName: result.fileName,
      url: result.mediaUrl,
    }]);
  }, [token]);

  const remove = useCallback((attachmentId: string) => {
    setUploads(prev => prev.filter(u => u.attachmentId !== attachmentId));
  }, []);

  return { uploads, upload, remove };
};
```

### AFTER (Updated Code) ✅
```typescript
// hooks/useMediaUpload.ts
import { useState, useCallback } from 'react';

export const useMediaUpload = (token: string) => {
  // ✅ Match API response structure
  const [uploads, setUploads] = useState<{
    mediaId: string;
    fileName: string;
    mediaUrl: string;
    mimeType: string;
    sizeBytes: number;
  }[]>([]);

  const upload = useCallback(async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch('/v1/api/social-media/upload', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` },
      body: formData,
    });

    const { items } = await response.json();
    
    // ✅ Use API fields directly
    const result = items[0];
    setUploads(prev => [...prev, {
      mediaId: result.mediaId,          // ✅ Direct from API
      fileName: result.fileName,
      mediaUrl: result.mediaUrl,        // ✅ Direct from API
      mimeType: result.mimeType,        // ✅ Store for reference
      sizeBytes: result.sizeBytes,      // ✅ Store for reference
    }]);
  }, [token]);

  // ✅ Use mediaId consistently
  const remove = useCallback((mediaId: string) => {
    setUploads(prev => prev.filter(u => u.mediaId !== mediaId));
  }, []);

  return { uploads, upload, remove };
};
```

---

## Example 3: Schedule Post Flow

### BEFORE (Old Code)
```typescript
const schedulePost = async (content: string) => {
  // Get uploaded media with attachmentId
  const { uploads } = useMediaUpload(token);

  const payload = {
    content,
    media: uploads.map(u => ({
      mediaId: u.attachmentId,  // ❌ Wrong field name
      mediaUrl: u.url,          // ❌ Not matching API field
      fileName: u.fileName,
    })),
    scheduledAt: new Date().toISOString(),
    targetIds: ['facebook:page_id', 'twitter:account_id'],  // Format: platform:accountId
  };

  const response = await fetch('/v1/api/social-posts/schedule', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  return response.json();
};
```

### AFTER (Updated Code) ✅
```typescript
const schedulePost = async (content: string) => {
  // Get uploaded media with mediaId
  const { uploads } = useMediaUpload(token);

  // ✅ Cleaner - no field renaming needed
  const payload = {
    content,
    media: uploads.map(u => ({
      mediaId: u.mediaId,          // ✅ Direct from upload
      mediaUrl: u.mediaUrl,        // ✅ Matches API field
      fileName: u.fileName,
    })),
    scheduledAt: new Date().toISOString(),
    targetIds: ['facebook:page_id', 'twitter:account_id'],  // Format: platform:accountId
  };

  const response = await fetch('/v1/api/social-posts/schedule', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  return response.json();
};
```

---

## Example 4: Draft Storage

### BEFORE (Old Code)
```typescript
// Save draft with media
const saveDraft = (content: string, mediaList: any[]) => {
  const draft = {
    id: generateId(),
    content,
    version: '1.0',
    media: mediaList.map(m => ({
      attachmentId: m.attachmentId,  // ❌ Stored internal field
      url: m.url,
      fileName: m.fileName,
    })),
    timestamp: new Date().toISOString(),
  };

  localStorage.setItem('draft', JSON.stringify(draft));
};

// Load draft
const loadDraft = () => {
  const draft = JSON.parse(localStorage.getItem('draft') || '{}');
  
  // ❌ Have to map back to component format
  return {
    content: draft.content,
    media: draft.media.map(m => ({
      attachmentId: m.attachmentId,
      url: m.url,
      fileName: m.fileName,
    })),
  };
};
```

### AFTER (Updated Code) ✅
```typescript
// Save draft with media
const saveDraft = (content: string, mediaList: UploadedMedia[]) => {
  const draft = {
    id: generateId(),
    content,
    version: '2.0',  // ✅ Version bump for clarity
    // ✅ Store entire media objects directly
    media: mediaList,
    timestamp: new Date().toISOString(),
  };

  localStorage.setItem('draft', JSON.stringify(draft));
};

// Load draft
const loadDraft = (): DraftPost | null => {
  const draft = JSON.parse(localStorage.getItem('draft') || 'null');
  
  if (!draft) return null;
  
  // ✅ No transformation needed
  return {
    content: draft.content,
    media: draft.media,  // Already has mediaId, mediaUrl, etc.
  };
};
```

---

## Example 5: Media Gallery Component

### BEFORE (Old Code)
```typescript
interface MediaGalleryProps {
  items: {
    attachmentId: string;  // ❌ Confusing naming
    url: string;
    fileName: string;
    width?: number;
    height?: number;
  }[];
  onRemove?: (attachmentId: string) => void;
}

export const MediaGallery: React.FC<MediaGalleryProps> = ({ items, onRemove }) => {
  return (
    <div className="gallery">
      {items.map(item => (
        <div key={item.attachmentId} className="gallery-item">
          <img src={item.url} alt={item.fileName} />
          {onRemove && (
            <button onClick={() => onRemove(item.attachmentId)}>
              Delete
            </button>
          )}
        </div>
      ))}
    </div>
  );
};
```

### AFTER (Updated Code) ✅
```typescript
interface MediaGalleryProps {
  items: UploadedMedia[];  // ✅ Use proper type
  onRemove?: (mediaId: string) => void;
}

export const MediaGallery: React.FC<MediaGalleryProps> = ({ items, onRemove }) => {
  return (
    <div className="gallery">
      {items.map(item => (
        <div key={item.mediaId} className="gallery-item">
          <img 
            src={item.mediaUrl}  // ✅ Use correct field
            alt={item.fileName}
            width={item.width}
            height={item.height}
          />
          {onRemove && (
            <button onClick={() => onRemove(item.mediaId)}>
              Delete
            </button>
          )}
        </div>
      ))}
    </div>
  );
};
```

---

## Example 6: Error Handling

### BEFORE (Old Code)
```typescript
const handleUploadError = async (response: Response) => {
  if (response.status === 413) {
    showError('File too large - max 12MB');  // ❌ Outdated limit
  } else if (response.status === 415) {
    showError('Invalid file type');
  } else if (response.status === 401) {
    showError('Please log in');
  } else {
    const { message } = await response.json();
    showError(message || 'Upload failed');
  }
};
```

### AFTER (Updated Code) ✅
```typescript
const handleUploadError = async (response: Response) => {
  if (response.status === 413) {
    showError('File too large - max 50MB');  // ✅ Updated limit
  } else if (response.status === 415) {
    showError('Unsupported file type. Use: JPG, PNG, MP4, PDF');
  } else if (response.status === 401) {
    showError('Authentication expired. Please log in again');
  } else {
    const { message } = await response.json();
    showError(message || 'Upload failed. Please try again');
  }
};
```

---

## Example 7: Type Definitions

### BEFORE (Old Code)
```typescript
// types/social.ts
export interface MediaUploadResponse {
  items: Array<{
    mediaId: string;       // Actually attachmentId
    mediaUrl: string;
    mimeType: string;
    fileName: string;
    sizeBytes: number;
  }>;
}

// Local storage type (different structure!)
export interface StoredMedia {
  attachmentId: string;    // Different from API!
  url: string;             // Different from API!
  fileName: string;
}

// Post schedule type
export interface SchedulePostRequest {
  content: string;
  media: Array<{
    mediaId: string;       // But here we used mediaId
    mediaUrl: string;      // And mediaUrl
    fileName: string;
  }>;
}
```

### AFTER (Updated Code) ✅
```typescript
// types/social.ts

// API Response (exactly as returned)
export interface MediaUploadResponse {
  items: UploadedMedia[];
}

// Uploaded media (use everywhere - consistency!)
export interface UploadedMedia {
  mediaId: string;        // ✅ SocialAsset ID
  mediaUrl: string;
  mimeType: string;
  fileName: string;
  sizeBytes: number;
  width?: number;
  height?: number;
  durationMs?: number;
  thumbnailUrl?: string;
}

// Draft post
export interface DraftPost {
  id: string;
  content: string;
  media: UploadedMedia[];  // ✅ Reuse same type
  scheduledAt?: string;
  platforms?: string[];
}

// Schedule post request (same as upload items!)
export interface SchedulePostRequest {
  content: string;
  media: UploadedMedia[];  // ✅ Reuse same type
  scheduledAt: string;
  platforms: string[];
}
```

---

## Example 8: Complete Component Update

### BEFORE (Old Code - Full Component)
```typescript
export const SocialPostComposer: React.FC = () => {
  const [content, setContent] = useState('');
  const [media, setMedia] = useState<Array<{
    attachmentId: string;
    url: string;
    fileName: string;
  }>>([]);

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files) return;

    const formData = new FormData();
    Array.from(files).forEach(f => formData.append('files', f));

    try {
      const res = await fetch('/v1/api/social-media/upload', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
        body: formData,
      });
      const { items } = await res.json();
      setMedia(prev => [...prev, ...items.map((i: any) => ({
        attachmentId: i.mediaId,
        url: i.mediaUrl,
        fileName: i.fileName,
      }))]);
    } catch (e) {
      console.error(e);
    }
  };

  const removeMedia = (attachmentId: string) => {
    setMedia(m => m.filter(x => x.attachmentId !== attachmentId));
  };

  const schedulePost = async () => {
    const res = await fetch('/v1/api/social-posts/schedule', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        content,
        media: media.map(m => ({
          mediaId: m.mediaId,
          mediaUrl: m.mediaUrl,
          fileName: m.fileName,
        })),
        targetIds: ['facebook:page_id'],  // Format: platform:accountId
      }),
    });
    // ...
  };

  return (
    <div>
      <textarea value={content} onChange={e => setContent(e.target.value)} />
      <input type="file" multiple onChange={handleFileSelect} />
      <div>
        {media.map(m => (
          <div key={m.attachmentId}>
            <img src={m.url} alt={m.fileName} />
            <button onClick={() => removeMedia(m.attachmentId)}>Remove</button>
          </div>
        ))}
      </div>
      <button onClick={schedulePost}>Schedule</button>
    </div>
  );
};
```

### AFTER (Updated Code) ✅
```typescript
export const SocialPostComposer: React.FC = () => {
  const [content, setContent] = useState('');
  const [media, setMedia] = useState<UploadedMedia[]>([]);  // ✅ Use proper type

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files) return;

    const formData = new FormData();
    Array.from(files).forEach(f => formData.append('files', f));

    try {
      const res = await fetch('/v1/api/social-media/upload', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
        body: formData,
      });
      const { items }: MediaUploadResponse = await res.json();
      // ✅ Use items directly - no transformation needed
      setMedia(prev => [...prev, ...items]);
    } catch (e) {
      console.error('Upload failed:', e);
    }
  };

  // ✅ Use mediaId parameter
  const removeMedia = (mediaId: string) => {
    setMedia(m => m.filter(x => x.mediaId !== mediaId));
  };

  const schedulePost = async () => {
    const res = await fetch('/v1/api/social-media/schedule', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        content,
        // ✅ Pass media items directly - no transformation
        media: media.map(m => ({
          mediaId: m.mediaId,
          mediaUrl: m.mediaUrl,
          fileName: m.fileName,
        })),
        platforms: ['facebook'],
      }),
    });
    // ...
  };

  return (
    <div>
      <textarea value={content} onChange={e => setContent(e.target.value)} />
      <input type="file" multiple onChange={handleFileSelect} />
      <div>
        {media.map(m => (
          <div key={m.mediaId}>  {/* ✅ Use mediaId */}
            <img src={m.mediaUrl} alt={m.fileName} />
            <button onClick={() => removeMedia(m.mediaId)}>Remove</button>
          </div>
        ))}
      </div>
      <button onClick={schedulePost}>Schedule</button>
    </div>
  );
};
```

---

## Summary of Changes

| Item | Before | After | Why |
|------|--------|-------|-----|
| Field name | `attachmentId` | `mediaId` | Matches API response |
| URL field | `url` | `mediaUrl` | Matches API field |
| Type definition | Inconsistent | `UploadedMedia` | Single source of truth |
| Storage | Custom mapping | Direct items | No transformation needed |
| Schedule payload | `attachmentId` | `mediaId` | Consistent with upload |
| Draft storage | Field renaming | Direct storage | Simpler code |

✅ **Result:** Less code, more consistent, easier to maintain
