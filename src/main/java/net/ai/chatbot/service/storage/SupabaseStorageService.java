package net.ai.chatbot.service.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Supabase Storage API.
 * Configuration (url, service-role-key) is provided by config server.
 * Handles file uploads, deletions, and signed URL generation.
 */
@Service
@Slf4j
public class SupabaseStorageService {

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key:}")
    private String serviceRoleKey;

    @Value("${supabase.bucket:social-media-assets}")
    private String bucket;

    /**
     * Default lifetime of signed URLs returned by {@link #createSignedUrl} and
     * {@link #createSignedUrls}. Kept short enough that leaked URLs are low-value,
     * but long enough that a page view (or a slow CDN fetch by Shopify) won't race
     * an expiry. Callers can override with the overloaded variants.
     */
    @Value("${supabase.signed-url-expiry-seconds:3600}")
    private int signedUrlExpirySeconds;

    /**
     * If the Supabase bucket is marked public in the dashboard, the {@code /public/}
     * URL format works forever with no signing required. Setting this to {@code true}
     * skips the sign round-trip entirely and returns the permanent public URL.
     *
     * Default is {@code false} (bucket assumed private) — safer: any miscon­figuration
     * falls back to short-lived signed URLs instead of accidentally leaking assets.
     */
    @Value("${supabase.bucket-public:false}")
    private boolean bucketPublic;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Validate that Supabase configuration is present and properly formatted.
     * Throws exception if config is missing or invalid.
     */
    private void validateConfig() {
        if (supabaseUrl == null || supabaseUrl.isBlank()) {
            throw new IllegalStateException(
                "Supabase URL is not configured. Check config server settings. " +
                "Expected property: supabase.url"
            );
        }
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new IllegalStateException(
                "Supabase service role key is not configured. Check config server settings. " +
                "Expected property: supabase.service-role-key"
            );
        }
        
        // Validate service role key format (should be long JWT-like string)
        if (serviceRoleKey.length() < 100) {
            log.warn(
                "Supabase service role key appears too short ({} chars). " +
                "This might be the wrong key. Ensure you're using the service_role key, not anon key. " +
                "Found in Supabase Dashboard → Settings → API → service_role",
                serviceRoleKey.length()
            );
        }
        
        // Check for common issues
        if (serviceRoleKey.contains(" ") || serviceRoleKey.contains("\n") || serviceRoleKey.contains("\t")) {
            throw new IllegalStateException(
                "Supabase service role key contains whitespace. " +
                "This is usually a copy-paste error. Remove any spaces, tabs, or newlines."
            );
        }
    }

    /**
     * Upload bytes to Supabase Storage.
     * Returns the public CDN URL.
     *
     * @param objectPath Path within the bucket (e.g., social-posts/user@example.com/123_photo.jpg)
     * @param bytes File content
     * @param contentType MIME type
     * @return Public CDN URL
     */
    public String upload(String objectPath, byte[] bytes, String contentType) {
        validateConfig();

        String url = supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.set("Content-Type", contentType != null ? contentType : "application/octet-stream");
        headers.set("x-upsert", "false"); // Don't overwrite existing files

        HttpEntity<byte[]> request = new HttpEntity<>(bytes, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Supabase upload failed: " + response.getBody());
            }

            // Construct public URL
            String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;
            log.info("Uploaded to Supabase: {}", publicUrl);
            return publicUrl;

        } catch (Exception e) {
            log.error("Failed to upload to Supabase: {}", e.getMessage(), e);

            // Hostname can't be resolved → the configured supabase.url points at a
            // Supabase project that doesn't exist (typo, deleted project, or free-tier
            // project purged after >90 days of inactivity).
            Throwable root = rootCause(e);
            if (root instanceof java.net.UnknownHostException) {
                String host = hostFromSupabaseUrl();
                throw new RuntimeException(
                    "Supabase project hostname '" + host + "' does not resolve (NXDOMAIN). " +
                    "The project in supabase.url likely doesn't exist. Check: " +
                    "(1) Typo in the project ref, " +
                    "(2) Project was deleted from Supabase Dashboard, " +
                    "(3) Free-tier project purged after long inactivity. " +
                    "Verify the URL at Supabase Dashboard → Settings → API → Project URL.",
                    e
                );
            }

            // Provide specific troubleshooting for common errors
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("Invalid Compact JWS")) {
                throw new RuntimeException(
                    "Supabase service role key is invalid. " +
                    "This usually means: (1) Wrong key (using anon instead of service_role), " +
                    "(2) Key was corrupted in copy-paste, (3) Key has changed. " +
                    "Get the service_role key from Supabase Dashboard → Settings → API → service_role",
                    e
                );
            } else if (errorMsg != null && errorMsg.contains("Unauthorized")) {
                throw new RuntimeException(
                    "Supabase returned Unauthorized (403). " +
                    "Verify the service role key is correct and valid at Supabase Dashboard → Settings → API",
                    e
                );
            } else if (errorMsg != null && errorMsg.contains("Unable to connect")) {
                throw new RuntimeException(
                    "Failed to connect to Supabase. Check: (1) Supabase URL is correct, " +
                    "(2) Internet connectivity, (3) Supabase project is active (not paused)",
                    e
                );
            }
            
            throw new RuntimeException("Supabase upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a time-limited signed URL for a single object.
     * Use this whenever you need a fresh URL — stored "public" URLs can actually
     * resolve to short-lived SAS URLs for private buckets and will stop working
     * after the underlying signing key rotates (typically every 24h on Supabase Cloud).
     *
     * @param objectPath Path within the bucket (must be non-null / non-blank)
     * @return Fully-qualified signed URL, or {@code null} if signing failed
     */
    public String createSignedUrl(String objectPath) {
        return createSignedUrl(objectPath, signedUrlExpirySeconds);
    }

    public String createSignedUrl(String objectPath, int expiresInSeconds) {
        if (objectPath == null || objectPath.isBlank()) return null;

        // Fast path: when the bucket is public, the non-expiring /public/ URL is valid
        // forever and no sign round-trip is required.
        if (bucketPublic && supabaseUrl != null && !supabaseUrl.isBlank()) {
            return publicUrlFor(objectPath);
        }

        try {
            validateConfig();
        } catch (Exception e) {
            log.warn("Cannot create signed URL — storage misconfigured: {}", e.getMessage());
            return null;
        }

        String url = supabaseUrl + "/storage/v1/object/sign/" + bucket + "/" + objectPath;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"expiresIn\": " + Math.max(60, expiresInSeconds) + "}";
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Signed URL request failed for {}: HTTP {}", objectPath, response.getStatusCode());
                return null;
            }
            JsonNode node = OBJECT_MAPPER.readTree(response.getBody());
            String signed = node.path("signedURL").asText(null);
            if (signed == null || signed.isBlank()) {
                signed = node.path("signedUrl").asText(null); // some versions use camelCase
            }
            if (signed == null || signed.isBlank()) return null;
            return supabaseUrl + "/storage/v1" + (signed.startsWith("/") ? signed : "/" + signed);
        } catch (Exception e) {
            log.warn("Failed to create signed URL for {}: {}", objectPath, e.getMessage());
            return null;
        }
    }

    /**
     * Generate signed URLs for many objects in a single HTTP call. This is the
     * efficient path for list endpoints — O(1) round-trips regardless of asset count.
     *
     * @param objectPaths Paths within the bucket
     * @return Map from objectPath → fully-qualified signed URL. Paths that failed
     *         to sign are simply absent from the map (callers should fall back to
     *         whatever URL they already have for those).
     */
    public Map<String, String> createSignedUrls(List<String> objectPaths) {
        return createSignedUrls(objectPaths, signedUrlExpirySeconds);
    }

    public Map<String, String> createSignedUrls(List<String> objectPaths, int expiresInSeconds) {
        if (objectPaths == null || objectPaths.isEmpty()) return Collections.emptyMap();

        // Dedupe + drop null/blank entries so we don't waste a round-trip slot.
        List<String> paths = new ArrayList<>();
        for (String p : objectPaths) {
            if (p != null && !p.isBlank() && !paths.contains(p)) paths.add(p);
        }
        if (paths.isEmpty()) return Collections.emptyMap();

        // Fast path: public bucket — build permanent /public/ URLs locally and
        // skip any Supabase round-trip.
        if (bucketPublic && supabaseUrl != null && !supabaseUrl.isBlank()) {
            Map<String, String> result = new HashMap<>();
            for (String p : paths) result.put(p, publicUrlFor(p));
            return result;
        }

        try {
            validateConfig();
        } catch (Exception e) {
            log.warn("Cannot create signed URLs — storage misconfigured: {}", e.getMessage());
            return Collections.emptyMap();
        }

        String url = supabaseUrl + "/storage/v1/object/sign/" + bucket;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("expiresIn", Math.max(60, expiresInSeconds));
            bodyMap.put("paths", paths);
            String body = OBJECT_MAPPER.writeValueAsString(bodyMap);

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Batch signed URL request failed: HTTP {}", response.getStatusCode());
                return Collections.emptyMap();
            }

            Map<String, String> result = new HashMap<>();
            JsonNode root = OBJECT_MAPPER.readTree(response.getBody());
            if (!root.isArray()) return Collections.emptyMap();

            for (JsonNode item : root) {
                // Supabase returns error as null (JsonNode.isNull()) on success
                JsonNode errorNode = item.path("error");
                if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                    String errText = errorNode.asText("");
                    if (!errText.isBlank()) {
                        log.debug("Sign failed for {}: {}", item.path("path").asText(""), errText);
                        continue;
                    }
                }
                String path = item.path("path").asText(null);
                String signed = item.path("signedURL").asText(null);
                if (signed == null || signed.isBlank()) {
                    signed = item.path("signedUrl").asText(null);
                }
                if (path != null && signed != null && !signed.isBlank()) {
                    result.put(path,
                            supabaseUrl + "/storage/v1" + (signed.startsWith("/") ? signed : "/" + signed));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to create batch signed URLs: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** Build the permanent public URL for an object (valid only on public buckets). */
    private String publicUrlFor(String objectPath) {
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;
    }

    /** Walk a cause chain to the deepest non-null cause. */
    private Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur;
    }

    /** Extract just the hostname from {@code supabaseUrl}, falling back to the full value. */
    private String hostFromSupabaseUrl() {
        if (supabaseUrl == null || supabaseUrl.isBlank()) return "<not configured>";
        try {
            return java.net.URI.create(supabaseUrl).getHost();
        } catch (Exception e) {
            return supabaseUrl;
        }
    }

    /**
     * Delete a file from Supabase Storage.
     *
     * @param objectPath Path within the bucket
     */
    public void delete(String objectPath) {
        validateConfig();

        String url = supabaseUrl + "/storage/v1/object/" + bucket;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Supabase expects a JSON body with "prefixes" array
        String body = "{\"prefixes\": [\"" + objectPath + "\"]}";

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);
            log.info("Deleted from Supabase: {}", objectPath);

        } catch (Exception e) {
            log.error("Failed to delete from Supabase: {}", e.getMessage(), e);
            // Don't throw - gracefully handle Supabase deletion failures
        }
    }
}
