-- Read-only assertions for a clean V3 starting point, before human E2E testing.
DO $$
DECLARE
    table_name text;
    row_count bigint;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['maintenance_plan','maintenance_plan_item','approval_request',
        'approval_action','maintenance_execution','maintenance_progress_log','acceptance_record',
        'maintenance_report','status_history','user_notification'] LOOP
        EXECUTE format('SELECT count(*) FROM %I', table_name) INTO row_count;
        IF row_count <> 0 THEN
            RAISE EXCEPTION 'Expected empty workflow table %, found % rows', table_name, row_count;
        END IF;
    END LOOP;
    IF (SELECT count(*) FROM department WHERE code IN ('KHOA_NOI','KHOA_NGOAI','PHONG_VTYT',
        'HOI_SUC','CHAN_DOAN_HINH_ANH','KHOA_NHI','KHOA_SAN','CAP_CUU','XET_NGHIEM','THAN_NHAN_TAO') AND active) <> 10 THEN
        RAISE EXCEPTION 'Expected ten active baseline departments';
    END IF;
    IF (SELECT count(*) FROM equipment WHERE equipment_code IN
        (SELECT 'TB-' || lpad(n::text,3,'0') FROM generate_series(1,32) n) AND active) <> 32 OR
       (SELECT count(DISTINCT e.department_id) FROM equipment e JOIN department d ON d.id=e.department_id
        WHERE e.active AND d.active) < 9 THEN
        RAISE EXCEPTION 'Expected 32 baseline equipment across nine clinical departments';
    END IF;
    IF (SELECT count(*) FROM user_account WHERE username IN ('admin','vtyt','bgd','khoa_noi','khoa_ngoai') AND active) <> 5 OR
       EXISTS (SELECT 1 FROM user_account WHERE password_hash !~ '^\$2[aby]\$') OR
       (SELECT count(DISTINCT department_id) FROM user_account WHERE role_code='KHOA_PHONG' AND active) < 2 THEN
        RAISE EXCEPTION 'Canonical accounts or department scopes invalid';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM maintenance_coverage WHERE classification='FREE') OR
       NOT EXISTS (SELECT 1 FROM maintenance_coverage WHERE classification='NOT_FREE') OR
       EXISTS (SELECT 1 FROM maintenance_coverage WHERE classification NOT IN ('FREE','NOT_FREE')) OR
       EXISTS (SELECT 1 FROM maintenance_coverage c
           LEFT JOIN service_provider p ON p.id=c.provider_id
           LEFT JOIN user_account u ON u.id=c.verified_by_user_id
           WHERE c.classification='FREE' AND (p.id IS NULL OR NOT p.active OR u.role_code IS DISTINCT FROM 'PHONG_VTYT'
               OR c.verified_at IS NULL OR NULLIF(btrim(c.basis_note),'') IS NULL
               OR c.effective_from > CURRENT_DATE OR c.effective_to < CURRENT_DATE)) THEN
        RAISE EXCEPTION 'FREE/NOT_FREE baseline invalid';
    END IF;
END $$;
