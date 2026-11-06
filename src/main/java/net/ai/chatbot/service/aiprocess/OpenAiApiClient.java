package net.ai.chatbot.service.aiprocess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small OpenAI client for embeddings and chat completions, including tool calls.
 * Uses its own HTTP client so request interceptors do not attach end-user headers.
 */
@Service
@Slf4j
public class OpenAiApiClient {

    static final String EMBEDDING_MODEL = "text-embedding-3-small";
    static final int EMBEDDING_DIMENSIONS = 1536;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.base.url:https://api.openai.com/v1}")
    private String baseUrl;

    public OpenAiApiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(90_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public List<List<Double>> embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<List<Double>> vectors = new ArrayList<>();
        int batchSize = 32;
        for (int offset = 0; offset < inputs.size(); offset += batchSize) {
            List<String> batch = inputs.subList(offset, Math.min(offset + batchSize, inputs.size()));
            vectors.addAll(embedBatch(batch));
        }
        return vectors;
    }

    public ChatCompletion complete(String model, List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        JsonNode root = post("/chat/completions", body);
        JsonNode message = root.path("choices").path(0).path("message");
        if (message.isMissingNode()) {
            throw new IllegalStateException("OpenAI chat response did not include a message");
        }

        String content = message.path("content").isNull() ? "" : message.path("content").asText("");
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            toolCalls.add(new ToolCall(
                    call.path("id").asText(),
                    call.path("function").path("name").asText(),
                    call.path("function").path("arguments").asText("{}")
            ));
        }
        return new ChatCompletion(content, toolCalls);
    }

    private List<List<Double>> embedBatch(List<String> batch) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", EMBEDDING_MODEL);
        body.put("input", batch);
        JsonNode root = post("/embeddings", body);

        List<JsonNode> rows = new ArrayList<>();
        root.path("data").forEach(rows::add);
        rows.sort(Comparator.comparingInt(node -> node.path("index").asInt()));

        List<List<Double>> vectors = new ArrayList<>();
        for (JsonNode row : rows) {
            List<Double> vector = new ArrayList<>();
            for (JsonNode value : row.path("embedding")) {
                vector.add(value.asDouble());
            }
            if (vector.size() != EMBEDDING_DIMENSIONS) {
                throw new IllegalStateException("Unexpected embedding size " + vector.size()
                        + " from model " + EMBEDDING_MODEL);
            }
            vectors.add(vector);
        }
        if (vectors.size() != batch.size()) {
            throw new IllegalStateException("Embedding count did not match input count");
        }
        return vectors;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        if (!isConfigured()) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    trimBase() + path,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            if (response.getBody() == null) {
                throw new IllegalStateException("Empty response from OpenAI");
            }
            return objectMapper.readTree(response.getBody());
        } catch (RestClientResponseException e) {
            log.error("OpenAI {} failed with status {}", path, e.getStatusCode().value());
            throw new IllegalStateException("OpenAI request failed (" + e.getStatusCode().value() + ")");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI request failed: " + e.getMessage(), e);
        }
    }

    private String trimBase() {
        String url = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/v1")) {
            return url;
        }
        return url + "/v1";
    }

    public record ChatCompletion(String content, List<ToolCall> toolCalls) {
    }

    public record ToolCall(String id, String name, String argumentsJson) {
    }
}
