package net.ai.chatbot.service.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
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
 * Client credentials come from configuration and never live in source
 * or Mongo. Production and sandbox each have their own App ID, Cert ID,
 * and RuName. The connect request's environment selects which set and
 * which eBay host to use. Because eBay requires the same RuName as
 * {@code redirect_uri} in both the authorize URL and the token exchange,
 * that RuName is taken from the selected environment.
 */
@Service
@Slf4j
public class EbayOAuthService {

    /** Retire tokens 2 min before eBay says they expire to absorb clock skew. */
    private static final Duration EXPIRY_SKEW = Duration.ofMinutes(2);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Flat keys. Used as the production set when the production.* keys are blank. */
    @Value("${ebay.oauth.app-id:${EBAY_APP_ID:}}")
    private String ebayAppId;

    @Value("${ebay.oauth.cert-id:${EBAY_CERT_ID:}}")
    private String ebayCertId;

    @Value("${ebay.oauth.ru-name:${EBAY_RU_NAME:}}")
    private String ebayRuName;

    /**
     * Default only when a connect request omits the environment.
     * An explicit PRODUCTION or SANDBOX from the dialog always wins.
     */
    @Value("${ebay.oauth.sandbox:${EBAY_SANDBOX:false}}")
    private boolean sandboxDefault;

    @Value("${ebay.oauth.production.app-id:${EBAY_PRODUCTION_APP_ID:}}")
    private String productionAppId;

    @Value("${ebay.oauth.production.cert-id:${EBAY_PRODUCTION_CERT_ID:}}")
    private String productionCertId;

    @Value("${ebay.oauth.production.ru-name:${EBAY_PRODUCTION_RU_NAME:}}")
    private String productionRuName;

    @Value("${ebay.oauth.sandbox-credentials.app-id:${EBAY_SANDBOX_APP_ID:}}")
    private String sandboxAppId;

    @Value("${ebay.oauth.sandbox-credentials.cert-id:${EBAY_SANDBOX_CERT_ID:}}")
    private String sandboxCertId;

    @Value("${ebay.oauth.sandbox-credentials.ru-name:${EBAY_SANDBOX_RU_NAME:}}")
    private String sandboxRuName;

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

    /** Media API host (image upload to eBay Picture Services) for the given environment. */
    public String mediaHost(String environment) {
        return isSandbox(environment)
                ? "https://apim.sandbox.ebay.com"
                : "https://apim.ebay.com";
    }

    /** RuName for the default environment (production unless {@code ebay.oauth.sandbox=true}). */
    public String ruName() {
        return ruName(null);
    }

    /** RuName for the authorize redirect_uri and the token exchange of this environment. */
    public String ruName(String environment) {
        return credentialsFor(environment).ruName;
    }

    /** App ID (client id) for the default environment. */
    public String appId() {
        return appId(null);
    }

    /** App ID (client id) registered for this environment. */
    public String appId(String environment) {
        return credentialsFor(environment).appId;
    }

    /**
     * Explicit {@code SANDBOX} or {@code PRODUCTION} from the connect
     * dialog selects that environment. The {@code ebay.oauth.sandbox}
     * flag applies only when the request omits an environment. Credential
     * shape never redirects a Production connect onto sandbox hosts.
     */
    public boolean isSandbox(String environment) {
        if (environment != null && !environment.isBlank()) {
            return "SANDBOX".equalsIgnoreCase(environment.trim());
        }
        return sandboxDefault;
    }

    public String normalizeEnvironment(String environment) {
        return isSandbox(environment) ? "SANDBOX" : "PRODUCTION";
    }

    @PostConstruct
    void logConfiguredTarget() {
        String productionId = appId("PRODUCTION");
        String sandboxId = appId("SANDBOX");
        log.info("eBay OAuth config defaultSandbox={} productionAppIdSuffix={} sandboxAppIdSuffix={} productionAuthorizeHost={} sandboxAuthorizeHost={}",
                sandboxDefault,
                suffix(productionId),
                suffix(sandboxId),
                authorizeHost("PRODUCTION"),
                authorizeHost("SANDBOX"));
    }

    private Credentials credentialsFor(String environment) {
        if (isSandbox(environment)) {
            return new Credentials(
                    firstNonBlank(sandboxAppId),
                    firstNonBlank(sandboxCertId),
                    firstNonBlank(sandboxRuName));
        }
        return new Credentials(
                firstNonBlank(productionAppId, ebayAppId),
                firstNonBlank(productionCertId, ebayCertId),
                firstNonBlank(productionRuName, ebayRuName));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static String suffix(String id) {
        if (id == null || id.isBlank()) return "none";
        String trimmed = id.trim();
        return trimmed.length() >= 4 ? trimmed.substring(trimmed.length() - 4) : trimmed;
    }

    // ── Token operations ──────────────────────────────────────────────────────

    /**
     * Exchange a one-time {@code code} for a refresh token + access token.
     * Callers should persist the refresh token (encrypted) and discard
     * the code. The {@code redirect_uri} parameter is the caller's
     * registered RuName.
     */
    public Map<String, Object> exchangeAuthorizationCode(String code, String environment) {
        requireCredentials(environment);
        log.info("eBay OAuth: exchanging authorization code for refresh token ({} env, host={})",
                normalizeEnvironment(environment), tokenHost(environment));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",   "authorization_code");
        form.add("code",         code);
        form.add("redirect_uri", ruName(environment));

        String raw = post(tokenHost(environment) + "/identity/v1/oauth2/token", form, environment);
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
        requireCredentials(environment);
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
            raw = post(tokenHost(environment) + "/identity/v1/oauth2/token", form, environment);
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

    private String post(String url, MultiValueMap<String, String> form, String environment) {
        Credentials credentials = credentialsFor(environment);
        String basic = Base64.getEncoder().encodeToString(
                (credentials.appId + ":" + credentials.certId).getBytes(StandardCharsets.UTF_8));
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

    private void requireCredentials(String environment) {
        Credentials credentials = credentialsFor(environment);
        if (credentials.appId.isBlank() || credentials.certId.isBlank() || credentials.ruName.isBlank()) {
            String label = isSandbox(environment) ? "sandbox" : "production";
            throw new RuntimeException(
                    "eBay OAuth " + label + " credentials are not configured. Set the "
                            + label + " app id, cert id, and RuName.");
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

    private record Credentials(String appId, String certId, String ruName) {}
}
