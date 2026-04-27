package net.ai.chatbot.dto.aliexpress;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliExpressProductDto {

    private String productId;
    private String skuCode;
    private String title;
    private String bodyHtml;
    private String brandName;
    private String categoryId;
    private String price;
    private String currency;
    private Integer inventoryQuantity;
    private String aeStatus;
    private List<String> images;

    private String enhancedTitle;
    private String enhancedBodyHtml;
    private String enhancedBrandName;
}
