package com.mercato.Controller;
import com.mercato.Service.SellerOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class SellerApplicationAccountController {
    private final SellerOnboardingService service;
    @GetMapping("/api/account/seller-application") public Map<String,Object> mine() { return service.myApplication(); }
}
