package com.mercato.Service;

import java.time.Instant;
import java.util.*;

/** Provider-neutral tracking evidence; map keys are the requested provider references. */
public interface TrackingClient {
    boolean enabled();
    int batchSize();
    Result fetch(Collection<String> references);
    record Event(String code,String description,Instant providerAt) {}
    record Update(String waybill,String status,String statusType,Instant providerAt,Instant eta,List<Event> events,boolean reverse) {}
    record Result(Map<String,Update> updates,boolean singleFallback) {}
}
