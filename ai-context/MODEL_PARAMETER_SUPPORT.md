# Model Parameter Support in N8N Chat Integration

## Summary
Updated the N8N chat integration to support dynamic model selection by passing the `model` parameter from the frontend through to N8N webhook calls.

## Changes Made

### 1. Message DTO (`src/main/java/net/ai/chatbot/dto/Message.java`)
Already contained the `model` field:
```java
private String model;
```

### 2. GenericN8NService (`src/main/java/net/ai/chatbot/service/n8n/GenericN8NService.java`)

#### Updated `sendMessage()` method:
- Added `message.getModel()` parameter to `executeWebhook()` call

#### Updated `executeWebhook()` method signature:
- Added `String model` parameter
- Passed model to `buildHeaders()` method

#### Updated `buildHeaders()` method:
- Added `String model` parameter
- Added conditional logic to include `model` header if provided:
```java
// Add model header if provided
if (model != null && !model.isBlank()) {
    headers.put("model", model);
}
```

## Flow

```
Frontend Request
    ↓
Message DTO (contains model field)
    ↓
AnonymousUserChatN8NController
    ↓
GenericN8NService.sendMessage()
    ↓
executeWebhook() (receives model)
    ↓
buildHeaders() (adds model to headers)
    ↓
N8N Webhook Call (includes "model" header)
```

## Usage

### Frontend Example:
```javascript
const response = await fetch(`${API_BASE_URL}/chat/generic`, {
    method: 'POST',
    headers: { 
        'Content-Type': 'application/json'
    },
    body: JSON.stringify({
        message: "Hello",
        sessionId: sessionId,
        role: 'user',
        model: 'gpt-4',  // Model parameter
        fileAttachments: []
    })
});
```

### N8N Webhook:
The N8N webhook will receive the model in the request headers:
```
Headers:
  - model: "gpt-4"
  - chatbotid: "..."
  - sessionid: "..."
  - email: "..."
  - fallbackmessage: "..."
  - greetingmessage: "..."
  - restrictdatasource: "true"
  - ...
```

## Backward Compatibility
- The model parameter is **optional**
- If not provided or empty, no `model` header is added
- Existing integrations without model selection continue to work

## Testing
✅ Compilation successful
✅ Backward compatible with existing calls
✅ Model header only added when model is provided

## Notes
- The model parameter is passed as a header, not in the request body
- N8N workflow must be configured to read the `model` header if model selection is needed
- No changes required to `N8nWebhookService` (used for knowledge base training only)
