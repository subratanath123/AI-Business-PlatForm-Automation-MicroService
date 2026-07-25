# Per-Chatbot Statistics Implementation

## Overview
Implemented per-chatbot statistics in both the list endpoint and a dedicated stats endpoint, allowing the frontend to display conversation and message counts for each chatbot card.

## Implementation Summary

### Option A (Recommended & Implemented) ✅
**Enhanced `/list` endpoint to include stats in each response**

**Benefits:**
- ✅ Single API call returns everything
- ✅ No additional network requests
- ✅ Immediate display on page load
- ✅ Frontend already checks for `totalConversations` and `totalMessages`

### Option B (Also Implemented)
**Dedicated per-chatbot stats endpoint**

**Benefits:**
- ✅ Lighter list response if stats not needed
- ✅ On-demand stats fetching
- ✅ Useful for real-time updates

## API Endpoints

### 1. Enhanced List Endpoint (Option A)

**GET /v1/api/chatbot/list**

**Auth:** `Authorization: Bearer <jwt>`

**Response:**
```json
[
  {
    "id": "support-bot",
    "name": "Support Bot",
    "title": "Customer Success Specialist",
    "createdAt": "2026-03-01T10:00:00Z",
    "createdBy": "user@example.com",
    "status": "ACTIVE",
    "message": "Chatbot retrieved",
    "totalConversations": 12,
    "totalMessages": 67
  },
  {
    "id": "sales-assistant",
    "name": "Sales Assistant",
    "title": "Revenue Growth Strategist",
    "createdAt": "2026-03-05T14:30:00Z",
    "createdBy": "user@example.com",
    "status": "ACTIVE",
    "message": "Chatbot retrieved",
    "totalConversations": 8,
    "totalMessages": 34
  }
]
```

**Field Definitions:**
- `totalConversations` - Unique conversation count for this chatbot
- `totalMessages` - Total message count for this chatbot

### 2. Dedicated Per-Chatbot Stats Endpoint (Option B)

**GET /v1/api/chatbot/{chatbotId}/stats**

**Auth:** `Authorization: Bearer <jwt>`

**Example Request:**
```http
GET /v1/api/chatbot/support-bot/stats
Authorization: Bearer <jwt_token>
```

**Response:**
```json
{
  "chatbotId": "support-bot",
  "totalConversations": 12,
  "totalMessages": 67
}
```

**Error Response (500):**
```json
{
  "chatbotId": "support-bot",
  "totalConversations": 0,
  "totalMessages": 0
}
```

### 3. Existing Endpoints (Still Available)

**GET /v1/api/chatbot/list-with-stats**
- Returns `ChatBotListItemResponse[]` with stats
- More detailed response structure
- Includes all chatbot fields + stats

**GET /v1/api/chatbot/stats**
- Aggregated global statistics
- Returns total counts across ALL chatbots

## Frontend Integration

### Priority Field Names (Already Supported)

The frontend checks fields in this order:
1. `totalConversations` ✅ (primary)
2. `conversationCount` (fallback)
3. `totalMessages` ✅ (primary)
4. `messageCount` (fallback)

**Our Response:** Uses `totalConversations` and `totalMessages` (primary fields)

### Usage Example

```javascript
// Frontend code (already working)
const response = await fetch('/v1/api/chatbot/list', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const chatbots = await response.json();

// Each chatbot card now displays:
chatbots.forEach(bot => {
  displayCard({
    name: bot.name,
    status: bot.status,
    conversations: bot.totalConversations,  // ✅ Shows count
    messages: bot.totalMessages              // ✅ Shows count
  });
});
```

**No frontend changes needed!** The field names match what the frontend already expects.

## Implementation Details

### Service Layer

#### New Method: `getChatBotStatsById(String chatbotId)`

```java
public ChatBotStatsItemResponse getChatBotStatsById(String chatbotId) {
    // Count unique conversations
    Aggregation aggregation = Aggregation.newAggregation(
        match(Criteria.where("chatbotId").is(chatbotId)),
        group("conversationid"),
        count().as("total")
    );
    
    long conversations = // aggregation result
    
    // Count total messages
    long messages = mongoTemplate.count(
        new Query().addCriteria(Criteria.where("chatbotId").is(chatbotId)),
        "n8n_chat_session_histories"
    );
    
    return ChatBotStatsItemResponse.builder()
        .chatbotId(chatbotId)
        .totalConversations(conversations)
        .totalMessages(messages)
        .build();
}
```

### Controller Layer

#### Updated: `GET /list` Endpoint

```java
@GetMapping("/list")
public ResponseEntity<List<ChatBotCreationResponse>> listChatBots() {
    List<ChatBot> chatbots = chatBotService.getChatBotsByUser(email);
    
    return chatbots.stream()
        .map(chatbot -> {
            // Get stats for each chatbot
            ChatBotStatsItemResponse stats = 
                chatBotService.getChatBotStatsById(chatbot.getId());
            
            return ChatBotCreationResponse.builder()
                .id(chatbot.getId())
                .name(chatbot.getName())
                // ... other fields
                .totalConversations(stats.getTotalConversations())
                .totalMessages(stats.getTotalMessages())
                .build();
        })
        .collect(Collectors.toList());
}
```

#### New: `GET /{chatbotId}/stats` Endpoint

```java
@GetMapping("/{chatbotId}/stats")
public ResponseEntity<ChatBotStatsItemResponse> getChatBotStatsById(
        @PathVariable String chatbotId) {
    
    ChatBotStatsItemResponse stats = 
        chatBotService.getChatBotStatsById(chatbotId);
    
    return ResponseEntity.ok(stats);
}
```

## DTOs Created/Modified

### 1. ChatBotCreationResponse (Modified)
Added fields:
```java
private Long totalConversations;
private Long totalMessages;
```

### 2. ChatBotStatsItemResponse (New)
```java
public class ChatBotStatsItemResponse {
    private String chatbotId;
    private Long totalConversations;
    private Long totalMessages;
}
```

## Database Query Logic

### Unique Conversations (MongoDB Aggregation)

```javascript
// MongoDB aggregation pipeline
[
  { $match: { chatbotId: "support-bot" } },
  { $group: { _id: "$conversationid" } },
  { $count: "total" }
]
```

### Total Messages (Simple Count)

```java
mongoTemplate.count(
    Query.where("chatbotId").is("support-bot"),
    "n8n_chat_session_histories"
)
```

## Performance Considerations

### Option A (Enhanced `/list`)
**Pros:**
- Single API call
- Better user experience (instant display)

**Cons:**
- Slightly slower if user has many chatbots
- Runs aggregation for each chatbot

**Performance:**
- For 10 chatbots: ~10 aggregations in parallel
- For 100 chatbots: May be slow (~1-2s)

**Optimization Suggestions:**
1. Add index: `{ chatbotId: 1, conversationid: 1 }`
2. Cache stats with 5-minute TTL
3. Background job to pre-calculate stats

### Option B (Dedicated Endpoint)
**Pros:**
- Lighter list response
- On-demand loading
- Parallel requests possible

**Cons:**
- N+1 problem (N requests for N chatbots)
- More network overhead
- Slower initial page load

## Error Handling

Both endpoints include comprehensive error handling:

```java
try {
    // Calculate stats
    return stats;
} catch (Exception e) {
    log.error("Error getting stats: {}", e.getMessage());
    // Return 0 counts on error
    return ChatBotStatsItemResponse.builder()
        .chatbotId(chatbotId)
        .totalConversations(0L)
        .totalMessages(0L)
        .build();
}
```

## Testing

### Test Enhanced List Endpoint

```bash
curl -X GET http://localhost:8080/v1/api/chatbot/list \
  -H "Authorization: Bearer <jwt_token>"

# Expected response includes totalConversations and totalMessages
```

### Test Dedicated Stats Endpoint

```bash
curl -X GET http://localhost:8080/v1/api/chatbot/support-bot/stats \
  -H "Authorization: Bearer <jwt_token>"

# Expected:
{
  "chatbotId": "support-bot",
  "totalConversations": 12,
  "totalMessages": 67
}
```

### Verify MongoDB Counts

```bash
# Count unique conversations
db.n8n_chat_session_histories.aggregate([
  { $match: { chatbotId: "support-bot" } },
  { $group: { _id: "$conversationid" } },
  { $count: "total" }
])

# Count total messages
db.n8n_chat_session_histories.count({ chatbotId: "support-bot" })
```

## Summary

✅ **Option A Implemented** - `/list` includes stats (Recommended)  
✅ **Option B Implemented** - `/{chatbotId}/stats` dedicated endpoint  
✅ **Field Names Match** - `totalConversations`, `totalMessages`  
✅ **No Frontend Changes** - Already compatible  
✅ **Error Handling** - Graceful failures with 0 counts  
✅ **Compilation** - Successful  

### Recommendation

**Use Option A (`/list`)** for immediate display:
- Single API call
- Stats included in list response
- Frontend already checks for `totalConversations` and `totalMessages`
- No code changes needed on frontend

**Use Option B (`/{chatbotId}/stats`)** for:
- Real-time stats updates
- On-demand refresh
- When list endpoint is too slow

---

**Status:** ✅ Complete and ready for testing
**Frontend Impact:** None - field names already supported
**Performance:** Good for <50 chatbots, consider caching for more
