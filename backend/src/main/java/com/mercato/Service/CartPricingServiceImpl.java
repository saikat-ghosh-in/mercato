package com.mercato.Service;

import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.ChargeType;
import com.mercato.Entity.cart.DeliveryMethod;
import com.mercato.Payloads.Response.DeliveryOptionDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CartPricingServiceImpl implements CartPricingService {
    private final PackageDeliveryPricing packages;

    @Value("${cart.shipping.threshold}")
    private BigDecimal CART_SHIPPING_THRESHOLD;
    @Value("${cart.shipping.charge}")
    private BigDecimal CART_SHIPPING_CHARGE;
    @Value("${cart.platform.fee}")
    private BigDecimal CART_PLATFORM_FEE;

    @Value("${cart.delivery.expedited-surcharge:49}")
    private BigDecimal expeditedSurcharge = BigDecimal.valueOf(49);
    @Value("${cart.delivery.standard-cap:250}")
    private BigDecimal standardCap = BigDecimal.valueOf(250);

    @jakarta.annotation.PostConstruct void validateConfiguration() {
        if (standardCap.signum() < 0 || standardCap.compareTo(BigDecimal.valueOf(250)) > 0 || expeditedSurcharge.signum() < 0
                || CART_SHIPPING_THRESHOLD.signum() < 0 || CART_SHIPPING_CHARGE.signum() < 0)
            throw new IllegalArgumentException("Delivery charges must be non-negative and standard delivery capped at ₹250 or less");
    }

    @Override
    public void applyCharges(Cart cart) {
        BigDecimal subtotal = cart.getSubtotal();
        long cartQty = cart.getCartQuantity();

        BigDecimal base = subtotal.signum() > 0 && subtotal.compareTo(CART_SHIPPING_THRESHOLD) < 0 ? CART_SHIPPING_CHARGE : BigDecimal.ZERO;
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        Map<String, BigDecimal> bulkyCosts = new HashMap<>();
        BigDecimal totalBulky = BigDecimal.ZERO;
        for (var item : cart.getCartItems()) {
            BigDecimal bulky = packages.surcharge(item.getProduct()).multiply(BigDecimal.valueOf(item.getQuantity()));
            bulkyCosts.put(item.getProduct().getProductId(), bulky); totalBulky = totalBulky.add(bulky);
            // Multiplying by subtotal avoids rounding the proportional share of the base charge.
            weights.put(item.getProduct().getProductId(), bulky.multiply(subtotal).add(base.multiply(item.getLineTotal())));
        }
        BigDecimal normalCost = base.add(totalBulky).min(standardCap);
        Map<String, BigDecimal> shares = com.mercato.Utils.DeliveryAllocation.split(normalCost, weights);
        BigDecimal standard = BigDecimal.ZERO, customerBulky = BigDecimal.ZERO, sellerCovered = BigDecimal.ZERO;
        for (var item : cart.getCartItems()) {
            String id = item.getProduct().getProductId(); BigDecimal share = shares.get(id);
            boolean covered = item.getProduct().isSellerPaysShipping();
            item.setSellerDeliveryCharge(covered ? share : BigDecimal.ZERO);
            item.setBuyerNormalDeliveryCharge(covered?BigDecimal.ZERO.setScale(2):share);
            if (covered) sellerCovered = sellerCovered.add(share);
            else {
                standard = standard.add(share);
                BigDecimal raw = weights.get(id);
                if (raw.signum() > 0) customerBulky = customerBulky.add(share.multiply(bulkyCosts.get(id)).multiply(subtotal).divide(raw, 2, RoundingMode.DOWN));
            }
        }
        boolean sellerPaid = standard.signum() == 0 && sellerCovered.signum() > 0;
        cart.setDeliveryOptions(java.util.List.of(
                new DeliveryOptionDTO(DeliveryMethod.STANDARD, "Standard", "3–5 business days", "", standard, customerBulky, sellerPaid),
                new DeliveryOptionDTO(DeliveryMethod.EXPEDITED, "Expedited", "1–2 business days", "", standard.add(expeditedSurcharge), customerBulky, sellerPaid),
                new DeliveryOptionDTO(DeliveryMethod.CONSOLIDATED, "Grouped delivery", "4–7 business days", "Extra time to group deliveries, aiming for fewer trips and lower carbon emissions.", standard, customerBulky, sellerPaid)
        ));
        if (cart.getDeliveryMethod() == null) cart.setDeliveryMethod(DeliveryMethod.STANDARD);
        Map<String,BigDecimal> upgradeWeights=new LinkedHashMap<>();
        cart.getCartItems().forEach(item->upgradeWeights.put(item.getProduct().getProductId(),item.getLineTotal()));
        var upgrades=com.mercato.Utils.DeliveryAllocation.split(cart.getDeliveryMethod()==DeliveryMethod.EXPEDITED && subtotal.signum()>0?expeditedSurcharge:BigDecimal.ZERO,upgradeWeights);
        cart.getCartItems().forEach(item->item.setBuyerExpeditedDeliveryCharge(upgrades.get(item.getProduct().getProductId())));

        if (subtotal.compareTo(BigDecimal.ZERO) == 0) {
            cart.clearCharges();
            cart.getCartItems().forEach(item->{item.setBuyerNormalDeliveryCharge(BigDecimal.ZERO);item.setBuyerExpeditedDeliveryCharge(BigDecimal.ZERO);item.setSellerDeliveryCharge(BigDecimal.ZERO);});
            return;
        }

        applyShipping(cart, subtotal);
        applyPlatformFee(cart);
        applyProcessing(cart, cartQty);
    }

    private void applyShipping(Cart cart, BigDecimal subtotal) {
        DeliveryOptionDTO selected = cart.getDeliveryOptions().stream()
                .filter(option -> option.code() == cart.getDeliveryMethod()).findFirst().orElseThrow();
        BigDecimal shipping = selected.price();

        cart.addOrUpdateCharge(
                ChargeType.SHIPPING,
                shipping,
                selected.name() + " delivery"
        );
    }

    private void applyPlatformFee(Cart cart) {
        cart.addOrUpdateCharge(
                ChargeType.PLATFORM_FEE,
                CART_PLATFORM_FEE,
                "Platform Fee"
        );
    }

    private void applyProcessing(Cart cart, long qty) {
        BigDecimal charge;
        if (qty <= 2) {
            charge = BigDecimal.ZERO;
        } else if (qty <= 10) {
            charge = BigDecimal.valueOf(25);
        } else {
            long extraQty = qty - 10;
            long blocks = (extraQty + 4) / 5;
            charge = BigDecimal.valueOf(25 + (blocks * 10L));
        }

        cart.addOrUpdateCharge(
                ChargeType.PROCESSING_AND_HANDLING,
                charge,
                "Processing & Handling"
        );
    }
}
