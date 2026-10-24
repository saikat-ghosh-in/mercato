package com.mercato.Commerce;
import com.mercato.Jobs.*;
import org.springframework.stereotype.Component;
@Component
public class CaptureAttentionHandler implements JobHandler {
    public String type(){return "CAPTURE_ATTENTION";}
    public void handle(JobRecord job){throw new ManualInterventionJobException("Extra verified capture requires operator reconciliation");}
}
