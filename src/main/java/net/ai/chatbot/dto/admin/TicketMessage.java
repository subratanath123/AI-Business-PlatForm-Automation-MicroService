package net.ai.chatbot.dto.admin;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TicketMessage {

    private String id;
    private String senderId;
    private String senderEmail;
    private String senderName;

    @Builder.Default
    private boolean isAdmin = false;

    private String content;

    @Builder.Default
    private List<String> attachmentUrls = new ArrayList<>();

    private Instant sentAt;
    private boolean isRead;
}
