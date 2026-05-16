package net.ai.chatbot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.PricingType;
import net.ai.chatbot.dto.admin.SubscriptionPlan;
import net.ai.chatbot.dto.admin.UserSubscription;
import net.ai.chatbot.dto.admin.request.CreatePlanRequest;
import net.ai.chatbot.service.admin.SubscriptionPlanService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class SubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    @GetMapping("/subscription/plans")
    public ResponseEntity<List<SubscriptionPlan>> getActivePlans() {
        try {
            List<SubscriptionPlan> plans = subscriptionPlanService.getActivePlans();
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            log.error("Error fetching active plans", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscription/plans/{planId}")
    public ResponseEntity<SubscriptionPlan> getPlanById(@PathVariable String planId) {
        try {
            SubscriptionPlan plan = subscriptionPlanService.getPlanById(planId);
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            log.error("Error fetching plan {}", planId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/subscription/plans/by-type/{pricingType}")
    public ResponseEntity<List<SubscriptionPlan>> getPlansByType(@PathVariable PricingType pricingType) {
        try {
            List<SubscriptionPlan> plans = subscriptionPlanService.getPlansByPricingType(pricingType);
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            log.error("Error fetching plans by type {}", pricingType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscription/plans/featured")
    public ResponseEntity<List<SubscriptionPlan>> getFeaturedPlans() {
        try {
            List<SubscriptionPlan> plans = subscriptionPlanService.getFeaturedPlans();
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            log.error("Error fetching featured plans", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscription/my")
    public ResponseEntity<UserSubscription> getMySubscription() {
        try {
            String userId = AuthUtils.getUserId();
            UserSubscription subscription = subscriptionPlanService.getUserSubscription(userId);
            return ResponseEntity.ok(subscription);
        } catch (Exception e) {
            log.error("Error fetching user subscription", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/subscription/history")
    public ResponseEntity<List<UserSubscription>> getSubscriptionHistory() {
        try {
            String userId = AuthUtils.getUserId();
            List<UserSubscription> history = subscriptionPlanService.getUserSubscriptionHistory(userId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error fetching subscription history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/admin/subscription/plans")
    public ResponseEntity<SubscriptionPlan> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            SubscriptionPlan plan = subscriptionPlanService.createPlan(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(plan);
        } catch (Exception e) {
            log.error("Error creating plan", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/admin/subscription/plans")
    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<SubscriptionPlan> plans = subscriptionPlanService.getAllPlans();
            plans.forEach(p -> log.info("Plan {} ({}) - isActive: {}", p.getName(), p.getId(), p.isActive()));
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            log.error("Error fetching all plans", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/admin/subscription/plans/{planId}")
    public ResponseEntity<SubscriptionPlan> updatePlan(
            @PathVariable String planId,
            @Valid @RequestBody CreatePlanRequest request) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            SubscriptionPlan plan = subscriptionPlanService.updatePlan(planId, request);
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            log.error("Error updating plan {}", planId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/admin/subscription/plans/{planId}/active")
    public ResponseEntity<Void> setActive(
            @PathVariable String planId,
            @RequestBody Map<String, Boolean> body) {

        if (!AuthUtils.isAdmin()) {
            log.warn("Non-admin user attempted to set plan active status: {}", planId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Boolean isActive = body.get("isActive");
            log.info("Setting plan {} active status to {}", planId, isActive);
            subscriptionPlanService.setActive(planId, isActive);
            log.info("Successfully updated plan {} active status to {}", planId, isActive);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error updating plan active status {}: {}", planId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/admin/subscription/plans/{planId}")
    public ResponseEntity<Void> deletePlan(@PathVariable String planId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            subscriptionPlanService.deletePlan(planId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting plan {}", planId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
