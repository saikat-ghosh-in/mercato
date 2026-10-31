package com.mercato.Service;
import com.mercato.Jobs.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.*;

@Component @RequiredArgsConstructor
public class DelhiveryTrackingJob implements JobHandler {
    private final ShipmentService shipments;
    @Override public String type(){return "DELHIVERY_TRACK";}
    @Override public void handle(JobRecord job){
        Object value=job.payload().get("ids");
        if(!(value instanceof List<?> ids)||ids.isEmpty()||ids.size()>10000)throw new PoisonJobException("Invalid tracking work");
        try{shipments.process(ids.stream().map(x->UUID.fromString(String.valueOf(x))).toList());}
        catch(IllegalArgumentException invalid){throw new PoisonJobException("Invalid tracking parcel identity");}
    }
    @Scheduled(fixedDelayString="${mercato.tracking.scan-ms:900000}",initialDelayString="${mercato.tracking.initial-delay-ms:30000}")
    public void scan(){shipments.pollActive();}
}
