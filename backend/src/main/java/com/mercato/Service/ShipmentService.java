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
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;

    public record Item(int lineNumber,int qty) {}
    public record Handoff(String operationId,Long expectedVersion,String waybill,boolean actualHandoff,List<Item> items) {}
    public record Change(String operationId,Long expectedVersion,String reason,String waybill) {}
    public record ParcelItem(int lineNumber,String productName,int qty) {}
    public record Parcel(UUID id,String fulfillmentId,String waybill,String status,Instant handedOffAt,Instant deliveredAt,
        Instant lastCheckedAt,Instant eta,String message,List<DelhiveryClient.Event> history,List<ParcelItem> items,long version) {}
    public record Eligible(String fulfillmentId,int lineNumber,String productName,int maximum) {}
    public record View(List<Parcel> shipments,List<Eligible> handoffItems,long version,boolean canCreate,boolean canRefresh,String message,List<Eligible> legacyDeliveryItems) {}
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
        boolean refresh=carrier.enabled()&&ps.stream().anyMatch(s->!s.getStatus().equals("DELIVERED")&&trackingReady(s)&&
            (s.getLastRequestedAt()==null||!s.getLastRequestedAt().isAfter(workflow.now().minusSeconds(60))));
        return new View(ps.stream().map(this::dto).toList(),eligible,o.getWorkflowRevision(),!eligible.isEmpty(),refresh,
            carrier.enabled()?"Tracking updates are checked periodically.":scope==TransitionTrigger.ADMIN?"Delhivery staging is not configured. Manual delivery is available.":"Tracking updates pending.",legacy);
    }
    private Parcel dto(Shipment s) {
        return new Parcel(s.getId(),s.getFulfillmentId(),s.getWaybill(),s.getStatus(),s.getHandedOffAt(),s.getDeliveredAt(),s.getLastCheckedAt(),s.getEta(),s.getSafeMessage(),history(s),
            s.getItems().stream().map(i->new ParcelItem(i.getLine().getOrderLineNumber(),i.getLine().getProductName(),i.getQty())).toList(),s.getVersion());
    }
    private List<DelhiveryClient.Event> history(Shipment s){try{return json.readValue(s.getHistory(),new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Persisted shipment history is unreadable",e);}}
    private void event(Shipment s,String code,String description,Instant at) {
        var events=new ArrayList<>(history(s));var e=new DelhiveryClient.Event(code,description,at);
        if(!events.contains(e))events.add(e);if(events.size()>100)events=new ArrayList<>(events.subList(events.size()-100,events.size()));
        s.setHistory(commands.encode(events));
    }
    private String op(String kind,String operation){WorkflowCommands.requireExternalOperation(operation);return "parcel:"+kind+":"+UUID.nameUUIDFromBytes((auth.getLoggedInUser().getEmail()+":"+operation).getBytes(StandardCharsets.UTF_8));}
    private String actor(TransitionTrigger scope){return scope==TransitionTrigger.SYSTEM?"DELHIVERY_STAGING":auth.getLoggedInUser().getEmail();}
    public View create(String fulfillment,TransitionTrigger scope,Handoff r) {
        if(r==null||!r.actualHandoff()||r.items()==null||r.items().isEmpty()||r.items().size()>100||r.items().stream().anyMatch(Objects::isNull)||r.waybill()==null||!r.waybill().matches("[A-Za-z0-9-]{1,80}"))throw bad("Confirm handoff, a valid waybill and shipped quantities.");
        String operation=op("handoff",r.operationId());
        return tx(()->{
            var o=scopedOrder(fulfillment,scope,true);String body=commands.encode(List.of(fulfillment,scope,r));
            String stored=commands.stored(o,operation,r.expectedVersion(),body);if(stored!=null)return commands.decode(stored,View.class);
            if(parcels.existsByWaybill(r.waybill()))throw bad("This waybill is already assigned to a parcel.");
            var s=new Shipment();s.setId(UUID.randomUUID());s.setOrder(o);s.setFulfillmentId(fulfillment);s.setWaybill(r.waybill());s.setStatus("SHIPPED");s.setHandedOffAt(workflow.now());
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
            var result=view(o,scope,fulfillment);commands.recordAggregate(o,operation,body,scope,actor(scope),"Actual carrier handoff", "PARCEL_HANDOFF",s.getItems().stream().mapToInt(ShipmentItem::getQty).sum(),commands.encode(result));return result;
        });
    }
    private void apply(Order o,ShipmentItem item,OrderLineAction action,TransitionTrigger scope,String operation,String reason) {
        var r=new OrderLineUpdateRequestDTO();r.setFulfillmentId(item.getLine().getFulfillmentId());r.setOrderLineNumber(item.getLine().getOrderLineNumber());r.setAction(action);r.setQty(item.getQty());r.setExpectedVersion(o.getWorkflowRevision());r.setOperationId(operation+":"+item.getLine().getId());r.setReason(reason);updates.applyParcel(r,scope);
    }
    public View change(UUID id,Change r,boolean deliver) {
        if(r==null||r.reason()==null||r.reason().isBlank()||r.reason().length()>500)throw bad("An audit reason is required.");
        if(!deliver&&(r.waybill()==null||!r.waybill().matches("[A-Za-z0-9-]{1,80}")))throw bad("A valid waybill is required.");
        String operation=op(deliver?"delivered":"correct",r.operationId());
        return tx(()->{
            String orderId=parcels.orderId(id).orElseThrow(()->missing(id.toString()));var o=orders.lockByOrderId(orderId).orElseThrow(()->missing(id.toString()));
            var s=parcels.findById(id).orElseThrow(()->missing(id.toString()));String body=commands.encode(List.of(id,deliver,r));
            String stored=commands.stored(o,operation,r.expectedVersion(),body);if(stored!=null)return commands.decode(stored,View.class);
            if(s.getStatus().equals("DELIVERED"))throw bad("Delivered parcels cannot be changed.");
            if(deliver)deliver(o,s,TransitionTrigger.ADMIN,operation,r.reason(),workflow.now());
            else {
                if(!s.getWaybill().equals(r.waybill())&&parcels.existsByWaybill(r.waybill()))throw bad("This waybill is already assigned.");
                s.setWaybill(r.waybill());s.setTrackingGeneration(s.getTrackingGeneration()+1);s.setProviderUpdatedAt(null);s.setStatus("SHIPPED");s.setEta(null);s.setSafeMessage(null);s.setLastRequestedAt(null);o.workflowChanged();event(s,"TRACKING_CORRECTED","Tracking details corrected",workflow.now());
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
            if(!carrier.enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Carrier tracking is not configured; manual delivery remains available.");
            enqueue(scopedParcels(o,scope,fulfillment?id:null),60);return view(o,scope,fulfillment?id:null);
        });
    }
    private void enqueue(List<Shipment> source,long interval) {
        var ids=new ArrayList<String>();var now=workflow.now();
        for(var s:source)if(!s.getStatus().equals("DELIVERED")&&trackingReady(s)&&(s.getLastRequestedAt()==null||!s.getLastRequestedAt().isAfter(now.minusSeconds(interval)))){s.setLastRequestedAt(now);ids.add(s.getId().toString());}
        if(!ids.isEmpty())jobs.enqueue("DELHIVERY_TRACK","parcel-poll:"+UUID.randomUUID(),Map.of("ids",ids));
    }
    public void pollActive(){if(carrier.enabled())tx(()->{
        var active=parcels.findAllById(parcels.active());
        active.stream().map(s->s.getOrder().getOrderId()).distinct().sorted().forEach(id->orders.lockByOrderId(id).orElseThrow());
        enqueue(active,900);return null;
    });}
    record Target(UUID id,String waybill,long generation){}
    private boolean trackingReady(Shipment s){return s.getNextTrackingAt()==null||!s.getNextTrackingAt().isAfter(workflow.now());}
    public void process(List<UUID> ids) {
        if(!carrier.enabled())return;
        var targets=tx(()->ids.stream().distinct().map(parcels::findById).flatMap(Optional::stream).filter(s->!s.getStatus().equals("DELIVERED")&&trackingReady(s)).map(s->new Target(s.getId(),s.getWaybill(),s.getTrackingGeneration())).toList());
        if(targets.isEmpty())return;
        DelhiveryClient.Result result;
        try{result=carrier.fetch(targets.stream().map(Target::waybill).toList());}
        catch(RuntimeException error){
            Instant next=error instanceof WaitingJobException waiting?workflow.now().plus(waiting.retryAfter()):null;
            for(var t:targets)recordResult(t,null,"Carrier updates are temporarily unavailable.",next);throw error;
        }
        for(var t:targets)recordResult(t,result.updates().get(t.waybill()),result.singleFallback()?"Carrier requested smaller tracking batches.":null);
    }
    private void recordResult(Target target,DelhiveryClient.Update update,String message) {
        recordResult(target,update,message,null);
    }
    private void recordResult(Target target,DelhiveryClient.Update update,String message,Instant next) {
        tx(()->{
            String id=parcels.orderId(target.id()).orElse(null);if(id==null)return null;
            var o=orders.lockByOrderId(id).orElseThrow();var s=parcels.findById(target.id()).orElseThrow();
            if(s.getTrackingGeneration()!=target.generation()||!s.getWaybill().equals(target.waybill())||s.getStatus().equals("DELIVERED"))return null;
            s.setLastCheckedAt(workflow.now());s.setObservedAt(workflow.now());s.setSafeMessage(message);
            if(next!=null&&(s.getNextTrackingAt()==null||next.isAfter(s.getNextTrackingAt())))s.setNextTrackingAt(next);
            if(update==null){s.setSafeMessage(message==null?"Carrier has no updates for this package yet.":message);return null;}
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
