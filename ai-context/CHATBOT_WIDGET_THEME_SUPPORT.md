# Chatbot Widget Theme & Styling Support

## Summary
Added comprehensive widget theme and styling support to chatbot creation, update, and public embedding endpoints to match frontend requirements.

## Changes Made

### 1. New DTOs

#### `ChatbotWidgetThemeDto.java`
Reusable nested theme object for widget configuration:
```java
public class ChatbotWidgetThemeDto {
    private String headerBackground;  // Hex color, e.g. #2D3748
    private String headerText;
    private String aiBackground;
    private String aiText;
    private String userBackground;
    private String userText;
    private String widgetPosition;    // "left" | "right"
    private String aiAvatar;          // Public URL for avatar image
    private Boolean hideMainBannerLogo;
}
```

#### `PublicChatbotResponseDto.java`
Public-facing response for widget embedding (no sensitive data):
```java
public class PublicChatbotResponseDto {
    private String id;
    private String name;
    private String title;
    private String greetingMessage;
    private Integer width;
    private Integer height;
    
    // Flat theme fields (backward compatibility)
    private String headerBackground;
    private String headerText;
    // ... all theme fields ...
    
    // Nested theme object (for new widgets)
    private ChatbotWidgetThemeDto theme;
}
```

### 2. Entity Updates

#### `ChatBot.java`
Added theme and model fields:
```java
// AI Model
private String model;

// Widget Theme
private String headerBackground;
private String headerText;
private String aiBackground;
private String aiText;
private String userBackground;
private String userText;
private String widgetPosition;
private String aiAvatarUrl;        // Resolved URL for widget
private String avatarFileId;       // Internal file reference
private Boolean hideMainBannerLogo;
```

### 3. Request DTO Updates

#### `ChatBotCreationRequest.java`
Added theme fields for create/update operations:
```java
// AI Model
private String model;

// Widget Theme (flat fields for easy frontend mapping)
private String widgetPosition;
private String headerBackground;
private String headerText;
private String aiBackground;
private String aiText;
private String userBackground;
private String userText;
private String aiAvatar;
private String avatarFileId;
private Boolean hideMainBannerLogo;
```

### 4. Service Layer Updates

#### `ChatBotService.java`

**`createChatBot()` method:**
- Now saves all theme fields and model to the database
- Maps flat theme fields from request to entity

**`updateChatBot()` method:**
- Updates theme fields and model along with other chatbot properties
- Maintains backward compatibility with existing data

### 5. Controller Updates

#### `AIChatBotPublicEndpointController.java`

**Updated `GET /v1/api/public/chatbot/{id}` endpoint:**
- Returns `PublicChatbotResponseDto` instead of raw `ChatBot` entity
- Provides both flat theme fields AND nested `theme` object for widget compatibility
- Parses width/height from string to integer (handles "400px" or "400")
- No sensitive data exposed (only public widget configuration)

## API Endpoints

### 1. Create Chatbot
**POST /v1/api/chatbot/create**

**Auth:** `Authorization: Bearer <jwt>`

**Request:**
```json
{
  "title": "Customer Support Bot",
  "name": "SupportBot",
  "hideName": false,
  "instructions": "Help customers with...",
  "restrictToDataSource": true,
  "fallbackMessage": "I couldn't find that...",
  "greetingMessage": "Hey, what can I do for you today?",
  "selectedDataSource": "url",
  "model": "gpt-4o",
  "width": "380",
  "height": "600",
  
  "widgetPosition": "right",
  "headerBackground": "#2D3748",
  "headerText": "#FFFFFF",
  "aiBackground": "#F7FAFC",
  "aiText": "#1A202C",
  "userBackground": "#3B82F6",
  "userText": "#FFFFFF",
  "aiAvatar": "https://your-api/v1/api/user/attachments/download/abc123",
  "hideMainBannerLogo": false,
  
  "qaPairs": [
    {
      "question": "What are your hours?",
      "answer": "We're open 9-5 EST"
    }
  ],
  "fileIds": ["file123"],
  "addedWebsites": ["https://example.com"],
  "addedTexts": ["Our product is..."]
}
```

**Response:**
```json
{
  "id": "698e03c054e0055b6894c48a",
  "title": "Customer Support Bot",
  "name": "SupportBot",
  "createdAt": "2026-03-18T10:00:00Z",
  "createdBy": "user@example.com",
  "status": "SUCCESS",
  "message": "Chatbot created successfully"
}
```

### 2. Update Chatbot
**PUT /v1/api/chatbot/{id}**

**Auth:** `Authorization: Bearer <jwt>`

**Request:** Same structure as create (partial updates supported)

**Response:** Full `ChatBot` entity

### 3. Public Widget Configuration
**GET /v1/api/public/chatbot/{id}**

**Auth:** None (public endpoint)

**Response (Flat - backward compatible):**
```json
{
  "id": "698e03c054e0055b6894c48a",
  "name": "JadeAIBot",
  "title": "JadeAIBot",
  "greetingMessage": "Hey, what can I do for you today?",
  "width": 380,
  "height": 600,
  "headerBackground": "#2D3748",
  "headerText": "#FFFFFF",
  "aiBackground": "#F7FAFC",
  "aiText": "#1A202C",
  "userBackground": "#3B82F6",
  "userText": "#FFFFFF",
  "widgetPosition": "right",
  "aiAvatar": "https://your-api/v1/api/user/attachments/download/abc123",
  "hideMainBannerLogo": false,
  "theme": {
    "headerBackground": "#2D3748",
    "headerText": "#FFFFFF",
    "aiBackground": "#F7FAFC",
    "aiText": "#1A202C",
    "userBackground": "#3B82F6",
    "userText": "#FFFFFF",
    "widgetPosition": "right",
    "aiAvatar": "https://your-api/v1/api/user/attachments/download/abc123",
    "hideMainBannerLogo": false
  }
}
```

## Widget Compatibility

The public endpoint returns theme in TWO formats:

1. **Flat fields at root level** - for existing widgets that expect `result.headerBackground`
2. **Nested `theme` object** - for new widgets that use `result.theme.headerBackground`

Widget code can use:
```javascript
const chatbotData = result.data || result;
const themeSource = chatbotData.theme || chatbotData;
// Use themeSource.headerBackground, etc.
```

## Key Features

✅ **Model Selection** - Support for specifying AI model (e.g., "gpt-4o")  
✅ **Complete Theme Control** - Header, AI bubble, user bubble colors  
✅ **Avatar Support** - AI avatar image URL  
✅ **Widget Position** - Left or right side placement  
✅ **Logo Control** - Show/hide main banner logo  
✅ **Backward Compatible** - Existing widgets continue to work  
✅ **Clean Code** - Minimal changes, reusable DTOs  
✅ **Public Endpoint** - No sensitive data exposed

## Migration Notes

- **Existing chatbots**: Theme fields will be `null` initially; update via PUT endpoint
- **Width/Height parsing**: Handles both "400px" and "400" formats
- **Avatar URL**: Frontend can upload to `/v1/api/user/attachments/upload` and use returned URL
- **Optional fields**: All theme fields are optional for flexibility

## Testing

✅ Compilation successful  
✅ All DTOs created  
✅ Service layer updated  
✅ Controllers updated  
✅ Backward compatibility maintained

## Frontend Integration

1. **Create chatbot with styling:**
   ```javascript
   POST /v1/api/chatbot/create
   {
     title, name, model,
     headerBackground, headerText,
     aiBackground, aiText,
     userBackground, userText,
     widgetPosition, aiAvatar,
     hideMainBannerLogo,
     // ... other fields
   }
   ```

2. **Widget loads configuration:**
   ```javascript
   GET /v1/api/public/chatbot/{id}
   // Returns both flat and nested theme for compatibility
   ```

3. **Update styling:**
   ```javascript
   PUT /v1/api/chatbot/{id}
   // Same fields as create
   ```

## File Avatar Upload Flow

1. Frontend uploads avatar: `POST /v1/api/user/attachments/upload`
2. Backend returns: `{ fileId: "abc123", downloadUrl: "https://..." }`
3. Frontend sends `aiAvatar: downloadUrl` and optionally `avatarFileId: "abc123"` in create/update request
4. Backend stores both `aiAvatarUrl` and `avatarFileId`
5. Public endpoint returns `aiAvatar` URL for widget to display
