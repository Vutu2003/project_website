BEGIN;
DO $$
BEGIN
    IF (SELECT count(*) FROM user_account) <> 5 OR
       (SELECT count(*) FROM user_account WHERE username IN ('admin','vtyt','bgd','khoa_noi','khoa_ngoai') AND active) <> 5 THEN
        RAISE EXCEPTION 'Expected five intentional canonical accounts';
    END IF;
    IF EXISTS (SELECT 1 FROM user_account WHERE username LIKE 'demo_%' OR password_hash !~ '^\$2[aby]\$') THEN
        RAISE EXCEPTION 'Invalid canonical account names or hashes';
    END IF;
    IF (SELECT count(DISTINCT department_id) FROM user_account WHERE role_code='KHOA_PHONG') <> 2 THEN
        RAISE EXCEPTION 'Two department scopes required';
    END IF;
    IF (SELECT count(*) FROM equipment) <> 8 OR (SELECT count(*) FROM maintenance_plan) <> 2 OR
       EXISTS (SELECT 1 FROM maintenance_plan WHERE status <> 'REPORTED') THEN
        RAISE EXCEPTION 'Unexpected baseline equipment or plans';
    END IF;
    IF EXISTS (SELECT 1 FROM maintenance_coverage WHERE classification NOT IN ('FREE','NOT_FREE')) OR
       NOT EXISTS (SELECT 1 FROM maintenance_coverage WHERE classification='NOT_FREE') OR
       EXISTS (SELECT 1 FROM maintenance_coverage c LEFT JOIN service_provider p ON p.id=c.provider_id
          WHERE c.classification='FREE' AND (p.id IS NULL OR NOT p.active OR c.verified_at IS NULL OR c.basis_note IS NULL OR c.effective_to < CURRENT_DATE)) THEN
        RAISE EXCEPTION 'Coverage baseline invalid';
    END IF;
    IF (SELECT count(*) FROM maintenance_report WHERE status='FINAL') <> 2 OR
       (SELECT count(*) FROM acceptance_record WHERE acceptance_type='HANDOVER_ACCEPTANCE' AND result='PASS' AND vtyt_confirmed_by_user_id IS NOT NULL) <> 2 OR
       EXISTS (SELECT 1 FROM maintenance_plan_item WHERE status <> 'COMPLETED') THEN
        RAISE EXCEPTION 'Historical evidence incomplete';
    END IF;
    IF EXISTS (SELECT 1 FROM user_notification) OR EXISTS (SELECT 1 FROM approval_request WHERE status <> 'DECIDED') THEN
        RAISE EXCEPTION 'Unexpected initial workflow noise';
    END IF;
END $$;
ROLLBACK;
