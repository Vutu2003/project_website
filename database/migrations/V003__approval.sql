BEGIN;

CREATE TABLE approval_request (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_approval_request PRIMARY KEY,
    request_type TEXT NOT NULL,
    plan_id BIGINT,
    plan_item_id BIGINT,
    proposed_provider_id BIGINT,
    rationale TEXT,
    warranty_impact_note TEXT,
    status TEXT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    submitted_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT fk_request_plan FOREIGN KEY (plan_id)
        REFERENCES maintenance_plan(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_request_item FOREIGN KEY (plan_item_id)
        REFERENCES maintenance_plan_item(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_request_provider FOREIGN KEY (proposed_provider_id)
        REFERENCES service_provider(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_request_creator FOREIGN KEY (created_by_user_id)
        REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_request_type CHECK (request_type IN ('PLAN_APPROVAL', 'VENDOR_SELECTION')),
    CONSTRAINT ck_request_status CHECK (status IN ('DRAFT', 'PENDING', 'DECIDED')),
    CONSTRAINT ck_request_subject CHECK (
        (request_type = 'PLAN_APPROVAL' AND plan_id IS NOT NULL AND plan_item_id IS NULL) OR
        (request_type = 'VENDOR_SELECTION' AND plan_id IS NULL AND plan_item_id IS NOT NULL)
    ),
    CONSTRAINT ck_request_plan_not_draft CHECK (request_type <> 'PLAN_APPROVAL' OR status <> 'DRAFT'),
    CONSTRAINT ck_request_submission CHECK (status = 'DRAFT' OR submitted_at IS NOT NULL),
    CONSTRAINT ck_request_resolution CHECK (status <> 'DECIDED' OR resolved_at IS NOT NULL),
    CONSTRAINT ck_request_vendor_proposal CHECK (
        request_type <> 'VENDOR_SELECTION' OR status = 'DRAFT' OR
        (proposed_provider_id IS NOT NULL AND NULLIF(BTRIM(rationale), '') IS NOT NULL)
    )
);

CREATE TABLE approval_action (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_approval_action PRIMARY KEY,
    request_id BIGINT NOT NULL CONSTRAINT uq_approval_action_request UNIQUE,
    actor_user_id BIGINT NOT NULL,
    outcome TEXT NOT NULL,
    comment TEXT,
    action_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_action_request FOREIGN KEY (request_id)
        REFERENCES approval_request(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_action_actor FOREIGN KEY (actor_user_id)
        REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_action_outcome CHECK (outcome IN ('APPROVE', 'REVISION_REQUIRED')),
    CONSTRAINT ck_action_revision_comment CHECK (outcome <> 'REVISION_REQUIRED' OR NULLIF(BTRIM(comment), '') IS NOT NULL)
);

COMMIT;
