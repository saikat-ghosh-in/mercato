package com.mercato.Commerce;
import com.mercato.Jobs.*;
import com.mercato.ExceptionHandler.InsufficientInventoryException;
import org.springframework.stereotype.Component;
@Component
public class AllocationHandler implements JobHandler {
    private final PaidOrderAllocation allocation;private final RefundObligations refunds;
    public AllocationHandler(PaidOrderAllocation allocation,RefundObligations refunds){this.allocation=allocation;this.refunds=refunds;}
    public String type(){return "ALLOCATE_ORDER";}
    public void handle(JobRecord job){String id=(String)job.payload().get("orderId");try{allocation.allocate(id);}catch(InsufficientInventoryException e){refunds.allocationFailed(id);}catch(org.springframework.dao.TransientDataAccessException e){throw new TransientJobException("Allocation database temporarily unavailable",e);}}
}
