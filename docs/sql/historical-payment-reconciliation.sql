-- Read-only historical reconciliation. Run with a SELECT-only database role.
-- A completed_at value is deliberately never treated as proof of capture.
BEGIN TRANSACTION READ ONLY;
WITH refund_totals AS (
 SELECT payment_fk,
   SUM(amount) FILTER (WHERE status='SUCCESS') AS returned,
   SUM(amount) FILTER (WHERE status IN ('PENDING','ONHOLD')) AS in_progress
 FROM refunds GROUP BY payment_fk
), facts AS (
 SELECT o.order_id,p.payment_id,p.gateway_reference,p.status AS payment_status,
   o.order_status,p.amount AS expected_amount,p.currency,p.captured_amount,p.captured_at,
   COALESCE(r.returned,0) AS returned,COALESCE(r.in_progress,0) AS in_progress,
   o.allocation_completed_at,o.automatic_full_refund,
   CASE
    WHEN p.captured_at IS NULL AND p.status IN ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED','REFUND_INITIATED') THEN 'UNVERIFIED_LEGACY_CAPTURE'
    WHEN p.captured_amount < COALESCE(r.returned,0)+COALESCE(r.in_progress,0) THEN 'REFUNDS_EXCEED_CAPTURE'
    WHEN p.captured_at IS NOT NULL AND o.order_status='CANCELLED' AND COALESCE(r.returned,0)<p.captured_amount THEN 'CANCELLED_CAPTURE_OWED'
    WHEN p.captured_at IS NOT NULL AND o.allocation_completed_at IS NULL AND NOT o.automatic_full_refund THEN 'CAPTURE_AWAITING_ALLOCATION'
    WHEN EXISTS(SELECT 1 FROM order_lines_snapshot l WHERE l.order_fk=o.id AND l.commission_rate_percent IS NULL) THEN 'UNKNOWN_LEGACY_COMMISSION'
   END AS issue
 FROM order_snapshot o JOIN payments p ON p.id=o.payment_fk
 LEFT JOIN refund_totals r ON r.payment_fk=p.id
)
SELECT * FROM facts WHERE issue IS NOT NULL ORDER BY captured_at NULLS FIRST,order_id;
-- Separate verified attempts preserve evidence of unusual multiple captures.
SELECT order_id,provider,provider_payment_id,amount,currency,captured_at
FROM gateway_payment_attempt WHERE captured_at IS NOT NULL
AND order_id IN(SELECT order_id FROM gateway_payment_attempt WHERE captured_at IS NOT NULL GROUP BY order_id HAVING COUNT(*)>1)
ORDER BY order_id,captured_at;
ROLLBACK;
