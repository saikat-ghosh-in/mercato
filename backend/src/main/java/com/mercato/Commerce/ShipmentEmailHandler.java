package com.mercato.Commerce;
import com.mercato.Jobs.*;
import com.mercato.Repository.OrderRepository;
import org.springframework.stereotype.Component;
import com.mercato.Mail.TrackedMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
@Component
public class ShipmentEmailHandler implements JobHandler {
    private final TrackedMailSender mail;private final OrderRepository orders;private final TransactionTemplate tx;
    public ShipmentEmailHandler(TrackedMailSender mail,OrderRepository orders,PlatformTransactionManager transactions){this.mail=mail;this.orders=orders;this.tx=new TransactionTemplate(transactions);}
    public String type(){return "SHIPMENT_EMAIL";}
    public void handle(JobRecord job){String id=(String)job.payload().get("orderId");if(Boolean.TRUE.equals(tx.execute(t->orders.findByOrderId(id).orElseThrow().getShipmentEmailSentAt()!=null)))return;mail.send(job,(String)job.payload().get("to"),"Your Mercato order has shipped","Order "+id+" has shipped. Track its items in Your orders.");tx.executeWithoutResult(t->orders.lockByOrderId(id).orElseThrow().setShipmentEmailSentAt(Instant.now()));}
}
