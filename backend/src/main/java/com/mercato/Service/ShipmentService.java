package com.mercato.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Entity.fulfillment.*;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Jobs.*;
import com.mercato.Payloads.Request.OrderLineUpdateRequestDTO;
import com.mercato.Repository.*;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Parcels reuse the order aggregate lock, workflow commands and inventory transitions. */
@Service @RequiredArgsConstructor
public class ShipmentService {
    private final ShipmentRepository parcels;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final SellerOrderVisibility visibility;
    private final FulfillmentWorkflow workflow;
    private final OrderLineUpdateServiceImpl updates;
    private final WorkflowCommands commands;
    private final AuthUtil auth;
    private final DurableJobs jobs;
    private final DelhiveryClient carrier;
    private final EasyshipClient easyship;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Value("${EASYSHIP_WEBHOOK_ENABLED:false}")
    private boolean webhookEnabled;

    public record Item(int lineNumber,int qty) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Handoff(String operationId,Long expectedVersion,String waybill,boolean actualHandoff,List<Item> items,String easyshipShipmentId) {
        public Handoff(String operationId,Long expectedVersion,String waybill,boolean actualHandoff,List<Item> items){this(operationId,expectedVersion,waybill,actualHandoff,items,null);}
    }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Change(String operationId,Long expectedVersion,String reason,String waybill,String easyshipShipmentId) {
        public Change(String operationId,Long expectedVersion,String reason,String waybill){this(operationId,expectedVersion,reason,waybill,null);}
    }
    public record ParcelItem(int lineNumber,String productName,int qty) {}
    public record Parcel(UUID id,String fulfillmentId,String waybill,String status,Instant handedOffAt,Instant deliveredAt,
        Instant lastCheckedAt,Instant eta,String message,List<TrackingClient.Event> history,List<ParcelItem> items,long version,String provider,String providerReference) {}
    public record Eligible(String fulfillmentId,int lineNumber,String productName,int maximum) {}
    public record View(List<Parcel> shipments,List<Eligible> handoffItems,long version,boolean canCreate,boolean canRefresh,String message,List<Eligible> legacyDeliveryItems,String trackingProvider,boolean canGenerateTracking) {}
    public record PrepareRequest(String operationId,Long expectedVersion,int lineNumber) {}
    public record Preparation(String state,String waybill,String easyshipShipmentId,String message,long retryAfterSeconds) {}
    private <T> T tx(java.util.function.Supplier<T> work){return new TransactionTemplate(transactions).execute(t->work.get());}
    private ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
    private ResourceNotFoundException missing(String id){return new ResourceNotFoundException("Shipment","id",id);}

    private Order scopedOrder(String id,TransitionTrigger scope,boolean fulfillment) {
        if(scope==TransitionTrigger.CUSTOMER) {
            // One authoritative parent ownership query; callers never accept an arbitrary child/AWB.
            var customer=auth.getLoggedInUser();
            return orders.findOwnedForShipment(id,customer.getUserId(),customer.getEmail()).orElseThrow(()->missing(id));
        }
        var o=(fulfillment?orders.lockByFulfillmentId(id):orders.lockByOrderId(id)).orElseThrow(()->missing(id));
        if(scope==TransitionTrigger.SELLER){
            String email=auth.getLoggedInUser().getEmail();
            if(o.getOrderLines().stream().noneMatch(l->l.getFulfillmentId().equals(id)&&l.getSellerEmail().equals(email)))throw missing(id);
            visibility.requireVisible(o);
        }
        return o;
    }
    private List<Shipment> scopedParcels(Order o,TransitionTrigger scope,String fulfillment) {
        return parcels.forOrder(o.getId()).stream().filter(s->scope!=TransitionTrigger.SELLER||
            (s.getFulfillmentId().equals(fulfillment)&&s.getSellerEmail().equals(auth.getLoggedInUser().getEmail()))).toList();
    }
    public View read(String id,TransitionTrigger scope,boolean fulfillment){return tx(()->view(scopedOrder(id,scope,fulfillment),scope,fulfillment?id:null));}
    private View view(Order o,TransitionTrigger scope,String fulfillment) {
        var ps=scopedParcels(o,scope,fulfillment);
        var eligible=scope==TransitionTrigger.CUSTOMER?List.<Eligible>of():o.getOrderLines().stream()
            .filter(l->fulfillment==null||l.getFulfillmentId().equals(fulfillment))
            .filter(l->scope!=TransitionTrigger.SELLER||l.getSellerEmail().equals(auth.getLoggedInUser().getEmail()))
            .map(l->new Eligible(l.getFulfillmentId(),l.getOrderLineNumber(),l.getProductName(),workflow.maximum(l,TransitionTrigger.ADMIN,OrderLineAction.SHIP)))
            .filter(e->e.maximum()>0).toList();
        var tracked=new HashMap<Long,Long>();
        if(scope==TransitionTrigger.ADMIN)parcels.undeliveredForOrder(o.getId()).forEach(row->tracked.put((Long)row[0],(Long)row[1]));
        var legacy=scope==TransitionTrigger.ADMIN?o.getOrderLines().stream().map(l->new Eligible(l.getFulfillmentId(),l.getOrderLineNumber(),l.getProductName(),
            Math.max(0,workflow.maximum(l,TransitionTrigger.ADMIN,OrderLineAction.DELIVER)-Math.toIntExact(tracked.getOrDefault(l.getId(),0L))))).filter(e->e.maximum()>0).toList():List.<Eligible>of();
        boolean refresh=ps.stream().anyMatch(s->client(s.getProvider()).enabled()&&!s.getStatus().equals("DELIVERED")&&trackingReady(s)&&
            (s.getLastRequestedAt()==null||!s.getLastRequestedAt().isAfter(workflow.now().minusSeconds(60))));
        String provider=easyship.enabled()?"EASYSHIP":carrier.enabled()?"DELHIVERY":"MANUAL";
        return new View(ps.stream().map(this::dto).toList(),eligible,o.getWorkflowRevision(),!eligible.isEmpty(),refresh,
            !provider.equals("MANUAL")?(webhookEnabled&&provider.equals("EASYSHIP")?"Tracking updates arrive automatically.":"Tracking updates are checked periodically."):scope==TransitionTrigger.ADMIN?"Sandbox tracking is not configured. Manual delivery is available.":"Tracking updates pending.",legacy,provider,easyship.creationEnabled());
    }
    /** Persist identity before I/O. An uncertain POST is reconciled, never blindly repeated. */
    public Preparation prepare(String fulfillment,TransitionTrigger scope,PrepareRequest request){
        if(scope!=TransitionTrigger.SELLER&&scope!=TransitionTrigger.ADMIN)throw missing(fulfillment);
        if(request==null||request.expectedVersion()==null||request.expectedVersion()<0||request.lineNumber()<1)throw bad("Choose an item and current workflow version.");
        String operation=op("prepare",request.operationId());
        UUID id=tx(()->{
            var o=scopedOrder(fulfillment,scope,true);
            UUID key=UUID.nameUUIDFromBytes((o.getOrderId()+":"+operation).getBytes(StandardCharsets.UTF_8));
            String body=commands.encode(List.of(fulfillment,scope,request));
            var rows=jdbc.queryForList("SELECT request_body FROM shipment_tracking_drafts WHERE id=? AND order_fk=?",key,o.getId());
            if(!rows.isEmpty()){
                if(!body.equals(rows.get(0).get("request_body")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Operation ID already used with different request");
                return key;
            }
            if(!easyship.creationEnabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Sandbox tracking generation is not configured.");
            if(request.expectedVersion()!=o.getWorkflowRevision())throw new ResponseStatusException(HttpStatus.CONFLICT,"Stale workflow version");
            var line=o.getOrderLines().stream().filter(l->l.getFulfillmentId().equals(fulfillment)&&l.getOrderLineNumber()==request.lineNumber()).findFirst().orElseThrow(()->missing(fulfillment));
            workflow.require(line,TransitionTrigger.ADMIN,OrderLineAction.SHIP,1);
            jdbc.update("INSERT INTO shipment_tracking_drafts(id,order_fk,request_body,waybill,state,created_at,updated_at) VALUES(?,?,?,?,'NEW',?,?)",key,o.getId(),body,"MCT"+key.toString().replace("-", "").toUpperCase(Locale.ROOT),java.sql.Timestamp.from(workflow.now()),java.sql.Timestamp.from(workflow.now()));
            return key;
        });
        var row=jdbc.queryForMap("SELECT state,waybill,provider_reference FROM shipment_tracking_drafts WHERE id=?",id);
        String state=(String)row.get("state"),waybill=(String)row.get("waybill");
        if(state.equals("READY"))return new Preparation(state,waybill,(String)row.get("provider_reference"),"Sandbox tracking is ready. Confirm handoff separately.",0);
        if(state.equals("FAILED"))return new Preparation(state,null,null,"Sandbox tracking could not be generated. Contact Mercato or enter existing sandbox tracking.",0);
        boolean create=Boolean.TRUE.equals(tx(()->jdbc.update("UPDATE shipment_tracking_drafts SET state='SENDING',updated_at=? WHERE id=? AND state='NEW'",java.sql.Timestamp.from(workflow.now()),id)==1));
        try{
            var prepared=create?easyship.createTracking("SANDBOX-"+id,waybill):easyship.findPrepared("SANDBOX-"+id,waybill);
            if(prepared==null){draftState(id,"UNCONFIRMED");return new Preparation("UNCONFIRMED",null,null,"Tracking is not confirmed yet. Check again shortly; another request will not be created.",10);}
            jdbc.update("UPDATE shipment_tracking_drafts SET state='READY',provider_reference=?,updated_at=? WHERE id=?",prepared.reference(),java.sql.Timestamp.from(workflow.now()),id);
            return new Preparation("READY",prepared.waybill(),prepared.reference(),"Sandbox tracking is ready. Confirm handoff separately.",0);
        }catch(WaitingJobException waiting){
            // A local rate limit/HTTP 429 is known not to have created tracking.
            if(create)draftState(id,"NEW");
            return new Preparation("WAITING",null,null,"Please wait briefly before checking sandbox tracking again.",Math.max(1,waiting.retryAfter().toSeconds()+1));
        }catch(ManualInterventionJobException rejected){
            draftState(id,"FAILED");return new Preparation("FAILED",null,null,"Sandbox tracking is unavailable. Contact Mercato or enter existing sandbox tracking.",0);
        }catch(TransientJobException uncertain){
            draftState(id,"UNCONFIRMED");return new Preparation("UNCONFIRMED",null,null,"The provider result is not confirmed. Check again to recover the same tracking request.",10);
        }
    }
    private void draftState(UUID id,String state){jdbc.update("UPDATE shipment_tracking_drafts SET state=?,updated_at=? WHERE id=? AND state<>'READY'",state,java.sql.Timestamp.from(workflow.now()),id);}
    private Parcel dto(Shipment s) {
        return new Parcel(s.getId(),s.getFulfillmentId(),s.getWaybill(),s.getStatus(),s.getHandedOffAt(),s.getDeliveredAt(),s.getLastCheckedAt(),s.getEta(),s.getSafeMessage(),history(s),
            s.getItems().stream().map(i->new ParcelItem(i.getLine().getOrderLineNumber(),i.getLine().getProductName(),i.getQty())).toList(),s.getVersion(),s.getProvider(),s.getProviderReference());
    }
    private List<TrackingClient.Event> history(Shipment s){try{return json.readValue(s.getHistory(),new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Persisted shipment history is unreadable",e);}}
    private void event(Shipment s,String code,String description,Instant at) {
        var events=new ArrayList<>(history(s));var e=new TrackingClient.Event(code,description,at);
        if(!events.contains(e))events.add(e);if(events.size()>100)events=new ArrayList<>(events.subList(events.size()-100,events.size()));
        s.setHistory(commands.encode(events));
    }
    private String op(String kind,String operation){WorkflowCommands.requireExternalOperation(operation);return "parcel:"+kind+":"+UUID.nameUUIDFromBytes((auth.getLoggedInUser().getEmail()+":"+operation).getBytes(StandardCharsets.UTF_8));}
    private String actor(TransitionTrigger scope){return scope==TransitionTrigger.SYSTEM?"SANDBOX_CARRIER":auth.getLoggedInUser().getEmail();}
    private TrackingClient client(String provider){return "EASYSHIP".equals(provider)?easyship:carrier;}
    public View create(String fulfillment,TransitionTrigger scope,Handoff r) {
        if(r==null||!r.actualHandoff()||r.items()==null||r.items().isEmpty()||r.items().size()>100||r.items().stream().anyMatch(Objects::isNull)||r.waybill()==null||!r.waybill().matches("[A-Za-z0-9-]{1,80}"))throw bad("Confirm handoff, a valid waybill and shipped quantities.");
        if(r.easyshipShipmentId()!=null&&!EasyshipClient.validReference(r.easyshipShipmentId()))throw bad("Use a valid Easyship sandbox shipment ID.");
        String operation=op("handoff",r.operationId());
        return tx(()->{
            var o=scopedOrder(fulfillment,scope,true);String body=commands.encode(List.of(fulfillment,scope,r));
            String stored=commands.stored(o,operation,r.expectedVersion(),body);if(stored!=null)return commands.decode(stored,View.class);
            if(easyship.enabled()&&r.easyshipShipmentId()==null)throw bad("An Easyship sandbox shipment ID is required.");
            if(parcels.existsByWaybill(r.waybill()))throw bad("This waybill is already assigned to a parcel.");
            var s=new Shipment();s.setId(UUID.randomUUID());s.setOrder(o);s.setFulfillmentId(fulfillment);s.setWaybill(r.waybill());s.setStatus("SHIPPED");s.setHandedOffAt(workflow.now());
            if(r.easyshipShipmentId()!=null){
                if(parcels.existsByProviderAndProviderReference("EASYSHIP",r.easyshipShipmentId()))throw bad("This Easyship shipment is already assigned to a parcel.");
                s.setProvider("EASYSHIP");s.setProviderReference(r.easyshipShipmentId());
            }
            var seen=new HashSet<Integer>();
            for(var requested:r.items().stream().sorted(Comparator.comparingInt(Item::lineNumber)).toList()){
                if(requested.qty()<=0||!seen.add(requested.lineNumber()))throw bad("Use unique lines and positive quantities.");
                var line=o.getOrderLines().stream().filter(l->l.getFulfillmentId().equals(fulfillment)&&l.getOrderLineNumber()==requested.lineNumber()).findFirst().orElseThrow(()->missing(fulfillment));
                workflow.require(line,TransitionTrigger.ADMIN,OrderLineAction.SHIP,requested.qty());
                s.setSellerEmail(line.getSellerEmail());var item=new ShipmentItem();item.setShipment(s);item.setLine(line);item.setQty(requested.qty());s.getItems().add(item);
            }
            // Multi-item handoffs lock products in the same stable order across orders.
            s.getItems().stream().map(i->i.getLine().getProductId()).distinct().sorted().forEach(id->products.findByProductIdForUpdate(id).orElseThrow(()->missing(id)));
            parcels.saveAndFlush(s);
            for(var item:s.getItems())apply(o,item,OrderLineAction.SHIP,scope,operation,"Actual carrier handoff confirmed");
            event(s,"HANDED_OVER","Handed to carrier",workflow.now());parcels.saveAndFlush(s);notice(s,"shipped");
            if(webhookEnabled&&"EASYSHIP".equals(s.getProvider()))enqueue(List.of(s),0);
            var result=view(o,scope,fulfillment);commands.recordAggregate(o,operation,body,scope,actor(scope),"Actual carrier handoff", "PARCEL_HANDOFF",s.getItems().stream().mapToInt(ShipmentItem::getQty).sum(),commands.encode(result));return result;
        });
    }
    private void apply(Order o,ShipmentItem item,OrderLineAction action,TransitionTrigger scope,String operation,String reason) {
        var r=new OrderLineUpdateRequestDTO();r.setFulfillmentId(item.getLine().getFulfillmentId());r.setOrderLineNumber(item.getLine().getOrderLineNumber());r.setAction(action);r.setQty(item.getQty());r.setExpectedVersion(o.getWorkflowRevision());r.setOperationId(operation+":"+item.getLine().getId());r.setReason(reason);updates.applyParcel(r,scope);
    }
    public View change(UUID id,Change r,boolean deliver) {
        if(r==null||r.reason()==null||r.reason().isBlank()||r.reason().length()>500)throw bad("An audit reason is required.");
        if(!deliver&&(r.waybill()==null||!r.waybill().matches("[A-Za-z0-9-]{1,80}")))throw bad("A valid waybill is required.");
        if(!deliver&&r.easyshipShipmentId()!=null&&!EasyshipClient.validReference(r.easyshipShipmentId()))throw bad("Use a valid Easyship sandbox shipment ID.");
        String operation=op(deliver?"delivered":"correct",r.operationId());
        return tx(()->{
            String orderId=parcels.orderId(id).orElseThrow(()->missing(id.toString()));var o=orders.lockByOrderId(orderId).orElseThrow(()->missing(id.toString()));
            var s=parcels.findById(id).orElseThrow(()->missing(id.toString()));String body=commands.encode(List.of(id,deliver,r));
            String stored=commands.stored(o,operation,r.expectedVersion(),body);if(stored!=null)return commands.decode(stored,View.class);
            if(s.getStatus().equals("DELIVERED"))throw bad("Delivered parcels cannot be changed.");
            if(deliver)deliver(o,s,TransitionTrigger.ADMIN,operation,r.reason(),workflow.now());
            else {
                if(!s.getWaybill().equals(r.waybill())&&parcels.existsByWaybill(r.waybill()))throw bad("This waybill is already assigned.");
                if(r.easyshipShipmentId()!=null){
                    if(!r.easyshipShipmentId().equals(s.getProviderReference())&&parcels.existsByProviderAndProviderReference("EASYSHIP",r.easyshipShipmentId()))throw bad("This Easyship shipment is already assigned.");
                    s.setProvider("EASYSHIP");s.setProviderReference(r.easyshipShipmentId());
                }
                s.setWaybill(r.waybill());s.setTrackingGeneration(s.getTrackingGeneration()+1);s.setProviderUpdatedAt(null);s.setStatus("SHIPPED");s.setEta(null);s.setSafeMessage(null);s.setLastRequestedAt(null);s.setNextTrackingAt(null);o.workflowChanged();event(s,"TRACKING_CORRECTED","Tracking details corrected",workflow.now());
            }
            parcels.saveAndFlush(s);var result=view(o,TransitionTrigger.ADMIN,null);
            commands.recordAggregate(o,operation,body,TransitionTrigger.ADMIN,actor(TransitionTrigger.ADMIN),r.reason(),deliver?"PARCEL_DELIVERED":"TRACKING_CORRECTED",0,commands.encode(result));return result;
        });
    }
    private void deliver(Order o,Shipment s,TransitionTrigger scope,String operation,String reason,Instant at) {
        for(var item:s.getItems())apply(o,item,OrderLineAction.DELIVER,scope,operation,reason);
        s.setStatus("DELIVERED");s.setDeliveredAt(at);event(s,"DELIVERED","Delivered",at);notice(s,"delivered");
    }
    private void notice(Shipment s,String stage) {
        String items=s.getItems().stream().map(i->i.getQty()+" × "+DelhiveryClient.safe(i.getLine().getProductName(),180)).collect(java.util.stream.Collectors.joining("\n"));
        jobs.enqueue("AUTH_EMAIL","parcel-mail:"+s.getId()+":"+stage,Map.of("to",s.getOrder().getCustomerEmail(),"subject","Your Mercato package is "+stage,"text","Your package for order "+s.getOrder().getOrderId()+" is "+stage+".\n\n"+items+"\n\nTracking: "+s.getWaybill()));
    }
    public View refresh(String id,TransitionTrigger scope,boolean fulfillment) {
        return tx(()->{
            var o=scopedOrder(id,scope,fulfillment);if(scope==TransitionTrigger.CUSTOMER)orders.lockByOrderId(o.getOrderId()).orElseThrow();
            if(!easyship.enabled()&&!carrier.enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Carrier tracking is not configured; manual delivery remains available.");
            enqueue(scopedParcels(o,scope,fulfillment?id:null),60);return view(o,scope,fulfillment?id:null);
        });
    }
    private void enqueue(List<Shipment> source,long interval) {
        var groups=new LinkedHashMap<String,List<String>>();var now=workflow.now();
        for(var s:source)if(client(s.getProvider()).enabled()&&!s.getStatus().equals("DELIVERED")&&trackingReady(s)&&(s.getLastRequestedAt()==null||!s.getLastRequestedAt().isAfter(now.minusSeconds(interval)))){
            s.setLastRequestedAt(now);groups.computeIfAbsent(s.getProvider(),key->new ArrayList<>()).add(s.getId().toString());
        }
        for(var group:groups.entrySet()){
            int size=Math.max(1,client(group.getKey()).batchSize());
            for(int start=0;start<group.getValue().size();start+=size)jobs.enqueue("EASYSHIP".equals(group.getKey())?"EASYSHIP_TRACK":"DELHIVERY_TRACK","parcel-poll:"+UUID.randomUUID(),Map.of("ids",group.getValue().subList(start,Math.min(start+size,group.getValue().size()))));
        }
    }
    public void pollActive(){if(easyship.enabled()||carrier.enabled())tx(()->{
        var active=parcels.findAllById(parcels.active());
        active.stream().map(s->s.getOrder().getOrderId()).distinct().sorted().forEach(id->orders.lockByOrderId(id).orElseThrow());
        enqueue(active.stream().filter(s->!webhookEnabled||!"EASYSHIP".equals(s.getProvider())).toList(),900);return null;
    });}
    /** A signed notification is a wake-up only; delivery still requires provider evidence. */
    public void trackingEvent(byte[] raw){
        com.fasterxml.jackson.databind.JsonNode event;
        try{event=json.readTree(raw);}catch(Exception invalid){throw bad("Invalid tracking event");}
        if(event==null||!event.isObject())throw bad("Invalid tracking event");
        String type=event.path("event_type").asText("");
        if(!Set.of("shipment.tracking.status.changed","shipment.tracking.checkpoints.created","shipment_tracking_status_changed","shipment_tracking_checkpoints_created").contains(type))return;
        if(!"shipment".equals(event.path("resource_type").asText("")))return;
        String reference=event.path("resource_id").asText(""),bodyReference=event.path("data").path("easyship_shipment_id").asText(reference);
        // Easyship's Test Webhook uses ESTEST00000, not a real shipment ID.
        if(!EasyshipClient.validReference(reference))return;
        if(!reference.equals(bodyReference))throw bad("Inconsistent tracking reference");
        String key;
        try{key="easyship-event:"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));}catch(Exception impossible){throw new IllegalStateException(impossible);}
        final String businessKey=key;
        tx(()->{
            var ids=jdbc.queryForList("SELECT id FROM shipments WHERE provider='EASYSHIP' AND provider_reference=? AND status<>'DELIVERED'",UUID.class,reference);
            if(!ids.isEmpty())jobs.enqueue("EASYSHIP_TRACK",businessKey,Map.of("ids",ids.stream().map(UUID::toString).toList(),"event",true));
            return null;
        });
    }
    public void processEvent(List<UUID> ids){
        process(ids,true);
    }
    record Target(UUID id,String waybill,long generation,String provider,String reference){}
    private boolean trackingReady(Shipment s){return s.getNextTrackingAt()==null||!s.getNextTrackingAt().isAfter(workflow.now());}
    public void process(List<UUID> ids) {
        process(ids,false);
    }
    private void process(List<UUID> ids,boolean event) {
        var targets=tx(()->{
            var source=ids.stream().distinct().map(parcels::findById).flatMap(Optional::stream).filter(s->client(s.getProvider()).enabled()&&!s.getStatus().equals("DELIVERED")).toList();
            if(event){
                Instant next=source.stream().filter(s->!trackingReady(s)).map(Shipment::getNextTrackingAt).max(Comparator.naturalOrder()).orElse(null);
                if(next!=null)throw new WaitingJobException("Waiting for tracking refresh window",Duration.between(workflow.now(),next).plusSeconds(1));
            }
            return source.stream().filter(this::trackingReady).map(s->new Target(s.getId(),s.getWaybill(),s.getTrackingGeneration(),s.getProvider(),"EASYSHIP".equals(s.getProvider())?s.getProviderReference():s.getWaybill())).toList();
        });
        if(targets.isEmpty())return;
        for(String provider:targets.stream().map(Target::provider).distinct().toList()){
            var group=targets.stream().filter(t->provider.equals(t.provider())).toList();int size=Math.max(1,client(provider).batchSize());
            for(int start=0;start<group.size();start+=size)processBatch(group.subList(start,Math.min(start+size,group.size())),client(provider));
        }
    }
    private void processBatch(List<Target> targets,TrackingClient selected){
        TrackingClient.Result result;
        try{result=selected.fetch(targets.stream().map(Target::reference).toList());}
        catch(RuntimeException error){
            Instant next=error instanceof WaitingJobException waiting?workflow.now().plus(waiting.retryAfter()):null;
            for(var t:targets)recordResult(t,null,"Carrier updates are temporarily unavailable.",next,false);throw error;
        }
        for(var t:targets)recordResult(t,result.updates().get(t.reference()),result.singleFallback()?"Carrier requested smaller tracking batches.":null);
    }
    private void recordResult(Target target,TrackingClient.Update update,String message) {
        recordResult(target,update,message,null,true);
    }
    private void recordResult(Target target,TrackingClient.Update update,String message,Instant next,boolean receivedResponse) {
        tx(()->{
            String id=parcels.orderId(target.id()).orElse(null);if(id==null)return null;
            var o=orders.lockByOrderId(id).orElseThrow();var s=parcels.findById(target.id()).orElseThrow();
            if(s.getTrackingGeneration()!=target.generation()||!s.getWaybill().equals(target.waybill())||!s.getProvider().equals(target.provider())||!Objects.equals("EASYSHIP".equals(s.getProvider())?s.getProviderReference():s.getWaybill(),target.reference())||s.getStatus().equals("DELIVERED"))return null;
            s.setLastCheckedAt(workflow.now());s.setObservedAt(workflow.now());s.setSafeMessage(message);
            if(next!=null&&(s.getNextTrackingAt()==null||next.isAfter(s.getNextTrackingAt())))s.setNextTrackingAt(next);
            if(receivedResponse&&next==null&&"EASYSHIP".equals(target.provider()))s.setNextTrackingAt(workflow.now().plusSeconds(60));
            if(update==null){s.setSafeMessage(message==null?"Carrier has no updates for this package yet.":message);return null;}
            if(!s.getWaybill().equals(update.waybill())){s.setSafeMessage("Tracking number needs administrator verification.");return null;}
            if(update.providerAt()==null||update.providerAt().isAfter(workflow.now())||update.providerAt().isBefore(s.getHandedOffAt())){s.setSafeMessage("Tracking update needs verification.");return null;}
            if(s.getProviderUpdatedAt()!=null&&update.providerAt().isBefore(s.getProviderUpdatedAt()))return null;
            s.setProviderUpdatedAt(update.providerAt());if(update.eta()!=null)s.setEta(update.eta());
            for(var e:update.events())event(s,e.code(),e.description(),e.providerAt());
            if(update.reverse()){s.setSafeMessage("Carrier reports a delivery exception. Contact Mercato for help.");return null;}
            if("DL".equalsIgnoreCase(update.statusType())&&"Delivered".equalsIgnoreCase(update.status())){
                boolean funded=s.getItems().stream().allMatch(i->workflow.maximum(i.getLine(),TransitionTrigger.ADMIN,OrderLineAction.DELIVER)>=i.getQty());
                if(funded)deliver(o,s,TransitionTrigger.SYSTEM,"parcel:provider:"+s.getId()+":"+s.getTrackingGeneration(),"Verified forward carrier delivery",update.providerAt());
                else s.setSafeMessage("Delivery update needs administrator verification.");
            }else if("UD".equalsIgnoreCase(update.statusType())&&"Dispatched".equalsIgnoreCase(update.status()))s.setStatus("OUT_FOR_DELIVERY");
            parcels.saveAndFlush(s);return null;
        });
    }
}
