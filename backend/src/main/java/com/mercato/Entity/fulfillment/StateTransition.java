package com.mercato.Entity.fulfillment;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "order_line_state_transitions",
        indexes = {
                @Index(name = "idx_transition_order_line", columnList = "order_line_fk"),
                @Index(name = "idx_transition_occurred_at", columnList = "occurred_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StateTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "state_transition_seq")
    @SequenceGenerator(name = "state_transition_seq", sequenceName = "state_transition_seq", initialValue = 95000001, allocationSize = 10)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_line_fk", referencedColumnName = "id", nullable = false)
    private OrderLine orderLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 30)
    private OrderLineStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private OrderLineStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private OrderLineAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 20)
    private TransitionTrigger triggeredBy;

    @Column(name = "qty_affected")
    private Integer qtyAffected;

    @Column(length = 500)
    private String reason;

    @Column(name = "occurred_at", updatable = false)
    private Instant occurredAt;
    @PrePersist void defaultTime(){if(occurredAt==null)occurredAt=Instant.now();}
}
