CREATE TABLE order_unit_states (
 unit_id varchar(40) PRIMARY KEY REFERENCES order_money_units(unit_id),
 stage varchar(20) NOT NULL CHECK(stage IN ('UNACCEPTED','ACCEPTED','PREPARING','PACKED','SHIPPED','CANCELLED','RETURNED')),
 cause varchar(40), operation_id varchar(120), shipped_at timestamp(6) with time zone, return_approved_at timestamp(6) with time zone,
 CHECK(stage <> 'RETURNED' OR (shipped_at IS NOT NULL AND return_approved_at IS NOT NULL))
);
-- Only a pristine, untouched line proves all monetary units are still unaccepted.
INSERT INTO order_unit_states(unit_id,stage)
 SELECT u.unit_id,'UNACCEPTED' FROM order_money_units u JOIN order_lines_snapshot l ON l.id=u.order_line_fk
 WHERE l.accepted_qty=0 AND l.preparing_qty=0 AND l.packed_qty=0 AND l.shipped_qty=0 AND l.cancelled_qty=0 AND l.delivered_qty=0
 AND NOT EXISTS(SELECT 1 FROM fulfillment_commands c WHERE c.order_id=(SELECT o.order_id FROM order_snapshot o WHERE o.id=l.order_fk) AND c.line_number=l.order_line_number);
ALTER TABLE refunds ADD COLUMN expected_payment_reference varchar(100);
CREATE TABLE refund_unit_allocations (
 id varchar(80) PRIMARY KEY, refund_fk bigint NOT NULL REFERENCES refunds(id), unit_id varchar(40) NOT NULL REFERENCES order_money_units(unit_id),
 merchandise_paise bigint NOT NULL CHECK(merchandise_paise>=0),delivery_paise bigint NOT NULL CHECK(delivery_paise>=0),
 platform_paise bigint NOT NULL CHECK(platform_paise>=0),handling_paise bigint NOT NULL CHECK(handling_paise>=0),cause varchar(40) NOT NULL,
 UNIQUE(refund_fk,unit_id)
);
CREATE INDEX idx_refund_alloc_unit ON refund_unit_allocations(unit_id);
