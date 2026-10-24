-- Explicit reconciliation of recorded feature migrations against the actual OCI V1.
-- Flyway supplies one transaction; historical BEGIN/COMMIT wrappers were removed.

-- Recorded prod/beta drift: canonicalize key order and remove redundant unique indexes.
-- These collection/join primary keys have no incoming foreign keys in either recorded schema.
ALTER TABLE product_secondary_images DROP CONSTRAINT product_secondary_images_pkey;
ALTER TABLE product_secondary_images ADD CONSTRAINT product_secondary_images_pkey PRIMARY KEY(product_id,image_order);
ALTER TABLE user_role DROP CONSTRAINT user_role_pkey;
ALTER TABLE user_role ADD CONSTRAINT user_role_pkey PRIMARY KEY(user_id,role_id);
ALTER TABLE cart_reservations DROP CONSTRAINT IF EXISTS cart_reservations_cart_item_fk_key;
ALTER TABLE carts DROP CONSTRAINT IF EXISTS carts_guest_token_key;
ALTER TABLE order_reservations DROP CONSTRAINT IF EXISTS order_reservations_order_line_fk_key;
ALTER TABLE order_snapshot DROP CONSTRAINT IF EXISTS order_snapshot_payment_fk_key;
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_role_name_key;
ALTER TABLE carts DROP CONSTRAINT IF EXISTS carts_user_fk_key;
ALTER TABLE carts DROP CONSTRAINT IF EXISTS ukh1qmekib8opln8dks61n3r6hl;
ALTER TABLE carts ADD CONSTRAINT uk_cart_user_fk UNIQUE(user_fk);
ALTER TABLE email_verification_tokens DROP CONSTRAINT IF EXISTS email_verification_tokens_token_key;
ALTER TABLE email_verification_tokens DROP CONSTRAINT IF EXISTS ukewmvysc7e9y6uy7og2c21axa9;
ALTER TABLE email_verification_tokens ADD CONSTRAINT uk_email_verification_token UNIQUE(token);
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_gateway_reference_key;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS uk9v7e32gxe6xobw4amlnq8pr7k;
ALTER TABLE payments ADD CONSTRAINT uk_payment_gateway_reference UNIQUE(gateway_reference);
-- Beta already has this enum-domain check; add the exact recorded expression to prod.
-- Unexpected legacy values cause a failed migration requiring investigation, never row deletion.
DO $$ BEGIN
  IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='payments'::regclass AND conname='payments_payment_method_check') THEN
    ALTER TABLE payments ADD CONSTRAINT payments_payment_method_check CHECK (((payment_method)::text = ANY ((ARRAY['UNKNOWN'::character varying, 'CARD'::character varying, 'UPI'::character varying, 'NET_BANKING'::character varying, 'WALLET'::character varying, 'CREDIT_CARD_EMI'::character varying, 'DEBIT_CARD_EMI'::character varying, 'CARDLESS_EMI'::character varying, 'PAY_LATER'::character varying])::text[])));
  END IF;
END $$;

-- Source: backend/sql/20261018-rich-product-details.sql
-- Additive schema expansion. Apply before deploying the rich-product backend.
-- Existing rows and basic product fields are preserved. Safe to re-run.
ALTER TABLE products ADD COLUMN IF NOT EXISTS brand varchar(100);
ALTER TABLE products ADD COLUMN IF NOT EXISTS product_code varchar(100);
ALTER TABLE products ADD COLUMN IF NOT EXISTS materials text;
ALTER TABLE products ADD COLUMN IF NOT EXISTS care text;
ALTER TABLE products ADD COLUMN IF NOT EXISTS warranty text;
ALTER TABLE products ADD COLUMN IF NOT EXISTS package_contents text;
ALTER TABLE products ADD COLUMN IF NOT EXISTS delivery_returns text;
CREATE TABLE IF NOT EXISTS product_highlights (
  product_id bigint NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  highlight_order integer NOT NULL,
  highlight varchar(300),
  PRIMARY KEY (product_id, highlight_order)
);
CREATE TABLE IF NOT EXISTS product_specifications (
  product_id bigint NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  spec_order integer NOT NULL,
  spec_label varchar(100) NOT NULL,
  spec_value varchar(500) NOT NULL,
  PRIMARY KEY (product_id, spec_order)
);

-- Source: docs/sql/2026-10-marketplace-journey.sql
-- Additive PostgreSQL migration. Back up first; deploy before the matching API.
ALTER TABLE carts ADD COLUMN IF NOT EXISTS delivery_method varchar(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS is_default boolean NOT NULL DEFAULT false;
-- Normalize legacy rows deterministically to one default per owner.
WITH ranked AS (
  SELECT id, row_number() OVER (PARTITION BY user_id ORDER BY is_default DESC, id) AS row_rank
  FROM addresses WHERE user_id IS NOT NULL
)
UPDATE addresses a SET is_default = (r.row_rank = 1) FROM ranked r WHERE a.id = r.id;
CREATE UNIQUE INDEX IF NOT EXISTS uk_address_default_owner ON addresses(user_id) WHERE is_default = true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS seller_status varchar(20);
-- Existing sellers retain their access; new seller applications explicitly start PENDING.
UPDATE users u SET seller_status = 'APPROVED'
WHERE seller_status IS NULL AND EXISTS (
  SELECT 1 FROM user_role ur JOIN roles r ON r.role_id = ur.role_id
  WHERE ur.user_id = u.id AND r.role_name = 'ROLE_SELLER'
);
ALTER TABLE order_snapshot ADD COLUMN IF NOT EXISTS delivery_method varchar(20);
ALTER TABLE order_snapshot ADD COLUMN IF NOT EXISTS delivery_estimate varchar(100);
ALTER TABLE order_lines_snapshot ADD COLUMN IF NOT EXISTS commission_rate_percent numeric(5,2);
-- Historical orders intentionally retain NULL for unknown delivery/commission snapshots.

CREATE TABLE IF NOT EXISTS seller_applications (
  id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  user_fk bigint NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  legal_business_name varchar(150) NOT NULL, business_type varchar(40) NOT NULL,
  pan varchar(10) NOT NULL, gstin varchar(15), tax_registration_type varchar(30) NOT NULL,
  enrolment_number varchar(30), pickup_address varchar(500) NOT NULL,
  pickup_state varchar(80) NOT NULL, pickup_pincode varchar(6) NOT NULL,
  support_email varchar(100) NOT NULL, product_categories varchar(300) NOT NULL,
  policy_version varchar(40) NOT NULL, submitted_at timestamptz NOT NULL,
  reviewed_at timestamptz, reviewed_by varchar(30), review_note varchar(1000)
);
CREATE TABLE IF NOT EXISTS commission_settings (id bigint PRIMARY KEY, rate numeric(5,2) NOT NULL);
INSERT INTO commission_settings(id,rate) VALUES(1,5.00) ON CONFLICT(id) DO NOTHING;
CREATE TABLE IF NOT EXISTS commission_changes (
  id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  previous_rate numeric(5,2) NOT NULL, rate numeric(5,2) NOT NULL,
  reason varchar(1000) NOT NULL, created_by varchar(30) NOT NULL,
  created_at timestamptz NOT NULL, effective_at timestamptz,
  status varchar(20) NOT NULL DEFAULT 'NOTIFYING'
);
CREATE TABLE IF NOT EXISTS seller_notices (
  id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  user_id varchar(30) NOT NULL, email varchar(100) NOT NULL,
  change_fk bigint NOT NULL REFERENCES commission_changes(id),
  event_type varchar(20) NOT NULL, created_at timestamptz NOT NULL,
  email_sent_at timestamptz, read_at timestamptz, next_attempt_at timestamptz,
  attempts integer NOT NULL DEFAULT 0, superseded boolean NOT NULL DEFAULT false,
  UNIQUE(user_id,change_fk,event_type)
);
CREATE INDEX IF NOT EXISTS idx_seller_notice_inbox ON seller_notices(user_id,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_seller_notice_outbox ON seller_notices(next_attempt_at,id) WHERE email_sent_at IS NULL AND superseded=false;
CREATE INDEX IF NOT EXISTS idx_commission_pending ON commission_changes(status);
ALTER TABLE products ADD COLUMN IF NOT EXISTS listing_status varchar(20);
ALTER TABLE products ADD COLUMN IF NOT EXISTS listing_review_note varchar(1000);
ALTER TABLE products ADD COLUMN IF NOT EXISTS listing_reviewed_at timestamptz;
ALTER TABLE products ADD COLUMN IF NOT EXISTS listing_reviewed_by varchar(255);
-- Null remains legacy-approved. All new seller requests explicitly use PENDING.
ALTER TABLE order_snapshot ADD COLUMN IF NOT EXISTS shipping_charge numeric(15,2);
ALTER TABLE order_snapshot ADD COLUMN IF NOT EXISTS platform_fee numeric(15,2);
-- Do not invent a fee split for historical charges.
CREATE TABLE IF NOT EXISTS seller_promotions (
  id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  request_id varchar(36) NOT NULL UNIQUE, seller_id varchar(255) NOT NULL,
  product_id varchar(30) NOT NULL, tier varchar(20) NOT NULL,
  duration_days integer NOT NULL CHECK(duration_days IN (3,7,14)),
  daily_rate numeric(12,2) NOT NULL CHECK(daily_rate>0),
  fee numeric(12,2) NOT NULL CHECK(fee>0),
  credited numeric(12,2) NOT NULL DEFAULT 0 CHECK(credited>=0 AND credited<=fee),
  starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL, status varchar(20) NOT NULL DEFAULT 'BOOKED',
  cancelled_at timestamptz, CHECK(ends_at>starts_at)
);
CREATE INDEX IF NOT EXISTS idx_promotion_window ON seller_promotions(starts_at,ends_at);
CREATE INDEX IF NOT EXISTS idx_promotion_seller ON seller_promotions(seller_id);

-- Source: docs/sql/2026-10-package-pricing.sql
-- Apply after 2026-10-marketplace-journey.sql, with the API stopped and a database backup.
ALTER TABLE products ADD COLUMN IF NOT EXISTS weight_kg numeric(8,3);
ALTER TABLE products ADD COLUMN IF NOT EXISTS length_cm numeric(8,2);
ALTER TABLE products ADD COLUMN IF NOT EXISTS width_cm numeric(8,2);
ALTER TABLE products ADD COLUMN IF NOT EXISTS height_cm numeric(8,2);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_product_package_measurements' AND conrelid = 'products'::regclass) THEN
    ALTER TABLE products ADD CONSTRAINT ck_product_package_measurements CHECK (
      (weight_kg IS NULL AND length_cm IS NULL AND width_cm IS NULL AND height_cm IS NULL)
      OR (weight_kg IS NOT NULL AND length_cm IS NOT NULL AND width_cm IS NOT NULL AND height_cm IS NOT NULL
        AND weight_kg BETWEEN 0.001 AND 1000 AND length_cm BETWEEN 0.01 AND 600
        AND width_cm BETWEEN 0.01 AND 600 AND height_cm BETWEEN 0.01 AND 600)
    );
  END IF;
END $$;
ALTER TABLE order_lines_snapshot ADD COLUMN IF NOT EXISTS commission_cap_per_item numeric(10,2);
-- Historical order snapshots intentionally remain unchanged and uncapped.
ALTER TABLE commission_settings ADD COLUMN IF NOT EXISTS pricing_revision integer;
-- Requested staging launch baseline. Never increase a lower rate or reset subsequent admin changes.
UPDATE commission_settings SET rate = LEAST(rate, 0.50), pricing_revision = 2
WHERE COALESCE(pricing_revision, 0) < 2;

-- Source: docs/sql/2026-10-seller-delivery.sql
-- Apply after 2026-10-package-pricing.sql with API stopped and a backup available.
ALTER TABLE products ADD COLUMN IF NOT EXISTS seller_pays_shipping boolean NOT NULL DEFAULT false;
ALTER TABLE order_lines_snapshot ADD COLUMN IF NOT EXISTS seller_delivery_charge numeric(12,2);
-- Historical orders retain NULL (no seller-funded delivery under their captured terms).

-- Source: docs/sql/2026-10-listing-request-history.sql
-- Apply after earlier marketplace migrations, with the API stopped and a backup available.
CREATE TABLE IF NOT EXISTS listing_requests (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    seller_id varchar(30) NOT NULL,
    product_id varchar(30) NOT NULL,
    product_name varchar(255) NOT NULL,
    category_name varchar(255) NOT NULL,
    price numeric(15,2) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','SUPERSEDED','WITHDRAWN')),
    requested_at timestamp with time zone NOT NULL,
    reviewed_at timestamp with time zone,
    reviewed_by varchar(30),
    note varchar(1000),
    imported boolean NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS idx_listing_request_seller ON listing_requests(seller_id,requested_at);
CREATE INDEX IF NOT EXISTS idx_listing_request_product ON listing_requests(product_id,status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_listing_request_open ON listing_requests(product_id) WHERE status='PENDING';
-- Import only the known current moderation state, never invent prior submissions.
INSERT INTO listing_requests(seller_id,product_id,product_name,category_name,price,status,requested_at,reviewed_at,reviewed_by,note,imported)
SELECT u.user_id,p.product_id,p.product_name,c.category_name,p.selling_price,p.listing_status,p.created_at,p.listing_reviewed_at,p.listing_reviewed_by,p.listing_review_note,true
FROM products p JOIN users u ON u.id=p.seller_fk JOIN categories c ON c.id=p.category_id
WHERE p.listing_status IN ('PENDING','APPROVED','REJECTED')
AND coalesce(p.listing_review_note,'') <> 'Created directly by an administrator.'
AND NOT EXISTS(SELECT 1 FROM listing_requests r WHERE r.product_id=p.product_id);

-- Source: docs/sql/2026-10-account-access.sql
-- Additive PostgreSQL migration. Apply before starting the new application.
-- Stop old application instances first: their retired cleanup job could delete disabled accounts.
ALTER TABLE users ADD COLUMN IF NOT EXISTS self_deactivated boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS seller_suspended boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS access_version bigint NOT NULL DEFAULT 0;

-- Existing disabled accounts stay disabled; do not infer that they voluntarily paused.
-- Account retention replaces the former seven-day automatic purge.
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.role_id FROM users u CROSS JOIN roles r
WHERE r.role_name = 'ROLE_USER'
  AND NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id=u.id AND ur.role_id=r.role_id);

CREATE TABLE IF NOT EXISTS account_access_events (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    subject_id varchar(30) NOT NULL,
    actor_id varchar(30) NOT NULL,
    actor_username varchar(20) NOT NULL,
    action varchar(40) NOT NULL,
    reason varchar(500) NOT NULL,
    previous_state varchar(160) NOT NULL,
    next_state varchar(160) NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_access_subject_time ON account_access_events(subject_id, created_at);

-- Use a migration owner distinct from the application role. Grant the application SELECT/INSERT
-- on account_access_events and usage of its identity sequence; withhold UPDATE/DELETE on this table.
-- No cascading FK: access decisions retain their actor/subject identifiers independently.
