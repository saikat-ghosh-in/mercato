package com.mercato.mail;
import com.mercato.Mail.BrandedEmail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrandedEmailTest {
    @Test void smtpMessageIncludesHtmlPlainTextUtf8AndAnEmbeddedLogo()throws Exception{
        var sent=new java.util.concurrent.atomic.AtomicReference<jakarta.mail.internet.MimeMessage>();
        var mail=new org.springframework.mail.javamail.JavaMailSenderImpl(){@Override public void send(jakarta.mail.internet.MimeMessage message){sent.set(message);}};
        new com.mercato.Mail.BrandedMailSender(mail,"Mercato <test@example.test>","https://mercato.example").send("recipient@example.test","Verify your Mercato email","Plain text fallback ₹1000.00",new BrandedEmail.Action("Verify email","/verify-email?token=synthetic-token"));
        var bytes=new java.io.ByteArrayOutputStream();sent.get().saveChanges();sent.get().writeTo(bytes);
        var parsed=new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()),new java.io.ByteArrayInputStream(bytes.toByteArray()));
        assertTrue(parsed.isMimeType("multipart/mixed"));assertEquals("recipient@example.test",MimeContent.recipient(parsed));
        assertTrue(MimeContent.plainText(parsed).contains("₹1000.00"));
        String raw=bytes.toString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(raw.contains("Content-ID: <mercato-logo>"));assertTrue(raw.contains("image/png"));assertTrue(raw.contains("text/html"));assertTrue(raw.contains("text/plain"));
    }
    @Test void verificationHasBrandButtonAndFallbackWithSafeEscaping(){
        var content=new BrandedEmail("https://mercato.example").render("Verify your Mercato email","Private plain-text fallback",new BrandedEmail.Action("Verify email","/verify-email?token=test-token"));
        assertTrue(content.html().contains("cid:mercato-logo"));assertTrue(content.html().contains("mercato"));
        assertTrue(content.html().contains("href=\"https://mercato.example/verify-email?token=test-token\""));
        assertTrue(content.html().contains(">Verify email</a>"));assertTrue(content.html().contains("If the button"));
        assertTrue(content.plainText().contains("Private plain-text fallback"));
        assertFalse(content.html().contains("<script"));
        var escaped=new BrandedEmail("https://mercato.example").render("<script>alert(1)</script>","<img src=x onerror=alert(1)> & private",null);
        assertFalse(escaped.html().contains("<script>"));assertTrue(escaped.html().contains("&lt;img"));
    }
    @Test void callsToActionCannotPointAtAnExternalOrScriptDestination(){
        var template=new BrandedEmail("https://mercato.example");
        for(String path:new String[]{"https://evil.example","//evil.example","javascript:alert(1)","/\\evil.example"})
            assertThrows(IllegalArgumentException.class,()->template.render("Title","Body",new BrandedEmail.Action("Open",path)));
    }
    @Test void reportUsesReadableFinancialRowsInsteadOfUnformattedText(){
        String html=new BrandedEmail("https://mercato.example").render("Your monthly report","Fulfilled sales: ₹1000.00\nCommission: ₹5.00\nKnown net earnings: ₹995.00",new BrandedEmail.Action("View dashboard","/seller/dashboard")).html();
        assertTrue(html.contains("Fulfilled sales"));assertTrue(html.contains("₹1000.00"));assertTrue(html.contains("text-align:right"));
    }
}
