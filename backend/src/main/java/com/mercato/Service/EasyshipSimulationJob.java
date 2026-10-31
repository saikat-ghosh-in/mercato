package com.mercato.Service;
import com.mercato.Jobs.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class EasyshipSimulationJob implements JobHandler {
    private final ShipmentService shipments;
    public String type(){return "EASYSHIP_SIMULATE";}
    public void handle(JobRecord job){
        try{shipments.simulate(UUID.fromString(String.valueOf(job.payload().get("id"))),Integer.parseInt(String.valueOf(job.payload().get("stage"))));}
        catch(IllegalArgumentException invalid){throw new PoisonJobException("Invalid sandbox simulation work");}
    }
}
