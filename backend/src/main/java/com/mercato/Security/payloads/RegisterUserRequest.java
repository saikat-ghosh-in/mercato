package com.mercato.Security.payloads;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 120, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^[+]?[0-9]{7,15}$",
            message = "Invalid phone number"
    )
    private String phoneNumber;

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    @Pattern(regexp = "CUSTOMER|SELLER")
    private String accountType = "CUSTOMER";

    @Size(max = 120)
    private String confirmPassword;

    @jakarta.validation.Valid
    private com.mercato.Payloads.Request.SellerApplicationRequest sellerApplication;

    @jakarta.validation.constraints.AssertTrue(message = "Passwords must match")
    public boolean isPasswordConfirmationValid() {
        return confirmPassword == null || confirmPassword.equals(password);
    }

    @jakarta.validation.constraints.AssertTrue(message = "Seller business details are required")
    public boolean isSellerApplicationPresent() {
        return !"SELLER".equals(accountType) || sellerApplication != null;
    }
}
