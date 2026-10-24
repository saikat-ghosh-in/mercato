package com.mercato.Service;

import com.mercato.Entity.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import jakarta.annotation.PostConstruct;

/** Delivery surcharge per packed sellable unit. Values are configurable business defaults. */
@Component
public class PackageDeliveryPricing {
    @Value("${cart.delivery.included-weight-kg:5}") private BigDecimal includedKg = new BigDecimal("5");
    @Value("${cart.delivery.weight-block-kg:5}") private BigDecimal blockKg = new BigDecimal("5");
    @Value("${cart.delivery.weight-block-charge:50}") private BigDecimal blockCharge = new BigDecimal("50");
    @Value("${cart.delivery.max-standard-side-cm:100}") private BigDecimal standardSideCm = new BigDecimal("100");
    @Value("${cart.delivery.oversize-minimum:100}") private BigDecimal oversizeMinimum = new BigDecimal("100");
    @Value("${cart.delivery.volumetric-divisor:5000}") private BigDecimal divisor = new BigDecimal("5000");

    @PostConstruct void validateConfiguration() {
        if (java.util.stream.Stream.of(includedKg,blockKg,standardSideCm,divisor).anyMatch(value -> value.signum() <= 0)
                || blockCharge.signum() < 0 || oversizeMinimum.signum() < 0)
            throw new IllegalArgumentException("Package delivery thresholds must be positive and charges non-negative");
    }

    public BigDecimal surcharge(Product product) {
        // Existing listings without measurements keep their previous pricing until backfilled.
        if (product == null || product.getWeightKg() == null || product.getLengthCm() == null
                || product.getWidthCm() == null || product.getHeightCm() == null) return BigDecimal.ZERO;
        BigDecimal volumetricKg = product.getLengthCm().multiply(product.getWidthCm()).multiply(product.getHeightCm()).divide(divisor, 16, RoundingMode.CEILING);
        BigDecimal chargeableKg = product.getWeightKg().max(volumetricKg);
        BigDecimal extraKg = chargeableKg.subtract(includedKg).max(BigDecimal.ZERO);
        BigDecimal weightFee = extraKg.divide(blockKg, 0, RoundingMode.CEILING).multiply(blockCharge);
        BigDecimal longest = product.getLengthCm().max(product.getWidthCm()).max(product.getHeightCm());
        return (longest.compareTo(standardSideCm) > 0 ? weightFee.max(oversizeMinimum) : weightFee).setScale(2, RoundingMode.HALF_UP);
    }
}
