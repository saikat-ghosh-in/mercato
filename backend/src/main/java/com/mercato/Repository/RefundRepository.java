package com.mercato.Repository;


import com.mercato.Entity.fulfillment.payment.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByGatewayReference(String gatewayReference);

    Optional<Refund> findByRefundId(String refundId);

    boolean existsByPayment_Id(Long paymentId);
    java.util.List<Refund> findByPayment_IdOrderByIdAsc(Long paymentId);
}
