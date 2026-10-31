package com.mercato.Service;
import java.time.*;
import java.util.List;

/** Elapsed days from actual handoff, never sped-up minutes or invented historical carrier scans. */
public final class SandboxTrackingSchedule {
    private SandboxTrackingSchedule(){}
    public static final List<Duration> OFFSETS=List.of(Duration.ofDays(1),Duration.ofDays(3),Duration.ofDays(3).plusHours(8));
    public static final List<String> STATUSES=List.of("in_transit_to_customer","out_for_delivery","delivered");
    public static int dueStage(Instant handoff,Instant now){
        int stage=0;for(var offset:OFFSETS)if(!now.isBefore(handoff.plus(offset)))stage++;return stage;
    }
}
