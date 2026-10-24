package com.mercato.Commerce;

import com.fasterxml.jackson.databind.*;
import com.mercato.ExceptionHandler.CustomBadRequestException;
import com.mercato.Jobs.DurableJobs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class PaymentInbox {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final DurableJobs jobs;
    private final String secret;
    public PaymentInbox(JdbcTemplate jdbc,ObjectMapper json,DurableJobs jobs,@Value("${cashfree.secret.key}") String secret) {
        this.jdbc=jdbc;this.json=json;this.jobs=jobs;this.secret=secret;
    }
    /** No provider/SMTP/allocation calls here. Acknowledgement follows the inbox+job commit. */
    @Transactional
    public void receive(byte[] raw,String signature,String timestamp) {
        if(raw.length>262144 || timestamp==null || timestamp.length()>64 || signature==null || signature.length()>256 || !WebhookAuthenticator.valid(raw,signature,timestamp,secret))
            throw new CustomBadRequestException("Invalid webhook signature");
        try {persist(normalize(json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(raw)),raw,signature,timestamp);}
        catch(CustomBadRequestException e){throw e;}
        catch(java.io.IOException e){throw new CustomBadRequestException("Invalid webhook payload");}
    }
    /** Called only with responses fetched using server-side gateway credentials. */
    @Transactional
    public void polled(String orderId,JsonNode attempt) {
        var root=json.createObjectNode();root.put("type","PAYMENT_POLL");
        var data=root.putObject("data");data.putObject("order").put("order_id",orderId);data.set("payment",attempt);
        persist(normalize(root),null,null,null);
    }
    private Map<String,Object> normalize(JsonNode root) {
        String type=root.path("type").asText();JsonNode data=root.path("data");
        boolean refund=type.equals("REFUND_STATUS_WEBHOOK");
        JsonNode details=data.path(refund?"refund":"payment");
        String order=data.path("order").path("order_id").asText(details.path("order_id").asText());
        String identity=details.path(refund?"refund_id":"cf_payment_id").asText();
        if(identity.isBlank() || identity.length()>100 || order.length()>30) throw new CustomBadRequestException("Invalid gateway identity");
        String status=details.path(refund?"refund_status":"payment_status").asText();
        if(status.isBlank()) status=switch(type){case "PAYMENT_SUCCESS_WEBHOOK"->"SUCCESS";case "PAYMENT_FAILED_WEBHOOK"->"FAILED";case "PAYMENT_USER_DROPPED_WEBHOOK"->"USER_DROPPED";default->"UNSUPPORTED";};
        var event=new TreeMap<String,Object>();event.put("kind",refund?"refund":"payment");event.put("type",type);event.put("orderId",order);event.put("identity",identity);event.put("status",status.toUpperCase(Locale.ROOT));
        String amount=details.path(refund?"refund_amount":"payment_amount").asText("");
        String currency=details.path(refund?"refund_currency":"payment_currency").asText("");
        if(!amount.isBlank()) event.put("amount",PaymentRules.decimal(amount).toPlainString());
        if(!currency.isBlank()) event.put("currency",currency);
        event.put("group",details.path("payment_group").asText("unknown"));
        event.put("providerReference",details.path("cf_refund_id").asText(""));
        return event;
    }
    private void persist(Map<String,Object> event,byte[] raw,String signature,String timestamp) {
        try {
            String encoded=json.writeValueAsString(event);
            String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8)));
            int added=jdbc.update("INSERT INTO gateway_event_inbox(id,order_id,event_json,received_at,verified_raw_body,verified_signature,verified_timestamp) VALUES(?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",id,event.get("orderId"),encoded,Timestamp.from(Instant.now()),raw,signature,timestamp);
            if(added==0 && raw!=null)jdbc.update("UPDATE gateway_event_inbox SET verified_raw_body=?,verified_signature=?,verified_timestamp=? WHERE id=? AND verified_raw_body IS NULL",raw,signature,timestamp,id);
            if(added==1) jobs.enqueue("PAYMENT_EVENT","gateway-event:"+id,Map.of("eventId",id));
        }catch(java.security.NoSuchAlgorithmException|com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot encode gateway event",e);}
    }
}
