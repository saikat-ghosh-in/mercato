package com.mercato.Controller;

import com.mercato.Service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController @RequiredArgsConstructor
public class EasyshipWebhookController {
    private final EasyshipWebhookVerifier verifier;
    private final ShipmentService shipments;
    @PostMapping("/api/public/easyship/webhook")
    public Object receive(@RequestHeader(value="X-EASYSHIP-SIGNATURE",required=false) String signature,@RequestBody byte[] body){
        verifier.verify(signature);
        if(body.length>262144)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Tracking event too large");
        shipments.trackingEvent(body);
        return Map.of("received",true);
    }
}
