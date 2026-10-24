-- V6 source: additive capture evidence, deduplicated gateway inbox and attempts.
-- Never infer capture from completed_at. Legacy evidence is reconciled read-only first.
ALTER TABLE payments ADD COLUMN captured_amount numeric(15,2);
ALTER TABLE payments ADD COLUMN captured_at timestamp(6) with time zone;
ALTER TABLE order_snapshot ADD COLUMN allocation_completed_at timestamp(6) with time zone;
ALTER TABLE order_snapshot ADD COLUMN shipment_email_queued_at timestamp(6) with time zone;
ALTER TABLE order_snapshot ADD COLUMN automatic_full_refund boolean NOT NULL DEFAULT false;
ALTER TABLE payments ADD CONSTRAINT ck_verified_capture CHECK ((captured_amount IS NULL AND captured_at IS NULL) OR (captured_amount>=0 AND captured_at IS NOT NULL));
CREATE TABLE gateway_event_inbox (
 id varchar(64) PRIMARY KEY, order_id varchar(30) NOT NULL, event_json text NOT NULL,
 received_at timestamp(6) with time zone NOT NULL, processed_at timestamp(6) with time zone
);
CREATE INDEX idx_gateway_inbox_received ON gateway_event_inbox(received_at) WHERE processed_at IS NULL;
CREATE TABLE gateway_payment_attempt (
 id varchar(150) PRIMARY KEY,order_id varchar(30) NOT NULL,provider varchar(20) NOT NULL,
 provider_payment_id varchar(100) NOT NULL,status varchar(30) NOT NULL,amount numeric(15,2),currency varchar(3),
 captured_at timestamp(6) with time zone,updated_at timestamp(6) with time zone NOT NULL,
 CONSTRAINT uk_provider_attempt UNIQUE(provider,provider_payment_id),
 CONSTRAINT fk_gateway_attempt_order FOREIGN KEY(order_id) REFERENCES order_snapshot(order_id)
);
CREATE INDEX idx_gateway_attempt_order ON gateway_payment_attempt(order_id);
-- Full/partial refund obligations share a payment. Drop the legacy one-to-one constraint
-- by column semantics because both deployed schemas use different generated names.
DO $$ DECLARE entry record; BEGIN
 FOR entry IN SELECT c.conname FROM pg_constraint c
  WHERE c.conrelid='public.refunds'::regclass AND c.contype='u'
  AND c.conkey=ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid='public.refunds'::regclass AND attname='payment_fk')]::smallint[]
 LOOP EXECUTE format('ALTER TABLE public.refunds DROP CONSTRAINT %I',entry.conname); END LOOP;
END $$;
