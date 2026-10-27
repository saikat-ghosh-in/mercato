package com.mercato.Entity.fulfillment.payment;

import com.mercato.Entity.fulfillment.OrderMoneyUnit;
import jakarta.persistence.*;
import lombok.*;

/** Exact components of one stable obligation; never proportional cross-seller refund guesses. */
@Entity @Table(name="refund_unit_allocations") @Getter @Setter @NoArgsConstructor
public class RefundUnitAllocation {
    @Id @Column(length=80) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="refund_fk",nullable=false,updatable=false) private Refund refund;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="unit_id",nullable=false,updatable=false) private OrderMoneyUnit unit;
    @Column(nullable=false,updatable=false) private long merchandisePaise;
    @Column(nullable=false,updatable=false) private long deliveryPaise;
    @Column(nullable=false,updatable=false) private long platformPaise;
    @Column(nullable=false,updatable=false) private long handlingPaise;
    @Column(nullable=false,length=40,updatable=false) private String cause;
    public long totalPaise(){return Math.addExact(merchandisePaise,Math.addExact(deliveryPaise,Math.addExact(platformPaise,handlingPaise)));}
}
