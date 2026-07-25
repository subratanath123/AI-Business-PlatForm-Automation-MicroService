# Chatbot Reply API - Test Cases

This document provides test cases and example curl commands to test the newly implemented chatbot reply API endpoint.

## Endpoint

```
POST http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply
```

## Prerequisites

Before testing, you need:
1. A valid JWT token from Clerk authentication
2. An existing chatbot ID that you own
3. An existing conversation ID in the database
4. The server running on port 8080

---

## Test Case 1: Valid Request (Success Case)

### Request

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "your_conversation_id",
    "chatbotId": "your_chatbot_id",
    "message": "Thank you for your inquiry! How can I help you further?",
    "role": "assistant"
  }'
```

### Expected Response (200 OK)

```json
{
  "success": true,
  "messageId": "msg_1707385649123_a1b2c3d4",
  "conversationId": "your_conversation_id",
  "chatbotId": "your_chatbot_id",
  "message": "Thank you for your inquiry! How can I help you further?",
  "role": "assistant",
  "timestamp": 1707385649000,
  "savedToDatabase": true,
  "error": null
}
```

---

## Test Case 2: Missing Authorization Token

### Request

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "your_conversation_id",
    "chatbotId": "your_chatbot_id",
    "message": "Test message",
    "role": "assistant"
  }'
```

### Expected Response (401 Unauthorized)

```json
{
  "success": false,
  "messageId": null,
  "conversationId": null,
  "chatbotId": null,
  "message": "Invalid or expired token",
  "role": null,
  "timestamp": 1707385649000,
  "savedToDatabase": false,
  "error": "UNAUTHORIZED"
}
```

---

## Test Case 3: Empty Message

### Request

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "your_conversation_id",
    "chatbotId": "your_chatbot_id",
    "message": "",
    "role": "assistant"
  }'
```

### Expected Response (400 Bad Request)

```json
{
  "success": false,
  "messageId": null,
  "conversationId": null,
  "chatbotId": null,
  "message": "Message cannot be empty",
  "role": null,
  "timestamp": 1707385649000,
  "savedToDatabase": false,
  "error": "EMPTY_MESSAGE"
}
```

---

## Test Case 4: Message Too Long

### Request

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "your_conversation_id",
    "chatbotId": "your_chatbot_id",
    "message": "'"$(python3 -c "print('x' * 10001)")"'",
    "role": "assistant"
  }'
```

### Expected Response (400 Bad Request)

```json
{
  "success": false,
  "messageId": null,
  "conversationId": null,
  "chatbotId": null,
  "message": "Message exceeds maximum length of 10000 characters",
  "role": null,
  "timestamp": 1707385649000,
  "savedToDatabase": false,
  "error": "MESSAGE_TOO_LONG"
}
```

---

## Test Case 5: Invalid Role (Not "assistant")

### Request

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "your_conversation_id",
    "chatbotId": "your_chatbot_id",
    "message": "Test message",
    "role": "user"
  }'
```

### Expected Response (400 Bad Request)

```json
{
  "success": false,
  "messageId": null,
  "conversationId": null,
  "chatbotId": null,
  "message": "Role must be 'assistant'",
  "role": null,
  "timestamp": 1707385649000,
  "savedToDatabase": false,
  "error": "INVALID_ROLE"
}
```

---

## Test Case 6: Conversation Not Found

### Request

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "nonexistent_conversation_id",
    "chatbotId": "your_chatbot_id",
    "message": "Test message",
    "role": "assistant"
  }'
```

### Expected Response (404 Not Found)

```json
{
  "success": false,
  "messageId": null,
  "conversationId": null,
  "chatbotId": null,
  "message": "Conversation not found: nonexistent_conversation_id",
  "role": null,
  "timestamp": 1707385649000,
  "savedToDatabase": false,
  "error": "CONVERSATION_NOT_FOUND"
}
```

---

## Test Case 7: Chatbot Not Found

### Request

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "your_conversation_id",
    "chatbotId": "nonexistent_chatbot_id",
    "message": "Test message",
    "role": "assistant"
  }'
```

### Expected Response (404 Not Found)

```json
{
  "success": false,
  "messageId": null,
  "conversationId": null,
  "chatbotId": null,
  "message": "Chatbot not found: nonexistent_chatbot_id",
  "role": null,
  "timestamp": 1707385649000,
  "savedToDatabase": false,
  "error": "CHATBOT_NOT_FOUND"
}
```

---

## Test Case 8: Chatbot ID Mismatch (Conversation belongs to different chatbot)

### Request

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conversation_from_other_chatbot",
    "chatbotId": "your_chatbot_id",
    "message": "Test message",
    "role": "assistant"
  }'
```

### Expected Response (404 Not Found)

```json
{
  "success": false,
  "messageId": null,
  "conversationId": null,
  "chatbotId": null,
  "message": "Conversation not found: conversation_from_other_chatbot",
  "role": null,
  "timestamp": 1707385649000,
  "savedToDatabase": false,
  "error": "CONVERSATION_NOT_FOUND"
}
```

---

## Test Case 9: Insufficient Permissions (User doesn't own the chatbot)

### Request

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Authorization: Bearer JWT_TOKEN_OF_DIFFERENT_USER" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "your_conversation_id",
    "chatbotId": "chatbot_owned_by_someone_else",
    "message": "Test message",
    "role": "assistant"
  }'
```

### Expected Response (403 Forbidden)

```json
{
  "success": false,
  "messageId": null,
  "conversationId": null,
  "chatbotId": null,
  "message": "You do not have permission to reply in this conversation",
  "role": null,
  "timestamp": 1707385649000,
  "savedToDatabase": false,
  "error": "INSUFFICIENT_PERMISSIONS"
}
```

---

## Test Case 10: Special Characters in Message

### Request

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "your_conversation_id",
    "chatbotId": "your_chatbot_id",
    "message": "Hello! 👋 Here'\''s some information:\n- Point 1\n- Point 2\n\nSpecial chars: @#$%^&*()",
    "role": "assistant"
  }'
```

### Expected Response (200 OK)

Should successfully save the message with special characters and newlines preserved.

---

## Verification Steps

After sending a successful reply, verify the data in MongoDB:

### Query MongoDB

```javascript
use your_database_name;

db.n8n_chat_session_histories.find({
  conversationid: "your_conversation_id",
  senderType: "admin_reply"
}).sort({ createdAt: -1 }).limit(1);
```

### Expected Document Structure

```json
{
  "_id": "msg_1707385649123_a1b2c3d4",
  "conversationid": "your_conversation_id",
  "chatbotId": "your_chatbot_id",
  "email": "admin@example.com",
  "aiMessage": "Thank you for your inquiry! How can I help you further?",
  "userMessage": null,
  "role": "assistant",
  "senderType": "admin_reply",
  "adminUserId": "admin@example.com",
  "status": "sent",
  "createdAt": ISODate("2026-02-11T10:30:00.000Z"),
  "mode": "admin",
  "isAnonymous": false
}
```

---

## Performance Testing

### Response Time Requirements

- Target: < 500ms for successful requests
- Database Save: Should complete within 200ms
- Token Verification: Should complete within 50ms

### Load Test (Using Apache Bench)

```bash
ab -n 100 -c 10 -p request_payload.json -T application/json \
   -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
   http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply
```

---

## Integration with Frontend

The frontend in `app/ai-chatbots/[id]/page.tsx` should call this endpoint when the admin sends a reply from the conversation history panel:

```typescript
const sendAdminReply = async (conversationId: string, chatbotId: string, message: string) => {
  const response = await fetch('http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${jwtToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      conversationId,
      chatbotId,
      message,
      role: 'assistant'
    })
  });
  
  const data = await response.json();
  
  if (data.success) {
    console.log('Reply sent successfully:', data.messageId);
    // Refresh conversation history to show new message
  } else {
    console.error('Failed to send reply:', data.message);
  }
};
```

---

## Troubleshooting

### Issue: 401 Unauthorized
- **Cause:** Invalid or expired JWT token
- **Solution:** Obtain a fresh token from Clerk authentication

### Issue: 403 Forbidden
- **Cause:** User doesn't own the chatbot
- **Solution:** Ensure you're using the token of the chatbot owner

### Issue: 404 Conversation Not Found
- **Cause:** Conversation doesn't exist or doesn't belong to the chatbot
- **Solution:** Verify conversation ID and chatbot ID in the database

### Issue: 500 Database Error
- **Cause:** MongoDB connection issue or database constraint violation
- **Solution:** Check MongoDB logs and connection status

---

## Security Checklist

- ✅ JWT token validation
- ✅ User ownership verification
- ✅ Conversation existence check
- ✅ Chatbot-conversation relationship verification
- ✅ Input validation (message length, role)
- ✅ SQL injection prevention (using MongoDB ORM)
- ✅ XSS prevention (data sanitization)
- ✅ Rate limiting (should be added at API gateway level)

---

## Next Steps

1. Test all cases with actual data
2. Monitor response times in production
3. Set up logging and monitoring alerts
4. Add rate limiting if needed
5. Document API in OpenAPI/Swagger format
6. Update frontend to use this endpoint

---

**Status:** Ready for Testing  
**Date:** February 11, 2026  
**Version:** 1.0

