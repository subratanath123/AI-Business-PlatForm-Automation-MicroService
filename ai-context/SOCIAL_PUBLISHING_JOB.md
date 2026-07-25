# Social Media Publishing Job

## Overview
Automated background job that publishes scheduled posts to Facebook and Twitter.

## Architecture

### 1. **SocialPostScheduler** (Job)
- Runs every 60 seconds
- Queries posts with `status="scheduled"` and `scheduledAt <= now`
- Delegates to `SocialPostPublisher`

### 2. **SocialPostPublisher** (Orchestrator)
- Resolves tokens for each target
- Routes to platform-specific publishers
- Updates post status to `"published"`
- Handles partial failures (some targets succeed, others fail)

### 3. **FacebookPublisher**
- Calls Facebook Graph API v18.0
- Endpoint: `POST /{pageId}/feed`
- Uses page access token

### 4. **TwitterPublisher**
- Calls Twitter API v2
- Endpoint: `POST /2/tweets`
- Uses OAuth 2.0 bearer token

## Clean Code Principles

✅ **Single Responsibility**: Each class has one job
- Scheduler: Polling
- Orchestrator: Coordination
- Publishers: Platform-specific API calls

✅ **Dependency Injection**: All dependencies via constructor

✅ **Error Handling**: Graceful failures with detailed logging

✅ **Separation of Concerns**: Token resolution, publishing, and status updates separated

✅ **Testability**: Each component can be unit tested independently

## Files Created

```
src/main/java/net/ai/chatbot/
├── service/social/
│   ├── publisher/
│   │   ├── FacebookPublisher.java      (Graph API integration)
│   │   ├── TwitterPublisher.java       (Twitter API v2 integration)
│   │   └── SocialPostPublisher.java    (Orchestrator)
│   └── scheduler/
│       └── SocialPostScheduler.java    (Scheduled job)
└── ChatApplication.java                 (Added @EnableScheduling)
```

## Configuration

- **Interval**: 60 seconds
- **Initial Delay**: 10 seconds (waits after startup)
- **Batch Size**: All due posts in each cycle

## Logging

- Info: Post publishing start/success
- Warn: Partial failures
- Error: Complete failures with stack trace

## Next Steps (If Needed)

1. Add retry logic for failed posts
2. Add rate limiting
3. Add support for images/media
4. Add webhook notifications for failures
5. Add metrics/monitoring
