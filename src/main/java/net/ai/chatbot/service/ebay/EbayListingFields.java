package net.ai.chatbot.service.ebay;

import net.ai.chatbot.service.storage.LocalUserStorageService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure checks for the Sell Inventory payload. eBay's inventory service
 * answers with error 25001 when it cannot fetch an image URL, so local
 * and loopback addresses are never placed in {@code imageUrls}.
 */
final class EbayListingFields {

    static final int MAX_IMAGES = 24;
    static final int MAX_TITLE = 80;
    static final int MAX_SUBTITLE = 55;
    static final int MAX_DESCRIPTION = 4000;

    private static final Set<String> CONDITIONS = Set.of(
            "NEW", "LIKE_NEW", "NEW_OTHER", "NEW_WITH_DEFECTS",
            "CERTIFIED_REFURBISHED", "EXCELLENT_REFURBISHED", "VERY_GOOD_REFURBISHED",
            "GOOD_REFURBISHED", "SELLER_REFURBISHED",
            "USED_EXCELLENT", "USED_VERY_GOOD", "USED_GOOD", "USED_ACCEPTABLE",
            "FOR_PARTS_OR_NOT_WORKING", "PRE_OWNED_EXCELLENT", "PRE_OWNED_FAIR"
    );

    private static final Pattern ACTIVE_HTML = Pattern.compile(
            "(?is)<\\s*(script|iframe|object|embed|form|style)\\b[^>]*>.*?</\\s*\\1\\s*>"
                    + "|<\\s*(script|iframe|object|embed|form|style)\\b[^>]*/>");
    private static final Pattern MEDIA_IMAGE_URL = Pattern.compile("\"imageUrl\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MEDIA_MESSAGE = Pattern.compile("\"(?:longMessage|message)\"\\s*:\\s*\"([^\"]*)\"");
    /** Hosts that always mean this machine, whatever the configured base URLs say. */
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");

    private EbayListingFields() {}

    record DecodedImage(byte[] bytes, String contentType, String extension) {}

    /**
     * A picture URL that names a file in this app's local storage.
     * {@code relativePath} is set only when the URL carries the full
     * {@code users/{folder}/{kind}/{file}} path; {@code path} is the URL path
     * used for the loopback fallback; {@code ownHost} is false for a
     * Supabase URL, which is only checked against local copies.
     */
    record LocalImageRef(String fileName, String relativePath, String path, boolean ownHost) {}

    static boolean isPublicHttpsImage(String url) {
        URI uri = parseHttpUri(url);
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        return !isUnreachableHost(uri.getHost());
    }

    /** Host label used in user-facing errors. Never returns the full URL. */
    static String hostOf(String url) {
        if (url == null || url.isBlank()) return "empty";
        String trimmed = url.trim();
        if (trimmed.regionMatches(true, 0, "data:", 0, 5)) return "data-url";
        if (trimmed.regionMatches(true, 0, "file:", 0, 5)) return "file-url";
        URI uri = parseHttpUri(trimmed);
        if (uri == null || uri.getHost() == null || uri.getHost().isBlank()) return "invalid-url";
        return uri.getHost().toLowerCase(Locale.ROOT);
    }

    static String imageRejection(String url) {
        String host = hostOf(url);
        if ("data-url".equals(host)) {
            return "An image is stored only as a browser data URL. eBay cannot download that. Paste a public https image URL, or upload the file again.";
        }
        if ("subratapc.net".equals(host) || host.endsWith(".subratapc.net")) {
            return "The image is on subratapc.net, which is only this computer. eBay cannot download it, and the file is not in this app's local storage, so it could not be copied to eBay Picture Services. Upload the picture again.";
        }
        if ("localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
            return "The image URL points at localhost. eBay cannot download it.";
        }
        if (!"https".equalsIgnoreCase(schemeOf(url))) {
            return "eBay only accepts https image URLs (" + host + " was not https).";
        }
        return "The image host " + host + " is not reachable by eBay. Use a public https image URL.";
    }

    /** Lower-case host of an origin such as {@code https://subratapc.net}, or null. */
    static String hostOfOrigin(String origin) {
        if (origin == null || origin.isBlank()) return null;
        URI uri = parseHttpUri(origin);
        if (uri == null || uri.getHost() == null || uri.getHost().isBlank()) return null;
        return uri.getHost().toLowerCase(Locale.ROOT);
    }

    /**
     * Map a picture URL the app itself emitted to the stored file it names.
     * Accepted: a relative path, or an http(s) URL on a loopback host,
     * subratapc.net, one of {@code ownHosts}, or a Supabase storage host.
     * The file is identified by a {@code users/{folder}/{images|uploads}/{file}}
     * segment when present, otherwise by the last path segment, which must be
     * a safe stored file name. Returns null for anything else.
     */
    static LocalImageRef localImageRef(String url, Set<String> ownHosts) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.trim();
        URI uri = parseHttpUri(trimmed);
        if (uri == null) return null;
        boolean ownHost;
        if (uri.getScheme() == null && uri.getHost() == null) {
            if (!trimmed.startsWith("/")) return null;
            ownHost = true;
        } else {
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) return null;
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isEmpty()) return null;
            if (isOwnHost(host, ownHosts)) {
                ownHost = true;
            } else if (host.endsWith(".supabase.co") || host.endsWith(".supabase.in")) {
                ownHost = false;
            } else {
                return null;
            }
        }
        String path = uri.getRawPath();
        if (path == null || path.isBlank() || path.endsWith("/") || path.contains("..")) return null;
        String[] segments = path.split("/");
        String relative = null;
        for (int i = 0; i + 3 < segments.length; i++) {
            if ("users".equals(segments[i])
                    && (LocalUserStorageService.IMAGES.equals(segments[i + 2])
                        || LocalUserStorageService.UPLOADS.equals(segments[i + 2]))
                    && i + 4 == segments.length) {
                relative = "users/" + segments[i + 1] + "/" + segments[i + 2] + "/" + segments[i + 3];
                break;
            }
        }
        String fileName = segments.length == 0 ? "" : segments[segments.length - 1];
        if (!LocalUserStorageService.isSafeFileName(fileName)) return null;
        return new LocalImageRef(fileName, relative, path, ownHost);
    }

    static boolean isOwnHost(String host, Set<String> ownHosts) {
        if (host == null || host.isBlank()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (LOOPBACK_HOSTS.contains(h) || h.endsWith(".localhost")) return true;
        if (h.equals("subratapc.net") || h.endsWith(".subratapc.net")) return true;
        return ownHosts != null && ownHosts.contains(h);
    }

    static DecodedImage decodeDataUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (!trimmed.regionMatches(true, 0, "data:image/", 0, "data:image/".length())) return null;
        int comma = trimmed.indexOf(',');
        if (comma < 0) return null;
        String meta = trimmed.substring("data:".length(), comma);
        if (!meta.toLowerCase(Locale.ROOT).contains("base64")) return null;
        String mime = meta.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(trimmed.substring(comma + 1).replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (bytes.length == 0) return null;
        String ext = switch (mime) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            default -> "jpg";
        };
        if (!mime.startsWith("image/")) mime = "image/jpeg";
        return new DecodedImage(bytes, mime, ext);
    }

    /** Address the user typed into the Add inventory location form. */
    record LocationInput(String name, String addressLine1, String addressLine2, String city,
                         String stateOrProvince, String postalCode, String country) {}

    /**
     * Body for Inventory API {@code createInventoryLocation}: one ENABLED
     * WAREHOUSE. Throws {@link IllegalArgumentException} naming the first
     * field that is missing or invalid.
     */
    static Map<String, Object> locationBody(LocationInput in) {
        if (in == null) throw new IllegalArgumentException("Location details are required.");
        String name = requiredText(in.name(), "Location name", 100);
        String line1 = requiredText(in.addressLine1(), "Address line 1", 128);
        String line2 = optionalText(in.addressLine2(), "Address line 2", 128);
        String city = requiredText(in.city(), "City", 128);
        String country = in.country() == null ? "" : in.country().trim().toUpperCase(Locale.ROOT);
        if (!country.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Country must be a 2-letter code such as US or GB.");
        }
        String state = optionalText(in.stateOrProvince(), "State or province", 128);
        if (state == null && (country.equals("US") || country.equals("CA"))) {
            throw new IllegalArgumentException("State or province is required for " + country + " addresses.");
        }
        String postal = requiredText(in.postalCode(), "Postal code", 16);
        if (!postal.matches("[A-Za-z0-9 -]{2,16}")) {
            throw new IllegalArgumentException("Postal code may only contain letters, digits, spaces and hyphens.");
        }

        Map<String, Object> address = new LinkedHashMap<>();
        address.put("addressLine1", line1);
        if (line2 != null) address.put("addressLine2", line2);
        address.put("city", city);
        if (state != null) address.put("stateOrProvince", state);
        address.put("postalCode", postal);
        address.put("country", country);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("location", Map.of("address", address));
        body.put("locationTypes", List.of("WAREHOUSE"));
        body.put("merchantLocationStatus", "ENABLED");
        body.put("name", name);
        return body;
    }

    /** A merchantLocationKey (max 36 chars of letters, digits, '-', '_') derived from the name. */
    static String locationKey(String name, String suffix) {
        String slug = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) slug = "location";
        String tail = suffix == null ? "" : suffix.replaceAll("[^A-Za-z0-9]", "");
        if (tail.length() > 8) tail = tail.substring(0, 8);
        int room = 36 - (tail.isEmpty() ? 0 : tail.length() + 1);
        if (slug.length() > room) slug = slug.substring(0, room).replaceAll("-+$", "");
        return tail.isEmpty() ? slug : slug + "-" + tail;
    }

    /**
     * The location an offer should use: {@code preferred} when it is still
     * listed and enabled, otherwise the first enabled location, otherwise null.
     * Each map carries {@code merchantLocationKey} and {@code merchantLocationStatus}.
     */
    static String chooseLocationKey(List<Map<String, Object>> locations, String preferred) {
        if (locations == null || locations.isEmpty()) return null;
        String first = null;
        for (Map<String, Object> loc : locations) {
            Object key = loc.get("merchantLocationKey");
            if (key == null || key.toString().isBlank()) continue;
            Object status = loc.get("merchantLocationStatus");
            if (status != null && !"ENABLED".equalsIgnoreCase(status.toString())) continue;
            if (preferred != null && preferred.equals(key.toString())) return preferred;
            if (first == null) first = key.toString();
        }
        return first;
    }

    /**
     * Which stored photos go on which parsed product. One product gets every
     * photo; one product per photo is matched by index; any other split gives
     * every product all photos, since the AI does not say which photo it used.
     */
    static List<List<String>> assignImages(int productCount, List<String> imageUrls) {
        List<List<String>> out = new ArrayList<>();
        List<String> urls = imageUrls == null ? List.of() : imageUrls;
        for (int i = 0; i < productCount; i++) {
            if (productCount > 1 && productCount == urls.size()) out.add(List.of(urls.get(i)));
            else out.add(new ArrayList<>(urls.subList(0, Math.min(urls.size(), MAX_IMAGES))));
        }
        return out;
    }

    private static String requiredText(String value, String label, int max) {
        String text = optionalText(value, label, max);
        if (text == null) throw new IllegalArgumentException(label + " is required.");
        return text;
    }

    private static String optionalText(String value, String label, int max) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.length() > max) throw new IllegalArgumentException(label + " must be at most " + max + " characters.");
        return text;
    }

    static String positivePrice(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.trim().replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty() || cleaned.chars().filter(ch -> ch == '.').count() > 1) return null;
        try {
            BigDecimal value = new BigDecimal(cleaned);
            if (value.signum() <= 0) return null;
            return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean numericCategoryId(String categoryId) {
        return categoryId != null && categoryId.trim().matches("\\d{1,12}");
    }

    static String localeFor(String marketplaceId) {
        if (marketplaceId == null) return "en_US";
        return switch (marketplaceId.toUpperCase(Locale.ROOT)) {
            case "EBAY_GB" -> "en_GB";
            case "EBAY_DE" -> "de_DE";
            case "EBAY_FR" -> "fr_FR";
            case "EBAY_IT" -> "it_IT";
            case "EBAY_ES" -> "es_ES";
            case "EBAY_NL" -> "nl_NL";
            case "EBAY_BE" -> "nl_BE";
            case "EBAY_AU" -> "en_AU";
            case "EBAY_CA" -> "en_CA";
            default -> "en_US";
        };
    }

    static String normalizeCondition(String raw) {
        if (raw == null || raw.isBlank()) return "NEW";
        String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (CONDITIONS.contains(value)) return value;
        throw new IllegalArgumentException(
                "Unsupported eBay condition \"" + raw.trim() + "\". Use NEW or another eBay condition value.");
    }

    /**
     * Fill locale, clamp text, drop empty aspects, and refuse image URLs
     * eBay cannot fetch. A missing image list is left missing; an empty
     * list is removed so eBay is not sent {@code imageUrls: []}.
     */
    @SuppressWarnings("unchecked")
    static void prepareInventoryBody(Map<String, Object> body, String marketplaceId) {
        if (body == null) return;
        Object locale = body.get("locale");
        if (!(locale instanceof String s) || s.isBlank()) {
            body.put("locale", localeFor(marketplaceId));
        }
        Object condition = body.get("condition");
        if (condition == null || condition.toString().isBlank()) {
            body.put("condition", "NEW");
        } else {
            body.put("condition", normalizeCondition(condition.toString()));
        }

        Object productObj = body.get("product");
        if (!(productObj instanceof Map<?, ?> rawProduct)) return;
        Map<String, Object> product = (Map<String, Object>) rawProduct;
        clampField(product, "title", MAX_TITLE);
        clampField(product, "subtitle", MAX_SUBTITLE);
        Object description = product.get("description");
        if (description instanceof String text) {
            String safe = stripActiveHtml(text);
            if (safe.length() > MAX_DESCRIPTION) safe = cutHtml(safe, MAX_DESCRIPTION);
            if (safe.isBlank()) product.remove("description");
            else product.put("description", safe);
        }
        Map<String, List<String>> aspects = cleanAspects(product.get("aspects"));
        if (aspects == null || aspects.isEmpty()) product.remove("aspects");
        else product.put("aspects", aspects);

        Object images = product.get("imageUrls");
        if (!(images instanceof List<?> list)) return;
        List<String> kept = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (Object entry : list) {
            if (entry == null) continue;
            String url = entry.toString().trim();
            if (url.isEmpty()) continue;
            if (isPublicHttpsImage(url)) {
                if (kept.size() < MAX_IMAGES) kept.add(url);
            } else {
                dropped.add(hostOf(url));
            }
        }
        if (!dropped.isEmpty() && kept.isEmpty()) {
            throw new IllegalArgumentException(
                    "eBay cannot download the image URL"
                            + (dropped.size() == 1 ? "" : "s")
                            + " (" + String.join(", ", dropped) + "). "
                            + "Use a publicly reachable https address. "
                            + "subratapc.net and localhost stay on this computer, and eBay returns error 25001 when it cannot fetch a picture.");
        }
        if (kept.isEmpty()) product.remove("imageUrls");
        else product.put("imageUrls", kept);
    }

    static String summary(Map<String, Object> body) {
        if (body == null) return "body=null";
        StringBuilder sb = new StringBuilder();
        sb.append("locale=").append(body.get("locale"));
        sb.append(" condition=").append(body.get("condition"));
        Object productObj = body.get("product");
        if (productObj instanceof Map<?, ?> product) {
            Object title = product.get("title");
            Object description = product.get("description");
            sb.append(" titleLen=").append(title instanceof String s ? s.length() : -1);
            sb.append(" descLen=").append(description instanceof String s ? s.length() : -1);
            sb.append(" html=").append(description instanceof String s && s.indexOf('<') >= 0);
            Object images = product.get("imageUrls");
            if (images instanceof List<?> list) {
                List<String> hosts = new ArrayList<>();
                for (Object entry : list) hosts.add(hostOf(entry == null ? "" : entry.toString()));
                sb.append(" imageHosts=").append(hosts);
            } else {
                sb.append(" imageHosts=[]");
            }
            Object aspects = product.get("aspects");
            sb.append(" aspectKeys=").append(aspects instanceof Map<?, ?> m ? m.size() : 0);
        }
        Object availability = body.get("availability");
        if (availability instanceof Map<?, ?> avail) {
            Object ship = avail.get("shipToLocationAvailability");
            if (ship instanceof Map<?, ?> shipMap) sb.append(" qty=").append(shipMap.get("quantity"));
        }
        return sb.toString();
    }

    /** Multipart body for Media API {@code createImageFromFile}: one part named {@code image}. */
    static byte[] mediaUploadBody(String boundary, byte[] image, String filename, String contentType) {
        String safeName = filename == null || !LocalUserStorageService.isSafeFileName(filename) ? "image.jpg" : filename;
        String mime = contentType == null || !contentType.startsWith("image/") ? "image/jpeg" : contentType;
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes = tail.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[headBytes.length + image.length + tailBytes.length];
        System.arraycopy(headBytes, 0, out, 0, headBytes.length);
        System.arraycopy(image, 0, out, headBytes.length, image.length);
        System.arraycopy(tailBytes, 0, out, headBytes.length + image.length, tailBytes.length);
        return out;
    }

    /** The EPS {@code imageUrl} from a Media API image response, or null. */
    static String mediaImageUrl(String json) {
        if (json == null || json.isBlank()) return null;
        Matcher matcher = MEDIA_IMAGE_URL.matcher(json);
        if (!matcher.find()) return null;
        String url = matcher.group(1).replace("\\/", "/").replace("\\u0026", "&").trim();
        return isPublicHttpsImage(url) ? url : null;
    }

    static String mediaError(String json) {
        if (json == null || json.isBlank()) return "empty response";
        Matcher matcher = MEDIA_MESSAGE.matcher(json);
        if (matcher.find()) return matcher.group(1).trim();
        String oneLine = json.replaceAll("\\s+", " ");
        return oneLine.length() > 240 ? oneLine.substring(0, 240) : oneLine;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> cleanAspects(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return null;
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) continue;
            String name = entry.getKey().toString().trim();
            if (name.isEmpty() || name.length() > 40) continue;
            List<String> values = new ArrayList<>();
            Object value = entry.getValue();
            if (value instanceof List<?> list) {
                for (Object item : list) addAspectValue(values, item);
            } else {
                addAspectValue(values, value);
            }
            if (!values.isEmpty()) out.put(name, values);
        }
        return out;
    }

    private static void addAspectValue(List<String> values, Object item) {
        if (item == null || values.size() >= 30) return;
        String text = item.toString().trim();
        if (text.isEmpty()) return;
        if (text.length() > 50) text = text.substring(0, 50);
        if (!values.contains(text)) values.add(text);
    }

    private static void clampField(Map<String, Object> product, String key, int max) {
        Object value = product.get(key);
        if (!(value instanceof String text)) return;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            product.remove(key);
            return;
        }
        if (trimmed.length() > max) trimmed = trimmed.substring(0, max).trim();
        product.put(key, trimmed);
    }

    private static String stripActiveHtml(String html) {
        String stripped = ACTIVE_HTML.matcher(html).replaceAll("");
        return stripped.trim();
    }

    private static String cutHtml(String html, int max) {
        String cut = html.substring(0, max);
        int lt = cut.lastIndexOf('<');
        int gt = cut.lastIndexOf('>');
        if (lt > gt) cut = cut.substring(0, lt);
        return cut.trim();
    }

    private static String schemeOf(String url) {
        URI uri = parseHttpUri(url);
        return uri == null || uri.getScheme() == null ? "" : uri.getScheme();
    }

    private static URI parseHttpUri(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            return URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isUnreachableHost(String host) {
        if (host == null || host.isBlank()) return true;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.equals("127.0.0.1") || h.equals("0.0.0.0") || h.equals("::1")) return true;
        if (h.endsWith(".local") || h.endsWith(".localhost") || h.endsWith(".internal")) return true;
        if (h.equals("subratapc.net") || h.endsWith(".subratapc.net")) return true;
        if (h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("169.254.")) return true;
        if (h.startsWith("172.")) {
            String[] parts = h.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return true;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }
}
