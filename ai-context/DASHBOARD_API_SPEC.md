# Dashboard API Specification

## Base Information

- **Base URL**: `/v1/api/dashboard`
- **Authentication**: Required (JWT Token in Authorization header)
- **Content-Type**: `application/json`
- **CORS**: Enabled for all origins

---

## Endpoints Overview

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/stats` | GET | Get comprehensive dashboard statistics |
| `/stats/overall` | GET | Get overall platform statistics |
| `/stats/chatbots` | GET | Get chatbot-specific statistics |
| `/stats/conversations` | GET | Get conversation statistics |
| `/stats/usage` | GET | Get usage and engagement statistics |
| `/stats/usage-over-time` | GET | Get time series data for trends |
| `/top/chatbots` | GET | Get top chatbots by activity |
| `/top/users` | GET | Get top active users |

---

## 1. Get Comprehensive Dashboard Statistics

Get all dashboard statistics in a single request.

### Request

```http
GET /v1/api/dashboard/stats
Authorization: Bearer <JWT_TOKEN>
```

### Response

**Status Code**: `200 OK`

**Response Body**: `DashboardStatsResponse`

```json
{
  "overallStats": {
    "totalChatBots": 150,
    "totalConversations": 3420,
    "totalMessages": 12500,
    "totalUsers": 85,
    "activeChatBots": 120,
    "activeConversationsToday": 45,
    "totalKnowledgeBases": 200
  },
  "chatBotStats": {
    "totalChatBots": 150,
    "chatBotsByStatus": {
      "CREATED": 10,
      "TRAINING": 5,
      "COMPLETED": 120,
      "FAILED": 15
    },
    "chatBotsCreatedToday": 3,
    "chatBotsCreatedThisWeek": 12,
    "chatBotsCreatedThisMonth": 45,
    "averageChatBotsPerUser": 1.76,
    "chatBotsByDataSource": {
      "FILE": 50,
      "WEBSITE": 30,
      "TEXT": 40,
      "NONE": 30
    }
  },
  "conversationStats": {
    "totalConversations": 3420,
    "conversationsToday": 45,
    "conversationsThisWeek": 320,
    "conversationsThisMonth": 1200,
    "averageMessagesPerConversation": 3.65,
    "longestConversation": 45,
    "conversationsByMode": {
      "chat": 3000,
      "embedded": 420
    },
    "anonymousConversations": 500,
    "authenticatedConversations": 2920
  },
  "usageStats": {
    "totalMessages": 12500,
    "messagesToday": 180,
    "messagesThisWeek": 1200,
    "messagesThisMonth": 4500,
    "averageMessagesPerDay": 150.5,
    "peakMessagesInDay": 450,
    "messagesByHour": {
      "0": 50,
      "1": 30,
      "2": 20,
      "9": 200,
      "10": 350,
      "11": 400,
      "14": 380,
      "15": 420,
      "16": 400,
      "17": 350,
      "18": 280
    },
    "totalUsers": 85,
    "activeUsersToday": 25,
    "activeUsersThisWeek": 60,
    "activeUsersThisMonth": 75
  },
  "usageOverTime": [
    {
      "date": "2024-01-01",
      "conversations": 10,
      "messages": 35,
      "users": 5,
      "chatBots": 2
    },
    {
      "date": "2024-01-02",
      "conversations": 15,
      "messages": 50,
      "users": 8,
      "chatBots": 2
    }
  ],
  "topChatBots": [
    {
      "chatBotId": "chatbot-123",
      "chatBotName": "Customer Support Bot",
      "chatBotTitle": "Customer Support Assistant",
      "conversationCount": 450,
      "messageCount": 1800,
      "uniqueUsers": 120,
      "status": "COMPLETED",
      "createdBy": "user@example.com"
    }
  ],
  "topActiveUsers": [
    {
      "email": "user@example.com",
      "conversationCount": 120,
      "messageCount": 450,
      "chatBotsCreated": 15,
      "lastActivityDate": "2024-01-15T10:30:00Z"
    }
  ]
}
```

---

## 2. Get Overall Statistics

Get high-level platform statistics.

### Request

```http
GET /v1/api/dashboard/stats/overall
Authorization: Bearer <JWT_TOKEN>
```

### Response

**Status Code**: `200 OK`

**Response Body**: `OverallStats`

```json
{
  "totalChatBots": 150,
  "totalConversations": 3420,
  "totalMessages": 12500,
  "totalUsers": 85,
  "activeChatBots": 120,
  "activeConversationsToday": 45,
  "totalKnowledgeBases": 200
}
```

### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `totalChatBots` | `number` | Total number of chatbots in the system |
| `totalConversations` | `number` | Total number of conversations |
| `totalMessages` | `number` | Total number of messages across all conversations |
| `totalUsers` | `number` | Total number of users |
| `activeChatBots` | `number` | Number of chatbots with status COMPLETED or TRAINING |
| `activeConversationsToday` | `number` | Number of conversations started today |
| `totalKnowledgeBases` | `number` | Total number of knowledge bases |

---

## 3. Get Chatbot Statistics

Get statistics specific to chatbots.

### Request

```http
GET /v1/api/dashboard/stats/chatbots
Authorization: Bearer <JWT_TOKEN>
```

### Response

**Status Code**: `200 OK`

**Response Body**: `ChatBotStats`

```json
{
  "totalChatBots": 150,
  "chatBotsByStatus": {
    "CREATED": 10,
    "TRAINING": 5,
    "COMPLETED": 120,
    "FAILED": 15
  },
  "chatBotsCreatedToday": 3,
  "chatBotsCreatedThisWeek": 12,
  "chatBotsCreatedThisMonth": 45,
  "averageChatBotsPerUser": 1.76,
  "chatBotsByDataSource": {
    "FILE": 50,
    "WEBSITE": 30,
    "TEXT": 40,
    "NONE": 30
  }
}
```

### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `totalChatBots` | `number` | Total number of chatbots |
| `chatBotsByStatus` | `object` | Count of chatbots grouped by status (CREATED, TRAINING, COMPLETED, FAILED) |
| `chatBotsCreatedToday` | `number` | Number of chatbots created today |
| `chatBotsCreatedThisWeek` | `number` | Number of chatbots created in the last 7 days |
| `chatBotsCreatedThisMonth` | `number` | Number of chatbots created in the last 30 days |
| `averageChatBotsPerUser` | `number` | Average number of chatbots created per user |
| `chatBotsByDataSource` | `object` | Count of chatbots grouped by data source type |

---

## 4. Get Conversation Statistics

Get statistics about conversations.

### Request

```http
GET /v1/api/dashboard/stats/conversations
Authorization: Bearer <JWT_TOKEN>
```

### Response

**Status Code**: `200 OK`

**Response Body**: `ConversationStats`

```json
{
  "totalConversations": 3420,
  "conversationsToday": 45,
  "conversationsThisWeek": 320,
  "conversationsThisMonth": 1200,
  "averageMessagesPerConversation": 3.65,
  "longestConversation": 45,
  "conversationsByMode": {
    "chat": 3000,
    "embedded": 420
  },
  "anonymousConversations": 500,
  "authenticatedConversations": 2920
}
```

### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `totalConversations` | `number` | Total number of conversations |
| `conversationsToday` | `number` | Number of conversations started today |
| `conversationsThisWeek` | `number` | Number of conversations started in the last 7 days |
| `conversationsThisMonth` | `number` | Number of conversations started in the last 30 days |
| `averageMessagesPerConversation` | `number` | Average number of messages per conversation |
| `longestConversation` | `number` | Maximum number of messages in a single conversation |
| `conversationsByMode` | `object` | Count of conversations grouped by mode (chat, embedded, etc.) |
| `anonymousConversations` | `number` | Number of conversations from anonymous users |
| `authenticatedConversations` | `number` | Number of conversations from authenticated users |

---

## 5. Get Usage Statistics

Get usage and engagement statistics.

### Request

```http
GET /v1/api/dashboard/stats/usage
Authorization: Bearer <JWT_TOKEN>
```

### Response

**Status Code**: `200 OK`

**Response Body**: `UsageStats`

```json
{
  "totalMessages": 12500,
  "messagesToday": 180,
  "messagesThisWeek": 1200,
  "messagesThisMonth": 4500,
  "averageMessagesPerDay": 150.5,
  "peakMessagesInDay": 450,
  "messagesByHour": {
    "0": 50,
    "1": 30,
    "2": 20,
    "9": 200,
    "10": 350,
    "11": 400,
    "14": 380,
    "15": 420,
    "16": 400,
    "17": 350,
    "18": 280
  },
  "totalUsers": 85,
  "activeUsersToday": 25,
  "activeUsersThisWeek": 60,
  "activeUsersThisMonth": 75
}
```

### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `totalMessages` | `number` | Total number of messages |
| `messagesToday` | `number` | Number of messages sent today |
| `messagesThisWeek` | `number` | Number of messages sent in the last 7 days |
| `messagesThisMonth` | `number` | Number of messages sent in the last 30 days |
| `averageMessagesPerDay` | `number` | Average number of messages per day (calculated over last 30 days) |
| `peakMessagesInDay` | `number` | Maximum number of messages in a single day |
| `messagesByHour` | `object` | Count of messages grouped by hour of day (0-23) |
| `totalUsers` | `number` | Total number of users |
| `activeUsersToday` | `number` | Number of unique users active today |
| `activeUsersThisWeek` | `number` | Number of unique users active in the last 7 days |
| `activeUsersThisMonth` | `number` | Number of unique users active in the last 30 days |

---

## 6. Get Usage Over Time (Time Series)

Get time series data for trend analysis.

### Request

```http
GET /v1/api/dashboard/stats/usage-over-time?days=30
Authorization: Bearer <JWT_TOKEN>
```

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `days` | `number` | No | `30` | Number of days to retrieve data for |

### Response

**Status Code**: `200 OK`

**Response Body**: `Array<TimeSeriesData>`

```json
[
  {
    "date": "2024-01-01",
    "conversations": 10,
    "messages": 35,
    "users": 5,
    "chatBots": 2
  },
  {
    "date": "2024-01-02",
    "conversations": 15,
    "messages": 50,
    "users": 8,
    "chatBots": 2
  },
  {
    "date": "2024-01-03",
    "conversations": 20,
    "messages": 65,
    "users": 10,
    "chatBots": 3
  }
]
```

### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `date` | `string` | Date in YYYY-MM-DD format |
| `conversations` | `number` | Number of conversations on this date |
| `messages` | `number` | Number of messages on this date |
| `users` | `number` | Number of active users on this date |
| `chatBots` | `number` | Number of chatbots created on this date |

**Note**: The array is sorted by date in ascending order. Dates with no activity will have zero values.

---

## 7. Get Top Chatbots

Get the most active chatbots ranked by conversation count.

### Request

```http
GET /v1/api/dashboard/top/chatbots?limit=10
Authorization: Bearer <JWT_TOKEN>
```

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `limit` | `number` | No | `10` | Maximum number of chatbots to return |

### Response

**Status Code**: `200 OK`

**Response Body**: `Array<TopChatBot>`

```json
[
  {
    "chatBotId": "chatbot-123",
    "chatBotName": "Customer Support Bot",
    "chatBotTitle": "Customer Support Assistant",
    "conversationCount": 450,
    "messageCount": 1800,
    "uniqueUsers": 120,
    "status": "COMPLETED",
    "createdBy": "user@example.com"
  },
  {
    "chatBotId": "chatbot-456",
    "chatBotName": "Sales Assistant",
    "chatBotTitle": "Sales Bot",
    "conversationCount": 320,
    "messageCount": 1100,
    "uniqueUsers": 85,
    "status": "COMPLETED",
    "createdBy": "admin@example.com"
  }
]
```

### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `chatBotId` | `string` | Unique identifier of the chatbot |
| `chatBotName` | `string` | Name of the chatbot |
| `chatBotTitle` | `string` | Title/display name of the chatbot |
| `conversationCount` | `number` | Total number of conversations for this chatbot |
| `messageCount` | `number` | Total number of messages for this chatbot |
| `uniqueUsers` | `number` | Number of unique users who interacted with this chatbot |
| `status` | `string` | Current status of the chatbot (CREATED, TRAINING, COMPLETED, FAILED) |
| `createdBy` | `string` | Email of the user who created the chatbot |

**Note**: Results are sorted by `conversationCount` in descending order.

---

## 8. Get Top Active Users

Get the most active users ranked by conversation count.

### Request

```http
GET /v1/api/dashboard/top/users?limit=10
Authorization: Bearer <JWT_TOKEN>
```

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `limit` | `number` | No | `10` | Maximum number of users to return |

### Response

**Status Code**: `200 OK`

**Response Body**: `Array<UserActivity>`

```json
[
  {
    "email": "user@example.com",
    "conversationCount": 120,
    "messageCount": 450,
    "chatBotsCreated": 15,
    "lastActivityDate": "2024-01-15T10:30:00Z"
  },
  {
    "email": "admin@example.com",
    "conversationCount": 95,
    "messageCount": 380,
    "chatBotsCreated": 8,
    "lastActivityDate": "2024-01-14T15:20:00Z"
  }
]
```

### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `email` | `string` | Email address of the user |
| `conversationCount` | `number` | Total number of conversations initiated by this user |
| `messageCount` | `number` | Total number of messages sent by this user |
| `chatBotsCreated` | `number` | Number of chatbots created by this user |
| `lastActivityDate` | `string` | ISO 8601 timestamp of the user's last activity |

**Note**: Results are sorted by `conversationCount` in descending order.

---

## Error Responses

All endpoints may return the following error responses:

### 401 Unauthorized

```json
{
  "error": "Unauthorized",
  "message": "Authentication required"
}
```

### 500 Internal Server Error

```json
{
  "error": "Internal Server Error",
  "message": "An error occurred while processing the request"
}
```

**Note**: On error, the response body may be empty (`null`) depending on the endpoint.

---

## TypeScript Type Definitions

For frontend TypeScript projects, here are the type definitions:

```typescript
// Overall Stats
interface OverallStats {
  totalChatBots: number;
  totalConversations: number;
  totalMessages: number;
  totalUsers: number;
  activeChatBots: number;
  activeConversationsToday: number;
  totalKnowledgeBases: number;
}

// Chatbot Stats
interface ChatBotStats {
  totalChatBots: number;
  chatBotsByStatus: Record<string, number>;
  chatBotsCreatedToday: number;
  chatBotsCreatedThisWeek: number;
  chatBotsCreatedThisMonth: number;
  averageChatBotsPerUser: number;
  chatBotsByDataSource: Record<string, number>;
}

// Conversation Stats
interface ConversationStats {
  totalConversations: number;
  conversationsToday: number;
  conversationsThisWeek: number;
  conversationsThisMonth: number;
  averageMessagesPerConversation: number;
  longestConversation: number;
  conversationsByMode: Record<string, number>;
  anonymousConversations: number;
  authenticatedConversations: number;
}

// Usage Stats
interface UsageStats {
  totalMessages: number;
  messagesToday: number;
  messagesThisWeek: number;
  messagesThisMonth: number;
  averageMessagesPerDay: number;
  peakMessagesInDay: number;
  messagesByHour: Record<string, number>;
  totalUsers: number;
  activeUsersToday: number;
  activeUsersThisWeek: number;
  activeUsersThisMonth: number;
}

// Time Series Data
interface TimeSeriesData {
  date: string;
  conversations: number;
  messages: number;
  users: number;
  chatBots: number;
}

// Top Chatbot
interface TopChatBot {
  chatBotId: string;
  chatBotName: string;
  chatBotTitle: string;
  conversationCount: number;
  messageCount: number;
  uniqueUsers: number;
  status: string;
  createdBy: string;
}

// User Activity
interface UserActivity {
  email: string;
  conversationCount: number;
  messageCount: number;
  chatBotsCreated: number;
  lastActivityDate: string;
}

// Complete Dashboard Response
interface DashboardStatsResponse {
  overallStats: OverallStats;
  chatBotStats: ChatBotStats;
  conversationStats: ConversationStats;
  usageStats: UsageStats;
  usageOverTime: TimeSeriesData[];
  topChatBots: TopChatBot[];
  topActiveUsers: UserActivity[];
}
```

---

## Usage Examples

### Fetch All Dashboard Stats

```javascript
const response = await fetch('/v1/api/dashboard/stats', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
});

const data = await response.json();
console.log(data.overallStats);
console.log(data.topChatBots);
```

### Fetch Usage Over Last 7 Days

```javascript
const response = await fetch('/v1/api/dashboard/stats/usage-over-time?days=7', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
});

const timeSeriesData = await response.json();
// Use for charting libraries like Chart.js, Recharts, etc.
```

### Fetch Top 5 Chatbots

```javascript
const response = await fetch('/v1/api/dashboard/top/chatbots?limit=5', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
});

const topChatBots = await response.json();
```

---

## Notes

1. **Authentication**: All endpoints require a valid JWT token in the Authorization header.
2. **CORS**: CORS is enabled for all origins, so you can call these endpoints from any frontend domain.
3. **Date Formats**: Dates are returned in ISO 8601 format (YYYY-MM-DD for dates, full ISO 8601 for timestamps).
4. **Time Zones**: All dates and times are in UTC.
5. **Null Values**: Some fields may be `null` if no data is available.
6. **Performance**: The `/stats` endpoint aggregates all data, so it may take longer to respond. Consider using individual endpoints for better performance.
7. **Rate Limiting**: Be mindful of rate limits when polling these endpoints frequently.

---

## Support

For questions or issues, please contact the backend team or refer to the main API documentation.

