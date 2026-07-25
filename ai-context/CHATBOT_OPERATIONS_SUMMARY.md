# Chatbot Operations - Quick Reference

## New API Endpoints

### 1. Delete Chatbot
```
DELETE /v1/api/chatbot/{id}
Auth: Bearer <jwt>

Response:
{
  "success": true,
  "message": "Chatbot deleted successfully",
  "id": "..."
}
```

### 2. Toggle Status (Enable/Disable)
```
PUT /v1/api/chatbot/{id}/toggle
Auth: Bearer <jwt>

Request:
{
  "status": "ACTIVE" | "DISABLED"
}

Response:
{
  "success": true,
  "message": "Chatbot status updated successfully",
  "id": "...",
  "status": "DISABLED"
}
```

### 3. Get Statistics
```
GET /v1/api/chatbot/stats
Auth: Bearer <jwt>

Response:
{
  "totalChatbots": 11,
  "totalConversations": 245,
  "totalMessages": 1234,
  "activeDomains": 8
}
```

### 4. List with Stats
```
GET /v1/api/chatbot/list-with-stats
Auth: Bearer <jwt>

Response:
[
  {
    "id": "...",
    "name": "SupportBot",
    "status": "ACTIVE",
    "totalConversations": 45,
    "totalMessages": 234,
    ...
  }
]
```

### 5. Public API (Enhanced)
```
GET /v1/api/public/chatbot/{id}
Auth: None

Response:
{
  "id": "...",
  "name": "SupportBot",
  "status": "DISABLED",  // NEW: Widget checks this
  "greetingMessage": "...",
  ...
}
```

## Widget Disabled State

When `status === "DISABLED"`:
- Widget shows: "This chatbot is currently unavailable."
- Input field hidden
- Attachment controls hidden
- Send button hidden

When `status === "ACTIVE"` or `null`:
- Widget works normally

## Status Values

- `"ACTIVE"` - Chatbot enabled
- `"DISABLED"` - Chatbot disabled
- `"CREATED"` - Initial state
- `"TRAINING"` - Training in progress
- `"COMPLETED"` - Training complete

## Statistics Calculation

**Total Chatbots:** Count of user's chatbots  
**Total Conversations:** Sum from `n8n_chat_session_histories`  
**Total Messages:** Sum of messages across all chatbots  
**Active Domains:** Count where `status === "ACTIVE"`

## Files Created

1. `ChatBotToggleRequest.java` - Toggle request DTO
2. `ChatBotStatsResponse.java` - Statistics response DTO
3. `ChatBotListItemResponse.java` - List item with stats DTO
4. `BACKEND_CHATBOT_OPERATIONS.md` - Full documentation

## Files Modified

1. `ChatBotService.java` - Added toggle, stats, delete methods
2. `AIChatBotController.java` - Added 4 new endpoints
3. `AIChatBotPublicEndpointController.java` - Added status field
4. `PublicChatbotResponseDto.java` - Added status field

## Testing

✅ Compilation successful  
✅ All endpoints implemented  
✅ Error handling complete  
✅ Widget support ready  

---

**Status:** ✅ Complete and ready for frontend integration
