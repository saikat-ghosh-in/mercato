package com.mercato.Controller;
import com.mercato.Service.CommissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class CommissionController {
    private final CommissionService service;
    public record Change(@NotNull BigDecimal rate, @NotBlank @Size(max=1000) String reason, @NotBlank @Size(max=30) String confirmation) {}
    @GetMapping("/admin/commission") public Map<String,Object> policy() { return service.policy(); }
    @PostMapping("/admin/commission") public Map<String,String> schedule(@Valid @RequestBody Change request) {
        service.schedule(request.rate(), request.reason(), request.confirmation()); return Map.of("message", "Seller notices queued; the rate remains unchanged");
    }
    @PostMapping("/admin/commission/{id}/cancel") public Map<String,String> cancel(@PathVariable Long id) {
        service.cancel(id); return Map.of("message", "Change cancelled; seller notices queued");
    }
    @GetMapping("/account/seller-notices") public List<Map<String,Object>> notices() { return service.myNotices(); }
    @PostMapping("/account/seller-notices/{id}/read") public Map<String,String> read(@PathVariable Long id) {
        service.markRead(id); return Map.of("message", "Notice marked read");
    }
}
