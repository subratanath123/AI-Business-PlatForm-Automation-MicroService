# Chatbot Reply API - Quick Reference

**Endpoint:** `POST /v1/api/n8n/authenticated/chatbot-reply`  
**Date:** February 11, 2026  
**Status:** ✅ Ready for Testing

---

## 🚀 Quick Start

### Request Example

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv_123",
    "chatbotId": "chatbot_456",
    "message": "Thank you for contacting us!",
    "role": "assistant"
  }'
```

### Success Response (200)

```json
{
  "success": true,
  "messageId": "msg_1707385649123_a1b2c3d4",
  "conversationId": "conv_123",
  "chatbotId": "chatbot_456",
  "message": "Thank you for contacting us!",
  "role": "assistant",
  "timestamp": 1707385649000,
  "savedToDatabase": true
}
```

---

## 📦 Request Fields

| Field | Required | Type | Validation | Example |
|-------|----------|------|------------|---------|
| `conversationId` | ✅ Yes | string | Non-empty, must exist | "conv_123" |
| `chatbotId` | ✅ Yes | string | Non-empty, must match conversation | "chatbot_456" |
| `message` | ✅ Yes | string | 1-10000 chars, trimmed | "Your reply text" |
| `role` | ✅ Yes | string | Must be "assistant" | "assistant" |

---

## 🔐 Authentication

**Header:** `Authorization: Bearer {JWT_TOKEN}`

- Token issued by Clerk
- User must own the chatbot
- Token must not be expired

---

## ❌ Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `UNAUTHORIZED` | 401 | Invalid/expired token |
| `MISSING_FIELD` | 400 | Required field missing |
| `EMPTY_MESSAGE` | 400 | Message is empty |
| `MESSAGE_TOO_LONG` | 400 | Message > 10000 chars |
| `INVALID_ROLE` | 400 | Role is not "assistant" |
| `INSUFFICIENT_PERMISSIONS` | 403 | User doesn't own chatbot |
| `CHATBOT_NOT_FOUND` | 404 | Chatbot doesn't exist |
| `CONVERSATION_NOT_FOUND` | 404 | Conversation doesn't exist |
| `DATABASE_ERROR` | 500 | Database save failed |

---

## 📁 Files Modified/Created

### Created
- `dto/ChatbotReplyRequest.java` - Request DTO
- `dto/ChatbotReplyResponse.java` - Response DTO
- `TEST_CHATBOT_REPLY_API.md` - Test cases
- `CHATBOT_REPLY_API_IMPLEMENTATION.md` - Full documentation

### Modified
- `dto/UserChatHistory.java` - Added fields (chatbotId, role, senderType, adminUserId, status)
- `service/aichatbot/ChatBotService.java` - Added saveAdminReply() and verifyConversationOwnership()
- `controller/AuthenticatedUserChatN8NController.java` - Added chatbotReply() endpoint

---

## 🗄️ Database Record

**Collection:** `n8n_chat_session_histories`

```json
{
  "_id": "msg_1707385649123_a1b2c3d4",
  "conversationid": "conv_123",
  "chatbotId": "chatbot_456",
  "email": "admin@example.com",
  "aiMessage": "Thank you for contacting us!",
  "userMessage": null,
  "role": "assistant",
  "senderType": "admin_reply",
  "adminUserId": "admin@example.com",
  "status": "sent",
  "createdAt": ISODate("2026-02-11T10:30:00Z"),
  "mode": "admin",
  "isAnonymous": false
}
```

---

## 🧪 Test Command

```bash
# Replace these values with your actual data
JWT_TOKEN="your_jwt_token_here"
CONVERSATION_ID="your_conversation_id"
CHATBOT_ID="your_chatbot_id"
MESSAGE="Your reply message here"

curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"conversationId\": \"${CONVERSATION_ID}\",
    \"chatbotId\": \"${CHATBOT_ID}\",
    \"message\": \"${MESSAGE}\",
    \"role\": \"assistant\"
  }"
```

---

## 🔍 Verify in MongoDB

```javascript
// Find the latest admin reply
db.n8n_chat_session_histories.find({
  conversationid: "conv_123",
  senderType: "admin_reply"
}).sort({ createdAt: -1 }).limit(1).pretty();
```

---

## ✅ Validation Checklist

Before calling the endpoint:
- [ ] JWT token is valid and not expired
- [ ] User owns the chatbot
- [ ] Conversation exists in database
- [ ] Conversation belongs to the specified chatbot
- [ ] Message is between 1 and 10,000 characters
- [ ] Role is set to "assistant"

---

## 📊 Response Time Targets

- **Total:** < 500ms
- **Database Save:** < 200ms
- **Token Verification:** < 50ms

---

## 🐛 Common Issues

### 401 Unauthorized
→ Obtain fresh JWT token from Clerk

### 403 Forbidden
→ Ensure user owns the chatbot

### 404 Conversation Not Found
→ Verify conversationId and chatbotId match

### 500 Database Error
→ Check MongoDB connection and logs

---

## 📝 Frontend Integration

```typescript
const sendAdminReply = async (
  conversationId: string,
  chatbotId: string,
  message: string,
  jwtToken: string
) => {
  const response = await fetch(
    'http://localhost:8080/v1/api/n8n/authenticated/chatbot-reply',
    {
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
    }
  );
  
  return await response.json();
};
```

---

## 📚 Full Documentation

For complete details, see:
- **Implementation:** `CHATBOT_REPLY_API_IMPLEMENTATION.md`
- **Test Cases:** `TEST_CHATBOT_REPLY_API.md`
- **Original Requirements:** See your requirements document

---

**Status:** ✅ Implementation Complete  
**Ready for:** Integration Testing & Production Deployment

