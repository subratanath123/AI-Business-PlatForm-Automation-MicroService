# Setup Guide: Calendar MCP Server

## Quick Start

Follow these steps to set up and run the Calendar MCP Server:

### 1. Rebuild the Project

After adding the MCP dependency, rebuild the project to resolve annotations:

```bash
./gradlew clean build
```

Or if using your IDE:
- IntelliJ IDEA: `Build > Rebuild Project`
- Eclipse: `Project > Clean... > Clean all projects`

### 2. Uncomment MCP Annotations

Once the build completes successfully, uncomment the MCP annotations in:

**File**: `src/main/java/net/ai/chatbot/mcp/calendar/tools/CalendarEventTool.java`

Change:
```java
// import org.springframework.ai.mcp.server.annotation.McpTool;
// import org.springframework.ai.mcp.server.annotation.McpToolParam;
```

To:
```java
import org.springframework.ai.mcp.server.annotation.McpTool;
import org.springframework.ai.mcp.server.annotation.McpToolParam;
```

And uncomment the `@McpTool` and `@McpToolParam` annotations on the method.

### 3. Configure Application Properties

Merge the MCP configuration into your existing `application-dev.yml` and `application-prod.yml`:

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        transport: http
        base-path: /mcp
        annotation-scan:
          enabled: true
```

### 4. Start the Application

```bash
./gradlew bootRun
```

Or run from your IDE.

### 5. Verify MCP Server is Running

Check that the MCP server is accessible:

```bash
# List available MCP tools
curl http://localhost:8080/mcp/tools/list

# Expected response: List of available tools including create_calendar_event
```

## Testing the Calendar Event Tool

### Get Google Calendar Access Token

1. Go to [Google OAuth Playground](https://developers.google.com/oauthplayground/)
2. Select "Calendar API v3"
3. Select scope: `https://www.googleapis.com/auth/calendar.events`
4. Click "Authorize APIs"
5. Click "Exchange authorization code for tokens"
6. Copy the `access_token`

### Invoke the Tool via MCP

```bash
curl -X POST http://localhost:8080/mcp/tools/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "name": "create_calendar_event",
    "arguments": {
      "accessToken": "YOUR_ACCESS_TOKEN_HERE",
      "summary": "Team Meeting",
      "description": "Weekly sync meeting",
      "startDateTime": "2024-01-15T10:00:00",
      "endDateTime": "2024-01-15T11:00:00",
      "timeZone": "America/New_York",
      "location": "Conference Room A",
      "attendees": ["john@example.com"]
    }
  }'
```

### Expected Response

```json
{
  "id": "abc123xyz",
  "summary": "Team Meeting",
  "description": "Weekly sync meeting",
  "location": "Conference Room A",
  "htmlLink": "https://calendar.google.com/event?eid=...",
  "status": "confirmed",
  "startDateTime": "2024-01-15T10:00:00",
  "endDateTime": "2024-01-15T11:00:00",
  "message": "Calendar event created successfully"
}
```

## Integration with n8n

### Step 1: Create HTTP Request Node

1. Add "HTTP Request" node in n8n
2. Configure:
   - Method: POST
   - URL: `http://your-server:8080/mcp/tools/invoke`
   - Authentication: None (or configure if you added security)

### Step 2: Configure Request Body

```json
{
  "name": "create_calendar_event",
  "arguments": {
    "accessToken": "{{$json.googleAccessToken}}",
    "summary": "{{$json.eventTitle}}",
    "description": "{{$json.eventDescription}}",
    "startDateTime": "{{$json.startTime}}",
    "endDateTime": "{{$json.endTime}}",
    "timeZone": "UTC",
    "location": "{{$json.location}}",
    "attendees": "{{$json.attendees}}"
  }
}
```

### Step 3: Test the Workflow

Execute the workflow and verify the calendar event is created.

## Troubleshooting

### Issue: Annotations not resolving

**Solution**: 
1. Ensure `spring-ai-starter-mcp-server:1.1.2` is in `build.gradle`
2. Run `./gradlew clean build --refresh-dependencies`
3. Restart your IDE

### Issue: MCP endpoint returns 404

**Solution**:
1. Check `application.yml` has MCP configuration
2. Verify `spring.ai.mcp.server.enabled=true`
3. Check logs for MCP server initialization

### Issue: Google Calendar API returns 401

**Solution**:
1. Verify access token is valid and not expired
2. Ensure token has `calendar.events` scope
3. Check token is passed correctly in `accessToken` parameter

### Issue: Google Calendar API returns 403

**Solution**:
1. Enable Google Calendar API in Google Cloud Console
2. Verify OAuth2 credentials are configured correctly
3. Check API quotas haven't been exceeded

## Security Considerations

### Production Deployment

1. **Use HTTPS**: Always use HTTPS in production
2. **Secure Tokens**: Never log or expose access tokens
3. **Add Authentication**: Protect MCP endpoints with Spring Security
4. **Rate Limiting**: Implement rate limiting to prevent abuse
5. **CORS Configuration**: Configure CORS appropriately

### Example Spring Security Configuration

```java
@Configuration
@EnableWebSecurity
public class McpSecurityConfig {
    
    @Bean
    public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) {
        return http
            .securityMatcher("/mcp/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mcp/tools/list").permitAll()
                .requestMatchers("/mcp/**").authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt())
            .build();
    }
}
```

## Monitoring

### Add Metrics

```java
@Timed(value = "calendar.event.creation", description = "Time to create calendar event")
public Mono<CalendarEventResponse> createCalendarEvent(...) {
    // implementation
}
```

### Health Check

```java
@Component
public class McpHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Check MCP server status
        return Health.up()
            .withDetail("mcp.server", "running")
            .withDetail("tools.registered", toolsCount)
            .build();
    }
}
```

## Next Steps

1. **Add More Tools**: Extend `CalendarEventTool` with list, update, delete operations
2. **Add Validation**: Implement request validation and error handling
3. **Add Tests**: Write unit and integration tests
4. **Add Documentation**: Generate OpenAPI/Swagger documentation
5. **Add Monitoring**: Integrate with Prometheus/Grafana

## References

- [Spring AI MCP Documentation](https://docs.spring.io/spring-ai-mcp/reference/spring-mcp.html)
- [Google Calendar API](https://developers.google.com/calendar/api/v3/reference)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- See `CALENDAR_MCP_SERVER.md` for detailed architecture documentation

