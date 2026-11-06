package net.ai.chatbot.service.aiprocess;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the n8n knowledge-base training webhook.
 * Text is chunked, embedded with OpenAI, and stored in the chatbot's Mongo collection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgebaseIngestionService {

    static final String TEXT_FIELD = "text";
    static final String EMBEDDING_FIELD = "embedding";

    private static final int CHUNK_SIZE = 1400;
    private static final int CHUNK_OVERLAP = 200;
    private static final int MAX_CHUNKS = 40;

    private final MongoTemplate mongoTemplate;
    private final OpenAiApiClient openAiApiClient;
    private final DocumentTextExtractor documentTextExtractor;

    public int ingestFile(byte[] fileBytes, String fileName, String email, String collectionName, String contentType) {
        String text = documentTextExtractor.extract(fileBytes, fileName, contentType);
        return ingestText(text, email, collectionName, contentType, fileName);
    }

    public int ingestText(String text, String email, String collectionName, String dataType, String source) {
        List<String> chunks = chunk(text);
        if (chunks.isEmpty()) {
            log.info("No text to index for source '{}'", source);
            return 0;
        }
        if (!openAiApiClient.isConfigured()) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }

        if (!mongoTemplate.collectionExists(collectionName)) {
            mongoTemplate.createCollection(collectionName);
        }

        List<List<Double>> vectors = openAiApiClient.embed(chunks);
        List<Document> documents = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < chunks.size(); i++) {
            Document metadata = new Document()
                    .append("email", email)
                    .append("dataType", dataType)
                    .append("source", source)
                    .append("chunk", i);
            documents.add(new Document()
                    .append(TEXT_FIELD, chunks.get(i))
                    .append(EMBEDDING_FIELD, vectors.get(i))
                    .append("metadata", metadata)
                    .append("createdAt", now.toString()));
        }
        mongoTemplate.getCollection(collectionName).insertMany(documents);
        log.info("Indexed {} chunk(s) into '{}' from '{}'", documents.size(), collectionName, source);
        return documents.size();
    }

    static List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length() && chunks.size() < MAX_CHUNKS) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            if (end < normalized.length()) {
                int lineBreak = normalized.lastIndexOf('\n', end);
                if (lineBreak > start + CHUNK_SIZE / 2) {
                    end = lineBreak;
                } else {
                    int space = normalized.lastIndexOf(' ', end);
                    if (space > start + CHUNK_SIZE / 2) {
                        end = space;
                    }
                }
            }
            String piece = normalized.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= normalized.length()) {
                break;
            }
            int next = Math.max(end - CHUNK_OVERLAP, start + 1);
            start = next;
        }
        return chunks;
    }
}
