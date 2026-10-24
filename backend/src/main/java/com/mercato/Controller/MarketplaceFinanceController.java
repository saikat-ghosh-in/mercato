package com.mercato.Controller;
import com.mercato.Service.MarketplaceFinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
@RestController @RequiredArgsConstructor
public class MarketplaceFinanceController {
    private final MarketplaceFinanceService finance;
    @GetMapping("/api/admin/finance") public Object admin(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through){return finance.report(from,through,true);}
    @GetMapping("/api/seller/finance") public Object seller(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through){return finance.report(from,through,false);}
}
