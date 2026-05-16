package net.ai.chatbot.dto.admin.request;

import lombok.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TicketMessageRequest {

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message must be at most 5000 characters")
    private String content;

    private List<String> attachmentUrls;
}
