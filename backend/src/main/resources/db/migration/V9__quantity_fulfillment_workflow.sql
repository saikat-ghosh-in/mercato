-- Expand the legacy generated enum constraint without editing historical migrations.
ALTER TABLE order_line_state_transitions DROP CONSTRAINT IF EXISTS order_line_state_transitions_action_check;
ALTER TABLE order_line_state_transitions ADD CONSTRAINT order_line_state_transitions_action_check
 CHECK (action IN ('CONFIRM','ACCEPT','START_PREPARING','MARK_PACKED','SHIP','CANCEL'));
ALTER TABLE order_snapshot ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE order_snapshot ADD COLUMN workflow_revision bigint NOT NULL DEFAULT 0;
ALTER TABLE order_snapshot ADD COLUMN workflow_version integer NOT NULL DEFAULT 2;
ALTER TABLE order_snapshot ADD COLUMN workflow_review_required boolean NOT NULL DEFAULT false;
ALTER TABLE order_snapshot ADD COLUMN workflow_review_reason varchar(255);
ALTER TABLE order_snapshot ADD COLUMN workflow_policy_adopted_at timestamp(6) with time zone;
ALTER TABLE order_snapshot ADD COLUMN confirmed_at timestamp(6) with time zone;
ALTER TABLE order_snapshot ADD COLUMN seller_actions_available_at timestamp(6) with time zone;
ALTER TABLE order_lines_snapshot ADD COLUMN preparing_qty integer NOT NULL DEFAULT 0;
ALTER TABLE order_lines_snapshot ADD COLUMN packed_qty integer NOT NULL DEFAULT 0;
ALTER TABLE order_lines_snapshot ADD COLUMN delivered_qty integer NOT NULL DEFAULT 0;
ALTER TABLE order_lines_snapshot ADD COLUMN workflow_baseline_ordered_qty integer;
ALTER TABLE order_lines_snapshot ADD COLUMN workflow_baseline_accepted_qty integer;
ALTER TABLE order_lines_snapshot ADD COLUMN workflow_baseline_at timestamp(6) with time zone;
-- Events prove only minimum earlier buckets; cancellation may have consumed accepted units.
-- Never invent delivered quantities or change historical shipped/cancelled counters.
WITH evidence AS (
 SELECT l.id,
  COALESCE(max(CASE WHEN t.action='ACCEPT' THEN GREATEST(COALESCE(t.qty_affected,1),0) END),0) AS accepted_minimum,
  COALESCE(sum(CASE WHEN t.action='SHIP' THEN GREATEST(COALESCE(t.qty_affected,1),0) ELSE 0 END),0) AS shipped_minimum
 FROM order_lines_snapshot l LEFT JOIN order_line_state_transitions t ON t.order_line_fk=l.id GROUP BY l.id
)
UPDATE order_lines_snapshot l SET
 accepted_qty=CASE WHEN l.accepted_qty<0 THEN l.accepted_qty ELSE GREATEST(COALESCE(l.accepted_qty,0),l.shipped_qty,e.accepted_minimum-l.cancelled_qty,e.shipped_minimum) END,
 preparing_qty=GREATEST(l.shipped_qty,e.shipped_minimum,0),packed_qty=GREATEST(l.shipped_qty,e.shipped_minimum,0)
FROM evidence e WHERE e.id=l.id;
ALTER TABLE order_lines_snapshot ALTER COLUMN accepted_qty SET NOT NULL;
UPDATE order_snapshot o SET confirmed_at=GREATEST(o.allocation_completed_at,p.captured_at),
 seller_actions_available_at=GREATEST(o.allocation_completed_at,p.captured_at)+interval '1 hour'
FROM payments p WHERE o.payment_fk=p.id AND o.allocation_completed_at IS NOT NULL AND p.captured_at IS NOT NULL AND p.captured_amount>0;
UPDATE order_snapshot o SET workflow_review_required=true,workflow_review_reason='Legacy confirmation/funding or quantities require review',workflow_version=1
WHERE (o.order_status <> 'CREATED' AND o.confirmed_at IS NULL) OR EXISTS (
 SELECT 1 FROM order_lines_snapshot l WHERE l.order_fk=o.id AND NOT
 (0<=l.delivered_qty AND l.delivered_qty<=l.shipped_qty AND l.shipped_qty<=l.packed_qty AND l.packed_qty<=l.preparing_qty AND l.preparing_qty<=l.accepted_qty AND l.accepted_qty<=l.ordered_qty-l.cancelled_qty AND l.cancelled_qty>=0));
-- Legacy obligations predate this SLA. Keep valid facts readable, but require explicit
-- policy adoption/reconciliation before quantity commands. No retroactive timeout jobs.
UPDATE order_snapshot SET workflow_version=1,workflow_review_required=true,
 workflow_review_reason=COALESCE(workflow_review_reason,'Legacy order requires prospective SLA adoption')
WHERE order_status<>'CREATED';
-- Review-marked legacy rows preserve their evidence; new and valid rows obey a hard database invariant.
ALTER TABLE order_lines_snapshot ADD COLUMN workflow_legacy_review boolean NOT NULL DEFAULT false;
UPDATE order_lines_snapshot l SET workflow_legacy_review=true FROM order_snapshot o WHERE l.order_fk=o.id AND o.workflow_review_required;
ALTER TABLE order_lines_snapshot ADD CONSTRAINT ck_workflow_quantities CHECK (workflow_legacy_review OR
 (0<=delivered_qty AND delivered_qty<=shipped_qty AND shipped_qty<=packed_qty AND packed_qty<=preparing_qty AND preparing_qty<=accepted_qty AND accepted_qty<=ordered_qty-cancelled_qty AND cancelled_qty>=0));
CREATE TABLE fulfillment_commands (
 order_id varchar(30) NOT NULL REFERENCES order_snapshot(order_id), operation_id varchar(120) NOT NULL,
 request_body text NOT NULL,outcome text NOT NULL,actor_type varchar(20) NOT NULL,actor_identity varchar(255) NOT NULL,
 reason varchar(500),reason_code varchar(50),fulfillment_id varchar(30),cancellation_ordinal_from integer,cancellation_ordinal_to integer,action varchar(30) NOT NULL,line_number integer NOT NULL,qty integer NOT NULL,
 unaccepted_qty integer NOT NULL,accepted_only_qty integer NOT NULL,preparing_only_qty integer NOT NULL,packed_only_qty integer NOT NULL,
 created_at timestamp(6) with time zone NOT NULL,PRIMARY KEY(order_id,operation_id));
CREATE TABLE fulfillment_work_items (
 order_id varchar(30) NOT NULL REFERENCES order_snapshot(order_id),kind varchar(50) NOT NULL,
 due_at timestamp(6) with time zone NOT NULL,observed_at timestamp(6) with time zone NOT NULL,
 affected_qty integer NOT NULL,resolved_at timestamp(6) with time zone,resolved_by varchar(255),resolution_reason varchar(500),PRIMARY KEY(order_id,kind));
CREATE OR REPLACE FUNCTION preserve_fulfillment_confirmation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF OLD.confirmed_at IS NOT NULL AND (NEW.confirmed_at IS DISTINCT FROM OLD.confirmed_at OR NEW.seller_actions_available_at IS DISTINCT FROM OLD.seller_actions_available_at) THEN RAISE EXCEPTION 'Fulfillment confirmation is immutable'; END IF;
 IF OLD.workflow_policy_adopted_at IS NOT NULL AND NEW.workflow_policy_adopted_at IS DISTINCT FROM OLD.workflow_policy_adopted_at THEN RAISE EXCEPTION 'Workflow adoption is immutable'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER immutable_fulfillment_confirmation BEFORE UPDATE ON order_snapshot FOR EACH ROW EXECUTE FUNCTION preserve_fulfillment_confirmation();

INSERT INTO fulfillment_work_items(order_id,kind,due_at,observed_at,affected_qty)
SELECT o.order_id,'LEGACY_WORKFLOW_REVIEW',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,
 COALESCE((SELECT sum(GREATEST(l.ordered_qty-l.cancelled_qty-l.shipped_qty,0)) FROM order_lines_snapshot l WHERE l.order_fk=o.id),0)
FROM order_snapshot o WHERE o.workflow_review_required;

ALTER TABLE order_snapshot ADD CONSTRAINT ck_workflow_revision CHECK (version>=0 AND workflow_revision>=0 AND workflow_version IN (1,2));
ALTER TABLE order_snapshot ADD CONSTRAINT ck_confirmation_release CHECK
 ((confirmed_at IS NULL AND seller_actions_available_at IS NULL) OR
 (confirmed_at IS NOT NULL AND seller_actions_available_at IS NOT NULL AND seller_actions_available_at=confirmed_at+interval '1 hour'));
CREATE OR REPLACE FUNCTION preserve_legacy_workflow_exemption() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='INSERT' AND NEW.workflow_legacy_review THEN RAISE EXCEPTION 'New quantity records cannot bypass validation'; END IF;
 IF TG_OP='UPDATE' AND OLD.workflow_baseline_at IS NOT NULL AND (NEW.workflow_baseline_at IS DISTINCT FROM OLD.workflow_baseline_at OR NEW.workflow_baseline_ordered_qty IS DISTINCT FROM OLD.workflow_baseline_ordered_qty OR NEW.workflow_baseline_accepted_qty IS DISTINCT FROM OLD.workflow_baseline_accepted_qty) THEN RAISE EXCEPTION 'Workflow adoption quantity baseline is immutable'; END IF;
 IF TG_OP='UPDATE' AND NOT OLD.workflow_legacy_review AND NEW.workflow_legacy_review THEN RAISE EXCEPTION 'Legacy exemption cannot be assigned to new records'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER legacy_workflow_exemption BEFORE INSERT OR UPDATE ON order_lines_snapshot FOR EACH ROW EXECUTE FUNCTION preserve_legacy_workflow_exemption();

ALTER TABLE order_lines_snapshot ADD CONSTRAINT ck_workflow_adoption_baseline CHECK
 ((workflow_baseline_at IS NULL AND workflow_baseline_ordered_qty IS NULL AND workflow_baseline_accepted_qty IS NULL) OR
 (workflow_baseline_at IS NOT NULL AND workflow_baseline_ordered_qty IS NOT NULL AND workflow_baseline_accepted_qty IS NOT NULL AND workflow_baseline_accepted_qty>=0 AND workflow_baseline_accepted_qty<=workflow_baseline_ordered_qty AND workflow_baseline_ordered_qty<=ordered_qty));
CREATE INDEX idx_fulfillment_commands_line_time ON fulfillment_commands(order_id,line_number,created_at);
CREATE INDEX idx_workflow_confirmed ON order_snapshot(confirmed_at) WHERE workflow_version=2 AND workflow_review_required=false;
