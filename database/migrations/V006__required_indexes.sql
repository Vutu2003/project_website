BEGIN;

-- UC12: equipment history across plans.
CREATE INDEX ix_item_equipment_plan ON maintenance_plan_item (equipment_id, plan_id);
-- UC08/UC11: status counts and item lists within a plan.
CREATE INDEX ix_item_plan_status ON maintenance_plan_item (plan_id, status);
-- UC04/UC07: director's pending approval queue.
CREATE INDEX ix_request_queue ON approval_request (status, request_type, submitted_at);
-- UC03/UC04: one active plan approval round per plan.
CREATE UNIQUE INDEX ux_request_pending_plan ON approval_request (plan_id)
    WHERE status = 'PENDING' AND request_type = 'PLAN_APPROVAL';
-- UC06/UC07: one active vendor decision round per item.
CREATE UNIQUE INDEX ux_request_pending_item ON approval_request (plan_item_id)
    WHERE status = 'PENDING' AND request_type = 'VENDOR_SELECTION';
-- BR05: chronology of plan and item transitions.
CREATE INDEX ix_history_plan_time ON status_history (plan_id, action_timestamp);
CREATE INDEX ix_history_item_time ON status_history (plan_item_id, action_timestamp);
-- UC08/UC12: chronology of notes within an attempt.
CREATE INDEX ix_progress_execution_time ON maintenance_progress_log (execution_id, event_at);

COMMIT;
