package com.mercato.Config;
import java.time.Clock;
import org.springframework.context.annotation.*;
@Configuration
public class WorkflowClockConfig {
    @Bean public Clock workflowClock() { return Clock.systemUTC(); }
}
