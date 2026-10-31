package com.mercato.Security.payloads;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    @jakarta.validation.constraints.Size(max=320)
    private String username;
    @NotBlank
    private String password;
}
