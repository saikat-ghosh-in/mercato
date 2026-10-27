package com.mercato.Entity.fulfillment;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Immutable checkout money. Unit number is a monetary identity, never a cancellation ordinal. */
@Entity @Table(name="order_money_units",uniqueConstraints=@UniqueConstraint(name="uk_money_unit_line_number",columnNames={"order_line_fk","unit_number"}))
@Getter @Setter @NoArgsConstructor
public class OrderMoneyUnit {
    @Id @Column(length=40,updatable=false) private String unitId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_line_fk",nullable=false,updatable=false) private OrderLine orderLine;
    @Column(nullable=false,updatable=false) private int unitNumber;
    @Column(nullable=false,updatable=false) private int financialPolicyVersion;
    @Column(nullable=false,updatable=false) private long netMerchandisePaise;
    @Column(updatable=false) private Long retailPaise;
    @Column(updatable=false) private Long discountPaise;
    @Column(precision=5,scale=2,updatable=false) private BigDecimal discountPercent;
    @Column(nullable=false,length=40,updatable=false) private String priceProvenance;
    @Column(nullable=false,updatable=false) private long buyerNormalDeliveryPaise;
    @Column(nullable=false,updatable=false) private long buyerExpeditedDeliveryPaise;
    @Column(nullable=false,updatable=false) private long platformPaise;
    @Column(nullable=false,updatable=false) private long handlingPaise;
    @Column(nullable=false,updatable=false) private long sellerDeliveryPaise;
    @Column(precision=5,scale=2,updatable=false) private BigDecimal commissionRatePercent;
    @Column(updatable=false) private Long commissionCapPaise;
    @Column(updatable=false) private Long commissionPaise;
    @Column(nullable=false,length=30,updatable=false) private String commissionPolicy;
    public long buyerTotalPaise(){return Math.addExact(netMerchandisePaise,Math.addExact(buyerNormalDeliveryPaise,Math.addExact(buyerExpeditedDeliveryPaise,Math.addExact(platformPaise,handlingPaise))));}
}
