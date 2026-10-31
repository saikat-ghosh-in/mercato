CREATE TABLE shipments (
 id uuid PRIMARY KEY, order_fk bigint NOT NULL REFERENCES order_snapshot(id),
 fulfillment_id varchar(30) NOT NULL, seller_email varchar(100) NOT NULL,
 waybill varchar(80) NOT NULL UNIQUE, status varchar(30) NOT NULL,
 handed_off_at timestamptz NOT NULL, delivered_at timestamptz, observed_at timestamptz, provider_updated_at timestamptz,
 last_checked_at timestamptz, last_requested_at timestamptz, next_tracking_at timestamptz, eta timestamptz,
 safe_message varchar(300), history text NOT NULL DEFAULT '[]', tracking_generation bigint NOT NULL DEFAULT 0,
 version bigint NOT NULL DEFAULT 0,
 CONSTRAINT ck_shipment_status CHECK(status IN ('SHIPPED','OUT_FOR_DELIVERY','DELIVERED'))
);
CREATE INDEX idx_shipments_order ON shipments(order_fk);
CREATE INDEX idx_shipments_active ON shipments(status,last_requested_at);
CREATE TABLE shipment_items (
 shipment_fk uuid NOT NULL REFERENCES shipments(id), line_fk bigint NOT NULL REFERENCES order_lines_snapshot(id),
 qty integer NOT NULL CHECK(qty>0), PRIMARY KEY(shipment_fk,line_fk)
);
