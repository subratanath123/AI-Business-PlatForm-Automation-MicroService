package net.ai.chatbot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.PromoCode;
import net.ai.chatbot.dto.admin.request.CreatePromoCodeRequest;
import net.ai.chatbot.dto.admin.response.PromoCodeValidationResponse;
import net.ai.chatbot.service.admin.PromoCodeService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    @PostMapping("/promo/validate")
    public ResponseEntity<PromoCodeValidationResponse> validatePromoCode(
            @RequestBody Map<String, Object> request) {
        try {
            String code = (String) request.get("code");
            String planId = (String) request.get("planId");
            BigDecimal amount = request.get("amount") != null ?
                    new BigDecimal(request.get("amount").toString()) : BigDecimal.ZERO;

            PromoCodeValidationResponse response = promoCodeService.validatePromoCode(code, planId, amount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error validating promo code", e);
            return ResponseEntity.ok(PromoCodeValidationResponse.builder()
                    .valid(false)
                    .message("Error validating promo code")
                    .build());
        }
    }

    @PostMapping("/admin/promo-codes")
    public ResponseEntity<PromoCode> createPromoCode(@Valid @RequestBody CreatePromoCodeRequest request) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            PromoCode promoCode = promoCodeService.createPromoCode(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(promoCode);
        } catch (Exception e) {
            log.error("Error creating promo code", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/admin/promo-codes")
    public ResponseEntity<Map<String, Object>> getAllPromoCodes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean isActive) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<PromoCode> promoCodes = promoCodeService.getAllPromoCodes(page, size, isActive);
            long total = promoCodeService.countPromoCodes(isActive);

            Map<String, Object> response = new HashMap<>();
            response.put("promoCodes", promoCodes);
            response.put("total", total);
            response.put("page", page);
            response.put("pageSize", size);
            response.put("totalPages", (int) Math.ceil((double) total / size));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching promo codes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/admin/promo-codes/{promoCodeId}")
    public ResponseEntity<PromoCode> getPromoCode(@PathVariable String promoCodeId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            PromoCode promoCode = promoCodeService.getPromoCodeById(promoCodeId);
            return ResponseEntity.ok(promoCode);
        } catch (Exception e) {
            log.error("Error fetching promo code {}", promoCodeId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/admin/promo-codes/{promoCodeId}")
    public ResponseEntity<PromoCode> updatePromoCode(
            @PathVariable String promoCodeId,
            @Valid @RequestBody CreatePromoCodeRequest request) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            PromoCode promoCode = promoCodeService.updatePromoCode(promoCodeId, request);
            return ResponseEntity.ok(promoCode);
        } catch (Exception e) {
            log.error("Error updating promo code {}", promoCodeId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/admin/promo-codes/{promoCodeId}/active")
    public ResponseEntity<Void> setActive(
            @PathVariable String promoCodeId,
            @RequestBody Map<String, Boolean> body) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            promoCodeService.setActive(promoCodeId, body.get("isActive"));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error updating promo code active status {}", promoCodeId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/admin/promo-codes/{promoCodeId}")
    public ResponseEntity<Void> deletePromoCode(@PathVariable String promoCodeId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            promoCodeService.deletePromoCode(promoCodeId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting promo code {}", promoCodeId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
