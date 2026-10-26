package com.mercato.Mapper;

import com.mercato.Entity.fulfillment.payment.Payment;
import com.mercato.Payloads.Response.PaymentResponseDTO;

public class PaymentMapper {

    public static PaymentResponseDTO toDTO(Payment payment) {
        return new PaymentResponseDTO(
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod().toString(),
                payment.getStatus().toString(),
                payment.getGatewayReference(),
                payment.getGatewayName(),
                payment.getGatewayResponseMessage(),
                payment.getCfOrderId(),
                payment.getInitiatedAt(),
                payment.getCompletedAt(),
                payment.getPaymentPolicy()==null?null:payment.getPaymentPolicy().name(),
                payment.getCapturedAmount(),
                payment.getCapturedAt(),
                payment.getAuthorizedAmount(),
                payment.getAuthorizedAt(),
                payment.getAuthorizationExpiresAt(),
                payment.isFinancialHalt(),
                payment.getFinancialHaltReason(),
                payment.getCaptureVerifiedAt(),
                payment.getProviderCapturedAt(),
                payment.getProviderCaptureTimeSource(),
                payment.getCapturedAtProvenance(),
                payment.getPaymentPolicyVersion()
        );
    }
}
