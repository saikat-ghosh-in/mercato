package com.mercato.Mail;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import com.mercato.Jobs.JobRecord;

/** Email-client-compatible tables, inline styles and escaped content; no scripts or tracking images. */
public final class BrandedEmail {
    public record Action(String label,String path){}
    public record Content(String html,String plainText){}
    private final String base;
    public BrandedEmail(String publicBase){
        URI uri=URI.create(publicBase);
        if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
            ||!("https".equals(uri.getScheme())||("http".equals(uri.getScheme())&&Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost()))))
            throw new IllegalArgumentException("Email links require the canonical HTTPS storefront or loopback demo");
        base=publicBase.replaceAll("/+$","");
    }
    public static Action action(JobRecord job){
        String key=job.businessKey();
        if(key.startsWith("verify-email:"))return new Action("Verify email","/verify-email?token="+encode(key.substring("verify-email:".length())));
        if(key.startsWith("password-reset:"))return new Action("Reset password","/reset-password?token="+encode(key.substring("password-reset:".length())));
        if(key.startsWith("step-up:"))return new Action("Continue sign in","/auth/mfa");
        if("SHIPMENT_EMAIL".equals(job.type()))return new Action("View your orders","/orders");
        return new Action("Open Mercato","/");
    }
    public Content render(String subject,String text,Action action){
        String target=null;
        if(action!=null){
            if(action.path()==null||!action.path().startsWith("/")||action.path().startsWith("//")||action.path().contains("\\")||action.path().contains("\r")||action.path().contains("\n"))throw new IllegalArgumentException("Email actions must stay on the storefront");
            target=base+action.path();
        }
        boolean verify=action!=null&&action.path().startsWith("/verify-email?");
        boolean reset=action!=null&&action.path().startsWith("/reset-password?");
        String title=verify?"Verify your email.":reset?"Reset your password.":subject;
        String intro=verify?"Welcome to Mercato. Confirm your email address to start discovering good finds from independent sellers.":reset?"We received a request to reset your Mercato password. Use the button below to choose a new one.":null;
        String body=intro==null?body(text):"<p style=\"margin:0;color:#4b5563;font-size:16px;line-height:26px\">"+escape(intro)+"</p>";
        String button=target==null?"":"<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:28px 0 24px\"><tr><td bgcolor=\"#111111\" style=\"border-radius:8px\"><a href=\""+escape(target)+"\" style=\"display:inline-block;background:#111111;color:#ffffff;border:1px solid #111111;border-radius:8px;padding:14px 26px;font-size:15px;line-height:20px;font-weight:700;text-decoration:none;text-align:center\">"+escape(action.label())+"</a></td></tr></table>";
        String security=verify?"This link expires. Please use your latest verification email. If you didn’t create this account, you can ignore this email.":reset?"If you didn’t request a password reset, you can ignore this email. Your password won’t change until you complete the reset.":"This is an operational message about your Mercato account.";
        String fallback=target==null?"":"<p style=\"margin:20px 0 0;color:#6b7280;font-size:12px;line-height:20px;word-break:break-all\">If the button doesn’t work, copy this link into your browser:<br><a href=\""+escape(target)+"\" style=\"color:#4b5563;text-decoration:underline;word-break:break-all\">"+escape(target)+"</a></p>";
        String html="""
            <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="light"><title>%s</title>
            <style>@media only screen and (max-width:600px){.email-wrap{padding:20px 12px!important}.email-content{padding:28px 22px!important}.email-title{font-size:28px!important;line-height:34px!important}}</style></head>
            <body style="margin:0;padding:0;background:#f4f5f6;color:#111111;font-family:Arial,Helvetica,sans-serif">
            <div style="display:none;max-height:0;overflow:hidden;font-size:1px;line-height:1px;color:#f4f5f6">%s</div>
            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0"><tr><td class="email-wrap" align="center" style="padding:40px 20px">
            <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:600px;background:#ffffff;border:1px solid #e8e9eb;border-radius:10px;overflow:hidden">
            <tr><td style="height:4px;background:#cbdcf1;font-size:1px;line-height:4px">&nbsp;</td></tr>
            <tr><td class="email-content" style="padding:36px 40px 40px">
            <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 36px"><tr><td width="38" valign="middle"><img src="cid:mercato-logo" alt="" width="32" height="32" style="display:block;border:0"></td><td valign="middle" style="font-family:Arial,Helvetica,sans-serif;font-size:27px;font-weight:700;letter-spacing:-1px;color:#111111">mercato</td></tr></table>
            <p style="margin:0 0 12px;color:#65758a;font-size:11px;line-height:16px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase">A world of good finds</p>
            <h1 class="email-title" style="margin:0 0 20px;font-size:32px;line-height:39px;letter-spacing:-1px;font-weight:700;color:#111111">%s</h1>
            %s%s
            <p style="margin:0;padding-top:20px;border-top:1px solid #eceef0;color:#6b7280;font-size:13px;line-height:21px">%s</p>%s
            </td></tr></table>
            <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="width:100%%;max-width:600px"><tr><td align="center" style="padding:24px 16px;color:#7b818a;font-size:12px;line-height:20px">Mercato · Good finds. Every day.<br><a href="%s/help" style="color:#5f6875;text-decoration:underline">Need a hand? Visit Help</a></td></tr></table>
            </td></tr></table></body></html>
            """.formatted(escape(subject),escape(subject),escape(title),body,button,escape(security),fallback,escape(base));
        return new Content(html,text+(target==null?"":"\n\n"+action.label()+": "+target)+"\n\n"+security+"\nHelp: "+base+"/help");
    }
    private static String body(String text){
        var result=new StringBuilder();boolean rows=false;
        for(String line:text.split("\\R",-1)){
            if(line.matches("[^:]{1,90}: (₹[0-9.]+|[0-9]+)")){
                if(!rows){result.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:18px 0 24px\">");rows=true;}
                int split=line.indexOf(": ");result.append("<tr><td style=\"padding:12px 0;border-bottom:1px solid #eceef0;color:#59616e;font-size:14px;line-height:21px\">").append(escape(line.substring(0,split))).append("</td><td style=\"padding:12px 0 12px 16px;border-bottom:1px solid #eceef0;text-align:right;font-size:15px;font-weight:700;white-space:nowrap\">").append(escape(line.substring(split+2))).append("</td></tr>");
            }else{
                if(rows){result.append("</table>");rows=false;}
                if(!line.isBlank())result.append("<p style=\"margin:0 0 12px;font-size:15px;line-height:25px;color:#4b5563;overflow-wrap:anywhere\">").append(escape(line)).append("</p>");
            }
        }
        if(rows)result.append("</table>");return result.toString();
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
