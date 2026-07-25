# n8n Integration Guide for Calendar MCP Server

## Overview

Your Calendar MCP Server exposes tools that n8n can call via HTTP requests. Here's how to integrate it.

## 🌐 Server URLs and Ports

### Default Configuration

Based on your application configuration:

```yaml
server:
  port: 8080  # Your Spring Boot application port

spring:
  ai:
    mcp:
      server:
        enabled: true
        transport: http
        base-path: /mcp  # MCP endpoints base path
```

### Available Endpoints

| Endpoint | Method | Purpose | URL |
|----------|--------|---------|-----|
| Tool Discovery | GET | List all available MCP tools | `http://localhost:8080/mcp/tools/list` |
| Tool Invocation | POST | Execute a specific tool | `http://localhost:8080/mcp/tools/invoke` |
| Health Check | GET | Check MCP server status | `http://localhost:8080/actuator/health` |

## 📋 Step-by-Step n8n Integration

### Step 1: Discover Available Tools

First, verify your MCP server is running and tools are registered.

#### Using curl:
```bash
curl http://localhost:8080/mcp/tools/list
```

#### Expected Response:
```json
{
  "tools": [
    {
      "name": "create_calendar_event",
      "description": "Create a new event in Google Calendar...",
      "inputSchema": {
        "type": "object",
        "properties": {
          "accessToken": {
            "type": "string",
            "description": "Google Calendar OAuth2 access token"
          },
          "summary": {
            "type": "string",
            "description": "Event title/summary"
          },
          "startDateTime": {
            "type": "string",
            "description": "Event start date/time in ISO 8601 format"
          },
          "endDateTime": {
            "type": "string",
            "description": "Event end date/time in ISO 8601 format"
          }
        },
        "required": ["accessToken", "summary", "startDateTime", "endDateTime"]
      }
    }
  ]
}
```

### Step 2: Set Up n8n Workflow

#### Option A: Using HTTP Request Node

1. **Add HTTP Request Node**
   - Drag "HTTP Request" node onto canvas
   - Connect it to your trigger (Manual, Webhook, Schedule, etc.)

2. **Configure HTTP Request Node**

   **Basic Settings:**
   - **Method**: `POST`
   - **URL**: `http://localhost:8080/mcp/tools/invoke`
   - **Authentication**: None (or configure if you added security)
   - **Response Format**: JSON

   **Headers:**
   ```json
   {
     "Content-Type": "application/json"
   }
   ```

   **Body (JSON):**
   ```json
   {
     "name": "create_calendar_event",
     "arguments": {
       "accessToken": "{{ $json.googleAccessToken }}",
       "summary": "{{ $json.eventTitle }}",
       "description": "{{ $json.eventDescription }}",
       "startDateTime": "{{ $json.startTime }}",
       "endDateTime": "{{ $json.endTime }}",
       "timeZone": "UTC",
       "location": "{{ $json.location }}",
       "attendees": {{ $json.attendees }}
     }
   }
   ```

#### Option B: Using Webhook Trigger + HTTP Request

**Complete n8n Workflow Example:**

```
Webhook Trigger → HTTP Request (MCP Tool) → Response
```

1. **Webhook Node Configuration:**
   - **Path**: `/create-calendar-event`
   - **Method**: POST
   - **Response Mode**: Last Node

2. **HTTP Request Node Configuration:**
   - **Method**: POST
   - **URL**: `http://localhost:8080/mcp/tools/invoke`
   - **Body**:
   ```json
   {
     "name": "create_calendar_event",
     "arguments": {
       "accessToken": "{{ $json.body.accessToken }}",
       "summary": "{{ $json.body.summary }}",
       "description": "{{ $json.body.description }}",
       "startDateTime": "{{ $json.body.startDateTime }}",
       "endDateTime": "{{ $json.body.endDateTime }}",
       "timeZone": "{{ $json.body.timeZone || 'UTC' }}",
       "location": "{{ $json.body.location }}",
       "attendees": {{ $json.body.attendees || [] }}
     }
   }
   ```

3. **Respond to Webhook Node:**
   - **Status Code**: 200
   - **Body**: `{{ $json }}`

## 🧪 Testing

### Test 1: Direct HTTP Request (curl)

```bash
curl -X POST http://localhost:8080/mcp/tools/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "name": "create_calendar_event",
    "arguments": {
      "accessToken": "YOUR_GOOGLE_ACCESS_TOKEN",
      "summary": "Team Meeting",
      "description": "Weekly sync",
      "startDateTime": "2024-01-15T10:00:00",
      "endDateTime": "2024-01-15T11:00:00",
      "timeZone": "America/New_York",
      "location": "Conference Room A",
      "attendees": ["john@example.com", "jane@example.com"]
    }
  }'
```

### Test 2: Using n8n's Manual Execution

1. Set up workflow as described above
2. In the HTTP Request node body, use hardcoded values:
```json
{
  "name": "create_calendar_event",
  "arguments": {
    "accessToken": "YOUR_ACTUAL_TOKEN",
    "summary": "Test Event",
    "startDateTime": "2024-01-20T14:00:00",
    "endDateTime": "2024-01-20T15:00:00",
    "timeZone": "UTC"
  }
}
```
3. Click "Execute Node" button
4. Check response in output panel

### Expected Success Response:

```json
{
  "id": "abc123xyz",
  "summary": "Team Meeting",
  "description": "Weekly sync",
  "location": "Conference Room A",
  "htmlLink": "https://calendar.google.com/event?eid=...",
  "status": "confirmed",
  "startDateTime": "2024-01-15T10:00:00",
  "endDateTime": "2024-01-15T11:00:00",
  "message": "Calendar event created successfully"
}
```

## 🔐 Getting Google Calendar Access Token

### Method 1: Google OAuth Playground (Quick Testing)

1. Go to https://developers.google.com/oauthplayground/
2. Click the gear icon (⚙️) in top right
3. Check "Use your own OAuth credentials"
4. Enter your OAuth2 Client ID and Secret
5. In left panel, select "Calendar API v3"
6. Select scope: `https://www.googleapis.com/auth/calendar.events`
7. Click "Authorize APIs"
8. Authorize with your Google account
9. Click "Exchange authorization code for tokens"
10. Copy the `access_token` value

### Method 2: Using n8n Google OAuth2 Node

1. **Add Google OAuth2 API Credentials in n8n:**
   - Go to n8n Settings → Credentials
   - Add "Google OAuth2 API"
   - Enter Client ID, Client Secret
   - Select scopes: `https://www.googleapis.com/auth/calendar.events`
   - Save and authorize

2. **Use in Workflow:**
```
Google Calendar Node → Set Node → HTTP Request (MCP)
```

The Google Calendar node will provide the access token automatically.

## 📊 Complete n8n Workflow Examples

### Example 1: Scheduled Weekly Meeting Creation

```
Schedule Trigger (Every Monday 9 AM)
  ↓
Set Node (Define event details)
  ↓
HTTP Request (Call MCP Tool)
  ↓
Slack Node (Notify team)
```

**Set Node Configuration:**
```json
{
  "eventTitle": "Weekly Team Sync",
  "eventDescription": "Weekly team meeting",
  "startTime": "{{ $now.plus(1, 'hour').toISO() }}",
  "endTime": "{{ $now.plus(2, 'hour').toISO() }}",
  "attendees": ["team@company.com"]
}
```

### Example 2: Webhook-Triggered Event Creation

```
Webhook (Receive event data)
  ↓
HTTP Request (Get Google Token)
  ↓
HTTP Request (Call MCP Tool)
  ↓
Respond to Webhook
```

**Webhook Payload Example:**
```bash
curl -X POST https://your-n8n-instance.com/webhook/create-event \
  -H "Content-Type: application/json" \
  -d '{
    "summary": "Client Meeting",
    "description": "Discuss Q1 objectives",
    "startDateTime": "2024-01-15T14:00:00",
    "endDateTime": "2024-01-15T15:00:00",
    "attendees": ["client@company.com"]
  }'
```

### Example 3: Form Submission → Calendar Event

```
Form Trigger (Webhook)
  ↓
Function Node (Format dates)
  ↓
HTTP Request (MCP Tool)
  ↓
Email Node (Send confirmation)
```

## 🌍 Production URLs

### When Deployed

Replace `localhost:8080` with your actual server URL:

**Local Development:**
```
http://localhost:8080/mcp/tools/invoke
```

**Production (with domain):**
```
https://api.yourcompany.com/mcp/tools/invoke
```

**Production (with IP):**
```
http://143.198.58.6:8080/mcp/tools/invoke
```

**Docker Container:**
```
http://container-name:8080/mcp/tools/invoke
```

### Port Configuration

Your application uses port **8080** by default (from `application.yml`):

```yaml
server:
  port: 8080
```

To change the port:
1. Edit `src/main/resources/application-dev.yml` or `application-prod.yml`
2. Change `server.port` value
3. Restart application
4. Update n8n URLs accordingly

## 🔒 Security Considerations for n8n

### 1. Secure Your MCP Endpoints

Add authentication to your MCP endpoints:

```java
@Configuration
public class McpSecurityConfig {
    @Bean
    public SecurityFilterChain mcpFilterChain(HttpSecurity http) {
        return http
            .securityMatcher("/mcp/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mcp/tools/list").permitAll()
                .requestMatchers("/mcp/**").authenticated()
            )
            .httpBasic()
            .and()
            .build();
    }
}
```

Then in n8n, add Basic Auth:
- **Username**: your-username
- **Password**: your-password

### 2. Use Environment Variables in n8n

Instead of hardcoding tokens:

```json
{
  "arguments": {
    "accessToken": "{{ $env.GOOGLE_CALENDAR_TOKEN }}",
    "summary": "{{ $json.summary }}"
  }
}
```

### 3. Use HTTPS in Production

Always use HTTPS when exposing to n8n:
```
https://api.yourcompany.com/mcp/tools/invoke
```

## 🐛 Troubleshooting

### Issue: Connection Refused

**Problem:** n8n can't connect to `localhost:8080`

**Solution:**
- If n8n is in Docker, use host IP or `host.docker.internal:8080`
- Check firewall settings
- Verify Spring Boot app is running: `curl http://localhost:8080/actuator/health`

### Issue: 404 Not Found

**Problem:** MCP endpoint returns 404

**Solution:**
- Verify MCP server is enabled in `application.yml`
- Check logs for MCP tool registration
- Confirm URL is `http://localhost:8080/mcp/tools/invoke` (not `/api/mcp/`)

### Issue: 401 Unauthorized from Google

**Problem:** Google Calendar API returns 401

**Solution:**
- Access token may be expired (they expire after 1 hour)
- Refresh token or get new one from OAuth playground
- Verify token has `calendar.events` scope

### Issue: 400 Bad Request

**Problem:** Invalid request format

**Solution:**
- Check JSON structure matches MCP format
- Verify `name` field is exactly "create_calendar_event"
- Ensure `arguments` object contains all required fields
- Check date format is ISO 8601: "2024-01-15T10:00:00"

## 📝 Complete n8n Node Configuration (Copy-Paste Ready)

```json
{
  "name": "Create Calendar Event via MCP",
  "type": "n8n-nodes-base.httpRequest",
  "position": [600, 300],
  "parameters": {
    "method": "POST",
    "url": "http://localhost:8080/mcp/tools/invoke",
    "authentication": "none",
    "requestFormat": "json",
    "bodyParametersJson": "={\n  \"name\": \"create_calendar_event\",\n  \"arguments\": {\n    \"accessToken\": \"{{ $json.accessToken }}\",\n    \"summary\": \"{{ $json.summary }}\",\n    \"description\": \"{{ $json.description }}\",\n    \"startDateTime\": \"{{ $json.startDateTime }}\",\n    \"endDateTime\": \"{{ $json.endDateTime }}\",\n    \"timeZone\": \"{{ $json.timeZone || 'UTC' }}\",\n    \"location\": \"{{ $json.location }}\",\n    \"attendees\": {{ $json.attendees || [] }}\n  }\n}",
    "options": {}
  }
}
```

## 🎯 Quick Reference

| What | Value |
|------|-------|
| **Server** | Your Spring Boot application |
| **Base URL** | `http://localhost:8080` |
| **MCP Endpoint** | `/mcp/tools/invoke` |
| **Full URL** | `http://localhost:8080/mcp/tools/invoke` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **Tool Name** | `create_calendar_event` |
| **Port** | `8080` (configurable in application.yml) |

## 📞 Support

If you encounter issues:
1. Check Spring Boot logs: `./gradlew bootRun`
2. Verify tool registration: `curl http://localhost:8080/mcp/tools/list`
3. Test with curl before using n8n
4. Check n8n execution logs for detailed errors

