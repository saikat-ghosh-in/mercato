package com.mercato.Payloads.Request;

import com.mercato.Entity.cart.DeliveryMethod;
import jakarta.validation.constraints.NotNull;

public record DeliveryMethodRequest(@NotNull DeliveryMethod deliveryMethod) {}
