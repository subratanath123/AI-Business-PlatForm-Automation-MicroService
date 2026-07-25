# n8n MCP Client Tool Node Configuration

## 📋 Overview

This guide shows you **exactly** what to enter in n8n's **MCP Client Tool** node to connect to your Calendar MCP Server.

**Important:** n8n's MCP Client Tool uses the **JSON-RPC 2.0** protocol standard. Your server now supports both REST-style endpoints and JSON-RPC 2.0 format at `POST /mcp`.

## 🔧 MCP Client Tool Node Configuration

### Step 1: Add MCP Client Tool Node

In your n8n workflow:
1. Click the **+** button to add a new node
2. Search for **"MCP Client Tool"** or **"Tool MCP"**
3. Select the **MCP Client Tool** node

### Step 2: Configure the MCP Server Connection

In the MCP Client Tool node configuration, fill in these fields:

#### **Server Name**
```
Calendar MCP Server
```
*This is just a friendly name for identification*

---

#### **Transport Type**
```
HTTP
```
**Select:** `HTTP` (not SSE/Stdio)

---

#### **Server URL**

**For localhost (development):**
```
http://localhost:8080/mcp
```

**For Docker n8n connecting to host machine:**
```
http://host.docker.internal:8080/mcp
```

**For production (with your server IP):**
```
http://143.198.58.6:8080/mcp
```

**For custom domain:**
```
https://api.yourdomain.com/mcp
```

---

#### **Authentication**
```
None
```
**Select:** `None` (no authentication required)

> ⚠️ **Note:** The MCP endpoints are publicly accessible. The Google Calendar access token is passed as a tool parameter, not for MCP authentication.

---

#### **Additional Headers** (Optional)
Leave empty or add if needed:
```json
{
  "X-Custom-Header": "value"
}
```

---

### Step 3: Verify Connection

After configuring, n8n should automatically:
1. Connect to your MCP server
2. Discover the `create_calendar_event` tool
3. Show it as an available tool in the node

---

## 🎯 Complete Configuration Summary

Here's what your MCP Client Tool node should look like:

```yaml
Server Name: Calendar MCP Server
Transport: HTTP
Server URL: http://localhost:8080/mcp
Authentication: None
```

---

## 📸 Visual Configuration Guide

### Configuration Screenshot Reference

```
┌─────────────────────────────────────────┐
│ MCP Client Tool                         │
├─────────────────────────────────────────┤
│                                         │
│ Server Name:                            │
│ ┌─────────────────────────────────────┐ │
│ │ Calendar MCP Server                 │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ Transport: [HTTP ▼]                     │
│                                         │
│ Server URL:                             │
│ ┌─────────────────────────────────────┐ │
│ │ http://localhost:8080/mcp           │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ Authentication: [None ▼]                │
│                                         │
└─────────────────────────────────────────┘
```

---

## 🔍 Tool Discovery

Once connected, n8n will automatically discover your tool:

**Available Tool:**
- **Name:** `create_calendar_event`
- **Description:** Create a new event in Google Calendar

**Tool Parameters** (automatically detected):
- `accessToken` (required) - Google Calendar OAuth2 token
- `summary` (required) - Event title
- `startDateTime` (required) - ISO 8601 format
- `endDateTime` (required) - ISO 8601 format
- `description` (optional) - Event description
- `location` (optional) - Event location
- `timeZone` (optional) - Defaults to UTC
- `attendees` (optional) - Array of email addresses

---

## 🔌 Using the Tool in n8n Workflow

### Example Workflow Setup

#### Node 1: Trigger (Manual/Webhook/Schedule)
```
Configure your trigger as needed
```

#### Node 2: Set Parameters (Optional)
```json
{
  "eventTitle": "Team Meeting",
  "eventDescription": "Quarterly planning session",
  "startTime": "2024-01-15T10:00:00",
  "endTime": "2024-01-15T11:00:00",
  "eventLocation": "Conference Room A",
  "googleToken": "ya29.a0AfB_byCd...",
  "attendeeEmails": ["alice@company.com", "bob@company.com"]
}
```

#### Node 3: MCP Client Tool
Configure tool invocation:

**Tool Selection:**
```
create_calendar_event
```

**Tool Parameters:**
```javascript
// Map your workflow data to tool parameters

accessToken: {{ $json.googleToken }}
summary: {{ $json.eventTitle }}
description: {{ $json.eventDescription }}
startDateTime: {{ $json.startTime }}
endDateTime: {{ $json.endTime }}
timeZone: America/New_York
location: {{ $json.eventLocation }}
attendees: {{ $json.attendeeEmails }}
```

---

## 🧪 Testing the Connection

### Test 1: Verify MCP Server Endpoint

Before configuring n8n, verify your server is accessible:

```bash
# Test from your terminal
curl http://localhost:8080/mcp/health

# Expected response:
# {"status":"UP","service":"Calendar MCP Server",...}
```

### Test 2: Check Tool Discovery

```bash
curl http://localhost:8080/mcp/tools/list

# Expected: List of available tools including create_calendar_event
```

### Test 3: Test in n8n

1. Add MCP Client Tool node
2. Configure as shown above
3. Click **"Test workflow"**
4. Provide required parameters
5. Execute and check results

---

## 🐛 Troubleshooting

### Issue 1: n8n Can't Connect to Server

**Error:** "Failed to connect to MCP server"

**Solutions:**

1. **Check server is running:**
   ```bash
   curl http://localhost:8080/mcp/health
   ```

2. **If n8n is in Docker:**
   Use `http://host.docker.internal:8080/mcp` instead of `localhost`

3. **Check firewall:**
   Ensure port 8080 is accessible

4. **Verify URL format:**
   - ✅ `http://localhost:8080/mcp`
   - ❌ `http://localhost:8080/mcp/` (no trailing slash)
   - ❌ `http://localhost:8080` (missing /mcp)

---

### Issue 2: Tool Not Discovered

**Error:** "No tools available"

**Solutions:**

1. **Check tools endpoint:**
   ```bash
   curl http://localhost:8080/mcp/tools/list
   ```

2. **Verify response format:**
   Should return JSON with `tools` array

3. **Check Spring Boot logs:**
   ```bash
   grep -i "mcp" /tmp/boot.log
   ```

4. **Restart Spring Boot application:**
   ```bash
   pkill -f gradle
   cd "/usr/local/Chat API" && ./gradlew bootRun
   ```

---

### Issue 3: Authentication Error

**Error:** "401 Unauthorized" when invoking tool

**Solutions:**

This is likely a **Google Calendar API error**, not an MCP server error.

1. **Check your Google OAuth token:**
   - Token must have scope: `https://www.googleapis.com/auth/calendar.events`
   - Token must not be expired (typically 1 hour lifetime)

2. **Get a new token:**
   - Visit: https://developers.google.com/oauthplayground/
   - Select "Calendar API v3"
   - Authorize and get access token

3. **Verify token format:**
   ```
   ya29.a0AfB_byC...  (starts with "ya29")
   ```

---

### Issue 4: n8n MCP Client Tool vs HTTP Request Node

**Question:** Which node should I use?

**Answer:**

| Feature | MCP Client Tool Node | HTTP Request Node |
|---------|---------------------|-------------------|
| **Auto-discovery** | ✅ Yes | ❌ No |
| **Tool schema** | ✅ Automatic | ❌ Manual |
| **Parameter validation** | ✅ Built-in | ❌ Manual |
| **AI Agent integration** | ✅ Seamless | ⚠️ Complex |
| **Simple workflows** | ⚠️ Overkill | ✅ Easier |

**Recommendation:**
- **Use MCP Client Tool** if you want AI agents to use the calendar tool
- **Use HTTP Request** if you just need to create events in workflows

---

## 📊 Connection Test Checklist

Before configuring n8n, verify:

- [ ] Spring Boot application is running (port 8080)
- [ ] `/mcp/health` returns 200 OK
- [ ] `/mcp/tools/list` returns tool definitions
- [ ] You can reach the server from where n8n is running
- [ ] You have a valid Google Calendar OAuth token
- [ ] OAuth token has correct scopes

---

## 🔐 Security Considerations

### Current Setup (Development)

✅ MCP endpoints are **publicly accessible**
✅ No authentication required to call MCP server
✅ Google Calendar token is passed as tool parameter

**This is fine for:**
- Development/testing
- Internal network deployments
- Trusted environments

### Production Recommendations

⚠️ **For production, consider adding:**

1. **API Key Authentication:**
   ```java
   // Add to ApiConfig.java
   .requestMatchers("/mcp/**")
       .hasHeader("X-API-Key")
   ```

2. **Rate Limiting:**
   - Prevent abuse
   - Use Spring Cloud Gateway or similar

3. **HTTPS:**
   ```
   https://api.yourdomain.com/mcp
   ```

4. **IP Whitelisting:**
   - Only allow requests from n8n server IP

---

## 🚀 Advanced: Multiple MCP Servers in n8n

You can add multiple MCP servers to n8n:

### Server 1: Calendar MCP Server
```
Name: Calendar MCP Server
URL: http://localhost:8080/mcp
Transport: HTTP
Auth: None
```

### Server 2: Another MCP Server (Future)
```
Name: Email MCP Server
URL: http://localhost:8081/mcp
Transport: HTTP
Auth: None
```

Each server's tools will be available in the MCP Client Tool node.

---

## 📝 Quick Reference Card

```
╔════════════════════════════════════════════╗
║  n8n MCP Client Tool Configuration        ║
╠════════════════════════════════════════════╣
║                                            ║
║  Server Name: Calendar MCP Server          ║
║  Transport: HTTP                           ║
║  Server URL: http://localhost:8080/mcp     ║
║  Authentication: None                      ║
║                                            ║
║  Available Tool: create_calendar_event     ║
║                                            ║
║  Required Parameters:                      ║
║    • accessToken (Google OAuth2)           ║
║    • summary (Event title)                 ║
║    • startDateTime (ISO 8601)              ║
║    • endDateTime (ISO 8601)                ║
║                                            ║
╚════════════════════════════════════════════╝
```

---

## 🔗 Additional Resources

### Your MCP Server Endpoints
- Health: http://localhost:8080/mcp/health
- Tools List: http://localhost:8080/mcp/tools/list
- Invoke: http://localhost:8080/mcp/tools/invoke

### Documentation Files
- `MCP_ENDPOINTS_WORKING.md` - Technical details
- `N8N_INTEGRATION.md` - General n8n integration
- `README_MCP.md` - MCP server overview
- `SETUP_MCP_SERVER.md` - Setup guide

### Google OAuth Resources
- OAuth Playground: https://developers.google.com/oauthplayground/
- Calendar API Docs: https://developers.google.com/calendar/api
- Required Scope: `https://www.googleapis.com/auth/calendar.events`

### n8n Resources
- MCP Client Tool Docs: https://docs.n8n.io/integrations/builtin/cluster-nodes/sub-nodes/n8n-nodes-langchain.toolmcp/
- n8n MCP Guide: https://docs.n8n.io/advanced-ai/accessing-n8n-mcp-server/

---

## ✅ Success Criteria

You'll know it's working when:

1. ✅ n8n MCP Client Tool node shows "Connected"
2. ✅ `create_calendar_event` tool appears in tool list
3. ✅ Tool parameters are auto-populated with descriptions
4. ✅ Test execution creates a calendar event in Google Calendar
5. ✅ You receive event details in the response

---

## 📞 Support

If you encounter issues:

1. **Check server logs:**
   ```bash
   tail -f /tmp/boot.log | grep -i mcp
   ```

2. **Test manually with curl:**
   ```bash
   curl -X POST http://localhost:8080/mcp/tools/invoke \
     -H "Content-Type: application/json" \
     -d '{"name":"create_calendar_event","arguments":{...}}'
   ```

3. **Verify n8n can reach your server:**
   - From n8n container/machine, run:
   ```bash
   curl http://localhost:8080/mcp/health
   ```

---

**Last Updated:** 2026-02-05  
**Status:** ✅ Ready for use


