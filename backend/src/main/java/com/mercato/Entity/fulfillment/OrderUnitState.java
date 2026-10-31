package com.mercato.Entity.fulfillment;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Mutable attribution, separate from the immutable quote. No historical bucket reconstruction. */
@Entity @Table(name="order_unit_states") @Getter @Setter @NoArgsConstructor
public class OrderUnitState {
    @Id @Column(length=40) private String unitId;
    @OneToOne(fetch=FetchType.LAZY) @MapsId @JoinColumn(name="unit_id") private OrderMoneyUnit unit;
    @Column(nullable=false,length=20) private String stage;
    @Column(length=40) private String cause;
    @Column(length=120) private String operationId;
    private Instant shippedAt;
    private Instant returnApprovedAt;
    public static OrderUnitState initial(OrderMoneyUnit unit) {
        var state=new OrderUnitState();state.unit=unit;state.unitId=unit.getUnitId();state.stage="UNACCEPTED";return state;
    }
}
