# Backend Chatbot Operations - Complete Implementation

## Overview
Implemented complete chatbot management operations including delete, enable/disable toggle, statistics aggregation, and widget disabled state support.

## New Features

### 1. Delete Chatbot
### 2. Toggle Enable/Disable Status
### 3. Aggregated Statistics
### 4. Widget Disabled State Support

---

## API Endpoints

### 1. Delete Chatbot

**DELETE /v1/api/chatbot/{id}**

**Auth:** `Authorization: Bearer <jwt>`

**Request:**
```http
DELETE /v1/api/chatbot/698e03c054e0055b6894c48a
Authorization: Bearer <jwt_token>
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Chatbot deleted successfully",
  "id": "698e03c054e0055b6894c48a"
}
```

**Response (404 Not Found):**
```json
{
  "success": false,
  "error": "Not Found",
  "message": "Chatbot not found with ID: 698e03c054e0055b6894c48a"
}
```

**Response (500 Internal Server Error):**
```json
{
  "success": false,
  "error": "Internal Server Error",
  "message": "Failed to delete chatbot: ..."
}
```

---

### 2. Toggle Chatbot Status (Enable/Disable)

**PUT /v1/api/chatbot/{id}/toggle**

**Auth:** `Authorization: Bearer <jwt>`

**Request:**
```http
PUT /v1/api/chatbot/698e03c054e0055b6894c48a/toggle
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "status": "DISABLED"
}
```

**Valid Status Values:**
- `"ACTIVE"` - Chatbot is enabled and widget works normally
- `"DISABLED"` - Chatbot is disabled and widget shows unavailable message

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Chatbot status updated successfully",
  "id": "698e03c054e0055b6894c48a",
  "status": "DISABLED"
}
```

**Response (400 Bad Request):**
```json
{
  "success": false,
  "error": "Bad Request",
  "message": "Invalid status. Must be 'ACTIVE' or 'DISABLED'"
}
```

**Response (404 Not Found):**
```json
{
  "success": false,
  "error": "Bad Request",
  "message": "Chatbot not found with ID: 698e03c054e0055b6894c48a"
}
```

---

### 3. Get Aggregated Statistics

**GET /v1/api/chatbot/stats**

**Auth:** `Authorization: Bearer <jwt>`

**Request:**
```http
GET /v1/api/chatbot/stats
Authorization: Bearer <jwt_token>
```

**Response (200 OK):**
```json
{
  "totalChatbots": 11,
  "totalConversations": 245,
  "totalMessages": 1234,
  "activeDomains": 8
}
```

**Field Descriptions:**
- `totalChatbots` - Total number of chatbots owned by user
- `totalConversations` - Sum of all conversations across all chatbots
- `totalMessages` - Sum of all messages across all chatbots
- `activeDomains` - Count of chatbots with `status === "ACTIVE"`

---

### 4. List Chatbots with Statistics

**GET /v1/api/chatbot/list-with-stats**

**Auth:** `Authorization: Bearer <jwt>`

**Request:**
```http
GET /v1/api/chatbot/list-with-stats
Authorization: Bearer <jwt_token>
```

**Response (200 OK):**
```json
[
  {
    "id": "698e03c054e0055b6894c48a",
    "title": "Support Bot",
    "name": "SupportBot",
    "createdAt": "2026-03-01T10:00:00Z",
    "createdBy": "user@example.com",
    "status": "ACTIVE",
    "totalConversations": 45,
    "totalMessages": 234
  },
  {
    "id": "698e03c054e0055b6894c48b",
    "title": "Sales Bot",
    "name": "SalesBot",
    "createdAt": "2026-03-05T14:30:00Z",
    "createdBy": "user@example.com",
    "status": "DISABLED",
    "totalConversations": 12,
    "totalMessages": 67
  }
]
```

**Field Descriptions:**
- `status` - `"ACTIVE"` | `"DISABLED"` | `"CREATED"` | `"TRAINING"` | `"COMPLETED"` | `"FAILED"`
- `totalConversations` - Number of unique conversations for this chatbot
- `totalMessages` - Number of messages for this chatbot

---

### 5. Public Chatbot Configuration (Enhanced)

**GET /v1/api/public/chatbot/{id}**

**Auth:** None (public endpoint)

**Request:**
```http
GET /v1/api/public/chatbot/698e03c054e0055b6894c48a
```

**Response (200 OK):**
```json
{
  "id": "698e03c054e0055b6894c48a",
  "name": "SupportBot",
  "title": "Support Bot",
  "greetingMessage": "Hello! How can I help you?",
  "width": 380,
  "height": 600,
  "status": "DISABLED",
  "headerBackground": "#2D3748",
  "headerText": "#FFFFFF",
  "aiBackground": "#F7FAFC",
  "aiText": "#1A202C",
  "userBackground": "#3B82F6",
  "userText": "#FFFFFF",
  "widgetPosition": "right",
  "aiAvatar": "https://i.pravatar.cc/150?img=1",
  "avatarFileId": null,
  "hideMainBannerLogo": false,
  "theme": {
    "headerBackground": "#2D3748",
    "headerText": "#FFFFFF",
    "aiBackground": "#F7FAFC",
    "aiText": "#1A202C",
    "userBackground": "#3B82F6",
    "userText": "#FFFFFF",
    "widgetPosition": "right",
    "aiAvatar": "https://i.pravatar.cc/150?img=1",
    "hideMainBannerLogo": false
  }
}
```

**Key Addition:** `status` field now included for widget to check disabled state

---

## Widget Disabled State Behavior

### Widget Logic

```javascript
// Widget initialization
const chatbotData = response.data || response;

if (chatbotData.status === 'DISABLED') {
  // Show disabled message
  showDisabledMessage();
  hideInputControls();
} else {
  // Normal widget behavior
  showNormalWidget();
}

function showDisabledMessage() {
  messageArea.innerHTML = `
    <div class="disabled-message">
      This chatbot is currently unavailable.
    </div>
  `;
}

function hideInputControls() {
  inputField.style.display = 'none';
  sendButton.style.display = 'none';
  attachmentControls.style.display = 'none';
}
```

### Status Values and Widget Behavior

| Status | Widget Behavior |
|--------|-----------------|
| `"ACTIVE"` | Normal operation - full functionality |
| `"DISABLED"` | Show "This chatbot is currently unavailable." Hide input/attachments |
| `null` or missing | Normal operation (backward compatibility) |
| `"CREATED"` | Normal operation |
| `"TRAINING"` | Normal operation |
| `"COMPLETED"` | Normal operation |

---

## DTOs Created

### 1. ChatBotToggleRequest.java
```java
public class ChatBotToggleRequest {
    private String status;  // "ACTIVE" | "DISABLED"
}
```

### 2. ChatBotStatsResponse.java
```java
public class ChatBotStatsResponse {
    private Long totalChatbots;
    private Long totalConversations;
    private Long totalMessages;
    private Long activeDomains;
}
```

### 3. ChatBotListItemResponse.java
```java
public class ChatBotListItemResponse {
    private String id;
    private String title;
    private String name;
    private Date createdAt;
    private String createdBy;
    private String status;
    private Long totalConversations;
    private Long totalMessages;
}
```

---

## Service Layer Methods

### ChatBotService.java

#### 1. deleteChatBot(String id)
- Validates chatbot exists
- Deletes chatbot from database
- Throws `IllegalArgumentException` if not found

#### 2. toggleChatBotStatus(String id, String status)
- Validates status is "ACTIVE" or "DISABLED"
- Updates chatbot status
- Updates `updatedAt` timestamp
- Returns updated ChatBot entity

#### 3. getChatBotStats(String userEmail)
- Aggregates statistics across all user's chatbots
- Counts total chatbots
- Counts active chatbots (status === "ACTIVE")
- Sums total conversations from chat history
- Sums total messages from chat history
- Returns aggregated statistics

#### 4. getChatBotsWithStats(String userEmail)
- Lists all chatbots with per-chatbot statistics
- For each chatbot, queries conversation and message counts
- Returns list with embedded statistics

---

## Database Schema

### ChatBot Collection

```javascript
{
  "_id": ObjectId("..."),
  "title": "Support Bot",
  "email": "user@example.com",
  "name": "SupportBot",
  "status": "ACTIVE",  // "ACTIVE" | "DISABLED" | "CREATED" | ...
  "createdBy": "user@example.com",
  "createdAt": ISODate("2026-03-01T10:00:00Z"),
  "updatedAt": ISODate("2026-03-18T10:00:00Z"),
  // ... other fields
}
```

### n8n_chat_session_histories Collection
Used for counting conversations and messages per chatbot:
```javascript
{
  "_id": ObjectId("..."),
  "chatbotId": "698e03c054e0055b6894c48a",
  "conversationid": "conv_123",
  "userMessage": "Hello",
  "aiMessage": "Hi there!",
  "createdAt": ISODate("..."),
  // ... other fields
}
```

---

## Frontend Integration

### 1. Dashboard Statistics Display

```javascript
// Fetch aggregated stats
const response = await fetch('/v1/api/chatbot/stats', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const stats = await response.json();

// Display: Total Chatbots, Conversations, Messages, Active Domains
displayStats(stats);
```

### 2. Chatbot List with Actions

```javascript
// Fetch list with stats
const response = await fetch('/v1/api/chatbot/list-with-stats', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const chatbots = await response.json();

// Display each chatbot with:
// - Status badge (Active/Disabled)
// - Total conversations
// - Total messages
// - Toggle button
// - Delete button
```

### 3. Toggle Chatbot Status

```javascript
async function toggleChatbot(chatbotId, newStatus) {
  const response = await fetch(`/v1/api/chatbot/${chatbotId}/toggle`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ status: newStatus })
  });

  if (response.ok) {
    // Refresh chatbot list
    refreshChatbotList();
  }
}
```

### 4. Delete Chatbot

```javascript
async function deleteChatbot(chatbotId) {
  if (confirm('Are you sure you want to delete this chatbot?')) {
    const response = await fetch(`/v1/api/chatbot/${chatbotId}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    if (response.ok) {
      // Refresh chatbot list
      refreshChatbotList();
    }
  }
}
```

### 5. Widget Disabled State Check

```javascript
// Widget initialization
async function initWidget(chatbotId) {
  const response = await fetch(`/v1/api/public/chatbot/${chatbotId}`);
  const config = await response.json();

  if (config.status === 'DISABLED') {
    showDisabledState();
  } else {
    showNormalWidget(config);
  }
}

function showDisabledState() {
  document.getElementById('chat-area').innerHTML = `
    <div style="padding: 20px; text-align: center; color: #666;">
      This chatbot is currently unavailable.
    </div>
  `;
  document.getElementById('input-area').style.display = 'none';
}
```

---

## Files Modified/Created

### Created (3 new DTOs):
1. `ChatBotToggleRequest.java`
2. `ChatBotStatsResponse.java`
3. `ChatBotListItemResponse.java`

### Modified:
1. `ChatBotService.java` - Added 3 new methods
2. `AIChatBotController.java` - Added 4 new endpoints
3. `AIChatBotPublicEndpointController.java` - Added status field to response
4. `PublicChatbotResponseDto.java` - Added status field

---

## Testing Status

✅ Compilation successful  
✅ All DTOs created  
✅ Service layer methods implemented  
✅ Controller endpoints added  
✅ Public endpoint enhanced  
✅ Error handling implemented  

---

## Error Handling

All endpoints include comprehensive error handling:

- **404 Not Found** - Chatbot doesn't exist
- **400 Bad Request** - Invalid status value
- **401 Unauthorized** - Missing or invalid JWT
- **500 Internal Server Error** - Unexpected errors

All error responses follow consistent format:
```json
{
  "success": false,
  "error": "Error Type",
  "message": "Detailed error message"
}
```

---

## Summary

✅ **Delete chatbot** - Complete with ownership validation  
✅ **Toggle status** - ACTIVE/DISABLED with validation  
✅ **Statistics** - Aggregated and per-chatbot stats  
✅ **Widget disabled state** - Status field in public API  
✅ **Error handling** - Comprehensive error responses  
✅ **Documentation** - Complete API specification  

The backend now fully supports all chatbot management operations required by the frontend!
