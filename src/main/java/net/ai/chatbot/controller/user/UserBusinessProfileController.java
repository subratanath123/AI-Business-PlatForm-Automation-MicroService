package net.ai.chatbot.controller.user;

import net.ai.chatbot.dto.user.BusinessProfileData;
import net.ai.chatbot.dto.user.SubscriptionSnapshotData;
import net.ai.chatbot.dto.user.UserBusinessProfileUpdateRequest;
import net.ai.chatbot.entity.UserBusinessProfile;
import net.ai.chatbot.service.user.UserBusinessProfileService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserBusinessProfileController {

    private final UserBusinessProfileService userBusinessProfileService;

    public UserBusinessProfileController(UserBusinessProfileService userBusinessProfileService) {
        this.userBusinessProfileService = userBusinessProfileService;
    }

    @RequestMapping(
            value = {"/v1/api/user/business-profile", "/api/user/business-profile"},
            method = RequestMethod.GET)
    public ResponseEntity<UserBusinessProfile> getProfile() {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userBusinessProfileService.getOrEmpty(userId));
    }

    /**
     * PUT and PATCH both accepted (proxies and clients vary; Next settings used PATCH).
     */
    @RequestMapping(
            value = {"/v1/api/user/business-profile", "/api/user/business-profile"},
            method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<UserBusinessProfile> upsertProfile(@RequestBody UserBusinessProfileUpdateRequest body) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        if (body == null || body.getBusinessProfile() == null) {
            return ResponseEntity.badRequest().build();
        }
        BusinessProfileData bp = sanitize(body.getBusinessProfile());
        SubscriptionSnapshotData sub = body.getSubscription() != null
                ? sanitizeSubscription(body.getSubscription())
                : SubscriptionSnapshotData.builder().planId("free").billingStatus("none").build();
        UserBusinessProfile saved = userBusinessProfileService.upsert(userId, bp, sub);
        return ResponseEntity.ok(saved);
    }

    private static BusinessProfileData sanitize(BusinessProfileData p) {
        if (p == null) {
            return BusinessProfileData.builder().build();
        }
        return BusinessProfileData.builder()
                .companyName(trim(p.getCompanyName(), 512))
                .jobTitle(trim(p.getJobTitle(), 200))
                .businessPhone(trim(p.getBusinessPhone(), 80))
                .website(trim(p.getWebsite(), 300))
                .industry(trim(p.getIndustry(), 120))
                .teamSize(trim(p.getTeamSize(), 80))
                .addressLine(trim(p.getAddressLine(), 512))
                .city(trim(p.getCity(), 120))
                .region(trim(p.getRegion(), 120))
                .postalCode(trim(p.getPostalCode(), 32))
                .country(trim(p.getCountry(), 120))
                .timezone(trim(p.getTimezone(), 80))
                .taxId(trim(p.getTaxId(), 64))
                .notes(trim(p.getNotes(), 2000))
                .build();
    }

    private static SubscriptionSnapshotData sanitizeSubscription(SubscriptionSnapshotData s) {
        String plan = trim(s.getPlanId(), 64);
        if (plan.isEmpty()) {
            plan = "free";
        }
        String status = trim(s.getBillingStatus(), 32);
        if (status.isEmpty()) {
            status = "none";
        }
        return SubscriptionSnapshotData.builder()
                .planId(plan)
                .billingStatus(status)
                .currentPeriodEnd(trim(s.getCurrentPeriodEnd(), 64))
                .build();
    }

    private static String trim(String v, int max) {
        if (v == null) {
            return "";
        }
        String t = v.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
