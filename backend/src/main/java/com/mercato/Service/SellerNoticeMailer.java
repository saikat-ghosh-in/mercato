package com.mercato.Service;
import com.mercato.Entity.SellerNotice;
import com.mercato.Repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;

/** Existing notice outbox: short claim/accept transactions around bounded SMTP work. */
@Service
public class SellerNoticeMailer {
 private final SellerNoticeRepository notices;private final CommissionSettingsRepository settings;private final CommissionService commissions;private final JavaMailSender mail;private final TransactionTemplate tx;
 @Value("${mercato.email.from}")private String from;
 public SellerNoticeMailer(SellerNoticeRepository notices,CommissionSettingsRepository settings,CommissionService commissions,JavaMailSender mail,PlatformTransactionManager transactions){this.notices=notices;this.settings=settings;this.commissions=commissions;this.mail=mail;this.tx=new TransactionTemplate(transactions);}
 private record Delivery(Long id,Long changeId,String to,String subject,String text){}
 public void send(Long id){
  Delivery delivery=tx.execute(t->{
   settings.lockSettings().orElseThrow();var notice=notices.findById(id).orElseThrow();var change=notice.getChange();
   if(notice.getEmailSentAt()!=null || (notice.getNextAttemptAt()!=null&&notice.getNextAttemptAt().isAfter(Instant.now())))return null;
   if("ANNOUNCED".equals(notice.getEventType())&&"CANCELLED".equals(change.getStatus())){notice.setSuperseded(true);return null;}
   notice.setAttempts(notice.getAttempts()+1);notice.setNextAttemptAt(Instant.now().plusSeconds(60));
   boolean cancelled="CANCELLED".equals(notice.getEventType());
   return new Delivery(id,change.getId(),notice.getEmail(),cancelled?"Mercato commission change cancelled":"Mercato commission notice — at least 60 days before any change",cancelled?"The proposed Mercato commission of "+change.getRate()+"% has been cancelled. Your current rate is unchanged. See Seller Dashboard → Notices.":"Mercato proposes changing commission from "+change.getPreviousRate()+"% to "+change.getRate()+"%.\n\nReason: "+change.getReason()+"\n\nThe change will take effect no earlier than 60 full days after all required email notices have been accepted by the mail service. Delayed notices extend the notice period. Existing orders keep their captured rate. See Seller Dashboard → Notices for the effective date. You may stop new listings before then; contact marketplace Help with questions.");
  });
  if(delivery==null)return;
  SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(delivery.to());message.setSubject(delivery.subject());message.setText(delivery.text());
  try{mail.send(message);tx.executeWithoutResult(t->{settings.lockSettings().orElseThrow();var notice=notices.findById(id).orElseThrow();if(notice.getEmailSentAt()==null){notice.setEmailSentAt(Instant.now());commissions.finishNotifying(notice.getChange());}});}
  catch(org.springframework.mail.MailException e){tx.executeWithoutResult(t->{var notice=notices.findById(id).orElseThrow();notice.setNextAttemptAt(Instant.now().plusSeconds(Math.min(21600,60L*(1L<<Math.min(notice.getAttempts(),8)))));});}
 }
}
