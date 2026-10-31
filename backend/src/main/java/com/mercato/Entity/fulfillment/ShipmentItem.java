package com.mercato.Entity.fulfillment;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.util.*;
@Entity @Table(name="shipment_items") @Getter @Setter @NoArgsConstructor @IdClass(ShipmentItem.Key.class)
public class ShipmentItem {
    @Id @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="shipment_fk") private Shipment shipment;
    @Id @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="line_fk") private OrderLine line;
    @Column(nullable=false) private int qty;
    @Column(nullable=false,columnDefinition="text") private String unitIds="[]";
    @Data @NoArgsConstructor @AllArgsConstructor public static class Key implements Serializable {private UUID shipment;private Long line;}
}
