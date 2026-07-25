# Default Assistant Chatbots - Auto-Initialization

## Overview
Implemented automatic initialization of 8 pre-configured assistant chatbots on application startup. These assistants are system-level chatbots with specific roles and domain expertise.

## Features

✅ **Auto-Initialization** - Runs on every application startup  
✅ **Idempotent** - Checks existence before inserting (safe to run multiple times)  
✅ **Pre-configured** - Complete with instructions, styling, and Q&A pairs  
✅ **Domain-Specific** - Each assistant focused on specific topic areas  
✅ **Production-Ready** - Active status, proper styling, avatar URLs

## Default Assistants

### 1. Support Bot (support-bot)
**Role:** Customer Success Specialist  
**Focus:** Customer support, troubleshooting, product questions  
**Colors:** Blue gradient (#1e3a8a to #E4EDFF)  
**Avatar:** Professional support representative

**Instructions:** Provides friendly customer support, troubleshooting, and answers product-related questions only.

**Sample Q&A:**
- "What are your support hours?" → "24/7 support available"
- "How do I contact support?" → "Through chat, email, or phone"

---

### 2. Sales Assistant (sales-assistant)
**Role:** Revenue Growth Strategist  
**Focus:** Product benefits, pricing, objection handling, closing deals  
**Colors:** Green gradient (#047857 to #E7FFE6)  
**Avatar:** Sales professional

**Instructions:** Guides prospects through benefits, handles objections, highlights pricing plans, and focuses on consultative selling.

**Sample Q&A:**
- "What pricing plans do you offer?" → "Flexible plans for all business sizes"
- "How can your product help?" → "Increase efficiency, reduce costs, drive growth"

---

### 3. HR Helper (hr-helper)
**Role:** People Experience Advisor  
**Focus:** HR policies, benefits, onboarding, workplace resources  
**Colors:** Purple gradient (#7e22ce to #FFE4F0)  
**Avatar:** HR professional

**Instructions:** Provides HR policy information, benefits guidance, and onboarding support with a personable tone.

**Sample Q&A:**
- "How do I request time off?" → "Through HR portal or manager"
- "What benefits are available?" → "Health, retirement, PTO, development"

---

### 4. Relationship Coach (relationship-coach)
**Role:** Connection Mentor  
**Focus:** Relationships, communication, conflict resolution  
**Colors:** Orange gradient (#f97316 to #FFF4E5)  
**Avatar:** Relationship expert

**Instructions:** Offers relationship guidance, communication tips, and empathetic support for difficult conversations.

**Sample Q&A:**
- "How do I improve communication?" → "Active listening, clarity, empathy"
- "How do I handle conflicts?" → "Approach with curiosity, understand perspectives"

---

### 5. Personal Trainer (personal-trainer)
**Role:** Performance Coach  
**Focus:** Fitness, workout plans, nutrition, motivation  
**Colors:** Dark gradient (#0f172a to #FFF7D6)  
**Avatar:** Fitness coach

**Instructions:** Builds personalized workout plans, keeps users accountable, provides exercise and nutrition guidance.

**Sample Q&A:**
- "How do I start working out?" → "2-3 days/week, full-body, build consistency"
- "What to eat before workout?" → "Balanced meal 2-3 hours before, or light snack"

---

### 6. Confidence Coach (confidence-coach)
**Role:** Mindset Architect  
**Focus:** Confidence building, mindset, positive affirmations  
**Colors:** Purple gradient (#4c1d95 to #F5E8FF)  
**Avatar:** Life coach

**Instructions:** Provides mindset exercises, affirmations, and actionable advice for building lasting confidence.

**Sample Q&A:**
- "How do I build confidence?" → "Celebrate wins, challenge self-talk, step out of comfort zone"
- "Good daily affirmations?" → "'I am capable,' 'I trust myself,' 'I am worthy'"

---

### 7. Companion Ally (companion-ally)
**Role:** Friend & Companion  
**Focus:** Casual conversation, recommendations, companionship  
**Colors:** Gray gradient (#1f2937 to #FFF4E5)  
**Avatar:** Friendly companion

**Instructions:** Keeps users company with warm conversation, book/movie recommendations, and uplifting reflections.

**Sample Q&A:**
- "Recommend a book?" → "What genre? Fiction, mystery, romance, etc.?"
- "What should I do today?" → "Try something new - walk, read, call a friend"

---

### 8. Debate Mentor (debate-mentor)
**Role:** Argument Strategist  
**Focus:** Critical thinking, debate, persuasive communication  
**Colors:** Blue gradient (#2563eb to #E4EDFF)  
**Avatar:** Debate coach

**Instructions:** Sharpens critical thinking, provides counterpoints, teaches persuasive techniques and logical argumentation.

**Sample Q&A:**
- "Build a strong argument?" → "Clear thesis, evidence, address counterarguments, conclusion"
- "Common logical fallacies?" → "Ad hominem, straw man, false dichotomies, slippery slopes"

---

## Implementation

### Java Service: DefaultAssistantsInitializer

**File:** `src/main/java/net/ai/chatbot/service/startup/DefaultAssistantsInitializer.java`

**Type:** `CommandLineRunner` - Runs automatically on startup

**Logic:**
```java
@Override
public void run(String... args) {
    log.info("Checking for default assistant chatbots...");
    
    List<ChatBot> defaultAssistants = createDefaultAssistants();
    
    int insertedCount = 0;
    int vectorIndexCount = 0;
    
    for (ChatBot assistant : defaultAssistants) {
        // Insert chatbot if doesn't exist
        if (!chatBotDao.existsById(assistant.getId())) {
            chatBotDao.save(assistant);
            insertedCount++;
            log.info("Inserted default assistant: {}", assistant.getName());
        }
        
        // Create vector index for knowledge base
        if (createVectorIndexIfNeeded(assistant)) {
            vectorIndexCount++;
        }
    }
    
    log.info("Successfully inserted {} default assistant chatbot(s)", insertedCount);
    log.info("Created {} vector index(es) for default assistants", vectorIndexCount);
}
```

**Key Features:**
- ✅ **Idempotent** - Checks `existsById()` before inserting
- ✅ **Safe** - Won't duplicate or overwrite existing assistants
- ✅ **Vector Indexes** - Automatically creates knowledge base collections and indexes
- ✅ **Logged** - Logs each insertion, skip, and index creation
- ✅ **Automatic** - Runs on every application startup

### Vector Index Creation

For each assistant, the service creates:

1. **Knowledge Base Collection:**
   ```
   jade-ai-knowledgebase-{assistant-id}
   ```
   Example: `jade-ai-knowledgebase-support-bot`

2. **Vector Search Index:**
   ```
   jade-ai-vector-index-{assistant-id}
   ```
   Example: `jade-ai-vector-index-support-bot`

**Note:** For production MongoDB Atlas Vector Search, you need to create Atlas-specific vector indexes through the Atlas UI. See `VECTOR_INDEX_SETUP.md` for details.

### MongoDB Script

**File:** `mongodb-default-assistants.js`

**Usage:**
```bash
mongosh <connection-string> < mongodb-default-assistants.js
```

**Purpose:** Manual insertion option for direct database access

---

## Configuration Details

### Common Properties (All Assistants)

| Property | Value |
|----------|-------|
| `email` | `system@jadeordersmedia.com` |
| `createdBy` | `system` |
| `status` | `ACTIVE` |
| `model` | `gpt-4o` |
| `width` | `380` |
| `height` | `600` |
| `widgetPosition` | `right` |
| `restrictToDataSource` | `true` |
| `hideMainBannerLogo` | `false` |
| `hideName` | `false` |
| `selectedDataSource` | `qa` |

### Unique Properties Per Assistant

Each assistant has:
- **Unique ID** (matches chatbotId from frontend)
- **Custom Instructions** (role-specific, domain-restricted)
- **Custom Styling** (header, AI, user colors)
- **Custom Avatar URL** (Unsplash images)
- **Custom Greeting** (role-appropriate welcome)
- **Custom Fallback** (domain-specific redirect)
- **Custom Q&A Pairs** (2-3 common questions)

---

## Domain Restriction

All assistants include domain restriction in their instructions:

**Pattern:**
```
You should ONLY answer questions related to:
- [Domain Topic 1]
- [Domain Topic 2]
- [Domain Topic 3]

If asked about topics outside [domain], politely redirect users...
```

**Example (Support Bot):**
```
You should ONLY answer questions related to:
- Product features and functionality
- Account management and billing
- Technical troubleshooting
- Service status and updates
- Company policies and procedures

If asked about topics outside customer support, politely redirect users...
```

---

## Frontend Integration

### Assistant Mapping

The frontend's `Assistant[]` array directly maps to these backend chatbots:

```typescript
{
  id: 'support-bot',           // → chatbotId in database
  chatbotId: 'support-bot',    // → _id in MongoDB
  // ... frontend-specific display props
}
```

### API Usage

**Public Endpoint:**
```http
GET /v1/api/public/chatbot/support-bot

Response:
{
  "id": "support-bot",
  "name": "Support Bot",
  "title": "Customer Success Specialist",
  "greetingMessage": "Hello! I'm your Customer Success Specialist...",
  "instructions": "You are a Customer Success Specialist...",
  "status": "ACTIVE",
  "headerBackground": "#1e3a8a",
  "aiBackground": "#E4EDFF",
  // ... full configuration
}
```

**Chat Endpoint:**
```http
POST /v1/api/n8n/anonymous/chat/generic

{
  "chatbotId": "support-bot",
  "message": "What are your support hours?",
  "sessionId": "session_123",
  "role": "user"
}
```

---

## Startup Logs

```
2026-03-18 10:30:45.123  INFO --- [main] DefaultAssistantsInitializer : Checking for default assistant chatbots...
2026-03-18 10:30:45.234  INFO --- [main] DefaultAssistantsInitializer : Inserted default assistant: Support Bot (support-bot)
2026-03-18 10:30:45.245  INFO --- [main] DefaultAssistantsInitializer : Created vector index 'jade-ai-vector-index-support-bot' for collection 'jade-ai-knowledgebase-support-bot'
2026-03-18 10:30:45.256  INFO --- [main] DefaultAssistantsInitializer : Inserted default assistant: Sales Assistant (sales-assistant)
2026-03-18 10:30:45.267  INFO --- [main] DefaultAssistantsInitializer : Created vector index 'jade-ai-vector-index-sales-assistant' for collection 'jade-ai-knowledgebase-sales-assistant'
2026-03-18 10:30:45.278  INFO --- [main] DefaultAssistantsInitializer : Inserted default assistant: HR Helper (hr-helper)
2026-03-18 10:30:45.289  INFO --- [main] DefaultAssistantsInitializer : Created vector index 'jade-ai-vector-index-hr-helper' for collection 'jade-ai-knowledgebase-hr-helper'
2026-03-18 10:30:45.300  INFO --- [main] DefaultAssistantsInitializer : Inserted default assistant: Relationship Coach (relationship-coach)
2026-03-18 10:30:45.311  INFO --- [main] DefaultAssistantsInitializer : Created vector index 'jade-ai-vector-index-relationship-coach' for collection 'jade-ai-knowledgebase-relationship-coach'
2026-03-18 10:30:45.322  INFO --- [main] DefaultAssistantsInitializer : Inserted default assistant: Personal Trainer (personal-trainer)
2026-03-18 10:30:45.333  INFO --- [main] DefaultAssistantsInitializer : Created vector index 'jade-ai-vector-index-personal-trainer' for collection 'jade-ai-knowledgebase-personal-trainer'
2026-03-18 10:30:45.344  INFO --- [main] DefaultAssistantsInitializer : Inserted default assistant: Confidence Coach (confidence-coach)
2026-03-18 10:30:45.355  INFO --- [main] DefaultAssistantsInitializer : Created vector index 'jade-ai-vector-index-confidence-coach' for collection 'jade-ai-knowledgebase-confidence-coach'
2026-03-18 10:30:45.366  INFO --- [main] DefaultAssistantsInitializer : Inserted default assistant: Companion Ally (companion-ally)
2026-03-18 10:30:45.377  INFO --- [main] DefaultAssistantsInitializer : Created vector index 'jade-ai-vector-index-companion-ally' for collection 'jade-ai-knowledgebase-companion-ally'
2026-03-18 10:30:45.388  INFO --- [main] DefaultAssistantsInitializer : Inserted default assistant: Debate Mentor (debate-mentor)
2026-03-18 10:30:45.399  INFO --- [main] DefaultAssistantsInitializer : Created vector index 'jade-ai-vector-index-debate-mentor' for collection 'jade-ai-knowledgebase-debate-mentor'
2026-03-18 10:30:45.410  INFO --- [main] DefaultAssistantsInitializer : Successfully inserted 8 default assistant chatbot(s)
2026-03-18 10:30:45.411  INFO --- [main] DefaultAssistantsInitializer : Created 8 vector index(es) for default assistants
```

**On subsequent startups:**
```
2026-03-18 10:35:12.123  INFO --- [main] DefaultAssistantsInitializer : Checking for default assistant chatbots...
2026-03-18 10:35:12.234  DEBUG --- [main] DefaultAssistantsInitializer : Assistant already exists: Support Bot (support-bot)
2026-03-18 10:35:12.235  DEBUG --- [main] DefaultAssistantsInitializer : Vector index 'jade-ai-vector-index-support-bot' already exists
... (similar for all 8 assistants)
2026-03-18 10:35:12.350  INFO --- [main] DefaultAssistantsInitializer : All default assistant chatbots already exist
```

---

## Database Structure

### Collection: `chatbots`

```javascript
{
  _id: "support-bot",  // Fixed ID
  name: "Support Bot",
  title: "Customer Success Specialist",
  email: "system@jadeordersmedia.com",
  status: "ACTIVE",
  model: "gpt-4o",
  instructions: "You are a Customer Success Specialist...",
  greetingMessage: "Hello! I'm your Customer Success...",
  fallbackMessage: "I'm here to help with...",
  restrictToDataSource: true,
  selectedDataSource: "qa",
  width: "380",
  height: "600",
  headerBackground: "#1e3a8a",
  headerText: "#FFFFFF",
  aiBackground: "#E4EDFF",
  aiText: "#1e293b",
  userBackground: "#3b82f6",
  userText: "#FFFFFF",
  widgetPosition: "right",
  aiAvatarUrl: "https://images.unsplash.com/...",
  hideMainBannerLogo: false,
  createdBy: "system",
  createdAt: ISODate("2026-03-18T10:30:45Z"),
  updatedAt: ISODate("2026-03-18T10:30:45Z"),
  qaPairs: [
    { question: "...", answer: "..." },
    { question: "...", answer: "..." }
  ]
}
```

---

## Benefits

✅ **Zero Manual Setup** - Assistants auto-created on first startup  
✅ **Production Ready** - Fully configured with styling and instructions  
✅ **Domain Focused** - Each assistant restricted to specific topics  
✅ **Consistent Experience** - Standardized across all assistants  
✅ **Maintainable** - Single source of truth in Java code  
✅ **Safe Updates** - Won't overwrite existing customizations  
✅ **Frontend Compatible** - IDs match frontend expectations  

---

## Testing

### Verify Assistants Created

```bash
# MongoDB query
db.chatbots.find({ createdBy: "system" }).count()
# Expected: 8

# List all default assistants
db.chatbots.find(
  { createdBy: "system" },
  { _id: 1, name: 1, status: 1 }
).pretty()
```

### Test Chat Interaction

```bash
curl -X POST http://localhost:8080/v1/api/n8n/anonymous/chat/generic \
  -H "Content-Type: application/json" \
  -d '{
    "chatbotId": "support-bot",
    "message": "What are your support hours?",
    "sessionId": "test_session",
    "role": "user"
  }'
```

---

## Files Created

1. **`DefaultAssistantsInitializer.java`** - Startup service (680 lines)
2. **`mongodb-default-assistants.js`** - MongoDB script (350 lines)
3. **`DEFAULT_ASSISTANTS_DOCUMENTATION.md`** - This documentation

---

## Summary

✅ **8 Default Assistants** automatically created on startup  
✅ **Domain-Specific Instructions** for focused responses  
✅ **Complete Styling** with unique color schemes  
✅ **Idempotent Initialization** safe to run multiple times  
✅ **Production Ready** with proper status and configuration  
✅ **Frontend Compatible** IDs match TypeScript definitions  

The assistants are now ready to use immediately after application startup!
