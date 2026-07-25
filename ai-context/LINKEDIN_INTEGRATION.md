# LinkedIn Integration - Complete Implementation

## Overview

This document describes the complete LinkedIn integration for the Social Media Suite, including account connection, posting to personal profiles, and media uploads.

## Key Features

- **Store LinkedIn Accounts**: OAuth 2.0 access tokens with ~60 day expiry
- **Post to Personal Profiles**: Using LinkedIn's UGC Posts API
- **Media Support**: Image and video attachments
- **Token Management**: Encrypted storage with expiry tracking
- **Multi-Account Support**: Users can connect multiple LinkedIn accounts

## Architecture

### Components

1. **DTOs**: Request/response objects for LinkedIn operations
2. **Entity**: `SocialAccount` updated to store LinkedIn data
3. **Service Layer**: `SocialAccountService` handles account management
4. **Publisher**: `LinkedInPublisher` handles API interactions
5. **Controller**: REST endpoints for frontend integration

### Platform Comparison

| Feature | Facebook | Twitter | LinkedIn |
|---------|----------|---------|----------|
| **Native Scheduling** | ✅ Yes | ❌ No | ❌ No |
| **Access Token Expiry** | 60 days | No expiry (with refresh) | 60 days |
| **Refresh Token** | ❌ No | ✅ Yes | ⚠️ Marketing Platform only |
| **Media Upload** | ✅ Yes | ✅ Yes | ✅ Yes |
| **Posting Cost** | Free tier available | $100/month (Basic) | Free tier available |

## API Endpoints

### 1. Connect LinkedIn Account

**POST** `/v1/api/social-accounts/linkedin`

**Headers:**
```
Authorization: Bearer <clerk_jwt>
Content-Type: application/json
```

**Request Body:**
```json
{
  "accessToken": "AQXxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "refreshToken": null,
  "expiresIn": 5183944,
  "linkedInUserId": "urn:li:person:AbCdEfGhIj",
  "displayName": "John Doe",
  "email": "john@example.com",
  "profilePicture": "https://media.licdn.com/dms/image/..."
}
```

**Response (200):**
```json
{
  "success": true,
  "platform": "linkedin",
  "accountId": "550e8400-e29b-41d4-a716-446655440002"
}
```

### 2. List Accounts (includes LinkedIn)

**GET** `/v1/api/social-accounts`

**Response:**
```json
{
  "accounts": [
    {
      "accountId": "550e8400-e29b-41d4-a716-446655440002",
      "platform": "linkedin",
      "connectedAt": "2024-01-15T10:30:00Z",
      "displayName": "John Doe"
    }
  ]
}
```

### 3. List Posting Targets

**GET** `/v1/api/social-accounts/targets?platform=linkedin`

**Response:**
```json
{
  "targets": [
    {
      "targetId": "550e8400-e29b-41d4-a716-446655440002",
      "accountId": "550e8400-e29b-41d4-a716-446655440002",
      "platform": "linkedin",
      "displayName": "LinkedIn - John Doe"
    }
  ]
}
```

### 4. Schedule/Publish Post (supports LinkedIn)

**POST** `/v1/api/social-media/schedule`

**Request:**
```json
{
  "targetIds": ["550e8400-e29b-41d4-a716-446655440002"],
  "content": "Excited to share our latest innovation! #Tech #Innovation",
  "media": [
    {
      "mediaId": "file_abc123",
      "mediaUrl": "https://api.example.com/...",
      "mimeType": "image/jpeg",
      "fileName": "product.jpg"
    }
  ],
  "immediate": true
}
```

**Response:**
```json
{
  "success": true,
  "postId": "post_xyz789",
  "status": "pending_publish",
  "scheduledAt": "2024-01-15T10:35:00Z"
}
```

## Data Model

### SocialAccount Entity (MongoDB)

```java
@Document(collection = "social_accounts")
public class SocialAccount {
    private String id;
    private String userId;
    private String platform; // "linkedin"
    private Date connectedAt;
    
    // LinkedIn-specific fields
    private String accessToken;      // encrypted
    private String refreshToken;     // encrypted (usually null)
    private Long expiresIn;          // seconds
    private String linkedInUserId;   // urn:li:person:AbCdEfGhIj
    private String displayName;
    private String email;
    private String profilePicture;
}
```

### Target ID Format

- **Facebook**: `{accountId}:{pageId}` (e.g., `abc123:page456`)
- **Twitter**: `{accountId}` (e.g., `def789`)
- **LinkedIn**: `{accountId}` (e.g., `ghi012`)

The system distinguishes platforms by checking if the targetId contains a colon (`:`) - only Facebook uses this format.

## LinkedIn API Integration

### Publishing Flow

1. **Resolve Token**: Fetch encrypted access token from database
2. **Upload Media** (if present):
   - Register upload with LinkedIn API
   - Upload media bytes to provided URL
   - Get asset URN
3. **Create UGC Post**:
   - Build post payload with content and media
   - POST to `/v2/ugcPosts`
   - Return post URN

### LinkedIn UGC Post Structure

```json
{
  "author": "urn:li:person:AbCdEfGhIj",
  "lifecycleState": "PUBLISHED",
  "specificContent": {
    "com.linkedin.ugc.ShareContent": {
      "shareCommentary": {
        "text": "Your post content here"
      },
      "shareMediaCategory": "IMAGE",
      "media": [
        {
          "status": "READY",
          "media": "urn:li:digitalmediaAsset:xyz"
        }
      ]
    }
  },
  "visibility": {
    "com.linkedin.ugc.MemberNetworkVisibility": "PUBLIC"
  }
}
```

## Security

### Token Encryption

All access tokens are encrypted using AES-256-GCM before storage:

```java
// Storage
String encrypted = encryptionUtils.encrypt(request.getAccessToken());
account.setAccessToken(encrypted);

// Retrieval
String decrypted = encryptionUtils.decrypt(account.getAccessToken());
```

### User Isolation

- All operations require JWT authentication
- `userId` extracted from JWT `sub` claim
- Database queries filtered by `userId`
- Token resolution verifies ownership

## Error Handling

### Common Errors

#### 401 Unauthorized
**Cause**: Access token expired or invalid

**Message**:
```
LinkedIn API access denied (401 Unauthorized). 
Your LinkedIn access token has likely expired (~60 days). 
Please reconnect your LinkedIn account.
```

**Solution**: User must reconnect their LinkedIn account through OAuth flow.

#### 403 Forbidden
**Cause**: User attempting to use another user's media

**Message**:
```
You do not have permission to use media: {mediaId}
```

**Solution**: Frontend should only allow users to select their own uploaded media.

#### 429 Rate Limited
**Cause**: Too many API requests

**Solution**: Implement retry logic with exponential backoff.

## Token Expiry Management

### LinkedIn Token Lifecycle

1. **Initial OAuth**: User grants access, receives token with ~60 day expiry
2. **Storage**: Token stored with `expiresIn` timestamp
3. **Usage**: Token used for posting until expiry
4. **Expiry**: After ~60 days, token becomes invalid (401 errors)
5. **Reconnection**: User must re-authenticate via OAuth

### Refresh Token Limitations

- **Basic Products** (Sign In + Share): No refresh token
- **Marketing Developer Platform**: Refresh tokens available with `offline_access` scope
- Most users will need to reconnect every 60 days

### Frontend UX Recommendations

1. **Display Token Status**: Show "Expires in X days" for each account
2. **Proactive Alerts**: Warn users 7 days before expiry
3. **Graceful Degradation**: Handle 401 errors by prompting reconnection
4. **Reconnect Flow**: Streamlined OAuth flow for re-authorization

```typescript
if (error.status === 401 && error.platform === 'linkedin') {
  showNotification({
    type: "warning",
    title: "LinkedIn Connection Expired",
    message: "Your LinkedIn access has expired. Please reconnect to continue posting.",
    actions: [
      { label: "Reconnect LinkedIn", onClick: () => initiateLinkedInOAuth() }
    ]
  });
}
```

## Scheduling Strategy

### Immediate Posts

LinkedIn posts with `immediate: true` are published immediately by the controller:

```java
if (isImmediate) {
    // Controller handles immediate publishing
    socialPostPublisher.publishPost(savedPost, true);
}
```

### Scheduled Posts

LinkedIn has no native scheduling API in basic products. Scheduled posts use the cron job:

1. **Save to DB**: Post stored with `status: "scheduled"`
2. **Cron Job**: `SocialPostScheduler` polls every minute
3. **Publishing**: When `scheduledAt` is reached, calls `LinkedInPublisher.publishImmediately()`

## Media Upload

### Supported Formats

- **Images**: JPEG, PNG, GIF
- **Videos**: MP4, MOV (up to 50MB)

### Upload Flow

1. **Register Upload**:
   ```
   POST /v2/assets?action=registerUpload
   Body: { registerUploadRequest: { owner, recipes, serviceRelationships } }
   Response: { uploadUrl, asset }
   ```

2. **Upload Bytes**:
   ```
   PUT {uploadUrl}
   Content-Type: application/octet-stream
   Body: [media bytes]
   ```

3. **Use Asset**:
   - Asset URN (`urn:li:digitalmediaAsset:xyz`) used in post payload

### Limitations

- **Single-part upload**: Current implementation uses simple upload
- **File size**: Up to 50MB (larger files require chunked upload)
- **Processing time**: LinkedIn processes media asynchronously

## Testing

### Manual Testing Steps

1. **Connect Account**:
   ```bash
   curl -X POST 'https://api.example.com/v1/api/social-accounts/linkedin' \
     -H 'Authorization: Bearer YOUR_JWT' \
     -H 'Content-Type: application/json' \
     -d '{
       "accessToken": "YOUR_LINKEDIN_TOKEN",
       "linkedInUserId": "urn:li:person:YOUR_ID",
       "displayName": "Test User",
       "email": "test@example.com"
     }'
   ```

2. **List Targets**:
   ```bash
   curl 'https://api.example.com/v1/api/social-accounts/targets?platform=linkedin' \
     -H 'Authorization: Bearer YOUR_JWT'
   ```

3. **Publish Post**:
   ```bash
   curl -X POST 'https://api.example.com/v1/api/social-media/schedule' \
     -H 'Authorization: Bearer YOUR_JWT' \
     -H 'Content-Type: application/json' \
     -d '{
       "targetIds": ["YOUR_ACCOUNT_ID"],
       "content": "Test post from API",
       "immediate": true
     }'
   ```

### Verification

1. Check logs for successful publishing
2. Verify post appears on LinkedIn profile
3. Check database for updated post status

## Files Changed/Created

### New Files

- `LinkedInConnectRequest.java` - DTO for connection request
- `LinkedInConnectResponse.java` - DTO for connection response
- `LinkedInPublisher.java` - Service for LinkedIn API integration

### Modified Files

- `SocialAccount.java` - Added LinkedIn fields
- `SocialAccountService.java` - Added `connectLinkedIn()` and updated helpers
- `SocialAccountController.java` - Added `/linkedin` endpoint
- `SocialPostPublisher.java` - Added LinkedIn publishing logic
- `TokenResolutionResponse.java` - Added LinkedIn fields

## Next Steps

### Production Enhancements

1. **Chunked Media Upload**: Support files >50MB
2. **Media Processing Status**: Poll LinkedIn for media processing completion
3. **Token Refresh Logic**: Implement refresh flow for Marketing Platform users
4. **Rate Limiting**: Add client-side rate limiting (100 posts/day per user)
5. **Analytics**: Track post performance via LinkedIn Analytics API
6. **Company Pages**: Support posting to LinkedIn company pages (requires additional permissions)

### Monitoring

- **Token Expiry Alerts**: Notify users before tokens expire
- **API Error Tracking**: Monitor 401/403/429 errors
- **Success Rates**: Track post publishing success/failure rates
- **Media Upload Performance**: Monitor upload times and failures

## Resources

- [LinkedIn UGC Posts API](https://learn.microsoft.com/en-us/linkedin/marketing/integrations/community-management/shares/ugc-post-api)
- [LinkedIn Authentication](https://learn.microsoft.com/en-us/linkedin/shared/authentication/authentication)
- [LinkedIn Media Upload](https://learn.microsoft.com/en-us/linkedin/marketing/integrations/community-management/shares/images-videos)
- [LinkedIn API Rate Limits](https://learn.microsoft.com/en-us/linkedin/shared/api-guide/concepts/rate-limits)

## Summary

The LinkedIn integration is now complete and follows the same patterns as Facebook and Twitter implementations. Users can connect their LinkedIn accounts, post to their personal profiles, and attach media. The system handles token encryption, user isolation, and graceful error handling for common scenarios like token expiry.
