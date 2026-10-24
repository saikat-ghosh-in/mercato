package com.mercato.Payloads.Request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SellerApplicationRequest {
    @NotBlank @Size(max = 100) private String storeName;
    @NotBlank @Size(max = 150) private String legalBusinessName;
    @NotBlank @Pattern(regexp = "SOLE_PROPRIETOR|PARTNERSHIP|COMPANY|LLP|OTHER") private String businessType;
    @NotBlank @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]", message = "Enter a valid PAN format") private String pan;
    @Size(max = 15) private String gstin;
    @NotBlank @Pattern(regexp = "GST_REGISTERED|ENROLLED_UNREGISTERED") private String taxRegistrationType;
    @Size(max = 30) private String enrolmentNumber;
    @NotBlank @Size(min = 10, max = 500) private String pickupAddress;
    @NotBlank @Size(max = 80) private String pickupState;
    @NotBlank @Pattern(regexp = "[1-9][0-9]{5}") private String pickupPincode;
    @NotBlank @Email @Size(max = 100) private String supportEmail;
    @NotBlank @Size(max = 300) private String productCategories;
    @AssertTrue(message = "Review the seller policies before applying") private boolean policiesAcknowledged;
    @NotBlank @Pattern(regexp = "2026-10-draft") private String policyVersion;

    @AssertTrue(message = "Provide a valid GSTIN matching PAN, or a GST portal enrolment number for review")
    public boolean isTaxRegistrationValid() {
        if ("GST_REGISTERED".equals(taxRegistrationType))
            return gstin != null && gstin.matches("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]")
                    && pan != null && gstin.substring(2, 12).equals(pan);
        return "ENROLLED_UNREGISTERED".equals(taxRegistrationType) && enrolmentNumber != null
                && enrolmentNumber.matches("[A-Za-z0-9/-]{6,30}") && (gstin == null || gstin.isBlank());
    }
}
