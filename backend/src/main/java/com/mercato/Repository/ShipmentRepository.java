package com.mercato.Repository;
import com.mercato.Entity.fulfillment.Shipment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ShipmentRepository extends JpaRepository<Shipment,UUID> {
    @Query("select distinct s from Shipment s join fetch s.items i join fetch i.line where s.order.id=:id order by s.handedOffAt,s.id")
    List<Shipment> forOrder(@Param("id") Long id);
    boolean existsByWaybill(String waybill);
    boolean existsByProviderAndProviderReference(String provider,String providerReference);
    @Query("select s.id from Shipment s where s.status<>'DELIVERED' order by s.handedOffAt,s.id") List<UUID> active();
    @Query("select s.order.orderId from Shipment s where s.id=:id") Optional<String> orderId(@Param("id")UUID id);
    @Query("select coalesce(sum(i.qty),0) from ShipmentItem i where i.line.id=:id and i.shipment.status<>'DELIVERED'")
    long undeliveredForLine(@Param("id")Long id);
    @Query("select i.line.id,sum(i.qty) from ShipmentItem i where i.shipment.order.id=:id and i.shipment.status<>'DELIVERED' group by i.line.id")
    List<Object[]> undeliveredForOrder(@Param("id")Long id);
}
