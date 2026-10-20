-- Apply after 2026-10-package-pricing.sql with API stopped and a backup available.
BEGIN;
ALTER TABLE products ADD COLUMN IF NOT EXISTS seller_pays_shipping boolean NOT NULL DEFAULT false;
ALTER TABLE order_lines_snapshot ADD COLUMN IF NOT EXISTS seller_delivery_charge numeric(12,2);
-- Historical orders retain NULL (no seller-funded delivery under their captured terms).
COMMIT;
