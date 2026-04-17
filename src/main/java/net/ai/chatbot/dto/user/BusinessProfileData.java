package net.ai.chatbot.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Business-facing profile fields mirrored from the Next.js settings page (Clerk publicMetadata).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessProfileData {

    private String companyName;
    private String jobTitle;
    private String businessPhone;
    private String website;
    private String industry;
    private String teamSize;
    private String addressLine;
    private String city;
    private String region;
    private String postalCode;
    private String country;
    private String timezone;
    private String taxId;
    private String notes;
}
