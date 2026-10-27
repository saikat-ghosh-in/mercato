-- Quote revisions use the cart row mutex; child-only quantity edits must also advance them.
ALTER TABLE carts ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK (version>=0);
ALTER TABLE cart_items ADD COLUMN selected_retail_price numeric(10,2);
ALTER TABLE cart_items ADD COLUMN selected_discount_percent numeric(5,2);
ALTER TABLE cart_items ADD COLUMN selected_price_provenance varchar(40);
ALTER TABLE cart_items ADD COLUMN selection_generation varchar(36);
-- Lineage only: this does not infer a historical retail price, discount or physical unit.
UPDATE cart_items SET selection_generation=md5('legacy-cart-selection:'||cart_item_id::text);
ALTER TABLE cart_items ALTER COLUMN selection_generation SET NOT NULL;

ALTER TABLE order_snapshot ADD COLUMN customer_user_id varchar(100);
ALTER TABLE order_snapshot ADD COLUMN checkout_operation_id varchar(120);
ALTER TABLE order_snapshot ADD COLUMN checkout_request_body text;
ALTER TABLE order_snapshot ADD COLUMN financial_policy_version integer;
ALTER TABLE order_snapshot ADD COLUMN handling_charge numeric(15,2);
ALTER TABLE order_snapshot ADD CONSTRAINT uk_order_checkout_intent UNIQUE(customer_user_id,checkout_operation_id);
ALTER TABLE order_snapshot ADD CONSTRAINT ck_checkout_intent CHECK
 ((checkout_operation_id IS NULL AND checkout_request_body IS NULL) OR
 (customer_user_id IS NOT NULL AND length(checkout_operation_id)>0 AND checkout_request_body IS NOT NULL));
ALTER TABLE order_lines_snapshot ADD COLUMN source_cart_item_id bigint;
ALTER TABLE order_lines_snapshot ADD COLUMN source_selection_generation varchar(36);
-- No monetary units, new policy, current product prices or missing charge composition
-- are invented for historical orders. Their unknowns remain NULL for explicit review.
CREATE TABLE order_money_units (
 unit_id varchar(40) PRIMARY KEY,
 order_line_fk bigint NOT NULL REFERENCES order_lines_snapshot(id),
 unit_number integer NOT NULL CHECK (unit_number>0),
 financial_policy_version integer NOT NULL CHECK (financial_policy_version=1),
 net_merchandise_paise bigint NOT NULL CHECK (net_merchandise_paise>=0),
 retail_paise bigint,discount_paise bigint,discount_percent numeric(5,2),
 price_provenance varchar(40) NOT NULL,
 buyer_normal_delivery_paise bigint NOT NULL CHECK (buyer_normal_delivery_paise>=0),
 buyer_expedited_delivery_paise bigint NOT NULL CHECK (buyer_expedited_delivery_paise>=0),
 platform_paise bigint NOT NULL CHECK (platform_paise>=0),
 handling_paise bigint NOT NULL CHECK (handling_paise>=0),
 seller_delivery_paise bigint NOT NULL CHECK (seller_delivery_paise>=0),
 commission_rate_percent numeric(5,2),commission_cap_paise bigint,commission_paise bigint,
 commission_policy varchar(30) NOT NULL,
 CONSTRAINT uk_money_unit_line_number UNIQUE(order_line_fk,unit_number),
 CONSTRAINT ck_unit_price_provenance CHECK
 ((price_provenance='UNKNOWN' AND retail_paise IS NULL AND discount_paise IS NULL AND discount_percent IS NULL) OR
 (price_provenance='PRODUCT_PRICE_AT_SELECTION' AND retail_paise IS NOT NULL AND discount_paise IS NOT NULL AND discount_percent IS NOT NULL AND retail_paise>=net_merchandise_paise AND discount_paise=retail_paise-net_merchandise_paise AND discount_percent>=0 AND discount_percent<=100)),
 CONSTRAINT ck_unit_commission CHECK
 ((commission_rate_percent IS NULL AND commission_paise IS NULL AND (commission_cap_paise IS NULL OR commission_cap_paise>=0) AND commission_policy='UNKNOWN') OR
 (commission_rate_percent IS NOT NULL AND commission_paise IS NOT NULL AND commission_rate_percent>=0 AND commission_paise>=0 AND
 ((commission_policy='PER_UNIT_CAPPED' AND commission_cap_paise IS NOT NULL AND commission_cap_paise>=0 AND commission_paise<=commission_cap_paise) OR
 (commission_policy='LEGACY_AGGREGATE' AND commission_cap_paise IS NULL))))
);
CREATE OR REPLACE FUNCTION immutable_unit_money() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 RAISE EXCEPTION 'Checkout monetary unit snapshots are immutable';
END $$;
CREATE TRIGGER preserve_unit_money BEFORE UPDATE OR DELETE ON order_money_units FOR EACH ROW EXECUTE FUNCTION immutable_unit_money();
CREATE OR REPLACE FUNCTION immutable_checkout_intent() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF (OLD.checkout_operation_id IS NOT NULL AND
  (NEW.checkout_operation_id IS DISTINCT FROM OLD.checkout_operation_id OR NEW.checkout_request_body IS DISTINCT FROM OLD.checkout_request_body OR NEW.customer_user_id IS DISTINCT FROM OLD.customer_user_id)) OR
  (OLD.financial_policy_version IS NOT NULL AND
   (NEW.financial_policy_version IS DISTINCT FROM OLD.financial_policy_version OR NEW.handling_charge IS DISTINCT FROM OLD.handling_charge))
 THEN RAISE EXCEPTION 'Checkout intent and financial policy are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER preserve_checkout_intent BEFORE UPDATE ON order_snapshot FOR EACH ROW EXECUTE FUNCTION immutable_checkout_intent();
