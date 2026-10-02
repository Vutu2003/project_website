BEGIN;
SET LOCAL lock_timeout = '10s';
LOCK TABLE department, equipment, maintenance_coverage, service_provider, user_account,
    maintenance_plan, maintenance_plan_item, approval_request, approval_action,
    maintenance_execution, maintenance_progress_log, acceptance_record,
    maintenance_report, status_history, user_notification IN ACCESS EXCLUSIVE MODE;

-- Keep a transaction-local snapshot to assert every existing master row is preserved.
CREATE TEMP TABLE v3_preserved_master ON COMMIT DROP AS
SELECT 'department' AS kind, id, to_jsonb(d) AS row_data FROM department d
UNION ALL SELECT 'equipment', id, to_jsonb(e) FROM equipment e
UNION ALL SELECT 'maintenance_coverage', id, to_jsonb(c) FROM maintenance_coverage c
UNION ALL SELECT 'service_provider', id, to_jsonb(p) FROM service_provider p
UNION ALL SELECT 'user_account', id, to_jsonb(u) FROM user_account u;

DELETE FROM user_notification;
DELETE FROM status_history;
DELETE FROM maintenance_report;
DELETE FROM acceptance_record;
DELETE FROM maintenance_progress_log;
DELETE FROM maintenance_execution;
DELETE FROM approval_action;
DELETE FROM approval_request;
DELETE FROM maintenance_plan_item;
DELETE FROM maintenance_plan;

\ir v3_catalog.sql

DO $$
BEGIN
    IF EXISTS (
        SELECT kind, id, row_data FROM v3_preserved_master
        EXCEPT
        (SELECT 'department', id, to_jsonb(d) FROM department d
        UNION ALL SELECT 'equipment', id, to_jsonb(e) FROM equipment e
        UNION ALL SELECT 'maintenance_coverage', id, to_jsonb(c) FROM maintenance_coverage c
        UNION ALL SELECT 'service_provider', id, to_jsonb(p) FROM service_provider p
        UNION ALL SELECT 'user_account', id, to_jsonb(u) FROM user_account u)
    ) THEN
        RAISE EXCEPTION 'Existing master data was modified; cleanup rolled back';
    END IF;
END $$;
\ir ../tests/validate_v3_baseline.sql
COMMIT;
