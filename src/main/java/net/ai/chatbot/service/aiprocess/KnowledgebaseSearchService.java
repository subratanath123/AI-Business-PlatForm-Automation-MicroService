package net.ai.chatbot.service.aiprocess;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.ai.chatbot.service.aiprocess.KnowledgebaseIngestionService.EMBEDDING_FIELD;
import static net.ai.chatbot.service.aiprocess.KnowledgebaseIngestionService.TEXT_FIELD;

/**
 * Finds knowledge chunks for a question. Uses Atlas vector search when the index is ready,
 * and falls back to in-process cosine similarity so chat still works on a fresh index.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgebaseSearchService {

    private static final int LIMIT = 6;
    private static final double MIN_SCORE = 0.25;
    /** Chunks ingested by the old n8n workflow store their text under "content". */
    private static final String LEGACY_TEXT_FIELD = "content";

    private final MongoTemplate mongoTemplate;

    public List<String> search(String collectionName, String indexName, List<Double> queryVector) {
        if (collectionName == null || collectionName.isBlank() || queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }
        if (!mongoTemplate.collectionExists(collectionName)) {
            return List.of();
        }

        List<ScoredText> atlasHits = vectorSearch(collectionName, indexName, queryVector);
        if (!atlasHits.isEmpty()) {
            return atlasHits.stream().map(ScoredText::text).toList();
        }
        return cosineSearch(collectionName, queryVector);
    }

    private List<ScoredText> vectorSearch(String collectionName, String indexName, List<Double> queryVector) {
        if (indexName == null || indexName.isBlank()) {
            return List.of();
        }
        try {
            Document vectorSearch = new Document("$vectorSearch", new Document()
                    .append("index", indexName)
                    .append("path", EMBEDDING_FIELD)
                    .append("queryVector", queryVector)
                    .append("numCandidates", 80)
                    .append("limit", LIMIT));
            Document project = new Document("$project", new Document()
                    .append(TEXT_FIELD, 1)
                    .append(LEGACY_TEXT_FIELD, 1)
                    .append("score", new Document("$meta", "vectorSearchScore")));

            List<ScoredText> hits = new ArrayList<>();
            for (Document document : mongoTemplate.getCollection(collectionName).aggregate(List.of(vectorSearch, project))) {
                String text = textOf(document);
                double score = document.get("score") instanceof Number number ? number.doubleValue() : 1.0;
                if (text != null && !text.isBlank() && score >= MIN_SCORE) {
                    hits.add(new ScoredText(text, score));
                }
            }
            return hits;
        } catch (Exception e) {
            log.info("Atlas vector search unavailable on '{}', using local similarity: {}", collectionName, e.getMessage());
            return List.of();
        }
    }

    private List<String> cosineSearch(String collectionName, List<Double> queryVector) {
        Query query = new Query().limit(400);
        query.fields().include(TEXT_FIELD).include(LEGACY_TEXT_FIELD).include(EMBEDDING_FIELD);
        List<Document> documents = mongoTemplate.find(query, Document.class, collectionName);

        List<ScoredText> scored = new ArrayList<>();
        for (Document document : documents) {
            String text = textOf(document);
            List<Double> embedding = toDoubles(document.get(EMBEDDING_FIELD));
            if (text == null || text.isBlank() || embedding.isEmpty()) {
                continue;
            }
            double score = cosine(queryVector, embedding);
            if (score >= MIN_SCORE) {
                scored.add(new ScoredText(text, score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredText::score).reversed());
        return scored.stream().limit(LIMIT).map(ScoredText::text).toList();
    }

    private static String textOf(Document document) {
        String text = document.getString(TEXT_FIELD);
        return text == null || text.isBlank() ? document.getString(LEGACY_TEXT_FIELD) : text;
    }

    private static List<Double> toDoubles(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<Double> doubles = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value instanceof Number number) {
                doubles.add(number.doubleValue());
            }
        }
        return doubles;
    }

    private static double cosine(List<Double> left, List<Double> right) {
        int length = Math.min(left.size(), right.size());
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < length; i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record ScoredText(String text, double score) {
    }
}
