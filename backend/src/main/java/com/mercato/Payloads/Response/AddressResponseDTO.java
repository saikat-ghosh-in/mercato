package com.mercato.Payloads.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AddressResponseDTO {
    private final String addressId;
    private final String userId;
    private final String recipientName;
    private final String recipientPhone;
    private final String addressLine1;
    private final String addressLine2;
    private final String city;
    private final String state;
    private final String pincode;
    private final String country;
    private final boolean defaultAddress;
}
