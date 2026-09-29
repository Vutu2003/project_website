BEGIN;

CREATE TABLE maintenance_execution (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_maintenance_execution PRIMARY KEY,
    plan_item_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    started_by_user_id BIGINT NOT NULL,
    attempt_no INTEGER NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    result_note TEXT,
    CONSTRAINT uq_execution_item_attempt UNIQUE (plan_item_id, attempt_no),
    CONSTRAINT fk_execution_item FOREIGN KEY (plan_item_id)
        REFERENCES maintenance_plan_item(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_execution_provider FOREIGN KEY (provider_id)
        REFERENCES service_provider(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_execution_starter FOREIGN KEY (started_by_user_id)
        REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_execution_attempt CHECK (attempt_no > 0),
    CONSTRAINT ck_execution_dates CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE TABLE maintenance_progress_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_maintenance_progress_log PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    recorded_by_user_id BIGINT NOT NULL,
    event_at TIMESTAMPTZ NOT NULL,
    work_note TEXT NOT NULL,
    damage_note TEXT,
    CONSTRAINT fk_progress_execution FOREIGN KEY (execution_id)
        REFERENCES maintenance_execution(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_progress_recorder FOREIGN KEY (recorded_by_user_id)
        REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_progress_work_note CHECK (BTRIM(work_note) <> '')
);

CREATE TABLE acceptance_record (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_acceptance_record PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    acceptance_type TEXT NOT NULL,
    result TEXT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    conclusion TEXT NOT NULL,
    recorded_by_user_id BIGINT NOT NULL,
    department_confirmed_by_user_id BIGINT,
    department_confirmed_at TIMESTAMPTZ,
    vtyt_confirmed_by_user_id BIGINT,
    vtyt_confirmed_at TIMESTAMPTZ,
    CONSTRAINT uq_acceptance_execution_type UNIQUE (execution_id, acceptance_type),
    CONSTRAINT fk_acceptance_execution FOREIGN KEY (execution_id)
        REFERENCES maintenance_execution(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_acceptance_recorder FOREIGN KEY (recorded_by_user_id)
        REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_acceptance_department_signer FOREIGN KEY (department_confirmed_by_user_id)
        REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_acceptance_vtyt_signer FOREIGN KEY (vtyt_confirmed_by_user_id)
        REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_acceptance_type CHECK (acceptance_type IN ('TECHNICAL_ACCEPTANCE', 'HANDOVER_ACCEPTANCE')),
    CONSTRAINT ck_acceptance_result CHECK (result IN ('PASS', 'FAIL')),
    CONSTRAINT ck_acceptance_conclusion CHECK (BTRIM(conclusion) <> ''),
    CONSTRAINT ck_acceptance_department_pair CHECK ((department_confirmed_by_user_id IS NULL) = (department_confirmed_at IS NULL)),
    CONSTRAINT ck_acceptance_vtyt_pair CHECK ((vtyt_confirmed_by_user_id IS NULL) = (vtyt_confirmed_at IS NULL)),
    CONSTRAINT ck_acceptance_handover_signers CHECK (
        acceptance_type <> 'HANDOVER_ACCEPTANCE' OR result <> 'PASS' OR
        (department_confirmed_by_user_id IS NOT NULL AND vtyt_confirmed_by_user_id IS NOT NULL)
    )
);

COMMIT;
