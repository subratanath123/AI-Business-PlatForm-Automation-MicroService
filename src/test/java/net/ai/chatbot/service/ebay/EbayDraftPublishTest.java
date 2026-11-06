package net.ai.chatbot.service.ebay;

import net.ai.chatbot.dao.EbayIntegrationDao;
import net.ai.chatbot.dao.EbayPendingConnectionDao;
import net.ai.chatbot.dao.EbayProductEnhancementJobDao;
import net.ai.chatbot.entity.EbayIntegration;
import net.ai.chatbot.entity.EbayProductEnhancementJob;
import net.ai.chatbot.entity.EbayProductEnhancementJob.EbayProductItem;
import net.ai.chatbot.service.shopify.ProductAIEnhancementService;
import net.ai.chatbot.service.storage.LocalUserStorageService;
import net.ai.chatbot.utils.EncryptionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EbayDraftPublishTest {

    private static final String LOCAL_URL =
            "https://subratapc.net/v1/api/public/user-files/d32d19f5-ffb2-4870-ac18-dc08009f27eb.png";
    private static final String EPS_URL = "https://i.ebayimg.com/00/s/MTI1Nlg3NDE=/z/abc/$_1.PNG";

    @Mock EbayIntegrationDao ebayDao;
    @Mock EbayPendingConnectionDao pendingDao;
    @Mock EbayProductEnhancementJobDao jobDao;
    @Mock EbayApiService api;
    @Mock EbayOAuthService oauth;
    @Mock ProductAIEnhancementService ai;
    @Mock EncryptionUtils encryption;
    @Mock EbayLocalImageResolver localImages;
    @Mock LocalUserStorageService storage;

    private EbayIntegrationService service;
    private EbayProductEnhancementJob job;
    private EbayProductItem raw;
    private EbayProductItem enhanced;

    @BeforeEach
    void setUp() {
        service = new EbayIntegrationService(ebayDao, pendingDao, jobDao, api, oauth, ai, encryption, localImages, storage);
        EbayIntegration integration = EbayIntegration.builder()
                .userId("u1").environment("PRODUCTION").active(true).connected(true)
                .encryptedRefreshToken("enc").activeMarketplaceId("EBAY_US").build();
        when(ebayDao.findByUserIdAndActiveTrue("u1")).thenReturn(Optional.of(integration));
        when(encryption.decrypt("enc")).thenReturn("refresh");
        when(oauth.getAccessToken(any(), any(), any())).thenReturn("tok");

        raw = item(new ArrayList<>(List.of(LOCAL_URL)));
        enhanced = item(new ArrayList<>());
        job = EbayProductEnhancementJob.builder().id("job1").userId("u1")
                .rawProducts(new ArrayList<>(List.of(raw)))
                .enhancedProducts(new ArrayList<>(List.of(enhanced))).build();
        when(jobDao.findById("job1")).thenReturn(Optional.of(job));

        when(localImages.isLocal(LOCAL_URL)).thenReturn(true);
        when(localImages.load(LOCAL_URL)).thenReturn(Optional.of(
                new EbayListingFields.DecodedImage(new byte[] { 1, 2, 3 }, "image/png", "png")));
        when(api.createImageFromFile(eq("tok"), eq("PRODUCTION"), any(), anyString(), anyString())).thenReturn(EPS_URL);
    }

    private static EbayProductItem item(List<String> images) {
        return EbayProductItem.builder().localId("d1").sku("12").marketplaceId("EBAY_US")
                .title("Angel Eau de Parfum").price("12.00").categoryId("11848").condition("NEW")
                .inventoryQuantity(23).lifecycleStatus("DRAFT").images(images).build();
    }

    @Test
    void hostsTheLocalImageEvenWhenOnlyTheRawCopyHasIt() {
        when(api.listInventoryLocations("tok", "PRODUCTION")).thenReturn(List.of());
        when(api.sellerRegistrationCompleted("tok", "PRODUCTION")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.publishDraft("u1", "job1", "d1"));

        verify(api).createImageFromFile(eq("tok"), eq("PRODUCTION"), any(), eq("image.png"), eq("image/png"));
        assertFalse(ex.getMessage().contains(EbayIntegrationService.NO_IMAGE_MESSAGE), ex.getMessage());
        assertTrue(ex.getMessage().contains(EbayIntegrationService.NO_LOCATION_MESSAGE), ex.getMessage());
        assertTrue(ex.getMessage().contains("https://www.ebay.com/sl/sell"), ex.getMessage());
        assertEquals(List.of(LOCAL_URL), raw.getImages(), "a failed publish keeps the stored picture URL");
        assertEquals(List.of(), enhanced.getImages());
        verify(api, never()).createOrReplaceInventoryItem(any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesWithTheEpsUrlAndTheEnabledLocation() {
        enhanced.setImages(new ArrayList<>(List.of(LOCAL_URL)));
        when(api.listInventoryLocations("tok", "PRODUCTION")).thenReturn(List.of(
                Map.of("merchantLocationKey", "old", "merchantLocationStatus", "DISABLED"),
                Map.of("merchantLocationKey", "home-warehouse", "merchantLocationStatus", "ENABLED")));
        when(api.sellerRegistrationCompleted("tok", "PRODUCTION")).thenReturn(true);
        when(api.createOrReplaceOffer(any(), any(), any(), any(), any())).thenReturn("offer1");
        when(api.publishOffer("tok", "PRODUCTION", "offer1")).thenReturn("listing1");

        Map<String, Object> result = service.publishDraft("u1", "job1", "d1");

        ArgumentCaptor<Map<String, Object>> inventory = ArgumentCaptor.forClass(Map.class);
        verify(api).createOrReplaceInventoryItem(eq("tok"), eq("PRODUCTION"), eq("EBAY_US"), eq("12"), inventory.capture());
        Map<String, Object> product = (Map<String, Object>) inventory.getValue().get("product");
        assertEquals(List.of(EPS_URL), product.get("imageUrls"));

        ArgumentCaptor<Map<String, Object>> offer = ArgumentCaptor.forClass(Map.class);
        verify(api).createOrReplaceOffer(eq("tok"), eq("PRODUCTION"), eq("EBAY_US"), eq("12"), offer.capture());
        assertEquals("home-warehouse", offer.getValue().get("merchantLocationKey"));
        assertEquals("PUBLISHED", result.get("lifecycleStatus"));
        assertEquals(List.of(EPS_URL), enhanced.getImages());
    }

    @Test
    void saysNoImageOnlyWhenTheDraftHasNone() {
        raw.setImages(new ArrayList<>());
        when(api.listInventoryLocations("tok", "PRODUCTION")).thenReturn(List.of(
                Map.of("merchantLocationKey", "home", "merchantLocationStatus", "ENABLED")));
        when(api.sellerRegistrationCompleted("tok", "PRODUCTION")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.publishDraft("u1", "job1", "d1"));

        assertEquals("Failed to publish draft: " + EbayIntegrationService.NO_IMAGE_MESSAGE, ex.getMessage());
        verify(api, never()).createImageFromFile(any(), any(), any(), any(), any());
    }

    @Test
    void reportsEbaysReasonWhenPictureHostingFails() {
        when(api.createImageFromFile(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("eBay picture upload failed: 403 Insufficient permissions"));
        when(api.listInventoryLocations("tok", "PRODUCTION")).thenReturn(List.of(
                Map.of("merchantLocationKey", "home", "merchantLocationStatus", "ENABLED")));
        when(api.sellerRegistrationCompleted("tok", "PRODUCTION")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.publishDraft("u1", "job1", "d1"));

        assertTrue(ex.getMessage().contains("403 Insufficient permissions"), ex.getMessage());
        assertFalse(ex.getMessage().contains(EbayIntegrationService.NO_IMAGE_MESSAGE), ex.getMessage());
    }
}
