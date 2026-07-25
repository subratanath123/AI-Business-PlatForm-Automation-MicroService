# Calendar MCP Server

## Overview

This is a clean implementation of an MCP (Model Context Protocol) server for Google Calendar event creation using Spring AI MCP Server.

## Architecture

The implementation follows clean code principles with clear separation of concerns:

```
net.ai.chatbot.mcp.calendar/
├── dto/
│   ├── CalendarEventRequest.java     # Request DTO
│   └── CalendarEventResponse.java    # Response DTO
├── service/
│   └── GoogleCalendarService.java     # Google Calendar API integration
├── tools/
│   └── CalendarEventTool.java        # MCP Tool definition
└── config/
    └── CalendarMcpServerConfig.java  # MCP server configuration
```

## Features

- **Clean Code**: Follows SOLID principles and clean architecture
- **MCP Protocol**: Implements Model Context Protocol for tool discovery and invocation
- **Google Calendar Integration**: Creates events in Google Calendar
- **Reactive**: Uses Spring WebFlux for non-blocking operations
- **Type-Safe**: Strongly typed DTOs with validation
- **Logging**: Comprehensive logging for debugging and monitoring

## MCP Tool

### Tool Name
`create_calendar_event`

### Tool Description
Create a new event in Google Calendar. Requires a Google Calendar OAuth2 access token.

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `accessToken` | String | Yes | Google Calendar OAuth2 access token |
| `summary` | String | Yes | Event title/summary |
| `description` | String | No | Event description |
| `startDateTime` | String | Yes | Event start in ISO 8601 format (e.g., 2024-01-15T10:00:00) |
| `endDateTime` | String | Yes | Event end in ISO 8601 format (e.g., 2024-01-15T11:00:00) |
| `timeZone` | String | No | Time zone (e.g., UTC, America/New_York). Defaults to UTC |
| `location` | String | No | Event location |
| `attendees` | List<String> | No | List of attendee email addresses |

### Response

```json
{
  "id": "event_id",
  "summary": "Meeting Title",
  "description": "Meeting description",
  "location": "Conference Room A",
  "htmlLink": "https://calendar.google.com/...",
  "status": "confirmed",
  "startDateTime": "2024-01-15T10:00:00",
  "endDateTime": "2024-01-15T11:00:00",
  "message": "Calendar event created successfully"
}
```

## Configuration

### application.yml

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

### Dependency

```gradle
implementation("org.springframework.ai:spring-ai-starter-mcp-server:1.1.2")
```

## Usage

### 1. MCP Client Discovery

MCP clients can discover available tools:

```bash
GET http://localhost:8080/mcp/tools/list
```

Response:
```json
{
  "tools": [
    {
      "name": "create_calendar_event",
      "description": "Create a new event in Google Calendar...",
      "inputSchema": { ... }
    }
  ]
}
```

### 2. Invoke Tool via MCP

```bash
POST http://localhost:8080/mcp/tools/invoke
Content-Type: application/json

{
  "name": "create_calendar_event",
  "arguments": {
    "accessToken": "ya29.a0AfH6SMB...",
    "summary": "Team Meeting",
    "description": "Weekly sync meeting",
    "startDateTime": "2024-01-15T10:00:00",
    "endDateTime": "2024-01-15T11:00:00",
    "timeZone": "America/New_York",
    "location": "Conference Room A",
    "attendees": ["john@example.com", "jane@example.com"]
  }
}
```

### 3. Direct Service Usage (within Spring application)

```java
@Autowired
private GoogleCalendarService calendarService;

public void createEvent() {
    CalendarEventRequest request = CalendarEventRequest.builder()
            .summary("Team Meeting")
            .startDateTime("2024-01-15T10:00:00")
            .endDateTime("2024-01-15T11:00:00")
            .build();
    
    Mono<CalendarEventResponse> response = calendarService
            .createEvent("your-access-token", request);
}
```

## Integration with n8n

1. Add HTTP Request node in n8n
2. Configure MCP tool discovery:
   - Method: GET
   - URL: `http://your-server:8080/mcp/tools/list`
   
3. Invoke the tool:
   - Method: POST
   - URL: `http://your-server:8080/mcp/tools/invoke`
   - Body: Include tool name and arguments

## Security

- **OAuth2**: Uses Google Calendar OAuth2 access tokens
- **No Token Storage**: Access tokens are passed in requests (not stored)
- **Spring Security**: Can be integrated with existing Spring Security configuration
- **HTTPS**: Use HTTPS in production for secure token transmission

## Error Handling

The service handles errors gracefully:

```java
return googleCalendarService.createEvent(accessToken, request)
    .doOnSuccess(response -> log.info("Event created: {}", response.getId()))
    .doOnError(error -> log.error("Failed: {}", error.getMessage()))
    .onErrorResume(error -> Mono.just(
        CalendarEventResponse.builder()
            .status("error")
            .message(error.getMessage())
            .build()
    ));
```

## Testing

### Unit Tests

```java
@Test
void testCreateCalendarEvent() {
    CalendarEventRequest request = CalendarEventRequest.builder()
            .summary("Test Event")
            .startDateTime("2024-01-15T10:00:00")
            .endDateTime("2024-01-15T11:00:00")
            .build();
    
    StepVerifier.create(googleCalendarService.createEvent("token", request))
            .assertNext(response -> {
                assertNotNull(response.getId());
                assertEquals("Test Event", response.getSummary());
            })
            .verifyComplete();
}
```

### Integration Tests

Use MockWebServer to mock Google Calendar API responses.

## Logging

Logging is structured for observability:

```
INFO  - MCP Tool invoked: create_calendar_event for event 'Team Meeting'
INFO  - Creating calendar event: Team Meeting
INFO  - Calendar event created successfully: abc123xyz
INFO  - Calendar event created via MCP: abc123xyz
```

## Clean Code Principles Applied

1. **Single Responsibility**: Each class has one clear purpose
2. **Dependency Injection**: All dependencies injected via constructor
3. **Immutability**: DTOs use `@Builder` pattern
4. **Reactive**: Non-blocking operations with Reactor
5. **Separation of Concerns**: DTOs, Services, Tools, and Config separated
6. **Type Safety**: Strong typing throughout
7. **Error Handling**: Proper error handling and logging
8. **Documentation**: Comprehensive JavaDoc and inline comments

## Extension Points

To add more calendar operations:

1. Add new MCP tool methods in `CalendarEventTool` class
2. Add corresponding service methods in `GoogleCalendarService`
3. Spring AI MCP will automatically discover and expose them

Example:
```java
@McpTool(name = "list_calendar_events")
public Mono<List<CalendarEventResponse>> listEvents(
        @McpToolParam(required = true) String accessToken,
        @McpToolParam(required = true) String startDate,
        @McpToolParam(required = true) String endDate) {
    // Implementation
}
```

## Production Considerations

1. **Rate Limiting**: Implement rate limiting for Google Calendar API calls
2. **Caching**: Cache frequently accessed calendar data
3. **Monitoring**: Add metrics and monitoring (Micrometer/Prometheus)
4. **Circuit Breaker**: Add resilience4j for fault tolerance
5. **API Keys**: Externalize configuration to environment variables
6. **Logging**: Use structured logging (JSON format) for production

## References

- [Spring AI MCP Documentation](https://docs.spring.io/spring-ai-mcp/reference/spring-mcp.html)
- [Google Calendar API](https://developers.google.com/calendar/api/v3/reference)
- [Model Context Protocol](https://modelcontextprotocol.io/)

