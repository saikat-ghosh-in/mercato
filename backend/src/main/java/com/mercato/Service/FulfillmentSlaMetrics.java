package com.mercato.Service;

import com.mercato.ExceptionHandler.CustomBadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class FulfillmentSlaMetrics {
    private final JdbcTemplate jdbc;
    private final FulfillmentWorkflow workflow;
    public FulfillmentSlaMetrics(JdbcTemplate jdbc,FulfillmentWorkflow workflow){this.jdbc=jdbc;this.workflow=workflow;}
    public record Coverage(Instant fromInclusive,Instant toExclusive,Instant asOf,
            String acceptanceCohortBasis,String deadlineEventDateBasis,boolean matureCohortOnly,boolean sellerScoped) {}
    public record Acceptance(long cohortOrders,long cohortLines,long orderedQty,
            long customerCancelledBeforeDeadlineQty,long acceptanceDenominatorQty,long onTimeAcceptedQty,
            BigDecimal onTimeAcceptanceRate,long timeoutCancelledQty) {}
    public record Report(Coverage coverage,Acceptance acceptance,List<Map<String,Object>> deadlines) {}

    /** sellerEmail is established by the caller's authenticated identity, never a request parameter. */
    @Transactional(readOnly=true)
    public Report report(String sellerEmail,Instant from,Instant to) {
        Instant now=workflow.now();if(from==null)from=now.minus(Duration.ofDays(30));if(to==null)to=now;
        if(!from.isBefore(to))throw new CustomBadRequestException("Metrics from must be earlier than to");
        var acceptance=jdbc.queryForObject("""
            WITH lines AS (
              SELECT o.order_id,l.order_line_number,l.seller_email,
                COALESCE(l.workflow_baseline_ordered_qty,l.ordered_qty)::bigint AS ordered_qty,
                COALESCE(l.workflow_baseline_accepted_qty,0)::bigint AS accepted_baseline,
                COALESCE(l.workflow_baseline_at,o.confirmed_at) AS anchor,
                GREATEST(o.seller_actions_available_at,o.workflow_policy_adopted_at)+interval '48 hours' AS deadline
              FROM order_snapshot o JOIN order_lines_snapshot l ON l.order_fk=o.id
              WHERE o.workflow_version=2 AND o.workflow_review_required=false
                AND o.confirmed_at IS NOT NULL AND o.allocation_completed_at IS NOT NULL
                AND l.workflow_legacy_review=false
            ), cohort AS (
              SELECT * FROM lines WHERE anchor>=? AND anchor<? AND deadline<=?
                AND (CAST(? AS varchar) IS NULL OR seller_email=?)
            ), quantities AS (
              SELECT l.order_id,l.order_line_number,l.ordered_qty,l.accepted_baseline,
                COALESCE(sum(CASE WHEN c.action='ACCEPT' AND c.created_at<l.deadline THEN c.qty ELSE 0 END),0) AS accepted,
                COALESCE(sum(CASE WHEN c.action='CANCEL' AND c.actor_type='CUSTOMER' AND c.created_at<l.deadline THEN c.qty ELSE 0 END),0) AS customer_cancelled,
                COALESCE(sum(CASE WHEN c.action='CANCEL' AND c.actor_type='CUSTOMER' AND c.created_at<l.deadline THEN c.accepted_only_qty ELSE 0 END),0) AS customer_accepted_cancelled,
                COALESCE(sum(CASE WHEN c.action='CANCEL' AND c.actor_type='SYSTEM' AND c.reason_code='SYSTEM_SELLER_DELAY' THEN c.qty ELSE 0 END),0) AS timeout_cancelled
              FROM cohort l LEFT JOIN fulfillment_commands c ON c.order_id=l.order_id
                AND c.line_number=l.order_line_number AND c.created_at>=l.anchor AND c.created_at<=?
              GROUP BY l.order_id,l.order_line_number,l.ordered_qty,l.accepted_baseline
            )
            SELECT count(DISTINCT order_id) AS cohort_orders,count(*) AS cohort_lines,
              COALESCE(sum(ordered_qty),0) AS ordered_qty,
              COALESCE(sum(customer_cancelled),0) AS customer_cancelled,
              COALESCE(sum(ordered_qty-customer_cancelled),0) AS denominator,
              COALESCE(sum(accepted_baseline+accepted-customer_accepted_cancelled),0) AS on_time_accepted,
              COALESCE(sum(timeout_cancelled),0) AS timeout_cancelled
            FROM quantities
            """,(r,n)->{
                long denominator=r.getLong("denominator"),accepted=r.getLong("on_time_accepted");
                BigDecimal rate=denominator==0?null:BigDecimal.valueOf(accepted).divide(BigDecimal.valueOf(denominator),8,RoundingMode.HALF_UP);
                return new Acceptance(r.getLong("cohort_orders"),r.getLong("cohort_lines"),r.getLong("ordered_qty"),r.getLong("customer_cancelled"),denominator,accepted,rate,r.getLong("timeout_cancelled"));
            },Timestamp.from(from),Timestamp.from(to),Timestamp.from(now),sellerEmail,sellerEmail,Timestamp.from(now));
        // Seller metrics use line-scoped command facts; aggregate order work items can include other sellers.
        List<Map<String,Object>> deadlines=sellerEmail==null?jdbc.queryForList("SELECT kind,count(*) AS orders,sum(affected_qty) AS affected_qty,max(extract(epoch from observed_at-due_at)) AS max_lateness_seconds FROM fulfillment_work_items WHERE due_at>=? AND due_at<? GROUP BY kind ORDER BY kind",Timestamp.from(from),Timestamp.from(to)):List.of();
        return new Report(new Coverage(from,to,now,"CONFIRMED_OR_PROSPECTIVE_ADOPTION_AT","DUE_AT",true,sellerEmail!=null),acceptance,deadlines);
    }
}
