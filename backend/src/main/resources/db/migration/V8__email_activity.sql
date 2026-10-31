-- Never copy message bodies, verification tokens, OTPs or SMTP credentials into this projection.
ALTER TABLE durable_jobs ADD COLUMN email_recipient varchar(320);
ALTER TABLE durable_jobs ADD COLUMN email_purpose varchar(80);
ALTER TABLE durable_jobs ADD COLUMN email_state varchar(24);
ALTER TABLE durable_jobs ADD COLUMN email_code varchar(64);
ALTER TABLE durable_jobs ADD COLUMN email_smtp_code integer;
ALTER TABLE durable_jobs ADD COLUMN email_accepted_at timestamptz;
ALTER TABLE durable_jobs ADD COLUMN email_attempt_at timestamptz;
CREATE INDEX ix_durable_jobs_email_activity ON durable_jobs(created_at DESC) WHERE email_purpose IS NOT NULL;
DO $$
DECLARE job record; body jsonb;
BEGIN
 FOR job IN SELECT id,payload,business_key,job_type,status FROM durable_jobs WHERE job_type IN ('AUTH_EMAIL','SHIPMENT_EMAIL') LOOP
  BEGIN body := job.payload::jsonb;
  EXCEPTION WHEN invalid_text_representation THEN body := '{}'::jsonb;
  END;
  UPDATE durable_jobs SET email_recipient=CASE WHEN jsonb_typeof(body->'to')='string' THEN left(body->>'to',320) ELSE NULL END,
   email_purpose=CASE WHEN job.business_key LIKE 'verify-email:%' THEN 'VERIFICATION'
   WHEN job.business_key LIKE 'password-reset:%' THEN 'PASSWORD_RESET' ELSE job.job_type END,
   email_state=CASE WHEN job.status='SUCCEEDED' THEN 'LEGACY_COMPLETED' ELSE 'QUEUED' END WHERE id=job.id;
 END LOOP;
END $$;
ALTER TABLE seller_notices ADD COLUMN email_failure_code varchar(64);
ALTER TABLE auth_email_challenges ADD COLUMN delivery_id uuid;
