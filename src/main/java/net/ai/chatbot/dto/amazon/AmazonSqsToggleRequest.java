package net.ai.chatbot.dto.amazon;

import lombok.Data;

@Data
public class AmazonSqsToggleRequest {
    private boolean enabled;
    /** ARN of the SQS queue to deliver notifications to (required when enabling). */
    private String sqsQueueArn;
}
