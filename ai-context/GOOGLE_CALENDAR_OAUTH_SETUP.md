# Google Calendar OAuth Integration - Setup Guide

## 🎯 Overview

This implementation provides secure Google Calendar OAuth integration for chatbots, allowing users to connect their Google Calendar accounts and manage tokens securely.

## 📋 Features

- **Secure Token Storage**: OAuth tokens are encrypted using AES-256-GCM encryption
- **Automatic Token Refresh**: Expired access tokens are automatically refreshed using refresh tokens
- **Chatbot Ownership Verification**: Users can only manage tokens for chatbots they own
- **5 REST Endpoints**: Complete CRUD operations for OAuth token management
- **MongoDB Integration**: Tokens stored in MongoDB with proper indexing
- **Clerk Authentication**: Integrated with existing Clerk JWT authentication

## 🔧 Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│                   Google Calendar OAuth                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Controller: GoogleCalendarOAuthController                   │
│     └─> Handles HTTP requests and responses                  │
│                                                               │
│  Services:                                                    │
│     ├─> GoogleOAuthService (token refresh, revoke)          │
│     └─> ChatbotOwnershipService (authorization)             │
│                                                               │
│  Utilities:                                                   │
│     └─> EncryptionUtils (AES-256-GCM encryption)            │
│                                                               │
│  Data Layer:                                                  │
│     ├─> GoogleCalendarToken (Entity)                        │
│     └─> GoogleCalendarTokenDao (Repository)                 │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

## 🚀 Quick Setup

### 1. Environment Variables

Add these to your environment (`.env` or system environment):

```bash
# Google OAuth Credentials
GOOGLE_CLIENT_ID=your_client_id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your_client_secret

# Encryption Key (32 bytes / 256 bits in hex format)
ENCRYPTION_KEY=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

### 2. Generate Encryption Key

To generate a secure encryption key, run this Java code once:

```java
import net.ai.chatbot.utils.EncryptionUtils;

public class GenerateKey {
    public static void main(String[] args) {
        String key = EncryptionUtils.generateEncryptionKey();
        System.out.println("Generated Encryption Key: " + key);
    }
}
```

Or use this command:
```bash
openssl rand -hex 32
```

### 3. Google OAuth Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the **Google Calendar API**
4. Go to **Credentials** → **Create Credentials** → **OAuth 2.0 Client ID**
5. Configure the OAuth consent screen
6. Add authorized redirect URIs for your frontend
7. Copy the **Client ID** and **Client Secret**

### 4. MongoDB Index Creation

The application automatically creates indexes, but you can manually create them:

```javascript
db.google_calendar_tokens.createIndex({ "chatbotId": 1 }, { unique: true });
db.google_calendar_tokens.createIndex({ "expiresAt": 1 });
db.google_calendar_tokens.createIndex({ "createdBy": 1 });
```

## 📡 API Endpoints

### Base URL
```
/v1/api/chatbot/google-calendar
```

### Authentication
All endpoints require Clerk JWT authentication via `Authorization: Bearer <token>` header.

---

### 1. Store OAuth Tokens

**Endpoint:** `POST /v1/api/chatbot/google-calendar/{chatbotId}`

**Description:** Store Google Calendar OAuth tokens for a chatbot.

**Headers:**
```
Authorization: Bearer <CLERK_JWT_TOKEN>
Content-Type: application/json
```

**Request Body:**
```json
{
  "accessToken": "ya29.a0AfH6SMBx...",
  "refreshToken": "1//0gw8K7_z...",
  "expiresIn": 3600,
  "tokenType": "Bearer"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Google Calendar tokens stored successfully",
  "chatbotId": "698576e4d5fd040c84aed7d8"
}
```

**Error Responses:**
- `401 Unauthorized`: Invalid or missing JWT token
- `403 Forbidden`: User does not own the chatbot
- `404 Not Found`: Chatbot not found
- `500 Internal Server Error`: Server error

---

### 2. Get Connection Status

**Endpoint:** `GET /v1/api/chatbot/google-calendar/{chatbotId}`

**Description:** Check if Google Calendar is connected and token status.

**Headers:**
```
Authorization: Bearer <CLERK_JWT_TOKEN>
```

**Success Response (200) - Connected:**
```json
{
  "connected": true,
  "chatbotId": "698576e4d5fd040c84aed7d8",
  "expiresAt": "2026-02-06T10:30:00Z",
  "isExpired": false
}
```

**Success Response (200) - Not Connected:**
```json
{
  "connected": false,
  "chatbotId": "698576e4d5fd040c84aed7d8"
}
```

---

### 3. Get Access Token

**Endpoint:** `GET /v1/api/chatbot/google-calendar/{chatbotId}/tokens`

**Description:** Get the current access token. Automatically refreshes if expired.

**Headers:**
```
Authorization: Bearer <CLERK_JWT_TOKEN>
```

**Success Response (200):**
```json
{
  "success": true,
  "accessToken": "ya29.a0AfH6SMBx...",
  "expiresAt": "2026-02-06T10:30:00Z",
  "tokenType": "Bearer"
}
```

**Note:** If the token is expired, it will be automatically refreshed and the new token returned.

**Error Responses:**
- `404 Not Found`: Tokens not found for this chatbot
- `410 Gone`: Token expired and refresh failed (user needs to reconnect)

---

### 4. Refresh Access Token

**Endpoint:** `POST /v1/api/chatbot/google-calendar/{chatbotId}/refresh`

**Description:** Manually refresh the access token.

**Headers:**
```
Authorization: Bearer <CLERK_JWT_TOKEN>
```

**Success Response (200):**
```json
{
  "success": true,
  "accessToken": "ya29.a0AfH6SMBx...",
  "expiresIn": 3600,
  "expiresAt": "2026-02-06T11:30:00Z",
  "tokenType": "Bearer"
}
```

**Error Responses:**
- `400 Bad Request`: Refresh failed
- `404 Not Found`: Tokens not found

---

### 5. Disconnect Google Calendar

**Endpoint:** `DELETE /v1/api/chatbot/google-calendar/{chatbotId}`

**Description:** Revoke the access token and delete stored credentials.

**Headers:**
```
Authorization: Bearer <CLERK_JWT_TOKEN>
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Google Calendar disconnected successfully",
  "chatbotId": "698576e4d5fd040c84aed7d8"
}
```

**Note:** This endpoint attempts to revoke the token with Google, but even if revocation fails, the tokens are deleted from the database.

---

## 🧪 Testing

### Using cURL

```bash
# Set your variables
CHATBOT_ID="698576e4d5fd040c84aed7d8"
CLERK_TOKEN="your_clerk_jwt_token"
BASE_URL="http://localhost:8080"

# 1. Store tokens
curl -X POST "$BASE_URL/v1/api/chatbot/google-calendar/$CHATBOT_ID" \
  -H "Authorization: Bearer $CLERK_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "test_access_token",
    "refreshToken": "test_refresh_token",
    "expiresIn": 3600,
    "tokenType": "Bearer"
  }'

# 2. Get connection status
curl "$BASE_URL/v1/api/chatbot/google-calendar/$CHATBOT_ID" \
  -H "Authorization: Bearer $CLERK_TOKEN"

# 3. Get tokens
curl "$BASE_URL/v1/api/chatbot/google-calendar/$CHATBOT_ID/tokens" \
  -H "Authorization: Bearer $CLERK_TOKEN"

# 4. Refresh token
curl -X POST "$BASE_URL/v1/api/chatbot/google-calendar/$CHATBOT_ID/refresh" \
  -H "Authorization: Bearer $CLERK_TOKEN"

# 5. Disconnect
curl -X DELETE "$BASE_URL/v1/api/chatbot/google-calendar/$CHATBOT_ID" \
  -H "Authorization: Bearer $CLERK_TOKEN"
```

## 🔐 Security Features

### 1. Encryption
- **Algorithm:** AES-256-GCM (Galois/Counter Mode)
- **Key Size:** 256 bits (32 bytes)
- **IV:** Randomly generated 128-bit IV for each encryption
- **Authentication:** GCM provides built-in authentication tag
- **Storage Format:** JSON with separate IV, encrypted data, and auth tag

### 2. Authorization
- **Clerk JWT Authentication:** All endpoints require valid JWT token
- **Chatbot Ownership:** Users can only access tokens for chatbots they own
- **Email-based verification:** User email from JWT is matched with chatbot creator

### 3. Token Management
- **Automatic Refresh:** Expired tokens are automatically refreshed
- **Secure Storage:** Tokens stored encrypted in MongoDB
- **Revocation:** Tokens can be revoked with Google on disconnect

## 📊 Database Schema

### Collection: `google_calendar_tokens`

```javascript
{
  "_id": ObjectId("..."),
  "chatbotId": "698576e4d5fd040c84aed7d8",  // Unique index
  "accessToken": "{\"iv\":\"...\",\"encryptedData\":\"...\",\"authTag\":\"...\"}",
  "refreshToken": "{\"iv\":\"...\",\"encryptedData\":\"...\",\"authTag\":\"...\"}",
  "expiresAt": ISODate("2026-02-06T10:30:00Z"),  // Indexed
  "tokenType": "Bearer",
  "createdAt": ISODate("2026-02-06T09:30:00Z"),
  "updatedAt": ISODate("2026-02-06T09:30:00Z"),
  "createdBy": "user@example.com"  // Indexed
}
```

## 🔄 Token Refresh Flow

```
┌─────────────┐
│  Get Token  │
└──────┬──────┘
       │
       ▼
  ┌─────────┐
  │ Expired?│──No──> Return token
  └────┬────┘
       │Yes
       ▼
  ┌──────────────────┐
  │ Refresh with     │
  │ Google OAuth API │
  └────┬─────────────┘
       │
       ▼
  ┌──────────────────┐
  │ Update database  │
  │ with new token   │
  └────┬─────────────┘
       │
       ▼
  Return new token
```

## 🐛 Troubleshooting

### Error: "Encryption key must be 32 bytes"
- Ensure your `ENCRYPTION_KEY` is exactly 64 hex characters (32 bytes)
- Generate a new key using `EncryptionUtils.generateEncryptionKey()`

### Error: "Failed to refresh token"
- Check that `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are correct
- Verify the refresh token hasn't been revoked
- User may need to reconnect their Google account

### Error: "User does not own this chatbot"
- Verify the JWT token is valid
- Check that the user email in JWT matches the chatbot's `createdBy` field

### MongoDB Connection Issues
- Ensure MongoDB is running
- Check connection string in application configuration
- Verify database permissions

## 📝 Implementation Checklist

- ✅ Entity: `GoogleCalendarToken`
- ✅ Repository: `GoogleCalendarTokenDao`
- ✅ Encryption: `EncryptionUtils`
- ✅ Services: `GoogleOAuthService`, `ChatbotOwnershipService`
- ✅ Controller: `GoogleCalendarOAuthController`
- ✅ DTOs: Request/Response objects
- ✅ Configuration: `application.yml`
- ✅ Security: Integrated with existing Clerk authentication
- ✅ Error Handling: Comprehensive error responses
- ✅ Logging: Detailed logging for debugging
- ✅ Documentation: Complete API documentation

## 🚀 Production Deployment

### Before Going Live:

1. **Generate a secure encryption key:**
   ```bash
   openssl rand -hex 32
   ```

2. **Set environment variables:**
   - Use secrets management (AWS Secrets Manager, Azure Key Vault, etc.)
   - Never commit keys to version control

3. **Configure Google OAuth:**
   - Add production redirect URIs
   - Configure OAuth consent screen
   - Set up proper scopes

4. **MongoDB:**
   - Enable authentication
   - Set up replica sets for high availability
   - Configure backup strategies

5. **Monitoring:**
   - Set up alerts for token refresh failures
   - Monitor encryption/decryption operations
   - Track OAuth API rate limits

## 📚 Related Documentation

- [Google OAuth 2.0 Documentation](https://developers.google.com/identity/protocols/oauth2)
- [Google Calendar API](https://developers.google.com/calendar/api/guides/overview)
- [Clerk Authentication](https://clerk.com/docs)
- [Spring WebFlux Reactive Programming](https://docs.spring.io/spring-framework/reference/web/webflux.html)

## 💡 Future Enhancements

- [ ] Add webhook support for calendar events
- [ ] Implement token rotation policy
- [ ] Add support for multiple calendar accounts per chatbot
- [ ] Create admin dashboard for token management
- [ ] Add metrics and analytics
- [ ] Implement rate limiting for API calls

---

**Version:** 1.0.0  
**Last Updated:** February 6, 2026  
**Status:** ✅ Production Ready








