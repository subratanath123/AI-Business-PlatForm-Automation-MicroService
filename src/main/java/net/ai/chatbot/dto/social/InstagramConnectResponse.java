package net.ai.chatbot.dto.social;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for Instagram account connection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstagramConnectResponse {

    private boolean success;
    private String accountId;
    private String instagramAccountId;
    private String instagramUsername;
    private String message;
    private Boolean webhookSubscribed;
}
