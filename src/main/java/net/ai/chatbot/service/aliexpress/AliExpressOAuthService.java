package net.ai.chatbot.service.aliexpress;

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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AliExpress Open Platform OAuth 2.0 (authorization code) + refresh.
 *
 * @see <a href="https://developers.aliexpress.com/">AliExpress Developers</a>
 */
@Service
@Slf4j
public class AliExpressOAuthService {

    private static final Duration EXPIRY_SKEW = Duration.ofMinutes(2);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${aliexpress.oauth.app-key:${ALIEXPRESS_APP_KEY:}}")
    private String appKey;

    @Value("${aliexpress.oauth.app-secret:${ALIEXPRESS_APP_SECRET:}}")
    private String appSecret;

    @Value("${aliexpress.oauth.redirect-uri:${ALIEXPRESS_REDIRECT_URI:}}")
    private String redirectUri;

    @Value("${aliexpress.oauth.authorize-host:https://oauth.aliexpress.com}")
    private String authorizeHost;

    @Value("${aliexpress.oauth.token-host:https://oauth.aliexpress.com}")
    private String tokenHost;

    private final WebClient.Builder webClientBuilder;

    private final Map<String, CachedAccessToken> tokenCache = new ConcurrentHashMap<>();

    public AliExpressOAuthService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public String appKey() {
        return appKey;
    }

    public String redirectUri() {
        return redirectUri;
    }

    public String buildAuthorizeUrl(String state) {
        requireCredentials();
        return authorizeHost + "/authorize"
                + "?response_type=code"
                + "&client_id=" + urlEnc(appKey)
                + "&redirect_uri=" + urlEnc(redirectUri)
                + "&state=" + urlEnc(state)
                + "&sp=ae";
    }

    public Map<String, Object> exchangeAuthorizationCode(String code) {
        requireCredentials();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",    "authorization_code");
        form.add("client_id",     appKey);
        form.add("client_secret", appSecret);
        form.add("code",          code);
        form.add("redirect_uri",  redirectUri);
        return postToken(form);
    }

    public String getAccessToken(String refreshToken) {
        requireCredentials();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("Cannot mint AliExpress session without a refresh token");
        }
        CachedAccessToken cached = tokenCache.get(refreshToken);
        if (cached != null && cached.expiresAt.isAfter(Instant.now().plus(EXPIRY_SKEW))) {
            return cached.token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",    "refresh_token");
        form.add("client_id",     appKey);
        form.add("client_secret", appSecret);
        form.add("refresh_token", refreshToken);
        Map<String, Object> parsed;
        try {
            parsed = postToken(form);
        } catch (Exception e) {
            tokenCache.remove(refreshToken);
            throw new RuntimeException("Failed to refresh AliExpress access token: " + e.getMessage(), e);
        }
        String access = Objects.toString(parsed.get("access_token"), null);
        if (access == null || access.isBlank()) {
            throw new RuntimeException("AliExpress token response did not contain access_token");
        }
        int expiresIn = toInt(parsed.get("expires_in"), 3600);
        tokenCache.put(refreshToken, new CachedAccessToken(access, Instant.now().plusSeconds(expiresIn)));
        return access;
    }

    public void invalidate(String refreshToken) {
        if (refreshToken != null) tokenCache.remove(refreshToken);
    }

    private Map<String, Object> postToken(MultiValueMap<String, String> form) {
        String url = tokenHost + "/token";
        log.info("AliExpress OAuth: POST {}", url);
        String raw = webClientBuilder.build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parseTokenJson(raw);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTokenJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("Empty response from AliExpress token endpoint");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            if (node.has("error")) {
                String err = node.path("error").asText();
                String desc = node.path("error_description").asText();
                throw new RuntimeException("AliExpress OAuth error: " + err
                        + (desc == null || desc.isBlank() ? "" : " — " + desc));
            }
            return OBJECT_MAPPER.convertValue(node, Map.class);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Could not parse AliExpress token response", e);
        }
    }

    private void requireCredentials() {
        if (appKey == null || appKey.isBlank()
                || appSecret == null || appSecret.isBlank()
                || redirectUri == null || redirectUri.isBlank()) {
            throw new RuntimeException(
                    "AliExpress OAuth is not configured. Set ALIEXPRESS_APP_KEY, ALIEXPRESS_APP_SECRET, "
                            + "and ALIEXPRESS_REDIRECT_URI (must match the callback URL registered on the Open Platform).");
        }
    }

    private static String urlEnc(String v) {
        return java.net.URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    private static int toInt(Object v, int fallback) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    record CachedAccessToken(String token, Instant expiresAt) {}
}
