-- Retry restoration may explicitly authorize a replacement selection for this line.
-- Keep the original immutable quote lineage and all monetary units unchanged.
ALTER TABLE order_lines_snapshot ADD COLUMN retry_cart_item_id bigint;
ALTER TABLE order_lines_snapshot ADD COLUMN retry_selection_generation varchar(36);
ALTER TABLE order_lines_snapshot ADD CONSTRAINT ck_retry_selection_binding CHECK
 ((retry_cart_item_id IS NULL AND retry_selection_generation IS NULL) OR
  (retry_cart_item_id IS NOT NULL AND retry_selection_generation IS NOT NULL AND length(retry_selection_generation)>0));
-- No backfill or foreign key: the referenced bag item is legitimately deleted on
-- allocation/removal, and its ID/generation remain the recorded retry authorization.
