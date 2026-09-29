BEGIN;

CREATE TABLE maintenance_report (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_maintenance_report PRIMARY KEY,
    plan_id BIGINT NOT NULL CONSTRAINT uq_report_plan UNIQUE,
    created_by_user_id BIGINT NOT NULL,
    report_number TEXT,
    report_date DATE NOT NULL,
    work_done TEXT,
    achieved TEXT,
    not_achieved TEXT,
    causes TEXT,
    next_work TEXT,
    resolutions TEXT,
    recommendations TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    finalized_at TIMESTAMPTZ,
    CONSTRAINT fk_report_plan FOREIGN KEY (plan_id)
        REFERENCES maintenance_plan(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_report_creator FOREIGN KEY (created_by_user_id)
        REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_report_status CHECK (status IN ('DRAFT', 'FINAL')),
    CONSTRAINT ck_report_final_fields CHECK (
        status <> 'FINAL' OR (finalized_at IS NOT NULL AND NULLIF(BTRIM(work_done), '') IS NOT NULL)
    )
);

CREATE TABLE status_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_status_history PRIMARY KEY,
    plan_id BIGINT,
    plan_item_id BIGINT,
    actor_user_id BIGINT NOT NULL,
    old_state TEXT,
    new_state TEXT NOT NULL,
    action TEXT NOT NULL,
    reason TEXT,
    action_timestamp TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_history_plan FOREIGN KEY (plan_id)
        REFERENCES maintenance_plan(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_history_item FOREIGN KEY (plan_item_id)
        REFERENCES maintenance_plan_item(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_history_actor FOREIGN KEY (actor_user_id)
        REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_history_target CHECK ((plan_id IS NULL) <> (plan_item_id IS NULL)),
    CONSTRAINT ck_history_action_nonblank CHECK (BTRIM(action) <> ''),
    CONSTRAINT ck_history_target_states CHECK (
        (plan_id IS NOT NULL
         AND new_state IN ('DRAFT', 'SUBMITTED', 'REVISION_REQUIRED', 'APPROVED', 'IN_PROGRESS', 'AWAITING_REPORT', 'REPORTED', 'CLOSED')
         AND (old_state IS NULL OR old_state IN ('DRAFT', 'SUBMITTED', 'REVISION_REQUIRED', 'APPROVED', 'IN_PROGRESS', 'AWAITING_REPORT', 'REPORTED', 'CLOSED')))
        OR
        (plan_item_id IS NOT NULL
         AND new_state IN ('PLANNED', 'UNDER_CONTRACT', 'PENDING_PROPOSAL', 'WAITING_VENDOR_APPROVAL', 'ASSIGNED_EXTERNAL', 'IN_MAINTENANCE', 'AWAITING_TECHNICAL_ACCEPTANCE', 'AWAITING_HANDOVER', 'COMPLETED', 'REWORK_REQUIRED', 'REPAIR_REQUIRED')
         AND (old_state IS NULL OR old_state IN ('PLANNED', 'UNDER_CONTRACT', 'PENDING_PROPOSAL', 'WAITING_VENDOR_APPROVAL', 'ASSIGNED_EXTERNAL', 'IN_MAINTENANCE', 'AWAITING_TECHNICAL_ACCEPTANCE', 'AWAITING_HANDOVER', 'COMPLETED', 'REWORK_REQUIRED', 'REPAIR_REQUIRED')))
    ),
    CONSTRAINT ck_history_reason_for_exception CHECK (
        new_state NOT IN ('REVISION_REQUIRED', 'REWORK_REQUIRED', 'REPAIR_REQUIRED') OR
        NULLIF(BTRIM(reason), '') IS NOT NULL
    )
);

COMMIT;
