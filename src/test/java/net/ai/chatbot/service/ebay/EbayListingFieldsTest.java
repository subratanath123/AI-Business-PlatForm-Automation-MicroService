package net.ai.chatbot.service.ebay;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbayListingFieldsTest {

    @Test
    void keepsPublicHttpsImagesAndDropsNothingWhenAbsent() {
        assertTrue(EbayListingFields.isPublicHttpsImage("https://i.ebayimg.com/images/g/abc/s-l1600.jpg"));
        assertTrue(EbayListingFields.isPublicHttpsImage("https://cdn.example.com/angel.jpg"));
        assertFalse(EbayListingFields.isPublicHttpsImage("https://subratapc.net/v1/api/public/user-files/abc.jpg"));
        assertFalse(EbayListingFields.isPublicHttpsImage("http://cdn.example.com/angel.jpg"));
        assertFalse(EbayListingFields.isPublicHttpsImage("https://localhost/angel.jpg"));
        assertFalse(EbayListingFields.isPublicHttpsImage("https://127.0.0.1/angel.jpg"));
        assertFalse(EbayListingFields.isPublicHttpsImage("data:image/jpeg;base64,aaaa"));
        assertEquals("subratapc.net", EbayListingFields.hostOf("https://subratapc.net/v1/api/public/user-files/abc.jpg"));

        Map<String, Object> body = angelBody(null);
        EbayListingFields.prepareInventoryBody(body, "EBAY_US");
        @SuppressWarnings("unchecked")
        Map<String, Object> product = (Map<String, Object>) body.get("product");
        assertFalse(product.containsKey("imageUrls"));
        assertEquals("en_US", body.get("locale"));
        assertEquals("NEW", body.get("condition"));
        assertTrue(product.get("description").toString().contains("<p>"));
    }

    @Test
    void refusesLocalImageUrlsInsteadOfSendingThem() {
        Map<String, Object> body = angelBody(List.of(
                "https://subratapc.net/v1/api/public/user-files/abc.jpg",
                "http://127.0.0.1:8080/photo.jpg"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> EbayListingFields.prepareInventoryBody(body, "EBAY_US"));
        assertTrue(ex.getMessage().contains("subratapc.net"));
        assertTrue(ex.getMessage().contains("25001"));
    }

    @Test
    void keepsAPublicImageWhenOneIsMixedWithALocalOne() {
        Map<String, Object> body = angelBody(List.of(
                "https://subratapc.net/v1/api/public/user-files/abc.jpg",
                "https://i.ebayimg.com/images/g/abc/s-l1600.jpg"));
        EbayListingFields.prepareInventoryBody(body, "EBAY_US");
        @SuppressWarnings("unchecked")
        Map<String, Object> product = (Map<String, Object>) body.get("product");
        assertEquals(List.of("https://i.ebayimg.com/images/g/abc/s-l1600.jpg"), product.get("imageUrls"));
    }

    @Test
    void priceAndCategoryChecksMatchTheAngelDraft() {
        assertNull(EbayListingFields.positivePrice(null));
        assertNull(EbayListingFields.positivePrice("0"));
        assertNull(EbayListingFields.positivePrice("0.00"));
        assertEquals("49.99", EbayListingFields.positivePrice("49.99"));
        assertFalse(EbayListingFields.numericCategoryId(null));
        assertFalse(EbayListingFields.numericCategoryId("Eau de Parfum"));
        assertTrue(EbayListingFields.numericCategoryId("11848"));
    }

    @Test
    void readsTheHostedPictureUrlFromTheMediaApiResponse() {
        String json = "{\"expirationDate\":\"2026-11-29T18:19:08Z\","
                + "\"imageUrl\":\"https://i.ebayimg.com/00/s/MTI1Nlg3NDE=/z/7o4AAeSw1~1q5N-c/$_1.PNG?set_id=8800005007\","
                + "\"maxDimensionImageUrl\":\"https://i.ebayimg.com/00/s/x/$_57.PNG\"}";
        assertEquals("https://i.ebayimg.com/00/s/MTI1Nlg3NDE=/z/7o4AAeSw1~1q5N-c/$_1.PNG?set_id=8800005007",
                EbayListingFields.mediaImageUrl(json));
        assertNull(EbayListingFields.mediaImageUrl("{\"imageUrl\":\"https://subratapc.net/a.png\"}"));
        assertEquals("Insufficient permissions", EbayListingFields.mediaError(
                "{\"errors\":[{\"errorId\":1100,\"message\":\"Insufficient permissions\"}]}"));

        byte[] body = EbayListingFields.mediaUploadBody("bound",
                "img".getBytes(StandardCharsets.UTF_8), "image.png", "image/png");
        String text = new String(body, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("--bound\r\nContent-Disposition: form-data; name=\"image\"; filename=\"image.png\""));
        assertTrue(text.contains("Content-Type: image/png\r\n\r\nimg\r\n--bound--"));
        assertFalse(text.contains("XML Payload"));
    }

    @Test
    void mapsTheFailingDraftUrlToItsStoredFile() {
        EbayListingFields.LocalImageRef ref = EbayListingFields.localImageRef(
                "https://subratapc.net/v1/api/public/user-files/d32d19f5-ffb2-4870-ac18-dc08009f27eb.png", Set.of());
        assertNotNull(ref);
        assertEquals("d32d19f5-ffb2-4870-ac18-dc08009f27eb.png", ref.fileName());
        assertNull(ref.relativePath());
        assertEquals("/v1/api/public/user-files/d32d19f5-ffb2-4870-ac18-dc08009f27eb.png", ref.path());
        assertTrue(ref.ownHost());
    }

    @Test
    void mapsEveryLocalUrlShapeTheAppEmits() {
        Set<String> own = Set.of("api.jadeordersmedia.com", "app.example.test");
        for (String url : List.of(
                "https://subratapc.net/v1/api/public/user-files/abc-def.jpg",
                "https://www.subratapc.net/v1/api/public/user-files/abc-def.jpg?v=2#x",
                "http://localhost:8080/v1/api/public/user-files/abc-def.jpg",
                "http://127.0.0.1:8080/api/public/user-files/abc-def.jpg",
                "http://[::1]:8080/v1/api/public/user-files/abc-def.jpg",
                "https://api.jadeordersmedia.com/v1/api/public/user-files/abc-def.jpg",
                "https://APP.example.test/v1/api/public/media/abc-def.jpg",
                "/v1/api/public/user-files/abc-def.jpg")) {
            EbayListingFields.LocalImageRef ref = EbayListingFields.localImageRef(url, own);
            assertNotNull(ref, url);
            assertEquals("abc-def.jpg", ref.fileName(), url);
            assertTrue(ref.ownHost(), url);
        }

        EbayListingFields.LocalImageRef byPath = EbayListingFields.localImageRef(
                "https://subratapc.net/storage/users/user_36z3oFaJOz9KfToNtKajVLYrGve/uploads/abc-def.png", own);
        assertNotNull(byPath);
        assertEquals("users/user_36z3oFaJOz9KfToNtKajVLYrGve/uploads/abc-def.png", byPath.relativePath());

        EbayListingFields.LocalImageRef supabase = EbayListingFields.localImageRef(
                "https://sgenamkaqezylpyiqbsb.supabase.co/storage/v1/object/public/media/users/u1/images/abc-def.png", own);
        assertNotNull(supabase);
        assertFalse(supabase.ownHost());
        assertEquals("users/u1/images/abc-def.png", supabase.relativePath());
    }

    @Test
    void leavesPublicAndUnsafeUrlsAlone() {
        Set<String> own = Set.of("api.jadeordersmedia.com");
        assertNull(EbayListingFields.localImageRef("https://cdn.example.com/abc-def.jpg", own));
        assertNull(EbayListingFields.localImageRef("https://i.ebayimg.com/00/s/abc.jpg", own));
        assertNull(EbayListingFields.localImageRef("https://subratapc.net/v1/api/public/user-files/../secret.jpg", own));
        assertNull(EbayListingFields.localImageRef("https://subratapc.net/v1/api/public/user-files/", own));
        assertNull(EbayListingFields.localImageRef("file:///C:/storage/abc.jpg", own));
        assertNull(EbayListingFields.localImageRef("data:image/png;base64,aaaa", own));
        assertNull(EbayListingFields.localImageRef("relative/abc.jpg", own));
        assertEquals("api.jadeordersmedia.com", EbayListingFields.hostOfOrigin("http://api.jadeordersmedia.com"));
        assertNull(EbayListingFields.hostOfOrigin(""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsAWarehouseLocationBody() {
        Map<String, Object> body = EbayListingFields.locationBody(new EbayListingFields.LocationInput(
                " Home warehouse ", "1 Main St", "", "San Jose", "CA", "95125", "us"));
        assertEquals("Home warehouse", body.get("name"));
        assertEquals(List.of("WAREHOUSE"), body.get("locationTypes"));
        assertEquals("ENABLED", body.get("merchantLocationStatus"));
        Map<String, Object> address = (Map<String, Object>) ((Map<String, Object>) body.get("location")).get("address");
        assertEquals("US", address.get("country"));
        assertEquals("CA", address.get("stateOrProvince"));
        assertFalse(address.containsKey("addressLine2"));

        Map<String, Object> gb = EbayListingFields.locationBody(new EbayListingFields.LocationInput(
                "London", "10 High St", "Unit 2", "London", null, "SW1A 1AA", "GB"));
        Map<String, Object> gbAddress = (Map<String, Object>) ((Map<String, Object>) gb.get("location")).get("address");
        assertEquals("Unit 2", gbAddress.get("addressLine2"));
        assertFalse(gbAddress.containsKey("stateOrProvince"));
    }

    @Test
    void refusesIncompleteLocations() {
        assertTrue(locationError(new EbayListingFields.LocationInput("", "1 Main St", null, "San Jose", "CA", "95125", "US"))
                .contains("Location name"));
        assertTrue(locationError(new EbayListingFields.LocationInput("Home", "", null, "San Jose", "CA", "95125", "US"))
                .contains("Address line 1"));
        assertTrue(locationError(new EbayListingFields.LocationInput("Home", "1 Main St", null, "San Jose", "CA", "95125", "USA"))
                .contains("2-letter"));
        assertTrue(locationError(new EbayListingFields.LocationInput("Home", "1 Main St", null, "San Jose", "", "95125", "US"))
                .contains("State or province"));
        assertTrue(locationError(new EbayListingFields.LocationInput("Home", "1 Main St", null, "San Jose", "CA", "95<125>", "US"))
                .contains("Postal code"));
        assertTrue(locationError(null).contains("required"));
    }

    @Test
    void makesShortSafeLocationKeys() {
        assertEquals("home-warehouse-ab12cd34", EbayListingFields.locationKey("Home Warehouse!", "ab12cd34-9999"));
        String key = EbayListingFields.locationKey("A very long warehouse name that keeps going", "ab12cd34");
        assertTrue(key.length() <= 36, key);
        assertTrue(key.matches("[a-z0-9-]+"), key);
        assertEquals("location-ab12cd34", EbayListingFields.locationKey("***", "ab12cd34"));
    }

    @Test
    void choosesTheDefaultOrFirstEnabledLocation() {
        List<Map<String, Object>> locations = List.of(
                Map.of("merchantLocationKey", "off", "merchantLocationStatus", "DISABLED"),
                Map.of("merchantLocationKey", "a", "merchantLocationStatus", "ENABLED"),
                Map.of("merchantLocationKey", "b", "merchantLocationStatus", "ENABLED"));
        assertEquals("b", EbayListingFields.chooseLocationKey(locations, "b"));
        assertEquals("a", EbayListingFields.chooseLocationKey(locations, "off"));
        assertEquals("a", EbayListingFields.chooseLocationKey(locations, null));
        assertNull(EbayListingFields.chooseLocationKey(List.of(), "b"));
        assertNull(EbayListingFields.chooseLocationKey(List.of(
                Map.of("merchantLocationKey", "off", "merchantLocationStatus", "DISABLED")), null));
    }

    @Test
    void givesParsedProductsTheirPhotos() {
        assertEquals(List.of(List.of("p1", "p2")), EbayListingFields.assignImages(1, List.of("p1", "p2")));
        assertEquals(List.of(List.of("p1"), List.of("p2")), EbayListingFields.assignImages(2, List.of("p1", "p2")));
        assertEquals(List.of(List.of("p1"), List.of("p1")), EbayListingFields.assignImages(2, List.of("p1")));
        assertEquals(List.of(List.of()), EbayListingFields.assignImages(1, List.of()));
    }

    private static String locationError(EbayListingFields.LocationInput input) {
        return assertThrows(IllegalArgumentException.class, () -> EbayListingFields.locationBody(input)).getMessage();
    }

    private static Map<String, Object> angelBody(List<String> images) {
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("title", "Angel Eau de Parfum by Thierry Mugler");
        product.put("description", "<p>Discover the enchanting allure of <strong>Angel Eau de Parfum</strong>.</p>");
        if (images != null) product.put("imageUrls", images);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product", product);
        body.put("condition", "NEW");
        body.put("availability", Map.of("shipToLocationAvailability", Map.of("quantity", 1)));
        return body;
    }
}
