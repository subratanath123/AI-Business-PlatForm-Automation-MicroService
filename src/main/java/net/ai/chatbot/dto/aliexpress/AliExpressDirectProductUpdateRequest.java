package net.ai.chatbot.dto.aliexpress;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class AliExpressDirectProductUpdateRequest {
    private String title;
    private String bodyHtml;
    @JsonAlias("brand")
    private String brandName;
    private String categoryId;
    private String price;
    private String currency;
    @JsonAlias("sku")
    private String skuCode;
    private Integer inventoryQuantity;
    private List<String> images;
    private String aeStatus;
}
