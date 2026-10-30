package com.mercato.Service;

import com.fasterxml.jackson.databind.*;
import com.mercato.Jobs.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.*;
import java.util.*;

/** Staging-only transport. No provider body or credential leaves this adapter. */
@Component
public class DelhiveryClient implements TrackingClient {
    public static final String ENDPOINT="https://staging-express.delhivery.com/api/v1/packages/json/";
    private final RestTemplate http; private final ObjectMapper json; private final boolean enabled; private final String token; private final int batchSize;
    public DelhiveryClient(RestTemplate http,ObjectMapper json,@Value("${DELHIVERY_ENABLED:false}")boolean enabled,
            @Value("${DELHIVERY_STAGING_TOKEN:}")String token,@Value("${DELHIVERY_TRACKING_BATCH_SIZE:50}")int batchSize) {
        if(batchSize<1||batchSize>50)throw new IllegalArgumentException("DELHIVERY_TRACKING_BATCH_SIZE must be 1..50");
        if(enabled&&(token==null||token.isBlank()))throw new IllegalArgumentException("DELHIVERY_STAGING_TOKEN is required when DELHIVERY_ENABLED=true");
        this.http=http;this.json=json;this.enabled=enabled;this.token=token;this.batchSize=batchSize;
    }
    public boolean enabled(){return enabled;}
    public int batchSize(){return batchSize;}
    public Result fetch(Collection<String> requested) {
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Tracking I/O inside transaction");
        if(!enabled)throw new ManualInterventionJobException("Staging tracking is disabled");
        var awbs=new ArrayList<>(new LinkedHashSet<>(requested));var found=new LinkedHashMap<String,Update>();boolean fallback=false;
        for(int start=0;start<awbs.size();start+=batchSize){
            var batch=awbs.subList(start,Math.min(start+batchSize,awbs.size()));
            try{found.putAll(call(batch));}catch(BatchSizeRejected size){
                if(batch.size()==1)throw new ManualInterventionJobException("Staging tracking rejected a single waybill");
                fallback=true;for(String awb:batch)found.putAll(call(List.of(awb)));
            }
        }
        return new Result(Map.copyOf(found),fallback);
    }
    private Map<String,Update> call(List<String> awbs) {
        for(String a:awbs)if(a==null||!a.matches("[A-Za-z0-9-]{1,80}"))throw new IllegalArgumentException("Invalid waybill");
        var uri=UriComponentsBuilder.fromUriString(ENDPOINT).queryParam("waybill",String.join(",",awbs)).build().encode().toUri();
        var headers=new HttpHeaders();headers.set("Authorization","Token "+token);headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        try{
            var response=http.exchange(uri,HttpMethod.GET,new HttpEntity<>(headers),String.class);
            JsonNode root=json.readTree(response.getBody());var found=new LinkedHashMap<String,Update>();var ambiguous=new HashSet<String>();
            for(JsonNode entry:root.path("ShipmentData")){
                var s=entry.path("Shipment");String awb=s.path("AWB").asText("");if(!awbs.contains(awb))continue;
                var status=s.path("Status");var events=new ArrayList<Event>();boolean reverse=false;
                for(JsonNode scan:s.path("Scans")){var d=scan.path("ScanDetail");String code=safe(d.path("ScanType").asText(""),60);String desc=safe(d.path("Scan").asText(""),240);events.add(new Event(code,desc,time(d.path("ScanDateTime").asText(""))));if(reverse(code,desc))reverse=true;}
                String name=safe(status.path("Status").asText(""),240),type=safe(status.path("StatusType").asText(""),60);
                reverse=reverse||reverse(type,name)||s.path("ReverseInTransit").asBoolean(false)||s.path("IsReverse").asBoolean(false)||s.path("ShipmentType").asText("").equalsIgnoreCase("reverse");
                var update=new Update(awb,name,type,time(status.path("StatusDateTime").asText("")),time(s.path("ExpectedDeliveryDate").asText("")),List.copyOf(events),reverse);
                if(found.putIfAbsent(awb,update)!=null)ambiguous.add(awb);
            }
            ambiguous.forEach(found::remove);return found;
        }catch(HttpStatusCodeException error){
            int status=error.getStatusCode().value();
            if(status==429)throw new WaitingJobException("Staging tracking rate limited",retryAfter(error.getResponseHeaders()));
            if(status==401||status==403)throw new ManualInterventionJobException("Staging tracking credentials rejected");
            if(status>=500)throw new TransientJobException("Staging tracking temporarily unavailable");
            String code="";try{code=json.readTree(error.getResponseBodyAsString()).path("code").asText("");}catch(Exception ignored){}
            if(status==400&&Set.of("BATCH_SIZE_EXCEEDED","MAX_WAYBILLS_EXCEEDED","TOO_MANY_WAYBILLS").contains(code.toUpperCase(Locale.ROOT)))throw new BatchSizeRejected();
            throw new ManualInterventionJobException("Staging tracking request rejected");
        }catch(ResourceAccessException error){throw new TransientJobException("Staging tracking timed out");}
        catch(java.io.IOException error){throw new TransientJobException("Staging tracking response unreadable");}
    }
    private static class BatchSizeRejected extends RuntimeException{}
    static Duration retryAfter(HttpHeaders headers){
        String value=headers==null?null:headers.getFirst("Retry-After");
        try{return Duration.ofSeconds(Math.max(1,Long.parseLong(value)));}catch(Exception ignored){}
        try{return Duration.ofSeconds(Math.max(1,Duration.between(Instant.now(),ZonedDateTime.parse(value,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds()));}catch(Exception ignored){}
        return Duration.ofMinutes(15);
    }
    public static Instant time(String value){
        if(value==null||value.isBlank())return null;
        try{return Instant.parse(value);}catch(Exception ignored){}
        try{return OffsetDateTime.parse(value.replace(' ','T')).toInstant();}catch(Exception ignored){}
        try{return LocalDateTime.parse(value.replace(' ','T')).atZone(ZoneId.of("Asia/Kolkata")).toInstant();}catch(Exception ignored){return null;}
    }
    public static String safe(String value,int max){if(value==null)return "";String clean=value.replaceAll("[\\p{Cntrl}<>]","").trim();return clean.substring(0,Math.min(max,clean.length()));}
    private static boolean reverse(String code,String description){String c=code.toUpperCase(Locale.ROOT),d=description.toUpperCase(Locale.ROOT);return Set.of("RT","RTO","DTO").contains(c)||d.contains("RTO")||d.contains("DTO")||d.contains("RETURN")||d.contains("REVERSE");}
    @Override public String toString(){return "DelhiveryClient[staging,enabled="+enabled+",batchSize="+batchSize+"]";}
}
