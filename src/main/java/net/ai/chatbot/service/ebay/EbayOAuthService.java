package net.ai.chatbot.service.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin client for eBay's OAuth 2.0 Identity service.
 * <p>
 * Handles the two flows we need:
 * <ul>
 *   <li>{@link #exchangeAuthorizationCode} — one-off at connect time,
 *       swaps a one-time {@code code} for a refresh token + access token.</li>
 *   <li>{@link #getAccessToken} — ongoing, mints short-lived access
 *       tokens from a stored refresh token. Tokens are cached per
 *       refresh-token for ~55 min to avoid hammering eBay.</li>
 * </ul>
 * <p>
 * Client credentials ({@code EBAY_APP_ID} / {@code EBAY_CERT_ID}) come
 * from environment variables and never live in source or Mongo.
 * Because eBay requires the same RuName as {@code redirect_uri} in both
 * the authorize URL and the token exchange, callers pass it in here.
 */
@Service
@Slf4j
public class EbayOAuthService {

    /** Retire tokens 2 min before eBay says they expire to absorb clock skew. */
    private static final Duration EXPIRY_SKEW = Duration.ofMinutes(2);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${ebay.oauth.app-id:${EBAY_APP_ID:}}")
    private String ebayAppId;

    @Value("${ebay.oauth.cert-id:${EBAY_CERT_ID:}}")
    private String ebayCertId;

    @Value("${ebay.oauth.ru-name:${EBAY_RU_NAME:}}")
    private String ebayRuName;

    @Value("${ebay.oauth.sandbox:${EBAY_SANDBOX:false}}")
    private boolean sandboxDefault;

    private final WebClient.Builder webClientBuilder;

    /** Keyed by refresh token so multiple integrations share the same cache. */
    private final Map<String, CachedAccessToken> tokenCache = new ConcurrentHashMap<>();

    public EbayOAuthService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    // ── Public helpers ────────────────────────────────────────────────────────

    /** OAuth authorize URL for the given environment. */
    public String authorizeHost(String environment) {
        return isSandbox(environment)
                ? "https://auth.sandbox.ebay.com"
                : "https://auth.ebay.com";
    }

    /** Identity/OAuth token host for the given environment. */
    public String tokenHost(String environment) {
        return isSandbox(environment)
                ? "https://api.sandbox.ebay.com"
                : "https://api.ebay.com";
    }

    /** REST API host for the given environment. */
    public String apiHost(String environment) {
        return tokenHost(environment);
    }

    /** Configured RuName — used as both the authorize redirect_uri and in token exchange. */
    public String ruName() {
        return ebayRuName;
    }

    public String appId() {
        return ebayAppId;
    }

    public boolean isSandbox(String environment) {
        if (environment == null || environment.isBlank()) return sandboxDefault;
        return "SANDBOX".equalsIgnoreCase(environment);
    }

    public String normalizeEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            return sandboxDefault ? "SANDBOX" : "PRODUCTION";
        }
        String e = environment.trim().toUpperCase(Locale.ROOT);
        return "SANDBOX".equals(e) ? "SANDBOX" : "PRODUCTION";
    }

    // ── Token operations ──────────────────────────────────────────────────────

    /**
     * Exchange a one-time {@code code} for a refresh token + access token.
     * Callers should persist the refresh token (encrypted) and discard
     * the code. The {@code redirect_uri} parameter is the caller's
     * registered RuName.
     */
    public Map<String, Object> exchangeAuthorizationCode(String code, String environment) {
        requireCredentials();
        log.info("eBay OAuth: exchanging authorization code for refresh token ({} env)", environment);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",   "authorization_code");
        form.add("code",         code);
        form.add("redirect_uri", ebayRuName);

        String raw = post(tokenHost(environment) + "/identity/v1/oauth2/token", form);
        return parseTokenResponse(raw);
    }

    /**
     * Returns a cached eBay access token for the given refresh token,
     * minting a new one from eBay if the cached value is missing or
     * expiring soon. The refresh token request must echo the scopes
     * originally granted by the user — we pass them through from the
     * caller (typically read back from the integration record).
     */
    public String getAccessToken(String refreshToken, String scopes, String environment) {
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
        if (scopes != null && !scopes.isBlank()) {
            form.add("scope", scopes);
        }

        String raw;
        try {
            raw = post(tokenHost(environment) + "/identity/v1/oauth2/token", form);
        } catch (Exception e) {
            tokenCache.remove(refreshToken);
            throw new RuntimeException("Failed to refresh eBay access token: " + e.getMessage(), e);
        }

        Map<String, Object> parsed = parseTokenResponse(raw);
        String accessToken = Objects.toString(parsed.get("access_token"), null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("eBay response did not contain an access_token");
        }
        int expiresIn = toInt(parsed.get("expires_in"), 7200);
        tokenCache.put(refreshToken, new CachedAccessToken(
                accessToken, Instant.now().plusSeconds(expiresIn)));
        return accessToken;
    }

    /** Drops the cached access token for a refresh token (useful on 401s). */
    public void invalidate(String refreshToken) {
        if (refreshToken != null) tokenCache.remove(refreshToken);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private String post(String url, MultiValueMap<String, String> form) {
        String basic = Base64.getEncoder().encodeToString(
                (ebayAppId + ":" + ebayCertId).getBytes(StandardCharsets.UTF_8));
        return webClientBuilder.build()
                .post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private void requireCredentials() {
        if (ebayAppId == null || ebayAppId.isBlank()
                || ebayCertId == null || ebayCertId.isBlank()
                || ebayRuName == null || ebayRuName.isBlank()) {
            throw new RuntimeException(
                    "eBay OAuth credentials are not configured. Set EBAY_APP_ID, "
                            + "EBAY_CERT_ID and EBAY_RU_NAME in the environment.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTokenResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("Empty response from eBay token endpoint");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            if (node.has("error")) {
                String err  = node.path("error").asText();
                String desc = node.path("error_description").asText();
                throw new RuntimeException("eBay OAuth error: " + err
                        + (desc.isBlank() ? "" : " — " + desc));
            }
            return OBJECT_MAPPER.convertValue(node, Map.class);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Could not parse eBay token response", e);
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
