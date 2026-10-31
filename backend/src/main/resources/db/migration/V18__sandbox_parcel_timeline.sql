-- Sandbox simulation and courier display metadata are additive; no historical statuses are invented.
ALTER TABLE shipments ADD COLUMN tracking_id uuid;
ALTER TABLE shipments ADD COLUMN courier_name varchar(120);
ALTER TABLE shipments ADD COLUMN manual_tracking boolean NOT NULL DEFAULT false;
ALTER TABLE shipments ADD COLUMN simulation_stage integer NOT NULL DEFAULT 0 CHECK (simulation_stage BETWEEN 0 AND 3);
ALTER TABLE shipments ADD COLUMN tracking_lookup_page integer NOT NULL DEFAULT 1 CHECK (tracking_lookup_page > 0);
ALTER TABLE shipment_tracking_drafts ADD COLUMN tracking_id uuid;
ALTER TABLE shipment_tracking_drafts ADD COLUMN courier_name varchar(120);
