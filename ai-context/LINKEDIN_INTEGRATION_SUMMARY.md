# LinkedIn Integration - Quick Summary

## What Was Added

LinkedIn social media integration, following the same pattern as Facebook and Twitter.

## Features

- ✅ Store LinkedIn OAuth 2.0 access tokens (60-day expiry)
- ✅ Post to personal LinkedIn profiles
- ✅ Upload and attach media (images, videos)
- ✅ Multi-account support (multiple LinkedIn accounts per user)
- ✅ Encrypted token storage (AES-256-GCM)
- ✅ Scheduled posting via cron job (no native scheduling API)

## New Endpoint

**POST** `/v1/api/social-accounts/linkedin`

**Request:**
```json
{
  "accessToken": "AQXxxx...",
  "refreshToken": null,
  "expiresIn": 5183944,
  "linkedInUserId": "urn:li:person:AbCdEfGhIj",
  "displayName": "John Doe",
  "email": "john@example.com",
  "profilePicture": "https://media.licdn.com/..."
}
```

**Response:**
```json
{
  "success": true,
  "platform": "linkedin",
  "accountId": "550e8400-e29b-41d4-a716-446655440002"
}
```

## Files Created

- `LinkedInConnectRequest.java` - Connection DTO
- `LinkedInConnectResponse.java` - Response DTO
- `LinkedInPublisher.java` - LinkedIn API integration

## Files Modified

- `SocialAccount.java` - Added LinkedIn fields
- `SocialAccountService.java` - Added `connectLinkedIn()` method
- `SocialAccountController.java` - Added `/linkedin` endpoint
- `SocialPostPublisher.java` - Added LinkedIn publishing
- `TokenResolutionResponse.java` - Added LinkedIn fields

## How It Works

1. **Connect**: User authenticates via LinkedIn OAuth, frontend sends token to backend
2. **Store**: Backend encrypts and stores access token with user metadata
3. **List**: User's LinkedIn accounts appear in targets list
4. **Post**: User creates post, selects LinkedIn target, system publishes via UGC Posts API
5. **Media**: Images/videos uploaded to LinkedIn, attached to post

## LinkedIn API Details

**Base URL**: `https://api.linkedin.com/v2`

**Post Endpoint**: `POST /ugcPosts`

**Post Structure**:
```json
{
  "author": "urn:li:person:AbCdEfGhIj",
  "lifecycleState": "PUBLISHED",
  "specificContent": {
    "com.linkedin.ugc.ShareContent": {
      "shareCommentary": { "text": "Post content" },
      "shareMediaCategory": "IMAGE",
      "media": [{ "status": "READY", "media": "urn:li:digitalmediaAsset:xyz" }]
    }
  },
  "visibility": { "com.linkedin.ugc.MemberNetworkVisibility": "PUBLIC" }
}
```

## Token Expiry

- **Access Token**: ~60 days (Sign In + Share products)
- **Refresh Token**: Not available in basic products (only Marketing Developer Platform)
- **Handling Expiry**: 401 errors prompt user to reconnect

## Target ID Format

- **Facebook**: `{accountId}:{pageId}` (contains colon)
- **Twitter**: `{accountId}` (no colon)
- **LinkedIn**: `{accountId}` (no colon)

System identifies platform by presence/absence of colon in targetId.

## Error Handling

**401 Unauthorized**: Token expired → prompt user to reconnect

**403 Forbidden**: User attempting to use someone else's media

**429 Rate Limited**: Too many API requests

All errors logged with clear messages to frontend.

## Scheduling

- **Immediate Posts**: Published immediately by controller
- **Scheduled Posts**: Stored in DB, published by cron job (`SocialPostScheduler`)
- **No Native Scheduling**: LinkedIn basic products don't support native post scheduling

## Testing

**Connect Account**:
```bash
curl -X POST 'https://api.example.com/v1/api/social-accounts/linkedin' \
  -H 'Authorization: Bearer YOUR_JWT' \
  -d '{"accessToken":"...","linkedInUserId":"urn:li:person:...","displayName":"Test"}'
```

**Publish Post**:
```bash
curl -X POST 'https://api.example.com/v1/api/social-media/schedule' \
  -H 'Authorization: Bearer YOUR_JWT' \
  -d '{"targetIds":["accountId"],"content":"Test post","immediate":true}'
```

## Platform Comparison

| Feature | Facebook | Twitter | LinkedIn |
|---------|----------|---------|----------|
| Native Scheduling | ✅ Yes | ❌ No | ❌ No |
| Token Expiry | 60 days | No (with refresh) | 60 days |
| Refresh Token | ❌ No | ✅ Yes | ⚠️ Marketing only |
| Posting Cost | Free | $100/month | Free |

## Next Steps

All LinkedIn integration is complete and tested. The system now supports Facebook, Twitter, and LinkedIn for social media posting with media attachments.

See `LINKEDIN_INTEGRATION.md` for full documentation.
