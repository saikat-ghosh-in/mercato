package com.mercato.Service;

import com.fasterxml.jackson.databind.*;
import com.mercato.Jobs.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.*;
import java.util.*;

/** Sandbox tracking only. No label, pickup, billing or production operation exists here. */
public class EasyshipClient implements TrackingClient {
    public static final String ENDPOINT="https://public-api-sandbox.easyship.com/2024-09/shipments/trackings";
    private final RestTemplate http;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean enabled;
    private final String token;
    private final int batchSize;
    private final String sandboxCourier;
    private Instant nextRequestAt=Instant.MIN;

    public EasyshipClient(RestTemplate http,ObjectMapper json,Clock clock,
            @Value("${EASYSHIP_ENABLED:false}") boolean enabled,
            @Value("${EASYSHIP_SANDBOX_TOKEN:}") String token,
            @Value("${EASYSHIP_TRACKING_BATCH_SIZE:50}") int batchSize) {
        this(http,json,clock,enabled,token,batchSize,"");
    }
    public EasyshipClient(RestTemplate http,ObjectMapper json,Clock clock,boolean enabled,String token,int batchSize,String sandboxCourier) {
        if(batchSize<1||batchSize>100)throw new IllegalArgumentException("EASYSHIP_TRACKING_BATCH_SIZE must be 1..100");
        if(enabled&&(token==null||!token.matches("sand_[A-Za-z0-9+/=_-]{6,}")))
            throw new IllegalArgumentException("A sandbox token is required when EASYSHIP_ENABLED=true");
        if(sandboxCourier!=null&&!sandboxCourier.isBlank())UUID.fromString(sandboxCourier);
        this.http=http;this.json=json;this.clock=clock;this.enabled=enabled;this.token=token;this.batchSize=batchSize;this.sandboxCourier=sandboxCourier;
    }
    public boolean enabled(){return enabled;}
    public int batchSize(){return batchSize;}
    public boolean creationEnabled(){return enabled&&sandboxCourier!=null&&!sandboxCourier.isBlank();}
    public record Prepared(String waybill,String reference){}
    public record Destination(String name,String phone,String email,String line1,String line2,String city,String state,String pincode){
        Map<String,String> request(){
            if(java.util.stream.Stream.of(name,phone,email,line1,city,state,pincode).anyMatch(v->v==null||v.isBlank()))throw new IllegalArgumentException("Order delivery details are incomplete");
            String digits=phone.replaceAll("[^0-9]","");
            String international=digits.length()==10?"+91"+digits:digits.length()==12&&digits.startsWith("91")?"+"+digits:phone;
            var result=new LinkedHashMap<String,String>();
            result.put("line_1",line1);if(line2!=null&&!line2.isBlank())result.put("line_2",line2);
            result.put("city",city);result.put("state",state);result.put("postal_code",pincode);result.put("country_alpha2","IN");
            result.put("contact_name",name);result.put("contact_phone",international);result.put("contact_email",email);
            return result;
        }
    }
    public static boolean validReference(String value){return value!=null&&value.matches("ES[A-Za-z0-9]{2}[0-9]{7,70}");}

    /** At most six sandbox calls per minute in this single-instance application; never sleep a worker. */
    public synchronized Result fetch(Collection<String> requested) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Tracking I/O inside transaction");
        if(!enabled)throw new ManualInterventionJobException("Easyship sandbox tracking is disabled");
        var ids=new ArrayList<>(new LinkedHashSet<>(requested));
        if(ids.isEmpty())return new Result(Map.of(),false);
        if(ids.size()>batchSize||ids.stream().anyMatch(id->!validReference(id)))throw new IllegalArgumentException("Invalid Easyship shipment references");
        var uri=UriComponentsBuilder.fromUriString(ENDPOINT).queryParam("easyship_shipment_id",String.join(",",ids))
            .queryParam("include_checkpoints",true).queryParam("per_page",100).build().encode().toUri();
        JsonNode root=request(HttpMethod.GET,uri,null);
        if(root==null||!root.path("shipments").isArray())throw new TransientJobException("Easyship sandbox response unreadable");
        var found=new LinkedHashMap<String,Update>();var ambiguous=new HashSet<String>();
        for(var shipment:root.path("shipments")){
            String id=shipment.path("easyship_shipment_id").asText("");if(!ids.contains(id))continue;
            if(found.putIfAbsent(id,parse(shipment))!=null)ambiguous.add(id);
        }
        ambiguous.forEach(found::remove);return new Result(Map.copyOf(found),false);
    }
    public synchronized Prepared createTracking(String reference,String waybill,Destination destination){
        if(!creationEnabled())throw new ManualInterventionJobException("Sandbox tracking generation is not configured");
        validatePreparation(reference,waybill);
        var origin=Map.of("line_1","1 Sandbox Road","city","Hong Kong","state","Hong Kong","postal_code","0000","country_alpha2","HK","contact_name","Mercato Sandbox","contact_phone","+85230085678","contact_email","sandbox@mercato.invalid");
        if(destination==null)throw new IllegalArgumentException("Order delivery details are required");
        var body=Map.of("tracking_number",waybill,"courier_service_id",sandboxCourier,"platform_order_number",reference,"origin_address",origin,"destination_address",destination.request(),"items",List.of(Map.of("description","Mercato sandbox test item - no physical shipment","quantity",1)));
        var row=request(HttpMethod.POST,java.net.URI.create("https://public-api-sandbox.easyship.com/2024-09/trackings"),body).path("tracking");
        String id=row.path("easyship_shipment_id").asText("");
        if(!validReference(id)||!waybill.equals(row.path("tracking_number").asText("")))throw new TransientJobException("Sandbox tracking result needs reconciliation");
        return new Prepared(waybill,id);
    }
    public synchronized Prepared findPrepared(String reference,String waybill){
        validatePreparation(reference,waybill);
        var uri=UriComponentsBuilder.fromUriString(ENDPOINT).queryParam("platform_order_number",reference).queryParam("include_checkpoints",true).queryParam("per_page",100).build().encode().toUri();
        var root=request(HttpMethod.GET,uri,null);var found=new ArrayList<Prepared>();
        if(!root.path("shipments").isArray())throw new TransientJobException("Sandbox tracking lookup unreadable");
        for(var row:root.path("shipments"))if(reference.equals(row.path("platform_order_number").asText(""))&&validReference(row.path("easyship_shipment_id").asText(""))){
            for(var tracking:row.path("trackings"))if(waybill.equals(tracking.path("tracking_number").asText("")))found.add(new Prepared(waybill,row.path("easyship_shipment_id").asText()));
        }
        if(found.size()>1)throw new ManualInterventionJobException("Ambiguous sandbox tracking lookup");
        return found.isEmpty()?null:found.get(0);
    }
    private static void validatePreparation(String reference,String waybill){
        if(reference==null||!reference.matches("SANDBOX-[A-Za-z0-9-]{1,80}")||waybill==null||!waybill.matches("MCT[A-Za-z0-9]{1,70}"))throw new IllegalArgumentException("Invalid sandbox request identity");
    }
    private JsonNode request(HttpMethod method,java.net.URI uri,Object body){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Tracking I/O inside transaction");
        if(!enabled)throw new ManualInterventionJobException("Easyship sandbox tracking is disabled");
        Instant now=clock.instant();
        if(now.isBefore(nextRequestAt))throw new WaitingJobException("Easyship sandbox request limit",Duration.between(now,nextRequestAt));
        nextRequestAt=now.plusSeconds(10);
        var headers=new HttpHeaders();headers.setBearerAuth(token);headers.setAccept(List.of(MediaType.APPLICATION_JSON));headers.setContentType(MediaType.APPLICATION_JSON);headers.set("User-Agent","Mercato-Sandbox/1.0");
        try{
            var response=http.exchange(uri,method,new HttpEntity<>(body,headers),String.class);
            if(!response.getStatusCode().is2xxSuccessful())throw new TransientJobException("Unexpected Easyship sandbox response");
            JsonNode root=json.readTree(response.getBody());if(root==null)throw new TransientJobException("Empty sandbox response");return root;
        }catch(HttpStatusCodeException error){
            int status=error.getStatusCode().value();
            if(status==429){Duration retry=DelhiveryClient.retryAfter(error.getResponseHeaders());nextRequestAt=now.plus(retry);throw new WaitingJobException("Easyship sandbox rate limited",retry);}
            if(status==401||status==403)throw new ManualInterventionJobException("Easyship sandbox credentials or permissions rejected");
            if(status>=500)throw new TransientJobException("Easyship sandbox temporarily unavailable");
            throw new ManualInterventionJobException("Easyship sandbox request rejected");
        }catch(ResourceAccessException error){throw new TransientJobException("Easyship sandbox timed out");}
        catch(java.io.IOException error){throw new TransientJobException("Easyship sandbox response unreadable");}
    }
    private Update parse(JsonNode shipment){
        String status=DelhiveryClient.safe(shipment.path("status").asText(""),240);
        boolean reverse=reverse(status);
        var numbers=new LinkedHashSet<String>();
        for(var tracking:shipment.path("trackings")){String number=tracking.path("tracking_number").asText("");if(!number.isBlank())numbers.add(number);}
        String waybill=numbers.size()==1?numbers.iterator().next():"";
        var events=new ArrayList<Event>();Instant latest=null;String latestStatus="";
        for(var checkpoint:shipment.path("checkpoints")){
            String primary=DelhiveryClient.safe(checkpoint.path("primary_status").asText(""),60);
            String message=DelhiveryClient.safe(checkpoint.path("message").asText(primary),240);
            reverse=reverse||reverse(primary)||reverse(message);
            Instant at=time(checkpoint.path("checkpoint_time").asText(""));
            if(at==null)continue;
            events.add(new Event(primary,message,at));
            if(latest==null||at.isAfter(latest)){latest=at;latestStatus=primary;}
            else if(at.equals(latest)&&!primary.equalsIgnoreCase(latestStatus))latestStatus="AMBIGUOUS";
        }
        // Delivered needs a dated matching forward checkpoint, never the label status alone.
        if(!status.equalsIgnoreCase(latestStatus))latest=null;
        String type="Delivered".equalsIgnoreCase(status)?"DL":"Out For Delivery".equalsIgnoreCase(status)?"UD":"";
        String normalized="UD".equals(type)?"Dispatched":status;
        return new Update(waybill,normalized,type,latest,time(shipment.path("eta_date").asText("")),List.copyOf(events),reverse);
    }
    private static boolean reverse(String value){String normalized=value.toLowerCase(Locale.ROOT);return normalized.contains("return")||normalized.contains("reverse")||normalized.contains("rto");}
    private static Instant time(String value){try{return OffsetDateTime.parse(value).toInstant();}catch(Exception ignored){return null;}}
    @Override public String toString(){return "EasyshipClient[sandbox,enabled="+enabled+",batchSize="+batchSize+"]";}
}
