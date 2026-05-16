package net.ai.chatbot.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.PromoCodeDao;
import net.ai.chatbot.dto.admin.PromoCode;
import net.ai.chatbot.dto.admin.request.CreatePromoCodeRequest;
import net.ai.chatbot.dto.admin.response.PromoCodeValidationResponse;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private final PromoCodeDao promoCodeDao;

    public PromoCode createPromoCode(CreatePromoCodeRequest request) {
        String code = request.getCode().toUpperCase().trim();

        if (promoCodeDao.existsByCode(code)) {
            throw new RuntimeException("Promo code already exists: " + code);
        }

        PromoCode promoCode = PromoCode.builder()
                .code(code)
                .name(request.getName())
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .minPurchaseAmount(request.getMinPurchaseAmount())
                .validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil())
                .maxUsageCount(request.getMaxUsageCount())
                .maxUsagePerUser(request.getMaxUsagePerUser())
                .applicablePlanIds(request.getApplicablePlanIds())
                .excludedPlanIds(request.getExcludedPlanIds())
                .isFirstTimeOnly(request.isFirstTimeOnly())
                .isReferralCode(request.isReferralCode())
                .referrerUserId(request.getReferrerUserId())
                .createdBy(AuthUtils.getEmail())
                .build();

        log.info("Creating promo code: {}", code);
        return promoCodeDao.save(promoCode);
    }

    public PromoCode getPromoCodeById(String id) {
        return promoCodeDao.findById(id)
                .orElseThrow(() -> new RuntimeException("Promo code not found: " + id));
    }

    public PromoCode getPromoCodeByCode(String code) {
        return promoCodeDao.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Promo code not found: " + code));
    }

    public List<PromoCode> getAllPromoCodes(int page, int size, Boolean isActive) {
        return promoCodeDao.findAll(page, size, isActive);
    }

    public long countPromoCodes(Boolean isActive) {
        return promoCodeDao.count(isActive);
    }

    public List<PromoCode> getValidPromoCodes() {
        return promoCodeDao.findValidCodes();
    }

    public PromoCodeValidationResponse validatePromoCode(String code, String planId, BigDecimal originalAmount) {
        String userId = AuthUtils.getUserId();

        try {
            PromoCode promoCode = promoCodeDao.findByCode(code).orElse(null);

            if (promoCode == null) {
                return PromoCodeValidationResponse.builder()
                        .valid(false)
                        .message("Invalid promo code")
                        .code(code)
                        .build();
            }

            if (!promoCodeDao.isCodeValid(code, userId, planId)) {
                return PromoCodeValidationResponse.builder()
                        .valid(false)
                        .message("Promo code is not valid or has expired")
                        .code(code)
                        .build();
            }

            if (promoCode.getMinPurchaseAmount() != null &&
                    originalAmount.compareTo(promoCode.getMinPurchaseAmount()) < 0) {
                return PromoCodeValidationResponse.builder()
                        .valid(false)
                        .message("Minimum purchase amount is " + promoCode.getMinPurchaseAmount())
                        .code(code)
                        .build();
            }

            BigDecimal discountAmount = calculateDiscount(promoCode, originalAmount);
            BigDecimal finalAmount = originalAmount.subtract(discountAmount);

            if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
                finalAmount = BigDecimal.ZERO;
            }

            return PromoCodeValidationResponse.builder()
                    .valid(true)
                    .message("Promo code applied successfully")
                    .code(code)
                    .discountType(promoCode.getDiscountType())
                    .discountValue(promoCode.getDiscountValue())
                    .discountAmount(discountAmount)
                    .finalAmount(finalAmount)
                    .build();

        } catch (Exception e) {
            log.error("Error validating promo code: {}", code, e);
            return PromoCodeValidationResponse.builder()
                    .valid(false)
                    .message("Error validating promo code")
                    .code(code)
                    .build();
        }
    }

    private BigDecimal calculateDiscount(PromoCode promoCode, BigDecimal originalAmount) {
        BigDecimal discount;

        switch (promoCode.getDiscountType()) {
            case PERCENTAGE:
                discount = originalAmount.multiply(promoCode.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                break;
            case FIXED_AMOUNT:
                discount = promoCode.getDiscountValue();
                break;
            case FREE_TRIAL_DAYS:
                discount = BigDecimal.ZERO;
                break;
            default:
                discount = BigDecimal.ZERO;
        }

        if (promoCode.getMaxDiscountAmount() != null &&
                discount.compareTo(promoCode.getMaxDiscountAmount()) > 0) {
            discount = promoCode.getMaxDiscountAmount();
        }

        return discount;
    }

    public void applyPromoCode(String promoCodeId, String userId) {
        log.info("Applying promo code {} for user {}", promoCodeId, userId);
        promoCodeDao.incrementUsage(promoCodeId, userId);
    }

    public PromoCode updatePromoCode(String id, CreatePromoCodeRequest request) {
        PromoCode promoCode = getPromoCodeById(id);

        String newCode = request.getCode().toUpperCase().trim();
        if (!promoCode.getCode().equals(newCode) && promoCodeDao.existsByCode(newCode)) {
            throw new RuntimeException("Promo code already exists: " + newCode);
        }

        promoCode.setCode(newCode);
        promoCode.setName(request.getName());
        promoCode.setDescription(request.getDescription());
        promoCode.setDiscountType(request.getDiscountType());
        promoCode.setDiscountValue(request.getDiscountValue());
        promoCode.setMaxDiscountAmount(request.getMaxDiscountAmount());
        promoCode.setMinPurchaseAmount(request.getMinPurchaseAmount());
        promoCode.setValidFrom(request.getValidFrom());
        promoCode.setValidUntil(request.getValidUntil());
        promoCode.setMaxUsageCount(request.getMaxUsageCount());
        promoCode.setMaxUsagePerUser(request.getMaxUsagePerUser());
        promoCode.setApplicablePlanIds(request.getApplicablePlanIds());
        promoCode.setExcludedPlanIds(request.getExcludedPlanIds());
        promoCode.setFirstTimeOnly(request.isFirstTimeOnly());
        promoCode.setReferralCode(request.isReferralCode());
        promoCode.setReferrerUserId(request.getReferrerUserId());

        log.info("Updating promo code: {}", newCode);
        return promoCodeDao.save(promoCode);
    }

    public void setActive(String id, boolean isActive) {
        log.info("Setting promo code {} active status to {}", id, isActive);
        promoCodeDao.setActive(id, isActive);
    }

    public void deletePromoCode(String id) {
        log.info("Deleting promo code: {}", id);
        promoCodeDao.delete(id);
    }
}
