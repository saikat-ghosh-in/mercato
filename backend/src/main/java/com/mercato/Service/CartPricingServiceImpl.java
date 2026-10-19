package com.mercato.Service;

import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.ChargeType;
import com.mercato.Entity.cart.DeliveryMethod;
import com.mercato.Payloads.Response.DeliveryOptionDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class CartPricingServiceImpl implements CartPricingService {

    @Value("${cart.shipping.threshold}")
    private BigDecimal CART_SHIPPING_THRESHOLD;
    @Value("${cart.shipping.charge}")
    private BigDecimal CART_SHIPPING_CHARGE;
    @Value("${cart.platform.fee}")
    private BigDecimal CART_PLATFORM_FEE;

    @Value("${cart.delivery.expedited-surcharge:99}")
    private BigDecimal expeditedSurcharge = BigDecimal.valueOf(99);

    @Override
    public void applyCharges(Cart cart) {
        BigDecimal subtotal = cart.getSubtotal();
        int cartQty = cart.getCartQuantity();

        BigDecimal standard = subtotal.compareTo(CART_SHIPPING_THRESHOLD) < 0 ? CART_SHIPPING_CHARGE : BigDecimal.ZERO;
        cart.setDeliveryOptions(java.util.List.of(
                new DeliveryOptionDTO(DeliveryMethod.STANDARD, "Standard", "3–5 business days", "₹" + CART_SHIPPING_CHARGE.toPlainString() + " below ₹" + CART_SHIPPING_THRESHOLD.toPlainString() + "; free from that amount. Mercato-arranged delivery.", standard),
                new DeliveryOptionDTO(DeliveryMethod.EXPEDITED, "Expedited", "1–2 business days", "Priority handling and delivery. Estimated from dispatch.", standard.add(expeditedSurcharge)),
                new DeliveryOptionDTO(DeliveryMethod.CONSOLIDATED, "Consolidated", "4–7 business days", "Allow 1–2 extra days for grouped dispatch. Emissions savings are not verified.", standard)
        ));
        if (cart.getDeliveryMethod() == null) cart.setDeliveryMethod(DeliveryMethod.STANDARD);

        if (subtotal.compareTo(BigDecimal.ZERO) == 0) {
            cart.clearCharges();
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

    private void applyProcessing(Cart cart, int qty) {
        BigDecimal charge;
        if (qty <= 2) {
            charge = BigDecimal.ZERO;
        } else if (qty <= 10) {
            charge = BigDecimal.valueOf(25);
        } else {
            int extraQty = qty - 10;
            int blocks = (int) Math.ceil(extraQty / 5.0);
            charge = BigDecimal.valueOf(25 + (blocks * 10L));
        }

        cart.addOrUpdateCharge(
                ChargeType.PROCESSING_AND_HANDLING,
                charge,
                "Processing & Handling"
        );
    }
}
