package com.mercato.Commerce;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.Locale;

/** Reads authenticated provider facts. SUCCESS and an auth_id alone prove neither capture nor hold. */
public final class PaymentEvidence {
    private PaymentEvidence() {}

    public enum State { CAPTURED, AUTHORIZED, RELEASED, INSUFFICIENT, CONFLICTING, NONE }
    public record Facts(State state, BigDecimal originalAmount, BigDecimal capturedAmount,
            String authorizationId, Instant authorizedAt, Instant expiresAt, Instant capturedAt,
            String captureTimeSource, String reason) {}

    public static Facts read(JsonNode event, BigDecimal orderAmount, String currency) {
        String status = event.path("status").asText();
        JsonNode authorization = event.path("authorization");
        String authId = event.path("authorizationId").asText("");
        if (authId.length() > 100) return conflict("Invalid authorization identity");
        if (!status.equals("SUCCESS")) {
            boolean collectionClaim = event.has("isCaptured") && (!event.path("isCaptured").isBoolean() || event.path("isCaptured").booleanValue());
            collectionClaim |= authorization.path("action").asText().equalsIgnoreCase("CAPTURE")
                && authorization.path("status").asText().equalsIgnoreCase("SUCCESS");
            if (authorization.hasNonNull("captured_amount")) {
                try { collectionClaim |= PaymentRules.decimal(authorization.path("captured_amount").asText()).signum() > 0; }
                catch (IllegalArgumentException e) { return conflict("Invalid captured amount"); }
            }
            return collectionClaim ? conflict("Payment status contradicts collection evidence")
                : new Facts(State.NONE, null, null, authId, null, null, null, null, null);
        }
        BigDecimal original;
        try {
            original = PaymentRules.decimal(event.path("amount").asText());
            if (!original.equals(orderAmount.setScale(2)) || !currency.equals(event.path("currency").asText()))
                return conflict("Original amount or currency differs from order");
        } catch (IllegalArgumentException e) {
            return conflict("Missing or invalid original amount");
        }
        if (event.path("authorizationAliasConflict").asBoolean()) return conflict("Authorization aliases disagree");
        if (event.has("providerOrderId") && !event.path("orderId").asText().equals(event.path("providerOrderId").asText()))
            return conflict("Provider order identity differs");
        if (!event.has("isCaptured"))
            return insufficient(original, authId, "Explicit capture flag required");
        if (!event.path("isCaptured").isBoolean()) return conflict("Capture flag must be boolean");
        boolean captured = event.path("isCaptured").booleanValue();
        String action = authorization.path("action").asText("").toUpperCase(Locale.ROOT);
        String actionStatus = authorization.path("status").asText("").toUpperCase(Locale.ROOT);
        BigDecimal capturedAmount = null;
        if (authorization.hasNonNull("captured_amount")) {
            try { capturedAmount = PaymentRules.decimal(authorization.path("captured_amount").asText()); }
            catch (IllegalArgumentException e) { return conflict("Invalid captured amount"); }
            if (capturedAmount.compareTo(original) > 0) return conflict("Capture exceeds original amount");
        }
        if (captured && ((!action.isEmpty() && !action.equals("CAPTURE"))
                || (!actionStatus.isEmpty() && !actionStatus.equals("SUCCESS"))))
            return conflict("Captured flag contradicts authorization action");
        if (!captured && ((action.equals("CAPTURE") && actionStatus.equals("SUCCESS"))
                || (capturedAmount != null && capturedAmount.signum() > 0)))
            return conflict("Uncaptured flag contradicts collection evidence");
        if (!action.isEmpty() && !action.equals("CAPTURE") && !action.equals("VOID"))
            return conflict("Unknown authorization action");
        Instant authAt = instant(authorization.path("start_time").asText());
        Instant providerExpiry = instant(authorization.path("end_time").asText());
        Instant expiresAt = authAt == null || providerExpiry == null ? null
                : earlier(providerExpiry, authAt.plus(Duration.ofDays(7)));
        if (expiresAt != null && !expiresAt.isAfter(authAt)) return conflict("Authorization expiry precedes start");
        if (captured) {
            if (authorization.isObject() && (!action.equals("CAPTURE") || !actionStatus.equals("SUCCESS")))
                return insufficient(original, authId, "Completed capture action required");
            if (!action.isEmpty() && capturedAmount == null) return insufficient(original, authId, "Capture action amount required");
            if (!event.path("source").asText().equals("AUTHENTICATED_POLL"))
                return insufficient(original, authId, "Authenticated reconciliation required for capture");
            BigDecimal amount = capturedAmount == null ? original : capturedAmount;
            if (amount.signum() <= 0) return conflict("Capture must be positive");
            Instant capturedAt = instant(authorization.path("action_time").asText());
            String timeSource = capturedAt == null ? "UNKNOWN" : "PROVIDER_ACTION";
            // payment_time may be authorization/initiation time. Completion belongs to an
            // ordinary immediate payment only; never reuse it for a later capture action.
            if (!authorization.isObject()) {
                capturedAt = instant(event.path("paymentCompletedAt").asText());
                timeSource = capturedAt == null ? "UNKNOWN" : "PROVIDER_COMPLETION";
            }
            return new Facts(State.CAPTURED, original, amount, authId, authAt, expiresAt, capturedAt, timeSource, null);
        }
        if (authId.isBlank() || authAt == null || expiresAt == null)
            return insufficient(original, authId, "Verified authorization identity and timezone-qualified dates required");
        if (!event.path("source").asText().equals("AUTHENTICATED_POLL"))
            return insufficient(original, authId, "Authenticated reconciliation required for authorization");
        if (action.equals("VOID") && actionStatus.equals("SUCCESS"))
            return new Facts(State.RELEASED, original, null, authId, authAt, expiresAt, null, null, null);
        if (!action.isEmpty() || !actionStatus.isEmpty())
            return insufficient(original, authId, "Authorization operation unresolved");
        return new Facts(State.AUTHORIZED, original, null, authId, authAt, expiresAt, null, null, null);
    }

    private static Facts conflict(String reason) {
        return new Facts(State.CONFLICTING, null, null, null, null, null, null, null, reason);
    }
    private static Facts insufficient(BigDecimal original, String id, String reason) {
        return new Facts(State.INSUFFICIENT, original, null, id, null, null, null, null, reason);
    }
    /** Local times have no safe implicit timezone. observedAt must never start an authorization clock. */
    public static Instant instant(String value) {
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (java.time.format.DateTimeParseException e) { return null; }
    }
    private static Instant earlier(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
}
