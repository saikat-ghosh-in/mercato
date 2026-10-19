-- Apply after 2026-10-marketplace-journey.sql, with the API stopped and a database backup.
BEGIN;
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
COMMIT;
