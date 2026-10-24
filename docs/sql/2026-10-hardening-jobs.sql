-- ADDITIVE REVIEW SCRIPT ONLY. Not a Flyway baseline or evidence of the current OCI schema.
-- Reconcile against the actual PostgreSQL schema-only dump before versioning/releasing.
CREATE TABLE IF NOT EXISTS durable_jobs (
    id uuid PRIMARY KEY,
    job_type varchar(120) NOT NULL,
    business_key varchar(255) NOT NULL UNIQUE,
    payload text NOT NULL,
    payload_version integer NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('READY','RUNNING','WAITING','SUCCEEDED','NEEDS_ATTENTION')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    due_at timestamp with time zone NOT NULL,
    lease_owner varchar(120),
    lease_token uuid,
    lease_expires_at timestamp with time zone,
    failure_fingerprint varchar(64),
    failure_count integer NOT NULL DEFAULT 0 CHECK (failure_count >= 0),
    last_failure varchar(512),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CHECK ((status = 'RUNNING' AND lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'RUNNING' AND lease_owner IS NULL AND lease_token IS NULL AND lease_expires_at IS NULL))
);
CREATE INDEX IF NOT EXISTS ix_durable_jobs_due ON durable_jobs(status,due_at);
CREATE INDEX IF NOT EXISTS ix_durable_jobs_lease ON durable_jobs(status,lease_expires_at);
CREATE TABLE IF NOT EXISTS durable_job_retry_audit (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES durable_jobs(id),
    actor varchar(120) NOT NULL,
    reason varchar(500) NOT NULL,
    previous_status varchar(24) NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_job_retry_audit_job ON durable_job_retry_audit(job_id,created_at);
