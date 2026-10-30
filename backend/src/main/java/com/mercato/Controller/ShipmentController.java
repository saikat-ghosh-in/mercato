package com.mercato.Controller;
import com.mercato.Service.ShipmentService;
import com.mercato.Entity.fulfillment.TransitionTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.UUID;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class ShipmentController {
    private final ShipmentService shipments;
    @PostMapping("/seller/fulfillment-orders/{id}/shipments/prepare") public Object sellerPrepare(@PathVariable String id,@RequestBody ShipmentService.PrepareRequest r){return shipments.prepare(id,TransitionTrigger.SELLER,r);}
    @PostMapping("/admin/fulfillment-orders/{id}/shipments/prepare") public Object adminPrepare(@PathVariable String id,@RequestBody ShipmentService.PrepareRequest r){return shipments.prepare(id,TransitionTrigger.ADMIN,r);}
    @GetMapping("/user/orders/{id}/shipments") public Object customer(@PathVariable String id){return shipments.read(id,TransitionTrigger.CUSTOMER,false);}
    @PostMapping("/user/orders/{id}/shipments/refresh") @ResponseStatus(HttpStatus.ACCEPTED) public Object customerRefresh(@PathVariable String id){return shipments.refresh(id,TransitionTrigger.CUSTOMER,false);}
    @GetMapping("/seller/fulfillment-orders/{id}/shipments") public Object seller(@PathVariable String id){return shipments.read(id,TransitionTrigger.SELLER,true);}
    @PostMapping("/seller/fulfillment-orders/{id}/shipments/refresh") @ResponseStatus(HttpStatus.ACCEPTED) public Object sellerRefresh(@PathVariable String id){return shipments.refresh(id,TransitionTrigger.SELLER,true);}
    @PostMapping("/seller/fulfillment-orders/{id}/shipments") public Object sellerCreate(@PathVariable String id,@RequestBody ShipmentService.Handoff r){return shipments.create(id,TransitionTrigger.SELLER,r);}
    @GetMapping("/admin/orders/{id}/shipments") public Object admin(@PathVariable String id){return shipments.read(id,TransitionTrigger.ADMIN,false);}
    @PostMapping("/admin/orders/{id}/shipments/refresh") @ResponseStatus(HttpStatus.ACCEPTED) public Object adminRefresh(@PathVariable String id){return shipments.refresh(id,TransitionTrigger.ADMIN,false);}
    @PostMapping("/admin/fulfillment-orders/{id}/shipments") public Object adminCreate(@PathVariable String id,@RequestBody ShipmentService.Handoff r){return shipments.create(id,TransitionTrigger.ADMIN,r);}
    @PatchMapping("/admin/shipments/{id}/tracking") public Object correct(@PathVariable UUID id,@RequestBody ShipmentService.Change r){return shipments.change(id,r,false);}
    @PostMapping("/admin/shipments/{id}/delivered") public Object delivered(@PathVariable UUID id,@RequestBody ShipmentService.Change r){return shipments.change(id,r,true);}
}
