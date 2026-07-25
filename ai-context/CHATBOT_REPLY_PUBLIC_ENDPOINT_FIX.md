# Chatbot Reply - Public Endpoint Fix

**Date:** February 11, 2026  
**Issue:** Admin replies not visible via public endpoint  
**Status:** ✅ Fixed

---

## 🐛 Problem Description

After fixing the admin reply visibility issue for authenticated users, admin replies were **still not showing** when accessed through the **public endpoint**:

```
GET http://localhost:8080/v1/api/public/chatHistory/{chatbotId}/{conversationId}
```

### Root Cause

The `getChatHistory()` method had this logic:

```java
if (!AuthUtils.isAdmin()) {
    criteria = criteria.and("email").is(AuthUtils.getEmail());
}
```

**The Issue:**
- For **public/unauthenticated requests**, `AuthUtils.getEmail()` returns `null`
- The condition `!AuthUtils.isAdmin()` evaluates to `true` (no auth = not admin)
- So it adds filter: `email = null`
- This matches **no messages** (because all messages have an email value)

**Result:** Public endpoint returned empty array `[]` even though messages existed ❌

---

## ✅ Solution

Modified `getChatHistory()` to check if the user is authenticated before applying email filter:

### Before (Broken)

```java
@Transactional
public List<UserChatHistory> getChatHistory(String chatbotId, String conversationId) {
    log.info("Getting All chats of the conversation: {}", conversationId);

    Criteria criteria = Criteria.where("chatbotId").is(chatbotId)
            .and("conversationid").is(conversationId);

    if (!AuthUtils.isAdmin()) {
        criteria = criteria.and("email").is(AuthUtils.getEmail()); // ❌ This is null for public requests!
    }

    return mongoTemplate.find(new Query().addCriteria(criteria), UserChatHistory.class);
}
```

### After (Fixed)

```java
@Transactional
public List<UserChatHistory> getChatHistory(String chatbotId, String conversationId) {
    log.info("Getting All chats of the conversation: {}", conversationId);

    Criteria criteria = Criteria.where("chatbotId").is(chatbotId)
            .and("conversationid").is(conversationId);

    // Only filter by email if user is authenticated and not an admin
    String userEmail = AuthUtils.getEmail();
    if (userEmail != null && !AuthUtils.isAdmin()) {
        criteria = criteria.and("email").is(userEmail);
        log.debug("Filtering chat history by email: {}", userEmail);
    } else if (userEmail == null) {
        log.debug("Public/unauthenticated request - returning all messages in conversation");
    } else {
        log.debug("Admin request - returning all messages in conversation");
    }

    return mongoTemplate.find(new Query().addCriteria(criteria), UserChatHistory.class);
}
```

---

## 🔍 How It Works Now

### Case 1: Public/Unauthenticated Request

```
GET /v1/api/public/chatHistory/{chatbotId}/{conversationId}
```

**Flow:**
1. `AuthUtils.getEmail()` returns `null`
2. Condition: `userEmail != null` → `false`
3. **No email filter applied**
4. Returns **all messages** in the conversation ✅

**Query:**
```java
Criteria.where("chatbotId").is(chatbotId)
    .and("conversationid").is(conversationId)
// No email filter - returns all messages
```

---

### Case 2: Authenticated Non-Admin Request

```
POST /v1/api/n8n/authenticated/chatHistory/{chatbotId}/{conversationId}
Authorization: Bearer {user_jwt_token}
```

**Flow:**
1. `AuthUtils.getEmail()` returns `"user@example.com"`
2. `AuthUtils.isAdmin()` returns `false`
3. Condition: `userEmail != null && !isAdmin()` → `true`
4. **Email filter applied**
5. Returns only messages for that user ✅

**Query:**
```java
Criteria.where("chatbotId").is(chatbotId)
    .and("conversationid").is(conversationId)
    .and("email").is("user@example.com")
// Filter by user email - returns only user's messages
```

---

### Case 3: Admin Request

```
POST /v1/api/n8n/authenticated/chatHistory/{chatbotId}/{conversationId}
Authorization: Bearer {admin_jwt_token}
```

**Flow:**
1. `AuthUtils.getEmail()` returns `"admin@company.com"`
2. `AuthUtils.isAdmin()` returns `true`
3. Condition: `userEmail != null && !isAdmin()` → `false`
4. **No email filter applied**
5. Returns **all messages** in the conversation ✅

**Query:**
```java
Criteria.where("chatbotId").is(chatbotId)
    .and("conversationid").is(conversationId)
// No email filter - admin sees all messages
```

---

## 📊 Comparison Table

| Scenario | userEmail | isAdmin() | Email Filter? | Returns |
|----------|-----------|-----------|---------------|---------|
| **Public endpoint** | `null` | `false` | ❌ No | All messages ✅ |
| **Authenticated user** | `"user@example.com"` | `false` | ✅ Yes | User's messages only ✅ |
| **Admin user** | `"admin@company.com"` | `true` | ❌ No | All messages ✅ |

---

## 🧪 Testing

### Test 1: Public Endpoint (No Auth)

```bash
curl -X GET "http://localhost:8080/v1/api/public/chatHistory/698576e4d5fd040c84aed7d8/session_1770743703337_lax2egqzx"
```

**Expected Response:**
```json
[
  {
    "id": "msg_1",
    "conversationid": "session_1770743703337_lax2egqzx",
    "chatbotId": "698576e4d5fd040c84aed7d8",
    "email": "user@example.com",
    "userMessage": "Hello",
    "aiMessage": "Hi there!",
    "role": "user",
    "senderType": "user",
    "createdAt": "2026-02-11T10:00:00Z"
  },
  {
    "id": "msg_2",
    "conversationid": "session_1770743703337_lax2egqzx",
    "chatbotId": "698576e4d5fd040c84aed7d8",
    "email": "user@example.com",
    "aiMessage": "Thanks for reaching out!",
    "userMessage": null,
    "role": "assistant",
    "senderType": "admin_reply",
    "adminUserId": "admin@company.com",
    "createdAt": "2026-02-11T10:05:00Z"
  }
]
```

✅ **Both user messages and admin replies are returned**

---

### Test 2: Authenticated User Endpoint

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatHistory/698576e4d5fd040c84aed7d8/session_1770743703337_lax2egqzx" \
  -H "Authorization: Bearer USER_JWT_TOKEN"
```

**Expected Response:**
```json
[
  {
    "email": "user@example.com",
    "userMessage": "Hello",
    "aiMessage": "Hi there!",
    ...
  },
  {
    "email": "user@example.com",
    "aiMessage": "Thanks for reaching out!",
    "senderType": "admin_reply",
    ...
  }
]
```

✅ **User sees their messages and admin replies** (because admin replies now use user's email)

---

### Test 3: Admin User Endpoint

```bash
curl -X POST "http://localhost:8080/v1/api/n8n/authenticated/chatHistory/698576e4d5fd040c84aed7d8/session_1770743703337_lax2egqzx" \
  -H "Authorization: Bearer ADMIN_JWT_TOKEN"
```

**Expected Response:**
```json
[
  {
    "email": "user@example.com",
    ...
  },
  {
    "email": "user@example.com",
    "senderType": "admin_reply",
    ...
  },
  {
    "email": "different_user@example.com",
    ...
  }
]
```

✅ **Admin sees ALL messages from ALL users in the conversation**

---

## 🔒 Security Considerations

### Is This Secure?

**Question:** Should the public endpoint return all messages without authentication?

**Answer:** This depends on your use case:

#### ✅ **Secure if:**
- The conversation ID is a unique, unguessable session ID (like `session_1770743703337_lax2egqzx`)
- Session IDs are treated as access tokens (knowing the session ID = permission to view)
- This is common for chat widgets where users need to see their conversation history without logging in

#### ⚠️ **Security Risk if:**
- Conversation IDs are sequential (e.g., `1`, `2`, `3`)
- Conversation IDs are easily guessable
- Messages contain sensitive personal information

### Recommendation

If security is a concern, you can:

1. **Add session validation** - Verify the request comes from the same IP/browser
2. **Use signed session IDs** - Make session IDs cryptographically signed
3. **Require authentication** - Remove the public endpoint and require JWT token
4. **Add rate limiting** - Prevent brute-force guessing of session IDs

---

## 📝 Summary of Both Fixes

### Fix #1: User Email in Admin Replies
**Problem:** Admin replies used admin's email  
**Solution:** Use conversation user's email instead  
**Impact:** Authenticated users can now see admin replies

### Fix #2: Public Endpoint Email Filter
**Problem:** Public endpoint filtered by `email = null`  
**Solution:** Skip email filter for unauthenticated requests  
**Impact:** Public endpoint returns all messages (including admin replies)

### Combined Effect

| Request Type | Email Filter | Admin Replies Visible? |
|--------------|--------------|------------------------|
| Public (no auth) | ❌ None | ✅ Yes (Fix #2) |
| Authenticated user | ✅ User's email | ✅ Yes (Fix #1) |
| Admin user | ❌ None | ✅ Yes (already worked) |

---

## 🎯 Files Modified

- **`ChatBotService.java`**
  - Updated `getChatHistory()` method
  - Added null check for `userEmail` before applying filter
  - Added debug logging for different scenarios

---

## ✅ Testing Checklist

- [x] Public endpoint returns admin replies
- [x] Authenticated user sees admin replies in their conversation
- [x] Admin user sees all messages
- [x] Email filter only applied when user is authenticated and not admin
- [x] No linter errors
- [x] Logging added for debugging

---

**Status:** ✅ Fixed and Verified  
**Date:** February 11, 2026  
**Impact:** Admin replies now visible on both public and authenticated endpoints

