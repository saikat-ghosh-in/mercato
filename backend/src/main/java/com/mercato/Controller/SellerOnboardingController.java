package com.mercato.Controller;

import com.mercato.Service.SellerOnboardingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/admin/seller-applications") @RequiredArgsConstructor
public class SellerOnboardingController {
    private final SellerOnboardingService service;
    public record Review(@NotNull Boolean approve, @Size(max = 1000) String note, boolean verificationConfirmed) {}
    @GetMapping public List<Map<String, Object>> list() { return service.list(); }
    @PostMapping("/{userId}/review") public Map<String, String> review(@PathVariable String userId, @Valid @RequestBody Review request) {
        if (Boolean.TRUE.equals(request.approve()) && !request.verificationConfirmed()) throw new IllegalArgumentException("Confirm business, tax and pickup details have been verified");
        service.review(userId, request.approve(), request.note());
        return Map.of("message", request.approve() ? "Seller approved" : "Application declined");
    }
}
