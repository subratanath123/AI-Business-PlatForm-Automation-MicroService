package net.ai.chatbot.service.aiprocess;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgebaseSearchServiceTest {

    @Test
    void readsChunksIngestedByN8nUnderContentField() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.collectionExists("kb")).thenReturn(true);
        when(mongo.find(any(Query.class), eq(Document.class), eq("kb"))).thenReturn(List.of(
                new Document("content", "We deliver across Australia.").append("embedding", List.of(1.0, 0.0)),
                new Document("text", "Native chunk.").append("embedding", List.of(0.9, 0.1)),
                new Document("content", "Unrelated.").append("embedding", List.of(0.0, 1.0))));

        List<String> hits = new KnowledgebaseSearchService(mongo).search("kb", null, List.of(1.0, 0.0));

        assertEquals(List.of("We deliver across Australia.", "Native chunk."), hits);
    }
}
