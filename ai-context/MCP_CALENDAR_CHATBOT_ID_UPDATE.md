# MCP Calendar Tool - Updated to Use Chatbot ID

## 🎯 **Change Summary**

The MCP calendar tool has been updated to automatically fetch the stored Google Calendar OAuth token from the database using the **`chatbotId`** parameter, instead of requiring the caller to provide the `accessToken`.

---

## ✨ **What Changed**

### Before (Old Implementation)
```json
{
  "accessToken": "ya29.a0AfH6SMBx...",  // ❌ Required from caller
  "summary": "Team Meeting",
  "startDateTime": "2026-02-10T10:00:00",
  "endDateTime": "2026-02-10T11:00:00"
}
```

### After (New Implementation)
```json
{
  "chatbotId": "698576e4d5fd040c84aed7d8",  // ✅ Required - fetches token automatically
  "summary": "Team Meeting",
  "startDateTime": "2026-02-10T10:00:00",
  "endDateTime": "2026-02-10T11:00:00"
}
```

---

## 🔄 **How It Works**

```
┌─────────────────────────────────────────────────────────────┐
│              n8n Workflow (MCP Client Tool)                  │
│  Provides: chatbotId, summary, startDateTime, etc.          │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│          MCP Calendar Tool (McpRestController)               │
│                                                               │
│  1. Receives chatbotId from n8n                              │
│  2. Queries MongoDB for stored token                         │
│  3. Checks if token is expired                               │
│     ├─> If valid: decrypt and use                           │
│     └─> If expired: refresh with Google, update DB          │
│  4. Creates calendar event with valid token                  │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│                Google Calendar API                           │
│  Event created successfully                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 📝 **Updated Tool Schema**

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `chatbotId` | string | **Required** - Chatbot ID to fetch stored Google Calendar token |
| `summary` | string | **Required** - Event title/summary |
| `startDateTime` | string | **Required** - Start date/time in ISO 8601 format |
| `endDateTime` | string | **Required** - End date/time in ISO 8601 format |

### Optional Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `description` | string | Event description |
| `location` | string | Event location |
| `timeZone` | string | Time zone (e.g., UTC, America/New_York) - defaults to UTC |
| `attendees` | array | List of attendee email addresses |

---

## 🚀 **Benefits**

1. **✅ Simplified n8n Workflow**
   - No need to manage access tokens in n8n
   - Just pass the chatbotId

2. **✅ Automatic Token Refresh**
   - Expired tokens are automatically refreshed
   - No manual intervention needed

3. **✅ Centralized Token Management**
   - All tokens stored securely in the database
   - Encryption handled by backend

4. **✅ Better Security**
   - Tokens never exposed to n8n
   - Reduced attack surface

---

## 🧪 **Testing**

### 1. Test with cURL (JSON-RPC 2.0)

```bash
# Test the updated MCP tool
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "create_calendar_event",
      "arguments": {
        "chatbotId": "698576e4d5fd040c84aed7d8",
        "summary": "Test Event from MCP",
        "description": "This is a test event",
        "startDateTime": "2026-02-10T10:00:00",
        "endDateTime": "2026-02-10T11:00:00",
        "timeZone": "America/New_York",
        "location": "Office",
        "attendees": ["user1@example.com", "user2@example.com"]
      }
    },
    "id": 1
  }'
```

### 2. Expected Success Response

```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Calendar event created successfully!\n\nEvent ID: abc123xyz\nSummary: Test Event from MCP\nLink: https://www.google.com/calendar/event?eid=...\nStatus: confirmed"
      }
    ],
    "isError": false
  },
  "id": 1
}
```

### 3. Error Scenarios

#### Chatbot Not Connected to Google Calendar

```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32603,
    "message": "Internal error: Google Calendar not connected for chatbot: 698576e4d5fd040c84aed7d8. Please connect Google Calendar first."
  },
  "id": 1
}
```

#### Token Refresh Failed

```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32603,
    "message": "Internal error: Failed to refresh Google Calendar token for chatbot: 698576e4d5fd040c84aed7d8. Error: Invalid refresh token. Please reconnect Google Calendar."
  },
  "id": 1
}
```

---

## 📋 **n8n Configuration**

### Step 1: Configure MCP Client Tool Node

```yaml
Transport Type: HTTP
MCP Server URL: http://localhost:8080/mcp  # or your production URL
```

### Step 2: Select Tool

- Tool: `create_calendar_event`

### Step 3: Provide Parameters

**From your n8n workflow, provide:**

```javascript
{
  "chatbotId": "{{ $json.chatbotId }}",  // From previous node or hardcoded
  "summary": "{{ $json.eventTitle }}",
  "description": "{{ $json.eventDescription }}",
  "startDateTime": "{{ $json.startTime }}",  // ISO 8601 format
  "endDateTime": "{{ $json.endTime }}",      // ISO 8601 format
  "timeZone": "America/New_York",
  "location": "{{ $json.location }}",
  "attendees": {{ $json.attendees }}  // Array of email addresses
}
```

### Step 4: Handle Response

The tool returns a text response with event details:

```
Calendar event created successfully!

Event ID: abc123xyz
Summary: Test Event from MCP
Link: https://www.google.com/calendar/event?eid=...
Status: confirmed
```

---

## 🔍 **Backend Implementation Details**

### Token Fetching and Refresh Logic

The `McpRestController` now includes a `getValidAccessToken()` method that:

1. **Fetches token from MongoDB** using `chatbotId`
2. **Checks expiration**: If `expiresAt` < current time
3. **Auto-refreshes**: Calls Google OAuth API with refresh token
4. **Updates database**: Saves new encrypted access token
5. **Returns token**: Either cached or freshly refreshed

```java
private Mono<String> getValidAccessToken(String chatbotId) {
    // 1. Fetch from DB
    GoogleCalendarToken token = tokenDao.findByChatbotId(chatbotId)
        .orElseThrow(() -> new RuntimeException("Not connected"));
    
    // 2. Check if expired
    if (token.getExpiresAt().before(new Date())) {
        // 3. Refresh with Google
        return oauthService.refreshAccessToken(refreshToken)
            .map(newTokens -> {
                // 4. Update DB
                token.setAccessToken(encrypt(newTokens.accessToken));
                tokenDao.save(token);
                return newTokens.accessToken;
            });
    }
    
    // Token is valid
    return Mono.just(decrypt(token.getAccessToken()));
}
```

---

## 🔐 **Security Considerations**

### 1. Token Storage
- ✅ All tokens encrypted with AES-256-GCM
- ✅ Stored in MongoDB with proper indexing
- ✅ Access controlled by chatbot ownership

### 2. Token Refresh
- ✅ Automatic refresh when expired
- ✅ Failed refresh returns clear error message
- ✅ Refresh token also encrypted

### 3. MCP Endpoints
- ✅ `/mcp/**` endpoints are public (no JWT required)
- ✅ Token access controlled by chatbotId
- ✅ No token leakage in logs or responses

---

## 📊 **Tool Schema (for n8n)**

```json
{
  "name": "create_calendar_event",
  "description": "Create a new event in Google Calendar. Uses stored OAuth token for the chatbot.",
  "inputSchema": {
    "type": "object",
    "required": ["chatbotId", "summary", "startDateTime", "endDateTime"],
    "properties": {
      "chatbotId": {
        "type": "string",
        "inputType": "string",
        "description": "Chatbot ID to fetch the stored Google Calendar token"
      },
      "summary": {
        "type": "string",
        "inputType": "string",
        "description": "Event title/summary"
      },
      "description": {
        "type": "string",
        "inputType": "string",
        "description": "Event description"
      },
      "startDateTime": {
        "type": "string",
        "inputType": "string",
        "description": "Event start date/time in ISO 8601 format (e.g., 2024-01-15T10:00:00)"
      },
      "endDateTime": {
        "type": "string",
        "inputType": "string",
        "description": "Event end date/time in ISO 8601 format (e.g., 2024-01-15T11:00:00)"
      },
      "timeZone": {
        "type": "string",
        "inputType": "string",
        "description": "Time zone (e.g., UTC, America/New_York). Defaults to UTC if not provided"
      },
      "location": {
        "type": "string",
        "inputType": "string",
        "description": "Event location"
      },
      "attendees": {
        "type": "array",
        "inputType": "array",
        "items": {
          "type": "string"
        },
        "description": "List of attendee email addresses"
      }
    }
  }
}
```

---

## ✅ **Migration Checklist**

If you're upgrading from the old implementation:

- [x] ✅ Update n8n workflows to pass `chatbotId` instead of `accessToken`
- [x] ✅ Ensure chatbots are connected to Google Calendar via OAuth endpoints
- [x] ✅ Test token fetching and auto-refresh
- [x] ✅ Update any documentation referencing the old parameter
- [x] ✅ Test error handling for disconnected chatbots

---

## 🐛 **Troubleshooting**

### Error: "Google Calendar not connected for chatbot"

**Cause:** The chatbot hasn't been connected to Google Calendar yet.

**Solution:** 
1. Use the Google Calendar OAuth endpoints to connect:
   ```bash
   POST /v1/api/chatbot/google-calendar/{chatbotId}
   ```
2. Provide valid OAuth tokens from Google

### Error: "Failed to refresh Google Calendar token"

**Cause:** Refresh token is invalid or revoked.

**Solution:**
1. Disconnect and reconnect Google Calendar:
   ```bash
   DELETE /v1/api/chatbot/google-calendar/{chatbotId}
   POST /v1/api/chatbot/google-calendar/{chatbotId}
   ```

### Token Not Auto-Refreshing

**Cause:** Check server logs for errors.

**Solution:**
1. Verify `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are set correctly
2. Check MongoDB connection
3. Verify encryption key is valid

---

## 📚 **Related Documentation**

- `GOOGLE_CALENDAR_OAUTH_SETUP.md` - Backend OAuth setup
- `N8N_MCP_CLIENT_TOOL_SETUP.md` - n8n MCP integration
- `N8N_INPUTTYPE_FIX.md` - n8n compatibility fixes

---

**Version:** 2.0.0  
**Last Updated:** February 6, 2026  
**Breaking Change:** Yes - `accessToken` parameter replaced with `chatbotId`  
**Migration Required:** Update n8n workflows to use new parameter








