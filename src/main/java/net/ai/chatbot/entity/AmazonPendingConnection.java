package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Short-lived record used to correlate the LWA authorization redirect
 * with the browser-side finalize call. Created when the user clicks
 * "Connect"; the {@code state} param is stamped on both the Amazon
 * authorize URL and the frontend callback URL so CSRF attacks can't
 * smuggle in someone else's authorization.
 */
@Document(collection = "amazon_pending_connections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmazonPendingConnection {

    @Id
    private String id;

    /** Random CSRF / correlation token embedded in Amazon's authorize URL. */
    @Indexed(unique = true)
    private String state;

    /** Clerk user id the connection belongs to. */
    @Indexed
    private String userId;

    /** SP-API region the user is connecting (NA / EU / FE). */
    private String region;

    private Instant createdAt;

    private Instant updatedAt;
}
