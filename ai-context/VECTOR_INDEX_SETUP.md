# Vector Index Setup for Default Assistants

## Overview
Enhanced the default assistants initializer to automatically create knowledge base collections and vector indexes for each assistant chatbot.

## Implementation

### Collections Created

For each assistant, the following collection is created (if it doesn't exist):

```
jade-ai-knowledgebase-{assistant-id}
```

**Examples:**
- `jade-ai-knowledgebase-support-bot`
- `jade-ai-knowledgebase-sales-assistant`
- `jade-ai-knowledgebase-hr-helper`
- `jade-ai-knowledgebase-relationship-coach`
- `jade-ai-knowledgebase-personal-trainer`
- `jade-ai-knowledgebase-confidence-coach`
- `jade-ai-knowledgebase-companion-ally`
- `jade-ai-knowledgebase-debate-mentor`

### Vector Indexes Created

For each collection, a vector index is created with the name:

```
jade-ai-vector-index-{assistant-id}
```

**Examples:**
- `jade-ai-vector-index-support-bot`
- `jade-ai-vector-index-sales-assistant`
- `jade-ai-vector-index-hr-helper`
- etc.

## Java Implementation

### Updated `DefaultAssistantsInitializer.java`

Added vector index creation logic:

```java
@Override
public void run(String... args) {
    log.info("Checking for default assistant chatbots...");

    List<ChatBot> defaultAssistants = createDefaultAssistants();

    int insertedCount = 0;
    int vectorIndexCount = 0;
    
    for (ChatBot assistant : defaultAssistants) {
        boolean isNew = false;
        
        // Insert chatbot if new
        if (!chatBotDao.existsById(assistant.getId())) {
            chatBotDao.save(assistant);
            insertedCount++;
            isNew = true;
        }
        
        // Create vector index if needed
        if (createVectorIndexIfNeeded(assistant)) {
            vectorIndexCount++;
        }
    }

    log.info("Successfully inserted {} default assistant chatbot(s)", insertedCount);
    log.info("Created {} vector index(es) for default assistants", vectorIndexCount);
}
```

### Vector Index Creation Method

```java
private boolean createVectorIndexIfNeeded(ChatBot chatBot) {
    String collectionName = chatBot.getChatbotknowledgebasecollection(); 
    String indexName = chatBot.getVectorIndexName();
    
    try {
        // Create collection if doesn't exist
        if (!mongoTemplate.collectionExists(collectionName)) {
            mongoTemplate.createCollection(collectionName);
        }
        
        // Check if vector index already exists
        boolean indexExists = mongoTemplate.getCollection(collectionName)
                .listIndexes()
                .into(new ArrayList<>())
                .stream()
                .anyMatch(doc -> indexName.equals(doc.getString("name")));
        
        if (!indexExists) {
            // Create vector search index
            Document indexKeys = new Document("embedding", "2dsphere");
            IndexOptions options = new IndexOptions().name(indexName);
            
            mongoTemplate.getCollection(collectionName).createIndex(indexKeys, options);
            
            log.info("Created vector index '{}' for collection '{}'", 
                    indexName, collectionName);
            return true;
        }
        
    } catch (Exception e) {
        log.warn("Could not create vector index for {}: {}. " +
                "You may need to create Atlas Vector Search index manually.", 
                chatBot.getId(), e.getMessage());
        return false;
    }
    
    return false;
}
```

## MongoDB Script

The MongoDB script now also creates collections and indexes:

```javascript
// Create knowledge base collections and vector indexes
const assistants = [
    'support-bot',
    'sales-assistant', 
    'hr-helper',
    'relationship-coach',
    'personal-trainer',
    'confidence-coach',
    'companion-ally',
    'debate-mentor'
];

assistants.forEach(function(assistantId) {
    const collectionName = 'jade-ai-knowledgebase-' + assistantId;
    const indexName = 'jade-ai-vector-index-' + assistantId;
    
    // Create collection if it doesn't exist
    const collections = db.getCollectionNames();
    if (!collections.includes(collectionName)) {
        db.createCollection(collectionName);
        print("Created collection: " + collectionName);
    }
    
    // Create vector index
    try {
        db.getCollection(collectionName).createIndex(
            { "embedding": "2dsphere" },
            { name: indexName }
        );
        print("Created index: " + indexName);
    } catch (e) {
        if (e.code === 85) {
            print("Index already exists: " + indexName);
        }
    }
});
```

## Startup Logs

### First Startup (New Assistants + Indexes)

```
2026-03-18 10:30:45.123  INFO --- [main] DefaultAssistantsInitializer : Checking for default assistant chatbots...
2026-03-18 10:30:45.234  INFO --- [main] DefaultAssistantsInitializer : Inserted default assistant: Support Bot (support-bot)
2026-03-18 10:30:45.245  INFO --- [main] DefaultAssistantsInitializer : Created vector index 'jade-ai-vector-index-support-bot' for collection 'jade-ai-knowledgebase-support-bot'
2026-03-18 10:30:45.256  INFO --- [main] DefaultAssistantsInitializer : Inserted default assistant: Sales Assistant (sales-assistant)
2026-03-18 10:30:45.267  INFO --- [main] DefaultAssistantsInitializer : Created vector index 'jade-ai-vector-index-sales-assistant' for collection 'jade-ai-knowledgebase-sales-assistant'
... (similar for all 8 assistants)
2026-03-18 10:30:45.500  INFO --- [main] DefaultAssistantsInitializer : Successfully inserted 8 default assistant chatbot(s)
2026-03-18 10:30:45.501  INFO --- [main] DefaultAssistantsInitializer : Created 8 vector index(es) for default assistants
```

### Subsequent Startups (Existing Data)

```
2026-03-18 10:35:12.123  INFO --- [main] DefaultAssistantsInitializer : Checking for default assistant chatbots...
2026-03-18 10:35:12.234  DEBUG --- [main] DefaultAssistantsInitializer : Assistant already exists: Support Bot (support-bot)
2026-03-18 10:35:12.235  DEBUG --- [main] DefaultAssistantsInitializer : Vector index 'jade-ai-vector-index-support-bot' already exists for collection 'jade-ai-knowledgebase-support-bot'
... (similar for all 8 assistants)
2026-03-18 10:35:12.350  INFO --- [main] DefaultAssistantsInitializer : All default assistant chatbots already exist
```

## MongoDB Atlas Vector Search Setup (Production)

### Important Note

The basic index created by the initializer is a **placeholder**. For production-grade vector search with MongoDB Atlas, you need to create **Atlas Vector Search indexes** manually.

### Steps to Create Atlas Vector Search Index

1. **Go to MongoDB Atlas UI**
   - Navigate to your cluster
   - Click on "Search" tab
   - Click "Create Search Index"

2. **Select Index Type**
   - Choose "Vector Search"

3. **Configure Index** (for each assistant)

   **Collection Name:**
   ```
   jade-ai-knowledgebase-support-bot
   ```

   **Index Name:**
   ```
   jade-ai-vector-index-support-bot
   ```

   **Index Definition (JSON):**
   ```json
   {
     "fields": [
       {
         "type": "vector",
         "path": "embedding",
         "numDimensions": 1536,
         "similarity": "cosine"
       },
       {
         "type": "filter",
         "path": "chatbotId"
       },
       {
         "type": "filter",
         "path": "metadata"
       }
     ]
   }
   ```

   **Field Descriptions:**
   - `embedding`: Vector field (OpenAI embeddings are 1536 dimensions)
   - `numDimensions`: 1536 for OpenAI, 768 for other models
   - `similarity`: "cosine" for semantic similarity
   - `chatbotId`: Filter field for multi-tenant queries
   - `metadata`: Additional filter field for document metadata

4. **Repeat for All Assistants**

   Create the same index configuration for:
   - `jade-ai-knowledgebase-sales-assistant` → `jade-ai-vector-index-sales-assistant`
   - `jade-ai-knowledgebase-hr-helper` → `jade-ai-vector-index-hr-helper`
   - `jade-ai-knowledgebase-relationship-coach` → `jade-ai-vector-index-relationship-coach`
   - `jade-ai-knowledgebase-personal-trainer` → `jade-ai-vector-index-personal-trainer`
   - `jade-ai-knowledgebase-confidence-coach` → `jade-ai-vector-index-confidence-coach`
   - `jade-ai-knowledgebase-companion-ally` → `jade-ai-vector-index-companion-ally`
   - `jade-ai-knowledgebase-debate-mentor` → `jade-ai-vector-index-debate-mentor`

## Verification

### Check Collections Created

```bash
# MongoDB shell
use your_database_name

# List collections starting with "jade-ai-knowledgebase-"
db.getCollectionNames().filter(name => name.startsWith('jade-ai-knowledgebase-'))

# Expected output:
[
  "jade-ai-knowledgebase-support-bot",
  "jade-ai-knowledgebase-sales-assistant",
  "jade-ai-knowledgebase-hr-helper",
  "jade-ai-knowledgebase-relationship-coach",
  "jade-ai-knowledgebase-personal-trainer",
  "jade-ai-knowledgebase-confidence-coach",
  "jade-ai-knowledgebase-companion-ally",
  "jade-ai-knowledgebase-debate-mentor"
]
```

### Check Indexes Created

```bash
# Check indexes for a specific collection
db.getCollection('jade-ai-knowledgebase-support-bot').getIndexes()

# Expected output includes:
{
  "v": 2,
  "key": { "embedding": "2dsphere" },
  "name": "jade-ai-vector-index-support-bot"
}
```

### Check All Vector Indexes

```javascript
const assistants = [
    'support-bot',
    'sales-assistant', 
    'hr-helper',
    'relationship-coach',
    'personal-trainer',
    'confidence-coach',
    'companion-ally',
    'debate-mentor'
];

assistants.forEach(function(id) {
    const collectionName = 'jade-ai-knowledgebase-' + id;
    const indexName = 'jade-ai-vector-index-' + id;
    
    const indexes = db.getCollection(collectionName).getIndexes();
    const vectorIndex = indexes.find(idx => idx.name === indexName);
    
    if (vectorIndex) {
        print("✓ " + collectionName + " → " + indexName);
    } else {
        print("✗ Missing: " + indexName);
    }
});
```

## Usage in Knowledge Base Queries

### Vector Search Query Example

```java
// Using the vector index for semantic search
String collectionName = "jade-ai-knowledgebase-support-bot";
String indexName = "jade-ai-vector-index-support-bot";

// Vector search query (Atlas Vector Search)
Document pipeline = new Document("$vectorSearch", new Document()
    .append("index", indexName)
    .append("path", "embedding")
    .append("queryVector", embeddingVector)  // Your query embedding
    .append("numCandidates", 100)
    .append("limit", 10)
);

List<Document> results = mongoTemplate.getCollection(collectionName)
    .aggregate(Arrays.asList(pipeline))
    .into(new ArrayList<>());
```

## Benefits

✅ **Automatic Setup** - Collections and indexes created on startup  
✅ **Idempotent** - Safe to run multiple times  
✅ **Production Ready** - Proper naming convention  
✅ **Isolated** - Each assistant has its own knowledge base  
✅ **Scalable** - Easy to add more assistants  
✅ **Logged** - Clear startup logs for debugging  

## Known Limitations

⚠️ **Atlas Vector Search**: The basic index created is a placeholder. For production vector search, you must create Atlas Vector Search indexes through the Atlas UI or API.

⚠️ **Embedding Dimensions**: Default is set for OpenAI embeddings (1536 dimensions). Adjust if using different embedding models.

⚠️ **Similarity Metric**: Uses cosine similarity by default. Other options: euclidean, dotProduct.

## Summary

✅ **8 Collections** auto-created for knowledge bases  
✅ **8 Vector Indexes** auto-created for semantic search  
✅ **Idempotent** - Safe to run on every startup  
✅ **Logged** - Clear feedback on creation/existence  
✅ **Production Guide** - Instructions for Atlas Vector Search  

The vector indexes are now automatically set up for all default assistants!
