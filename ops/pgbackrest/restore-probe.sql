-- Explicit operator provisioning only; separate from application/Flyway schema.
CREATE SCHEMA IF NOT EXISTS backup_probe AUTHORIZATION postgres;
REVOKE ALL ON SCHEMA backup_probe FROM PUBLIC;
CREATE TABLE IF NOT EXISTS backup_probe.pitr_marker (
    marker text PRIMARY KEY CHECK (marker ~ '^[a-f0-9]{32}$'),
    committed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    source_machine_id text NOT NULL CHECK (source_machine_id ~ '^[a-fA-F0-9]{32}$'),
    counts jsonb NOT NULL
);
REVOKE ALL ON ALL TABLES IN SCHEMA backup_probe FROM PUBLIC;
