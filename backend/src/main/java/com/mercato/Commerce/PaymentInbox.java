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
    private String apiVersion = "UNSPECIFIED";
    private java.time.Clock clock = java.time.Clock.systemUTC();
    public PaymentInbox(JdbcTemplate jdbc,ObjectMapper json,DurableJobs jobs,@Value("${cashfree.secret.key}") String secret) {
        this.jdbc=jdbc;this.json=json;this.jobs=jobs;this.secret=secret;
    }
    @org.springframework.beans.factory.annotation.Autowired
    void evidenceConfiguration(@Value("${cashfree.api.version:UNSPECIFIED}") String version,
            org.springframework.beans.factory.ObjectProvider<java.time.Clock> clocks) {
        this.apiVersion = version;
        this.clock = clocks.getIfAvailable(java.time.Clock::systemUTC);
    }
    /** No provider/SMTP/allocation calls here. Acknowledgement follows the inbox+job commit. */
    @Transactional
    public void receive(byte[] raw,String signature,String timestamp) {
        if(raw.length>262144 || timestamp==null || timestamp.length()>64 || signature==null || signature.length()>256 || !WebhookAuthenticator.valid(raw,signature,timestamp,secret))
            throw new CustomBadRequestException("Invalid webhook signature");
        try {persist(normalize(json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(raw),"SIGNED_WEBHOOK"),raw,signature,timestamp);}
        catch(CustomBadRequestException e){throw e;}
        catch(java.io.IOException e){throw new CustomBadRequestException("Invalid webhook payload");}
    }
    /** Called only with responses fetched using server-side gateway credentials. */
    @Transactional
    public void polled(String orderId,JsonNode attempt) {
        var root=json.createObjectNode();root.put("type","PAYMENT_POLL");
        var data=root.putObject("data");data.putObject("order").put("order_id",orderId);data.set("payment",attempt);
        persist(normalize(root,"AUTHENTICATED_POLL"),null,null,null);
    }
    private Map<String,Object> normalize(JsonNode root,String source) {
        String type=root.path("type").asText();JsonNode data=root.path("data");
        boolean refund=type.equals("REFUND_STATUS_WEBHOOK");
        JsonNode details=data.path(refund?"refund":"payment");
        String order=data.path("order").path("order_id").asText(details.path("order_id").asText());
        String identity=details.path(refund?"refund_id":"cf_payment_id").asText();
        if(identity.isBlank() || identity.length()>100 || order.isBlank() || order.length()>30) throw new CustomBadRequestException("Invalid gateway identity");
        String status=details.path(refund?"refund_status":"payment_status").asText();
        if(status.isBlank()) status=switch(type){case "PAYMENT_SUCCESS_WEBHOOK"->"SUCCESS";case "PAYMENT_FAILED_WEBHOOK"->"FAILED";case "PAYMENT_USER_DROPPED_WEBHOOK"->"USER_DROPPED";default->"UNSUPPORTED";};
        var event=new TreeMap<String,Object>();event.put("kind",refund?"refund":"payment");event.put("type",type);event.put("orderId",order);event.put("identity",identity);event.put("status",status.toUpperCase(Locale.ROOT));
        String amount=details.path(refund?"refund_amount":"payment_amount").asText("");
        String currency=details.path(refund?"refund_currency":"payment_currency").asText("");
        if(!amount.isBlank()) {
            try {event.put("amount",PaymentRules.decimal(amount).toPlainString());}
            catch(IllegalArgumentException invalid) {event.put("amount",amount);}
        }
        if(!currency.isBlank()) event.put("currency",currency);
        event.put("group",details.path("payment_group").asText("unknown"));
        event.put("providerReference",details.path("cf_refund_id").asText(""));
        if (!refund) {
            // Trust the authenticated caller path, never a provider-controlled type string.
            event.put("source",source);
            event.put("apiVersion",apiVersion);
            copy(details,event,"auth_id","authorizationId");
            copy(details,event,"bank_reference","bankReference");
            copy(details,event,"order_id","providerOrderId");
            copy(details,event,"payment_time","paymentAt");
            copy(details,event,"payment_completion_time","paymentCompletedAt");
            if (details.hasNonNull("is_captured")) event.put("isCaptured",details.get("is_captured"));
            JsonNode a = details.path("authorization"), b = details.path("authorisation");
            var spellings = new TreeMap<String,Object>();
            if (!a.isMissingNode() && !a.isNull()) spellings.put("authorization",financialAuthorization(a));
            if (!b.isMissingNode() && !b.isNull()) spellings.put("authorisation",financialAuthorization(b));
            if (!spellings.isEmpty()) event.put("authorizationSpellings",spellings);
            if (!a.isMissingNode() && !a.isNull() && !b.isMissingNode() && !b.isNull() && !a.equals(b))
                event.put("authorizationAliasConflict",true);
            JsonNode chosen = a.isObject() ? a : b;
            if (chosen.isObject()) {
                event.put("authorization",financialAuthorization(chosen));
            } else if ((!a.isMissingNode() && !a.isNull()) || (!b.isMissingNode() && !b.isNull())) {
                event.put("authorizationAliasConflict",true);
            }
        }
        return event;
    }
    private Map<String,Object> financialAuthorization(JsonNode value) {
        var fields = new TreeMap<String,Object>();
        for (String key : List.of("action","status","captured_amount","start_time","end_time","action_reference","action_time"))
            if (value.hasNonNull(key)) fields.put(key,value.get(key));
        return fields;
    }
    private void copy(JsonNode from,Map<String,Object> into,String source,String target) {
        if (from.hasNonNull(source)) into.put(target,from.get(source));
    }
    private void persist(Map<String,Object> event,byte[] raw,String signature,String timestamp) {
        try {
            String encoded=json.writeValueAsString(event);
            String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8)));
            int added=jdbc.update("INSERT INTO gateway_event_inbox(id,order_id,event_json,received_at,verified_raw_body,verified_signature,verified_timestamp) VALUES(?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",id,event.get("orderId"),encoded,Timestamp.from(clock.instant()),raw,signature,timestamp);
            if(added==0 && raw!=null)jdbc.update("UPDATE gateway_event_inbox SET verified_raw_body=?,verified_signature=?,verified_timestamp=? WHERE id=? AND verified_raw_body IS NULL",raw,signature,timestamp,id);
            if(added==1) jobs.enqueue("PAYMENT_EVENT","gateway-event:"+id,Map.of("eventId",id));
        }catch(java.security.NoSuchAlgorithmException|com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot encode gateway event",e);}
    }
}
