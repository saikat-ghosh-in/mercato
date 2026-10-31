package com.mercato.Entity.fulfillment;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="shipments") @Getter @Setter @NoArgsConstructor
public class Shipment {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_fk") private Order order;
    @Column(nullable=false,length=30) private String fulfillmentId;
    @Column(nullable=false,length=100) private String sellerEmail;
    @Column(nullable=false,unique=true,length=80) private String waybill;
    @Column(nullable=false,length=20) private String provider="DELHIVERY";
    @Column(length=80) private String providerReference;
    private UUID trackingId;
    @Column(length=120) private String courierName;
    @Column(nullable=false) private boolean manualTracking;
    @Column(nullable=false) private int simulationStage;
    @Column(nullable=false) private int trackingLookupPage=1;
    @Column(nullable=false,length=30) private String status;
    @Column(nullable=false) private Instant handedOffAt;
    private Instant deliveredAt;
    private Instant observedAt;
    private Instant providerUpdatedAt;
    private Instant lastCheckedAt;
    private Instant lastRequestedAt;
    private Instant nextTrackingAt;
    private Instant eta;
    @Column(length=300) private String safeMessage;
    @Column(nullable=false,columnDefinition="text") private String history="[]";
    @Column(nullable=false) private long trackingGeneration;
    @Version private long version;
    @OneToMany(mappedBy="shipment",cascade=CascadeType.ALL) private List<ShipmentItem> items=new ArrayList<>();
}
