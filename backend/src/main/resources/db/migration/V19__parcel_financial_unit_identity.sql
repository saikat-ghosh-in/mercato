-- Record exact shipped monetary identities so lost/RTO refunds cannot consume another package's units.
-- Empty legacy mappings remain empty; application requires unambiguous evidence before refunding them.
ALTER TABLE shipment_items ADD COLUMN unit_ids text NOT NULL DEFAULT '[]';
ALTER TABLE shipments DROP CONSTRAINT ck_shipment_status;
ALTER TABLE shipments ADD CONSTRAINT ck_shipment_status CHECK (status IN ('SHIPPED','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERY_ATTEMPTED','EXCEPTION','LOST_IN_TRANSIT','RETURNED_TO_SENDER','DELIVERED'));
-- Checkpoint18 jobs recorded generation in their stable business key. Preserve the same key/payload
-- contract on upgrade while making that generation explicit for the new worker's fencing check.
DO $$
DECLARE job record; body jsonb;
BEGIN
 FOR job IN SELECT id,payload,business_key FROM durable_jobs WHERE job_type='EASYSHIP_SIMULATE'
  AND business_key ~ '^sandbox-sim:[0-9a-f-]{36}:[0-9]+:[1-3]$' LOOP
  BEGIN body := job.payload::jsonb;
  EXCEPTION WHEN invalid_text_representation THEN CONTINUE;
  END;
  IF jsonb_typeof(body)='object' AND NOT (body ? 'generation') THEN
   UPDATE durable_jobs SET payload=jsonb_set(body,'{generation}',to_jsonb(split_part(job.business_key,':',3)::bigint))::text WHERE id=job.id;
  END IF;
 END LOOP;
END $$;
