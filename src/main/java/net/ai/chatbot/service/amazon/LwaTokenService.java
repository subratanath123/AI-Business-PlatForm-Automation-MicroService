package net.ai.chatbot.service.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin client for Amazon's Login-with-Amazon (LWA) token service.
 * <p>
 * Handles the two flows we need:
 * <ul>
 *   <li>{@link #exchangeAuthorizationCode} — one-off at connect time, swaps
 *       an {@code spapi_oauth_code} for a long-lived refresh token.</li>
 *   <li>{@link #getAccessToken} — ongoing, mints short-lived access tokens
 *       from a refresh token. Tokens are cached per refresh-token for up to
 *       ~50 min to avoid hammering LWA.</li>
 * </ul>
 * <p>
 * Both LWA client credentials come from environment variables
 * ({@code AMAZON_LWA_CLIENT_ID} / {@code AMAZON_LWA_CLIENT_SECRET}) so they
 * never live in source or Mongo.
 */
@Service
@Slf4j
public class LwaTokenService {

    private static final String LWA_TOKEN_URL = "https://api.amazon.com/auth/o2/token";
    /** Retire tokens 2 min before Amazon says they expire to absorb clock skew. */
    private static final Duration EXPIRY_SKEW = Duration.ofMinutes(2);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${amazon.lwa.client-id:${AMAZON_LWA_CLIENT_ID:}}")
    private String lwaClientId;

    @Value("${amazon.lwa.client-secret:${AMAZON_LWA_CLIENT_SECRET:}}")
    private String lwaClientSecret;

    private final WebClient.Builder webClientBuilder;

    /** Keyed by refresh token so multiple integrations share the same cache. */
    private final Map<String, CachedAccessToken> tokenCache = new ConcurrentHashMap<>();

    public LwaTokenService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * Exchange a one-time {@code spapi_oauth_code} for a refresh token. Callers
     * should persist the refresh token (encrypted) and discard the code.
     *
     * @return a map containing {@code refresh_token}, {@code access_token},
     *         {@code expires_in} etc.
     */
    public Map<String, Object> exchangeAuthorizationCode(String code, String redirectUri) {
        requireCredentials();
        log.info("LWA: exchanging authorization code for refresh token");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",    "authorization_code");
        form.add("code",          code);
        form.add("redirect_uri",  redirectUri);
        form.add("client_id",     lwaClientId);
        form.add("client_secret", lwaClientSecret);

        String raw = webClientBuilder.build()
                .post()
                .uri(LWA_TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return parseTokenResponse(raw);
    }

    /**
     * Returns a cached SP-API access token for the given refresh token,
     * minting a new one from LWA if the cached value is missing or
     * expiring soon.
     */
    public String getAccessToken(String refreshToken) {
        requireCredentials();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("Cannot mint access token without a refresh token");
        }
        CachedAccessToken cached = tokenCache.get(refreshToken);
        if (cached != null && cached.expiresAt.isAfter(Instant.now().plus(EXPIRY_SKEW))) {
            return cached.token;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",    "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id",     lwaClientId);
        form.add("client_secret", lwaClientSecret);

        String raw;
        try {
            raw = webClientBuilder.build()
                    .post()
                    .uri(LWA_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            tokenCache.remove(refreshToken);
            throw new RuntimeException("Failed to refresh Amazon LWA token: " + e.getMessage(), e);
        }

        Map<String, Object> parsed = parseTokenResponse(raw);
        String accessToken = Objects.toString(parsed.get("access_token"), null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("LWA response did not contain an access_token");
        }
        int expiresIn = toInt(parsed.get("expires_in"), 3600);
        tokenCache.put(refreshToken, new CachedAccessToken(
                accessToken, Instant.now().plusSeconds(expiresIn)));
        return accessToken;
    }

    /** Drops the cached access token for a refresh token (useful on 401s). */
    public void invalidate(String refreshToken) {
        if (refreshToken != null) tokenCache.remove(refreshToken);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requireCredentials() {
        if (lwaClientId == null || lwaClientId.isBlank()
                || lwaClientSecret == null || lwaClientSecret.isBlank()) {
            throw new RuntimeException(
                    "Amazon LWA credentials are not configured. Set AMAZON_LWA_CLIENT_ID "
                            + "and AMAZON_LWA_CLIENT_SECRET in the environment.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTokenResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("Empty response from LWA token endpoint");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            if (node.has("error")) {
                String err  = node.path("error").asText();
                String desc = node.path("error_description").asText();
                throw new RuntimeException("LWA error: " + err
                        + (desc.isBlank() ? "" : " — " + desc));
            }
            return OBJECT_MAPPER.convertValue(node, Map.class);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Could not parse LWA token response", e);
        }
    }

    private static int toInt(Object v, int fallback) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    /** Package-private for testing. */
    record CachedAccessToken(String token, Instant expiresAt) {}
}
