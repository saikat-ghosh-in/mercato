package com.mercato.Mail;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;

@Service
public class BrandedMailSender {
    private final JavaMailSender sender;private final String from;private final BrandedEmail template;
    public BrandedMailSender(JavaMailSender sender,@Value("${mercato.email.from}")String from,@Value("${frontend.base.url}")String publicBase){this.sender=sender;this.from=from;this.template=new BrandedEmail(publicBase);}
    public void send(String to,String subject,String text,BrandedEmail.Action action){
        try{
            var content=template.render(subject,text,action);
            var message=sender.createMimeMessage();
            var helper=new MimeMessageHelper(message,MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,"UTF-8");
            helper.setValidateAddresses(true);helper.setFrom(from);helper.setTo(to);helper.setSubject(subject);
            helper.setText(content.plainText(),content.html());
            helper.addInline("mercato-logo",new ClassPathResource("mail/mercato-mark.png"),"image/png");
            sender.send(message);
        }catch(MessagingException error){throw new MailPreparationException("Email composition failed",error);}
    }
}
