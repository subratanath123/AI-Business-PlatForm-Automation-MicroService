# MCP JSON-RPC 2.0 Implementation

## 🎯 Overview

Your Calendar MCP Server now fully supports the **JSON-RPC 2.0** protocol standard, which is what n8n's MCP Client Tool uses.

## ✅ Why n8n Makes POST Requests

### The MCP Protocol Standard

The Model Context Protocol (MCP) specification uses **JSON-RPC 2.0** as its communication protocol. This means:

1. **All requests use POST** - Even for "read" operations like listing tools
2. **Standard endpoint** - Single endpoint (`POST /mcp`) handles all methods
3. **Method routing** - The `method` field in the request body determines the operation
4. **Structured format** - Follows JSON-RPC 2.0 specification exactly

### JSON-RPC 2.0 Request Format

```json
{
  "jsonrpc": "2.0",
  "method": "tools/list",
  "params": {},
  "id": 1
}
```

### JSON-RPC 2.0 Response Format

```json
{
  "jsonrpc": "2.0",
  "result": {
    "tools": [...]
  },
  "id": 1
}
```

## 🔧 Implementation Details

### Dual Endpoint Support

Your server now supports **both** REST-style and JSON-RPC 2.0:

#### 1. JSON-RPC 2.0 Endpoint (for n8n and MCP clients)

```
POST /mcp
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "method": "tools/list",
  "params": {},
  "id": 1
}
```

#### 2. REST-style Endpoints (for direct HTTP calls)

```
GET /mcp/health
POST /mcp/tools/list
POST /mcp/tools/invoke
```

### Supported JSON-RPC Methods

| Method | Description | Example |
|--------|-------------|---------|
| `initialize` | Initialize MCP connection | Returns server info and capabilities |
| `tools/list` or `tools.list` | List available tools | Returns tool schemas |
| `tools/call` or `tools.call` | Invoke a specific tool | Executes tool with arguments |

## 📝 Complete Working Examples

### 1. Initialize Connection

**Request:**
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "initialize",
    "params": {},
    "id": 1
  }'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {
        "listChanged": false
      }
    },
    "serverInfo": {
      "name": "Calendar MCP Server",
      "version": "1.0.0"
    }
  },
  "id": 1
}
```

### 2. List Available Tools

**Request:**
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "params": {},
    "id": 2
  }'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "tools": [
      {
        "name": "create_calendar_event",
        "description": "Create a new event in Google Calendar. Requires a Google Calendar OAuth2 access token.",
        "inputSchema": {
          "type": "object",
          "required": ["accessToken", "summary", "startDateTime", "endDateTime"],
          "properties": {
            "accessToken": {
              "type": "string",
              "description": "Google Calendar OAuth2 access token"
            },
            "summary": {
              "type": "string",
              "description": "Event title/summary"
            },
            "description": {
              "type": "string",
              "description": "Event description"
            },
            "startDateTime": {
              "type": "string",
              "description": "Event start date/time in ISO 8601 format (e.g., 2024-01-15T10:00:00)"
            },
            "endDateTime": {
              "type": "string",
              "description": "Event end date/time in ISO 8601 format (e.g., 2024-01-15T11:00:00)"
            },
            "timeZone": {
              "type": "string",
              "description": "Time zone (e.g., UTC, America/New_York). Defaults to UTC if not provided"
            },
            "location": {
              "type": "string",
              "description": "Event location"
            },
            "attendees": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "List of attendee email addresses"
            }
          }
        }
      }
    ]
  },
  "id": 2
}
```

### 3. Call a Tool (Create Calendar Event)

**Request:**
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "create_calendar_event",
      "arguments": {
        "accessToken": "YOUR_GOOGLE_OAUTH2_TOKEN",
        "summary": "Team Meeting",
        "description": "Quarterly planning meeting",
        "startDateTime": "2026-02-15T10:00:00",
        "endDateTime": "2026-02-15T11:00:00",
        "timeZone": "America/New_York",
        "location": "Conference Room A",
        "attendees": ["john@example.com", "jane@example.com"]
      }
    },
    "id": 3
  }'
```

**Success Response:**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Calendar event created successfully!\n\nEvent ID: abc123xyz\nSummary: Team Meeting\nLink: https://calendar.google.com/event?eid=...\nStatus: confirmed"
      }
    ],
    "isError": false
  },
  "id": 3
}
```

**Error Response:**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32602,
    "message": "Missing required argument: accessToken"
  },
  "id": 3
}
```

## 🔒 Security Configuration

### Separate Security Filter Chains

The implementation uses **two security filter chains**:

#### 1. MCP Endpoints (No Authentication)
```java
@Bean
@Order(1)  // Higher priority
public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) {
    http
        .securityMatcher("/mcp", "/mcp/**")
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
}
```

#### 2. Other Endpoints (OAuth2 Authentication)
```java
@Bean
@Order(2)  // Lower priority
public SecurityFilterChain filterChain(HttpSecurity http, DomainService domainService) {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/v1/api/n8n/anonymous/**", "/v1/api/public/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(...));
    return http.build();
}
```

**Key Points:**
- MCP endpoints (`/mcp`, `/mcp/**`) are **publicly accessible**
- No Bearer token required for MCP operations
- Google Calendar access token is passed as a **tool parameter**, not for MCP authentication
- Other API endpoints still require OAuth2 authentication

## 🔌 n8n Integration

### MCP Client Tool Configuration

In n8n's MCP Client Tool node:

| Field | Value |
|-------|-------|
| **Server Name** | `Calendar MCP Server` |
| **Transport** | `HTTP` |
| **Server URL** | `http://localhost:8080/mcp` (or your domain) |
| **Authentication** | `None` |

### How n8n Communicates

1. **n8n sends POST request** to `http://localhost:8080/mcp`
2. **Request body** contains JSON-RPC 2.0 formatted method call
3. **Your server** routes based on the `method` field
4. **Response** returns in JSON-RPC 2.0 format
5. **n8n parses** the result and makes it available in the workflow

### Example n8n Workflow

```
[Trigger] → [MCP Client Tool: create_calendar_event] → [Process Result]
```

The MCP Client Tool node will:
1. Connect to your server at `http://localhost:8080/mcp`
2. Call `initialize` to establish connection
3. Call `tools/list` to discover available tools
4. Show `create_calendar_event` as an available action
5. When executed, call `tools/call` with the event parameters

## 🧪 Testing

### Test JSON-RPC Endpoints

```bash
# Test initialize
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"initialize","params":{},"id":1}'

# Test tools/list
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","params":{},"id":2}'

# Test tools/call
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "method":"tools/call",
    "params":{
      "name":"create_calendar_event",
      "arguments":{
        "accessToken":"test-token",
        "summary":"Test Meeting",
        "startDateTime":"2026-02-15T10:00:00",
        "endDateTime":"2026-02-15T11:00:00"
      }
    },
    "id":3
  }'
```

### Test REST Endpoints (Alternative)

```bash
# Health check
curl http://localhost:8080/mcp/health

# List tools (REST style)
curl -X POST http://localhost:8080/mcp/tools/list

# Invoke tool (REST style)
curl -X POST http://localhost:8080/mcp/tools/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "name": "create_calendar_event",
    "arguments": {
      "accessToken": "test-token",
      "summary": "Test Meeting",
      "startDateTime": "2026-02-15T10:00:00",
      "endDateTime": "2026-02-15T11:00:00"
    }
  }'
```

## 📚 JSON-RPC 2.0 Error Codes

| Code | Message | Description |
|------|---------|-------------|
| `-32600` | Invalid Request | The JSON sent is not a valid Request object |
| `-32601` | Method not found | The method does not exist / is not available |
| `-32602` | Invalid params | Invalid method parameter(s) |
| `-32603` | Internal error | Internal JSON-RPC error |

## 🎯 Key Takeaways

1. **n8n uses POST for everything** - This is the MCP/JSON-RPC 2.0 standard
2. **Single endpoint handles all methods** - `POST /mcp` routes based on `method` field
3. **No authentication for MCP endpoints** - Google Calendar token is a tool parameter
4. **Dual support** - Both JSON-RPC 2.0 and REST-style endpoints work
5. **Production ready** - Verified working at `https://api.jadeordersmedia.com/mcp/tools/list`

## 🔗 References

- [MCP Specification](https://spec.modelcontextprotocol.io/)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
- [Spring Security Filter Chain Ordering](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html#jc-httpsecurity-instances)
- [n8n MCP Client Tool Documentation](https://docs.n8n.io/)

## ✅ Status

- ✅ JSON-RPC 2.0 endpoint implemented
- ✅ Security configuration fixed (separate filter chains)
- ✅ `initialize` method working
- ✅ `tools/list` method working
- ✅ `tools/call` method working
- ✅ n8n integration ready
- ✅ Production deployment verified

Your MCP server is now fully compliant with the MCP specification and ready for n8n integration! 🚀

