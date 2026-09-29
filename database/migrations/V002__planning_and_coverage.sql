BEGIN;

CREATE TABLE maintenance_coverage (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_maintenance_coverage PRIMARY KEY,
    equipment_id BIGINT NOT NULL,
    provider_id BIGINT,
    contract_reference TEXT,
    coverage_scope TEXT,
    effective_from DATE,
    effective_to DATE,
    classification TEXT NOT NULL DEFAULT 'UNKNOWN',
    verified_by_user_id BIGINT,
    verified_at TIMESTAMPTZ,
    basis_note TEXT,
    CONSTRAINT fk_coverage_equipment FOREIGN KEY (equipment_id)
        REFERENCES equipment(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_coverage_provider FOREIGN KEY (provider_id)
        REFERENCES service_provider(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_coverage_verifier FOREIGN KEY (verified_by_user_id)
        REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_coverage_classification CHECK (classification IN ('UNKNOWN', 'FREE', 'NOT_FREE')),
    CONSTRAINT ck_coverage_dates CHECK (effective_from IS NULL OR effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_coverage_verified CHECK (
        classification = 'UNKNOWN' OR
        (verified_by_user_id IS NOT NULL AND verified_at IS NOT NULL AND NULLIF(BTRIM(basis_note), '') IS NOT NULL)
    ),
    CONSTRAINT ck_coverage_free_provider CHECK (classification <> 'FREE' OR provider_id IS NOT NULL)
);

CREATE TABLE maintenance_plan (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_maintenance_plan PRIMARY KEY,
    title TEXT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_plan_creator FOREIGN KEY (created_by_user_id)
        REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_plan_title_nonblank CHECK (BTRIM(title) <> ''),
    CONSTRAINT ck_plan_period CHECK (period_end >= period_start),
    CONSTRAINT ck_plan_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'REVISION_REQUIRED', 'APPROVED',
        'IN_PROGRESS', 'AWAITING_REPORT', 'REPORTED', 'CLOSED'
    )),
    CONSTRAINT ck_plan_version CHECK (version >= 0)
);

CREATE TABLE maintenance_plan_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_maintenance_plan_item PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    equipment_id BIGINT NOT NULL,
    department_id_at_plan BIGINT NOT NULL,
    planned_date DATE,
    status TEXT NOT NULL DEFAULT 'PLANNED',
    assigned_provider_id BIGINT,
    assignment_route TEXT,
    coverage_id BIGINT,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_plan_item_equipment UNIQUE (plan_id, equipment_id),
    CONSTRAINT fk_item_plan FOREIGN KEY (plan_id)
        REFERENCES maintenance_plan(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_item_equipment FOREIGN KEY (equipment_id)
        REFERENCES equipment(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_item_department_at_plan FOREIGN KEY (department_id_at_plan)
        REFERENCES department(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_item_provider FOREIGN KEY (assigned_provider_id)
        REFERENCES service_provider(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_item_coverage FOREIGN KEY (coverage_id)
        REFERENCES maintenance_coverage(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_item_status CHECK (status IN (
        'PLANNED', 'UNDER_CONTRACT', 'PENDING_PROPOSAL',
        'WAITING_VENDOR_APPROVAL', 'ASSIGNED_EXTERNAL', 'IN_MAINTENANCE',
        'AWAITING_TECHNICAL_ACCEPTANCE', 'AWAITING_HANDOVER',
        'COMPLETED', 'REWORK_REQUIRED', 'REPAIR_REQUIRED'
    )),
    CONSTRAINT ck_item_route CHECK (assignment_route IS NULL OR assignment_route IN ('UNDER_CONTRACT', 'EXTERNAL_APPROVED')),
    CONSTRAINT ck_item_assignment_fields CHECK (
        (assignment_route IS NULL AND assigned_provider_id IS NULL) OR
        (assignment_route IS NOT NULL AND assigned_provider_id IS NOT NULL AND coverage_id IS NOT NULL)
    ),
    CONSTRAINT ck_item_version CHECK (version >= 0)
);

COMMIT;
