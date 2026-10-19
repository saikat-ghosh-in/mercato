package com.mercato.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@RestController
public class MarketplaceTermsController {
    private final com.mercato.Service.CommissionService commissions;

    public MarketplaceTermsController(com.mercato.Service.CommissionService commissions) { this.commissions = commissions; }

    @GetMapping("/api/public/marketplace-terms")
    public Map<String, Object> terms() {
        return Map.of("version", "2026-10-package-pricing-draft", "draft", true, "commissionPercent", commissions.currentRate(),
                "commissionCapPerItem", com.mercato.Utils.CommissionMath.CAP_PER_ITEM,
                "commissionBasis", "Item value after seller discounts, excluding delivery and separately stated taxes",
                "shippingOperator", "Mercato", "settlement", "Proposed weekly settlement after delivery and the applicable return window; activation requires payout verification");
    }
}
