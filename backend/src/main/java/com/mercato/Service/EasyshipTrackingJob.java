package com.mercato.Service;

import com.mercato.Jobs.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** New provider work; the legacy handler remains installed for existing durable jobs. */
@Component @RequiredArgsConstructor
public class EasyshipTrackingJob implements JobHandler {
    private final ShipmentService shipments;
    @Override public String type(){return "EASYSHIP_TRACK";}
    @Override public void handle(JobRecord job){
        Object value=job.payload().get("ids");
        if(!(value instanceof List<?> ids)||ids.isEmpty()||ids.size()>10000)throw new PoisonJobException("Invalid tracking work");
        try{var parsed=ids.stream().map(x->UUID.fromString(String.valueOf(x))).toList();if(Boolean.TRUE.equals(job.payload().get("event")))shipments.processEvent(parsed);else shipments.process(parsed);}
        catch(IllegalArgumentException invalid){throw new PoisonJobException("Invalid tracking parcel identity");}
    }
}
