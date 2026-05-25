package net.ai.chatbot.dto.notification;

public enum NotificationType {
    USAGE_WARNING,   // usage crossed a percentage threshold
    USAGE_CRITICAL,  // usage at or above 90%
    BILLING_ALERT,   // custom user-defined billing alert
    SUBSCRIPTION,    // plan activated / renewed / cancelled
    SYSTEM           // general platform announcement
}
