-- Unmodified authenticated bytes for forensic/re-verification purposes. No API exposes
-- these columns; outbox payloads continue to contain only normalized payment facts.
-- Historical/polled events have no fabricated webhook proof.
ALTER TABLE gateway_event_inbox ADD COLUMN verified_raw_body bytea;
ALTER TABLE gateway_event_inbox ADD COLUMN verified_signature text;
ALTER TABLE gateway_event_inbox ADD COLUMN verified_timestamp varchar(64);
ALTER TABLE gateway_event_inbox ADD CONSTRAINT gateway_event_complete_proof CHECK (
  (verified_raw_body IS NULL AND verified_signature IS NULL AND verified_timestamp IS NULL)
  OR (verified_raw_body IS NOT NULL AND verified_signature IS NOT NULL AND verified_timestamp IS NOT NULL
      AND octet_length(verified_raw_body) <= 262144)
);
