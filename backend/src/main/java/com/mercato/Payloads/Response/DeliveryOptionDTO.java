package com.mercato.Payloads.Response;

import com.mercato.Entity.cart.DeliveryMethod;
import java.math.BigDecimal;

public record DeliveryOptionDTO(DeliveryMethod code, String name, String estimate, String description, BigDecimal price) {}
