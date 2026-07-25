# n8n MCP Client Tool - inputType Fix

## 🐛 Issue

When selecting the MCP tool in n8n, you received the error:
```
Cannot read properties of undefined (reading 'inputType')
```

## 🔍 Root Cause

n8n's MCP Client Tool expects each property in the tool's `inputSchema` to have an **`inputType`** field in addition to the standard JSON Schema `type` field.

### ❌ Before (Missing inputType)

```json
{
  "properties": {
    "accessToken": {
      "type": "string",
      "description": "Google Calendar OAuth2 access token"
    }
  }
}
```

### ✅ After (With inputType)

```json
{
  "properties": {
    "accessToken": {
      "type": "string",
      "description": "Google Calendar OAuth2 access token",
      "inputType": "string"
    }
  }
}
```

## 🔧 Solution

Updated the `handleToolsList()` method in `McpRestController.java` to include `inputType` for each property:

### Property Types Mapping

| JSON Schema Type | inputType Value |
|-----------------|-----------------|
| `string` | `"string"` |
| `array` | `"array"` |
| `object` | `"object"` |
| `number` | `"number"` |
| `boolean` | `"boolean"` |

### Complete Example

```java
Map<String, Object> accessTokenProp = new java.util.HashMap<>();
accessTokenProp.put("type", "string");
accessTokenProp.put("description", "Google Calendar OAuth2 access token");
accessTokenProp.put("inputType", "string");  // ← Added for n8n compatibility
```

## ✅ Verified Response

```json
{
  "jsonrpc": "2.0",
  "result": {
    "tools": [
      {
        "name": "create_calendar_event",
        "description": "Create a new event in Google Calendar...",
        "inputSchema": {
          "type": "object",
          "required": ["accessToken", "summary", "startDateTime", "endDateTime"],
          "properties": {
            "accessToken": {
              "type": "string",
              "description": "Google Calendar OAuth2 access token",
              "inputType": "string"
            },
            "summary": {
              "type": "string",
              "description": "Event title/summary",
              "inputType": "string"
            },
            "startDateTime": {
              "type": "string",
              "description": "Event start date/time in ISO 8601 format",
              "inputType": "string"
            },
            "endDateTime": {
              "type": "string",
              "description": "Event end date/time in ISO 8601 format",
              "inputType": "string"
            },
            "timeZone": {
              "type": "string",
              "description": "Time zone (e.g., UTC, America/New_York)",
              "inputType": "string"
            },
            "location": {
              "type": "string",
              "description": "Event location",
              "inputType": "string"
            },
            "description": {
              "type": "string",
              "description": "Event description",
              "inputType": "string"
            },
            "attendees": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "List of attendee email addresses",
              "inputType": "array"
            }
          }
        }
      }
    ]
  },
  "id": 1
}
```

## 🧪 Testing

```bash
# Test the updated schema
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","params":{},"id":1}' | python3 -m json.tool
```

Look for `"inputType"` in each property - it should now be present.

## 📝 n8n Usage

After this fix, when you:

1. Add the **MCP Client Tool** node in n8n
2. Configure it to connect to your server: `http://localhost:8080/mcp`
3. The tool should now **load successfully** without the inputType error
4. You'll see **`create_calendar_event`** as an available tool
5. Selecting it will show all the input fields properly

## 🎯 Status

✅ **Fixed** - Committed in: `a5f8957`

The tool schema now includes `inputType` for all properties, making it fully compatible with n8n's MCP Client Tool node.









