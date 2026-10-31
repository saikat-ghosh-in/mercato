-- A tracking request never ships an item or changes inventory. Keep its identity
-- across HTTP retries so a timeout cannot create duplicate sandbox trackings.
CREATE TABLE shipment_tracking_drafts (
 id uuid PRIMARY KEY,
 order_fk bigint NOT NULL REFERENCES order_snapshot(id),
 request_body text NOT NULL,
 waybill varchar(80) NOT NULL UNIQUE,
 provider_reference varchar(80),
 state varchar(20) NOT NULL CHECK (state IN ('NEW','SENDING','READY','UNCONFIRMED','FAILED')),
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 CHECK (state <> 'READY' OR provider_reference IS NOT NULL)
);
