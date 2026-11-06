package net.ai.chatbot.service.ebay;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EbayOAuthHostTest {

    @Test
    void productionSelectionUsesProductionHostsAndCredentials() {
        EbayOAuthService svc = bothEnvironments(true);
        assertEquals("https://auth.ebay.com", svc.authorizeHost("PRODUCTION"));
        assertEquals("https://api.ebay.com", svc.tokenHost("PRODUCTION"));
        assertEquals("https://apim.ebay.com", svc.mediaHost("PRODUCTION"));
        assertEquals("PRODUCTION", svc.normalizeEnvironment("PRODUCTION"));
        assertEquals("Sample-App-PRD-aaaaaaaa-bbbbbbbb", svc.appId("PRODUCTION"));
        assertEquals("Production_RuName", svc.ruName("PRODUCTION"));
    }

    @Test
    void sandboxSelectionUsesSandboxHostsAndCredentials() {
        EbayOAuthService svc = bothEnvironments(false);
        assertEquals("https://auth.sandbox.ebay.com", svc.authorizeHost("SANDBOX"));
        assertEquals("https://api.sandbox.ebay.com", svc.tokenHost("SANDBOX"));
        assertEquals("https://apim.sandbox.ebay.com", svc.mediaHost("SANDBOX"));
        assertEquals("SANDBOX", svc.normalizeEnvironment("SANDBOX"));
        assertEquals("Sample-App-SBX-aaaaaaaa-bbbbbbbb", svc.appId("SANDBOX"));
        assertEquals("Sandbox_RuName", svc.ruName("SANDBOX"));
    }

    @Test
    void sandboxFlagDoesNotOverrideExplicitProduction() {
        EbayOAuthService svc = bothEnvironments(true);
        assertEquals("https://auth.ebay.com", svc.authorizeHost("PRODUCTION"));
        assertEquals("https://api.ebay.com", svc.tokenHost("PRODUCTION"));
        assertEquals("PRODUCTION", svc.normalizeEnvironment("PRODUCTION"));
        assertEquals("Sample-App-PRD-aaaaaaaa-bbbbbbbb", svc.appId("PRODUCTION"));
    }

    @Test
    void omittedEnvironmentFollowsSandboxFlag() {
        EbayOAuthService productionDefault = bothEnvironments(false);
        assertEquals("PRODUCTION", productionDefault.normalizeEnvironment(null));
        assertEquals("https://auth.ebay.com", productionDefault.authorizeHost(null));
        assertEquals("Sample-App-PRD-aaaaaaaa-bbbbbbbb", productionDefault.appId(null));

        EbayOAuthService sandboxDefault = bothEnvironments(true);
        assertEquals("SANDBOX", sandboxDefault.normalizeEnvironment(null));
        assertEquals("https://auth.sandbox.ebay.com", sandboxDefault.authorizeHost(""));
        assertEquals("Sample-App-SBX-aaaaaaaa-bbbbbbbb", sandboxDefault.appId(null));
    }

    @Test
    void productionFallsBackToFlatKeys() {
        EbayOAuthService svc = new EbayOAuthService(null);
        ReflectionTestUtils.setField(svc, "sandboxDefault", false);
        ReflectionTestUtils.setField(svc, "ebayAppId", "Sample-App-PRD-flat");
        ReflectionTestUtils.setField(svc, "ebayCertId", "PRD-flat-cert");
        ReflectionTestUtils.setField(svc, "ebayRuName", "Flat_RuName");
        ReflectionTestUtils.setField(svc, "productionAppId", "");
        ReflectionTestUtils.setField(svc, "productionCertId", "");
        ReflectionTestUtils.setField(svc, "productionRuName", "");
        ReflectionTestUtils.setField(svc, "sandboxAppId", "Sample-App-SBX-aaaaaaaa-bbbbbbbb");
        ReflectionTestUtils.setField(svc, "sandboxCertId", "SBX-sample-cert");
        ReflectionTestUtils.setField(svc, "sandboxRuName", "Sandbox_RuName");

        assertEquals("https://auth.ebay.com", svc.authorizeHost("PRODUCTION"));
        assertEquals("Sample-App-PRD-flat", svc.appId("PRODUCTION"));
        assertEquals("Flat_RuName", svc.ruName("PRODUCTION"));
        assertEquals("Sample-App-SBX-aaaaaaaa-bbbbbbbb", svc.appId("SANDBOX"));
    }

    private static EbayOAuthService bothEnvironments(boolean sandboxDefault) {
        EbayOAuthService svc = new EbayOAuthService(null);
        ReflectionTestUtils.setField(svc, "sandboxDefault", sandboxDefault);
        ReflectionTestUtils.setField(svc, "ebayAppId", "Sample-App-PRD-aaaaaaaa-bbbbbbbb");
        ReflectionTestUtils.setField(svc, "ebayCertId", "PRD-cert");
        ReflectionTestUtils.setField(svc, "ebayRuName", "Production_RuName");
        ReflectionTestUtils.setField(svc, "productionAppId", "Sample-App-PRD-aaaaaaaa-bbbbbbbb");
        ReflectionTestUtils.setField(svc, "productionCertId", "PRD-cert");
        ReflectionTestUtils.setField(svc, "productionRuName", "Production_RuName");
        ReflectionTestUtils.setField(svc, "sandboxAppId", "Sample-App-SBX-aaaaaaaa-bbbbbbbb");
        ReflectionTestUtils.setField(svc, "sandboxCertId", "SBX-sample-cert");
        ReflectionTestUtils.setField(svc, "sandboxRuName", "Sandbox_RuName");
        return svc;
    }
}
