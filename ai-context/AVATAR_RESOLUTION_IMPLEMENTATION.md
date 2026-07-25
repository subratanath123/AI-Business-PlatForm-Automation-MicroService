# Chatbot Avatar Resolution Implementation

## Overview
Implemented smart avatar resolution logic that distinguishes between preset avatars (URLs) and custom uploaded avatars (file IDs), with proper handling of invalid blob URLs.

## Avatar Resolution Logic

### Frontend → Backend (Create/Update)

The frontend can send **one of two scenarios**:

#### Scenario 1: Preset Avatar
```json
{
  "aiAvatar": "https://i.pravatar.cc/150?img=1"
}
```
**Backend stores:** `aiAvatarUrl` with the full URL

#### Scenario 2: Custom Upload
```json
{
  "avatarFileId": "file_xxx"
}
```
**Backend stores:** `avatarFileId` only

#### Scenario 3: Default (Preset)
```json
{
  "aiAvatar": "https://i.pravatar.cc/150?img=1"
}
```
**Backend stores:** `aiAvatarUrl` with the full URL

### Backend → Widget (Public Response)

The public endpoint `GET /v1/api/public/chatbot/{id}` implements smart resolution:

#### 1. Valid HTTP/HTTPS URL (Preset Avatar)
**Condition:** `aiAvatarUrl` is a valid `http://` or `https://` URL

**Response:**
```json
{
  "id": "chatbot_123",
  "name": "MyBot",
  "aiAvatar": "https://i.pravatar.cc/150?img=1",
  "avatarFileId": null
}
```

**Widget behavior:** Displays the image from `aiAvatar` URL directly

#### 2. Custom Upload (File ID)
**Condition:** `avatarFileId` exists and `aiAvatarUrl` is not a valid URL

**Response:**
```json
{
  "id": "chatbot_123",
  "name": "MyBot",
  "aiAvatar": null,
  "avatarFileId": "file_xxx"
}
```

**Widget behavior:** Constructs URL as:
```
{apiUrl}/api/attachments/download/{avatarFileId}?chatbotId={chatbot_123}
```

#### 3. Blob URL (Ignored)
**Condition:** `aiAvatarUrl` starts with `blob:`

**Response:**
```json
{
  "id": "chatbot_123",
  "name": "MyBot",
  "aiAvatar": null,
  "avatarFileId": null
}
```

**Widget behavior:** Shows first letter of chatbot name as fallback

#### 4. No Avatar (Default)
**Condition:** No `aiAvatarUrl` and no `avatarFileId`

**Response:**
```json
{
  "id": "chatbot_123",
  "name": "MyBot",
  "aiAvatar": null,
  "avatarFileId": null
}
```

**Widget behavior:** Shows first letter of chatbot name (e.g., "M" for "MyBot")

## Implementation Details

### Public Endpoint Logic

```java
// Avatar resolution logic
String resolvedAiAvatar = null;
String resolvedAvatarFileId = null;

// Check if aiAvatarUrl is a valid http/https URL (preset avatar)
if (isValidHttpUrl(chatbot.getAiAvatarUrl())) {
    resolvedAiAvatar = chatbot.getAiAvatarUrl();
} 
// If no valid URL but has avatarFileId (custom upload)
else if (chatbot.getAvatarFileId() != null && !chatbot.getAvatarFileId().isBlank()) {
    resolvedAvatarFileId = chatbot.getAvatarFileId();
}
// Ignore blob URLs or invalid URLs - widget will show first letter
```

### URL Validation Method

```java
private boolean isValidHttpUrl(String url) {
    if (url == null || url.isBlank()) {
        return false;
    }
    
    String trimmedUrl = url.trim().toLowerCase();
    
    // Ignore blob URLs
    if (trimmedUrl.startsWith("blob:")) {
        return false;
    }
    
    // Accept only http or https URLs
    return trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://");
}
```

## Widget Integration

### Widget Avatar Loading Logic
```javascript
// Widget code (simplified)
const chatbotData = result.data || result;
const themeSource = chatbotData.theme || chatbotData;

let avatarSrc;

if (themeSource.aiAvatar) {
  // Use preset avatar URL directly
  avatarSrc = themeSource.aiAvatar;
} else if (themeSource.avatarFileId) {
  // Construct URL for custom upload
  avatarSrc = `${apiUrl}/api/attachments/download/${themeSource.avatarFileId}?chatbotId=${chatbotData.id}`;
} else {
  // Show first letter of name
  avatarSrc = null; // Triggers fallback UI
}
```

## Database Migration

### Existing Chatbots with Blob URLs

**Current state:** Some chatbots may have `aiAvatarUrl: "blob:http://..."` in MongoDB

**Behavior:**
- Backend now **ignores** blob URLs in `isValidHttpUrl()`
- Returns `aiAvatar: null` and `avatarFileId: null`
- Widget shows first letter of name as fallback
- No database migration needed - works immediately

**To fix existing chatbots:**
Users can update their chatbot with either:
1. A preset avatar URL: `PUT /v1/api/chatbot/{id}` with `aiAvatar: "https://..."`
2. An uploaded avatar: Upload file, then `PUT /v1/api/chatbot/{id}` with `avatarFileId: "file_xxx"`

## API Examples

### Create with Preset Avatar
```bash
POST /v1/api/chatbot/create
Authorization: Bearer <jwt>

{
  "name": "SupportBot",
  "title": "Support Bot",
  "aiAvatar": "https://i.pravatar.cc/150?img=5",
  ...
}
```

### Create with Custom Avatar
```bash
# Step 1: Upload avatar
POST /v1/api/user/attachments/upload
Authorization: Bearer <jwt>
Content-Type: multipart/form-data

file: avatar.png

# Response: { "fileId": "file_abc123", "downloadUrl": "..." }

# Step 2: Create chatbot
POST /v1/api/chatbot/create
Authorization: Bearer <jwt>

{
  "name": "SupportBot",
  "title": "Support Bot",
  "avatarFileId": "file_abc123",
  ...
}
```

### Update Avatar
```bash
PUT /v1/api/chatbot/{id}
Authorization: Bearer <jwt>

{
  "aiAvatar": "https://i.pravatar.cc/150?img=8"
}
```

### Widget Loads Config
```bash
GET /v1/api/public/chatbot/{id}
# No auth required

Response:
{
  "id": "chatbot_123",
  "name": "SupportBot",
  "title": "Support Bot",
  "greetingMessage": "Hello!",
  "width": 380,
  "height": 600,
  "aiAvatar": "https://i.pravatar.cc/150?img=5",  // or null
  "avatarFileId": null,  // or "file_abc123"
  ...
}
```

## Edge Cases Handled

✅ **Blob URLs** - Ignored, widget shows first letter  
✅ **Invalid URLs** - Ignored, widget shows first letter  
✅ **Missing avatar** - Widget shows first letter  
✅ **Empty strings** - Treated as null  
✅ **Case insensitive** - URL validation handles mixed case  
✅ **Whitespace** - Trimmed before validation  

## Testing Scenarios

### Test 1: Preset Avatar
```
aiAvatarUrl: "https://i.pravatar.cc/150?img=1"
avatarFileId: null

Expected: aiAvatar returned, avatarFileId null
```

### Test 2: Custom Upload
```
aiAvatarUrl: null
avatarFileId: "file_xyz"

Expected: aiAvatar null, avatarFileId returned
```

### Test 3: Blob URL (Legacy)
```
aiAvatarUrl: "blob:http://localhost:3000/abc-123"
avatarFileId: null

Expected: aiAvatar null, avatarFileId null (shows first letter)
```

### Test 4: Both Set (Custom Upload Wins)
```
aiAvatarUrl: "blob:http://..."  (invalid)
avatarFileId: "file_xyz"

Expected: aiAvatar null, avatarFileId returned
```

### Test 5: Default (No Avatar)
```
aiAvatarUrl: null
avatarFileId: null

Expected: aiAvatar null, avatarFileId null (shows first letter)
```

## Files Modified

1. **`PublicChatbotResponseDto.java`**
   - Added `avatarFileId` field

2. **`AIChatBotPublicEndpointController.java`**
   - Implemented avatar resolution logic in `getChatBot()`
   - Added `isValidHttpUrl()` validation method

## Benefits

✅ **Clean separation** - Preset vs custom avatars clearly distinguished  
✅ **Blob-safe** - Ignores invalid blob URLs from legacy data  
✅ **Widget-friendly** - Returns exactly what widget needs  
✅ **Backward compatible** - Existing preset avatars work unchanged  
✅ **Secure** - Custom uploads use proper file service  
✅ **Fallback ready** - Widget always has a display option  

## Completion Status

✅ Backend logic implemented  
✅ URL validation added  
✅ Public endpoint updated  
✅ Compilation successful  
✅ Documentation complete  

The backend now correctly resolves avatars and provides clean data to the widget for proper avatar display!
