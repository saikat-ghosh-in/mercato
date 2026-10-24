package com.mercato.Commerce;
import com.mercato.Jobs.*;
import com.mercato.Repository.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
@Component
public class ShipmentEmailHandler implements JobHandler {
    private final JavaMailSender mail;private final String from;private final OrderRepository orders;private final TransactionTemplate tx;
    public ShipmentEmailHandler(JavaMailSender mail,@Value("${mercato.email.from}")String from,OrderRepository orders,PlatformTransactionManager transactions){this.mail=mail;this.from=from;this.orders=orders;this.tx=new TransactionTemplate(transactions);}
    public String type(){return "SHIPMENT_EMAIL";}
    public void handle(JobRecord job){String id=(String)job.payload().get("orderId");if(Boolean.TRUE.equals(tx.execute(t->orders.findByOrderId(id).orElseThrow().getShipmentEmailSentAt()!=null)))return;var message=new SimpleMailMessage();message.setFrom(from);message.setTo((String)job.payload().get("to"));message.setSubject("Your Mercato order has shipped");message.setText("Order "+id+" has shipped. Track its items in Your orders.");try{mail.send(message);}catch(org.springframework.mail.MailException e){throw new TransientJobException("Shipment email delivery failed",e);}tx.executeWithoutResult(t->orders.lockByOrderId(id).orElseThrow().setShipmentEmailSentAt(Instant.now()));}
}
