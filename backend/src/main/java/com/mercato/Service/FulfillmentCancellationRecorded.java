package com.mercato.Service;

import com.mercato.Entity.fulfillment.TransitionTrigger;
import java.time.Instant;

/**
 * Synchronous transaction hook for Task 4: persist refund obligations here, never perform gateway I/O.
 * Cancellation ordinals are audit counts, never monetary unit IDs. UnitFinancials persists
 * stable least-advanced unit selection before this hook; refunds are requested after listeners return.
 * Duplicate commands emit no event. A failing listener rolls back the command and inventory together.
 */
public record FulfillmentCancellationRecorded(String orderId,String fulfillmentId,int orderLineNumber,
        String operationId,TransitionTrigger actorType,String actorIdentity,String reasonCode,String reason,
        int quantity,int cancellationOrdinalFrom,int cancellationOrdinalTo,
        int unacceptedQty,int acceptedOnlyQty,int preparingOnlyQty,int packedOnlyQty,Instant occurredAt) {}
