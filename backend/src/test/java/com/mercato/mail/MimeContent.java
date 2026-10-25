package com.mercato.mail;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
public final class MimeContent {
    private MimeContent(){}
    public static String plainText(Part part)throws Exception{
        if(part instanceof MimeMessage message)message.saveChanges();
        if(part.isMimeType("text/plain"))return (String)part.getContent();
        if(part.getContent() instanceof Multipart parts){for(int i=0;i<parts.getCount();i++){String text=plainText(parts.getBodyPart(i));if(!text.isEmpty())return text;}}
        return "";
    }
    public static String recipient(MimeMessage message)throws Exception{return message.getRecipients(Message.RecipientType.TO)[0].toString();}
}
