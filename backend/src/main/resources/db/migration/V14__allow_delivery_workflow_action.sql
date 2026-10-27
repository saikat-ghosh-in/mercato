ALTER TABLE order_line_state_transitions DROP CONSTRAINT IF EXISTS order_line_state_transitions_action_check;
ALTER TABLE order_line_state_transitions ADD CONSTRAINT order_line_state_transitions_action_check
 CHECK (action IN ('CONFIRM','ACCEPT','START_PREPARING','MARK_PACKED','SHIP','DELIVER','CANCEL'));
