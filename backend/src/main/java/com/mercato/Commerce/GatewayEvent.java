package com.mercato.Commerce;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Verified webhook evidence stays private; handlers consume the separate normalized facts. */
@Entity @Table(name="gateway_event_inbox") @Getter @Setter @NoArgsConstructor
public class GatewayEvent {
    @Id @Column(length=64) private String id;
    @Column(nullable=false,length=30) private String orderId;
    @Column(nullable=false,columnDefinition="text") private String eventJson;
    @Column(nullable=false) private Instant receivedAt;
    private Instant processedAt;
    @Column(columnDefinition="bytea") private byte[] verifiedRawBody;
    @Column(columnDefinition="text") private String verifiedSignature;
    @Column(length=64) private String verifiedTimestamp;
}
