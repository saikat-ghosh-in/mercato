-- Preserve historical unknown policy/evidence. Never manufacture a hold or capture from SUCCESS.
ALTER TABLE payments ADD COLUMN payment_policy varchar(30);
ALTER TABLE payments ADD COLUMN payment_policy_version integer;
ALTER TABLE payments ADD COLUMN capture_evidence_id varchar(64);
ALTER TABLE payments ADD COLUMN captured_at_provenance varchar(30);
ALTER TABLE payments ADD COLUMN capture_verified_at timestamp(6) with time zone;
ALTER TABLE payments ADD COLUMN provider_captured_at timestamp(6) with time zone;
ALTER TABLE payments ADD COLUMN provider_capture_time_source varchar(30);
ALTER TABLE payments ADD COLUMN authorization_id varchar(100);
ALTER TABLE payments ADD COLUMN authorized_amount numeric(15,2);
ALTER TABLE payments ADD COLUMN authorized_at timestamp(6) with time zone;
ALTER TABLE payments ADD COLUMN authorization_expires_at timestamp(6) with time zone;
ALTER TABLE payments ADD COLUMN financial_halt boolean NOT NULL DEFAULT false;
ALTER TABLE payments ADD COLUMN financial_halt_reason varchar(255);
ALTER TABLE payments ADD CONSTRAINT ck_payment_policy CHECK (payment_policy IN ('CAPTURE_ON_AUTH','DELAYED_CAPTURE'));
ALTER TABLE gateway_payment_attempt ADD COLUMN evidence_state varchar(30);
ALTER TABLE gateway_payment_attempt ADD COLUMN latest_evidence_id varchar(64);
ALTER TABLE gateway_payment_attempt ADD COLUMN capture_evidence_id varchar(64);
ALTER TABLE gateway_payment_attempt ADD COLUMN captured_at_provenance varchar(30);
ALTER TABLE gateway_payment_attempt ADD COLUMN capture_verified_at timestamp(6) with time zone;
ALTER TABLE gateway_payment_attempt ADD COLUMN provider_captured_at timestamp(6) with time zone;
ALTER TABLE gateway_payment_attempt ADD COLUMN provider_capture_time_source varchar(30);
ALTER TABLE gateway_payment_attempt ADD COLUMN evidence_source varchar(30);
ALTER TABLE gateway_payment_attempt ADD COLUMN api_version varchar(30);
ALTER TABLE gateway_payment_attempt ADD COLUMN authorization_id varchar(100);
ALTER TABLE gateway_payment_attempt ADD COLUMN authorized_amount numeric(15,2);
ALTER TABLE gateway_payment_attempt ADD COLUMN authorized_at timestamp(6) with time zone;
ALTER TABLE gateway_payment_attempt ADD COLUMN authorization_expires_at timestamp(6) with time zone;
ALTER TABLE gateway_payment_attempt ADD COLUMN captured_amount numeric(15,2);
ALTER TABLE gateway_payment_attempt ADD COLUMN evidence_reason varchar(255);
CREATE INDEX idx_attempt_evidence_attention ON gateway_payment_attempt(order_id,evidence_state)
 WHERE evidence_state IN ('INSUFFICIENT','CONFLICTING','AUTHORIZED');

-- Earlier readers recorded SUCCESS without retaining an explicit capture flag. Preserve claims,
-- but recheck them before dispatch. Existing policy remains NULL: no retroactive early capture.
UPDATE payments SET financial_halt=true,financial_halt_reason='LEGACY_CAPTURE_REQUIRES_RECONCILIATION'
 WHERE (captured_at IS NOT NULL OR status IN ('SUCCESS','REFUND_INITIATED','PARTIALLY_REFUNDED','REFUNDED'))
 AND capture_evidence_id IS NULL;
UPDATE payments SET captured_at_provenance='LEGACY_COMPATIBILITY' WHERE captured_at IS NOT NULL;
UPDATE gateway_payment_attempt SET captured_at_provenance='LEGACY_COMPATIBILITY' WHERE captured_at IS NOT NULL;
UPDATE payments SET financial_halt=true,financial_halt_reason='LEGACY_AUTHORIZATION_REQUIRES_REVIEW'
 WHERE status='AUTHORIZED' AND captured_at IS NULL;
INSERT INTO durable_jobs(id,job_type,business_key,payload,payload_version,status,attempts,due_at,failure_count,created_at,updated_at)
SELECT md5('reconcile-legacy-payment-evidence:'||p.payment_id)::uuid,'RECONCILE_ORDER',
 'reconcile-legacy-payment-evidence:'||p.payment_id,
 json_build_object('orderId',o.order_id,'version',1)::text,1,'READY',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
 FROM payments p JOIN order_snapshot o ON o.payment_fk=p.id
 WHERE p.financial_halt_reason IN ('LEGACY_CAPTURE_REQUIRES_RECONCILIATION','LEGACY_AUTHORIZATION_REQUIRES_REVIEW')
 ON CONFLICT (business_key) DO NOTHING;
