package net.ai.chatbot.dto.admin.response;

import lombok.*;
import net.ai.chatbot.dto.admin.PromoCode.DiscountType;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PromoCodeValidationResponse {

    private boolean valid;
    private String message;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
}
