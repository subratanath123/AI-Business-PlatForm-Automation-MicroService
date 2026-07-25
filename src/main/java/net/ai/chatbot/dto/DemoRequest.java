package net.ai.chatbot.dto;

import lombok.Data;

@Data
public class DemoRequest {
    private String fullName;
    private String email;
    private String company;
    private String phone;
    private String companySize;
    private String primaryGoal;
}
