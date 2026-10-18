package com.mercato.Payloads.Request;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
public class AddressRequestDTO {
    @NotBlank @Size(max = 100)
    private String recipientName;
    @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "Enter a valid 10-digit phone number")
    private String recipientPhone;
    @NotBlank @Size(min = 3, max = 255)
    private String addressLine1;
    @Size(max = 255)
    private String addressLine2;
    @NotBlank @Size(min = 2, max = 100)
    private String city;
    @NotBlank @Size(min = 2, max = 100)
    private String state;
    @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a valid 6-digit Indian pincode")
    private String pincode;
    @NotBlank @Size(min = 2, max = 100)
    private String country;
}
