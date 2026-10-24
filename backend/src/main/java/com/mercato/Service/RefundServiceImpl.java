package com.mercato.Service;
import com.mercato.Commerce.RefundObligations;
import com.mercato.Entity.fulfillment.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class RefundServiceImpl implements RefundService {
    private final RefundObligations obligations;
    public RefundServiceImpl(RefundObligations obligations){this.obligations=obligations;}
    @Transactional public void processRefundIfRequired(Order order){obligations.cancelled(order);}
}
