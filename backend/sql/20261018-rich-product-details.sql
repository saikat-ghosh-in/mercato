-- Additive schema expansion. Apply before deploying the rich-product backend.
-- Existing rows and basic product fields are preserved. Safe to re-run.
BEGIN;
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
COMMIT;
