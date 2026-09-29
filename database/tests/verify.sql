\set ON_ERROR_STOP on
BEGIN;

CREATE FUNCTION pg_temp.assert_true(label TEXT, condition BOOLEAN) RETURNS VOID
LANGUAGE plpgsql AS $fn$
BEGIN
    IF condition IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'FAIL %', label;
    END IF;
    RAISE NOTICE 'PASS %', label;
END
$fn$;

CREATE FUNCTION pg_temp.expect_error(label TEXT, statement TEXT, expected_state TEXT) RETURNS VOID
LANGUAGE plpgsql AS $fn$
DECLARE observed_state TEXT;
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS observed_state = RETURNED_SQLSTATE;
        IF observed_state <> expected_state THEN
            RAISE EXCEPTION 'FAIL %: expected SQLSTATE %, got %', label, expected_state, observed_state;
        END IF;
        RAISE NOTICE 'PASS % [rejected: %]', label, observed_state;
        RETURN;
    END;
    RAISE EXCEPTION 'FAIL %: invalid operation succeeded', label;
END
$fn$;

DO $tests$
DECLARE
    vtyt BIGINT;
    director BIGINT;
    clinical_user BIGINT;
    dept BIGINT;
    provider BIGINT;
    seed_plan BIGINT;
    seed_item BIGINT;
    seed_request BIGINT;
    test_plan BIGINT;
    test_equipment BIGINT;
    test_item BIGINT;
    test_request BIGINT;
    test_execution BIGINT;
    test_report BIGINT;
    state_value TEXT;
    expected_tables TEXT[] := ARRAY[
        'department','user_account','equipment','service_provider','maintenance_coverage',
        'maintenance_plan','maintenance_plan_item','approval_request','approval_action',
        'maintenance_execution','maintenance_progress_log','acceptance_record',
        'maintenance_report','status_history'];
BEGIN
    SELECT id INTO vtyt FROM user_account WHERE username='demo_vtyt';
    SELECT id INTO director FROM user_account WHERE username='demo_bgd';
    SELECT id INTO clinical_user FROM user_account WHERE username='demo_khoa_noi';
    SELECT id INTO dept FROM department WHERE code='KHOA_NOI';
    SELECT id INTO provider FROM service_provider WHERE name='Đơn vị bảo trì hợp đồng demo';
    SELECT id INTO seed_plan FROM maintenance_plan WHERE title='DEMO — Kế hoạch bảo trì tháng 09/2026';
    SELECT id INTO seed_item FROM maintenance_plan_item WHERE plan_id=seed_plan ORDER BY id LIMIT 1;
    SELECT id INTO seed_request FROM approval_request WHERE request_type='VENDOR_SELECTION' LIMIT 1;
    PERFORM pg_temp.assert_true('demo prerequisite', vtyt IS NOT NULL AND director IS NOT NULL AND clinical_user IS NOT NULL AND seed_request IS NOT NULL);

    PERFORM pg_temp.assert_true('A: exactly 14 expected public tables',
        (SELECT COUNT(*)=14 AND ARRAY_AGG(table_name::TEXT ORDER BY table_name)=ARRAY(SELECT UNNEST(expected_tables) ORDER BY 1)
         FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'));
    PERFORM pg_temp.assert_true('A: removed tables absent',
        NOT EXISTS (SELECT 1 FROM information_schema.tables
                    WHERE table_schema='public' AND table_name = ANY(ARRAY['role','user_role','vendor_proposal','maintenance_assignment','acceptance_participant','attachment'])));
    PERFORM pg_temp.assert_true('A: exactly 117 frozen columns',
        (SELECT COUNT(*)=117 FROM information_schema.columns WHERE table_schema='public' AND table_name=ANY(expected_tables)));
    PERFORM pg_temp.assert_true('B: 14 primary keys and 31 foreign keys',
        (SELECT COUNT(*)=14 FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public' AND c.contype='p')
        AND (SELECT COUNT(*)=31 FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public' AND c.contype='f'));
    PERFORM pg_temp.assert_true('B: all FK delete/update actions are restrictive',
        (SELECT COUNT(*)=31 FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace
         WHERE n.nspname='public' AND c.contype='f' AND c.confdeltype IN ('r','a') AND c.confupdtype IN ('r','a')));
    PERFORM pg_temp.expect_error('B: duplicate department code',
        $$INSERT INTO department(code,name) VALUES('KHOA_NOI','Duplicate')$$,'23505');
    PERFORM pg_temp.expect_error('B: duplicate username',
        $$INSERT INTO user_account(role_code,username,password_hash,display_name) SELECT 'ADMIN','demo_vtyt',password_hash,'Duplicate' FROM user_account WHERE username='demo_vtyt'$$,'23505');
    PERFORM pg_temp.expect_error('B: invalid role code',
        $$INSERT INTO user_account(role_code,username,password_hash,display_name) SELECT 'VENDOR','TEST-BAD-ROLE',password_hash,'Invalid' FROM user_account WHERE username='demo_vtyt'$$,'23514');
    PERFORM pg_temp.expect_error('B: department role requires department FK',
        $$INSERT INTO user_account(role_code,username,password_hash,display_name) SELECT 'KHOA_PHONG','TEST-BAD-SCOPE',password_hash,'Invalid' FROM user_account WHERE username='demo_vtyt'$$,'23514');
    PERFORM pg_temp.expect_error('B: duplicate equipment code',
        format('INSERT INTO equipment(department_id,equipment_code,name) VALUES(%s,%L,%L)',dept,'DEMO-EQ-001','Duplicate'),'23505');
    PERFORM pg_temp.expect_error('B: invalid department FK',
        $$INSERT INTO equipment(department_id,equipment_code,name) VALUES(-999999,'TEST-BAD-FK','invalid')$$,'23503');
    INSERT INTO equipment(department_id,equipment_code,name) VALUES(dept,'TEST-EQ-VALID','Valid test equipment') RETURNING id INTO test_equipment;
    PERFORM pg_temp.assert_true('B: valid department FK', test_equipment IS NOT NULL);

    INSERT INTO maintenance_plan(title,period_start,period_end,created_by_user_id)
        VALUES('Verification plan',DATE '2026-10-01',DATE '2026-10-31',vtyt) RETURNING id INTO test_plan;
    FOR state_value IN SELECT UNNEST(ARRAY['DRAFT','SUBMITTED','REVISION_REQUIRED','APPROVED','IN_PROGRESS','AWAITING_REPORT','REPORTED','CLOSED']) LOOP
        UPDATE maintenance_plan SET status=state_value WHERE id=test_plan;
    END LOOP;
    PERFORM pg_temp.assert_true('C: all eight frozen plan states accepted',
        (SELECT status='CLOSED' FROM maintenance_plan WHERE id=test_plan));
    UPDATE maintenance_plan SET status='DRAFT' WHERE id=test_plan;
    PERFORM pg_temp.assert_true('C: valid plan state and zero default version',
        (SELECT status='DRAFT' AND version=0 FROM maintenance_plan WHERE id=test_plan));
    PERFORM pg_temp.expect_error('C: invalid plan state',
        format('UPDATE maintenance_plan SET status=%L WHERE id=%s','BAD_STATE',test_plan),'23514');
    PERFORM pg_temp.expect_error('C: reversed plan dates',
        format('UPDATE maintenance_plan SET period_end=%L WHERE id=%s','2026-09-01',test_plan),'23514');
    PERFORM pg_temp.expect_error('J: negative plan version',
        format('UPDATE maintenance_plan SET version=-1 WHERE id=%s',test_plan),'23514');

    INSERT INTO maintenance_plan_item(plan_id,equipment_id,department_id_at_plan)
        VALUES(test_plan,test_equipment,dept) RETURNING id INTO test_item;
    FOR state_value IN SELECT UNNEST(ARRAY['PLANNED','UNDER_CONTRACT','PENDING_PROPOSAL','WAITING_VENDOR_APPROVAL','ASSIGNED_EXTERNAL','IN_MAINTENANCE','AWAITING_TECHNICAL_ACCEPTANCE','AWAITING_HANDOVER','COMPLETED','REWORK_REQUIRED','REPAIR_REQUIRED']) LOOP
        UPDATE maintenance_plan_item SET status=state_value WHERE id=test_item;
    END LOOP;
    PERFORM pg_temp.assert_true('D: all eleven frozen item states accepted',
        (SELECT status='REPAIR_REQUIRED' FROM maintenance_plan_item WHERE id=test_item));
    UPDATE maintenance_plan_item SET status='PLANNED' WHERE id=test_item;
    PERFORM pg_temp.assert_true('D: valid item state and zero default version',
        (SELECT status='PLANNED' AND version=0 FROM maintenance_plan_item WHERE id=test_item));
    PERFORM pg_temp.expect_error('D: invalid item state',
        format('UPDATE maintenance_plan_item SET status=%L WHERE id=%s','BAD_STATE',test_item),'23514');
    PERFORM pg_temp.expect_error('J: negative item version',
        format('UPDATE maintenance_plan_item SET version=-1 WHERE id=%s',test_item),'23514');
    PERFORM pg_temp.expect_error('H: duplicate equipment in one plan',
        format('INSERT INTO maintenance_plan_item(plan_id,equipment_id,department_id_at_plan) VALUES(%s,%s,%s)',test_plan,test_equipment,dept),'23505');
    PERFORM pg_temp.expect_error('H: invalid assignment route',
        format('UPDATE maintenance_plan_item SET assignment_route=%L WHERE id=%s','OTHER',test_item),'23514');
    PERFORM pg_temp.expect_error('H: provider assignment requires route and coverage',
        format('UPDATE maintenance_plan_item SET assigned_provider_id=%s WHERE id=%s',provider,test_item),'23514');

    PERFORM pg_temp.assert_true('E: UNKNOWN, FREE and NOT_FREE are distinct stored values',
        (SELECT COUNT(DISTINCT classification)=3 FROM maintenance_coverage)
        AND (SELECT COUNT(*)>=1 FROM maintenance_coverage WHERE classification='UNKNOWN' AND verified_by_user_id IS NULL));
    PERFORM pg_temp.expect_error('E: invalid coverage classification',
        format('UPDATE maintenance_coverage SET classification=%L WHERE classification=%L','PAID','UNKNOWN'),'23514');
    PERFORM pg_temp.expect_error('E: reversed coverage dates',
        format('INSERT INTO maintenance_coverage(equipment_id,effective_from,effective_to) VALUES(%s,%L,%L)',test_equipment,'2026-12-31','2026-01-01'),'23514');
    PERFORM pg_temp.expect_error('E: FREE coverage needs a provider',
        format('INSERT INTO maintenance_coverage(equipment_id,classification,verified_by_user_id,verified_at,basis_note) VALUES(%s,%L,%s,NOW(),%L)',test_equipment,'FREE',vtyt,'basis'),'23514');
    PERFORM pg_temp.expect_error('E: verified classification needs evidence',
        format('INSERT INTO maintenance_coverage(equipment_id,classification) VALUES(%s,%L)',test_equipment,'NOT_FREE'),'23514');

    INSERT INTO approval_request(request_type,plan_id,status,created_by_user_id,submitted_at)
        VALUES('PLAN_APPROVAL',test_plan,'PENDING',vtyt,NOW()) RETURNING id INTO test_request;
    PERFORM pg_temp.assert_true('F: valid plan approval target', test_request IS NOT NULL);
    PERFORM pg_temp.expect_error('F: invalid approval request type',
        format('INSERT INTO approval_request(request_type,plan_id,status,created_by_user_id,submitted_at) VALUES(%L,%s,%L,%s,NOW())','OTHER',test_plan,'PENDING',vtyt),'23514');
    PERFORM pg_temp.expect_error('F: invalid approval request status',
        format('INSERT INTO approval_request(request_type,plan_id,status,created_by_user_id,submitted_at) VALUES(%L,%s,%L,%s,NOW())','PLAN_APPROVAL',test_plan,'OTHER',vtyt),'23514');
    PERFORM pg_temp.expect_error('F: plan approval cannot be draft',
        format('INSERT INTO approval_request(request_type,plan_id,status,created_by_user_id) VALUES(%L,%s,%L,%s)','PLAN_APPROVAL',test_plan,'DRAFT',vtyt),'23514');
    PERFORM pg_temp.expect_error('F: both approval targets forbidden',
        format('INSERT INTO approval_request(request_type,plan_id,plan_item_id,status,created_by_user_id,submitted_at) VALUES(%L,%s,%s,%L,%s,NOW())','PLAN_APPROVAL',test_plan,test_item,'PENDING',vtyt),'23514');
    PERFORM pg_temp.expect_error('F: no approval target forbidden',
        format('INSERT INTO approval_request(request_type,status,created_by_user_id,submitted_at) VALUES(%L,%L,%s,NOW())','PLAN_APPROVAL','PENDING',vtyt),'23514');
    PERFORM pg_temp.expect_error('F: pending plan request is conditionally unique',
        format('INSERT INTO approval_request(request_type,plan_id,status,created_by_user_id,submitted_at) VALUES(%L,%s,%L,%s,NOW())','PLAN_APPROVAL',test_plan,'PENDING',vtyt),'23505');
    INSERT INTO approval_request(request_type,plan_id,status,created_by_user_id,submitted_at,resolved_at)
        VALUES('PLAN_APPROVAL',test_plan,'DECIDED',vtyt,NOW(),NOW());
    INSERT INTO approval_request(request_type,plan_id,status,created_by_user_id,submitted_at,resolved_at)
        VALUES('PLAN_APPROVAL',test_plan,'DECIDED',vtyt,NOW(),NOW());
    PERFORM pg_temp.assert_true('F: multiple historical decided rounds allowed',
        (SELECT COUNT(*)=2 FROM approval_request WHERE plan_id=test_plan AND status='DECIDED'));
    PERFORM pg_temp.expect_error('F: submitted vendor request needs provider and rationale',
        format('INSERT INTO approval_request(request_type,plan_item_id,status,created_by_user_id,submitted_at) VALUES(%L,%s,%L,%s,NOW())','VENDOR_SELECTION',test_item,'PENDING',vtyt),'23514');
    INSERT INTO approval_request(request_type,plan_item_id,status,created_by_user_id)
        VALUES('VENDOR_SELECTION',test_item,'DRAFT',vtyt);
    PERFORM pg_temp.assert_true('F: vendor request may start as draft',
        (SELECT COUNT(*)=1 FROM approval_request WHERE plan_item_id=test_item AND status='DRAFT'));
    INSERT INTO approval_request(request_type,plan_item_id,proposed_provider_id,rationale,status,created_by_user_id,submitted_at)
        VALUES('VENDOR_SELECTION',test_item,provider,'Test provider reason','PENDING',vtyt,NOW());
    PERFORM pg_temp.expect_error('F: pending vendor request is conditionally unique',
        format('INSERT INTO approval_request(request_type,plan_item_id,proposed_provider_id,rationale,status,created_by_user_id,submitted_at) VALUES(%L,%s,%s,%L,%L,%s,NOW())','VENDOR_SELECTION',test_item,provider,'Another reason','PENDING',vtyt),'23505');
    PERFORM pg_temp.expect_error('G: invalid approval outcome',
        format('INSERT INTO approval_action(request_id,actor_user_id,outcome,action_at) VALUES(%s,%s,%L,NOW())',test_request,director,'REJECT'),'23514');
    PERFORM pg_temp.expect_error('G: duplicate terminal approval action',
        format('INSERT INTO approval_action(request_id,actor_user_id,outcome,action_at) VALUES(%s,%s,%L,NOW())',seed_request,director,'APPROVE'),'23505');
    PERFORM pg_temp.expect_error('G: revision decision needs comment',
        format('INSERT INTO approval_action(request_id,actor_user_id,outcome,action_at) VALUES(%s,%s,%L,NOW())',test_request,director,'REVISION_REQUIRED'),'23514');

    INSERT INTO status_history(plan_id,actor_user_id,new_state,action,action_timestamp)
        VALUES(test_plan,vtyt,'DRAFT','CREATE',NOW());
    INSERT INTO status_history(plan_item_id,actor_user_id,new_state,action,action_timestamp)
        VALUES(test_item,vtyt,'PLANNED','CREATE',NOW());
    PERFORM pg_temp.assert_true('I: typed plan and item histories accepted',
        (SELECT COUNT(*)=2 FROM status_history WHERE plan_id=test_plan OR plan_item_id=test_item));
    PERFORM pg_temp.expect_error('I: both history targets null',
        format('INSERT INTO status_history(actor_user_id,new_state,action,action_timestamp) VALUES(%s,%L,%L,NOW())',vtyt,'DRAFT','CREATE'),'23514');
    PERFORM pg_temp.expect_error('I: both history targets populated',
        format('INSERT INTO status_history(plan_id,plan_item_id,actor_user_id,new_state,action,action_timestamp) VALUES(%s,%s,%s,%L,%L,NOW())',test_plan,test_item,vtyt,'DRAFT','CREATE'),'23514');
    PERFORM pg_temp.expect_error('I: item history rejects plan-only state',
        format('INSERT INTO status_history(plan_item_id,actor_user_id,new_state,action,action_timestamp) VALUES(%s,%s,%L,%L,NOW())',test_item,vtyt,'APPROVED','CREATE'),'23514');
    PERFORM pg_temp.expect_error('I: invalid old state for item',
        format('INSERT INTO status_history(plan_item_id,actor_user_id,old_state,new_state,action,action_timestamp) VALUES(%s,%s,%L,%L,%L,NOW())',test_item,vtyt,'APPROVED','PLANNED','TEST'),'23514');
    PERFORM pg_temp.expect_error('I: repair history needs reason',
        format('INSERT INTO status_history(plan_item_id,actor_user_id,new_state,action,action_timestamp) VALUES(%s,%s,%L,%L,NOW())',test_item,vtyt,'REPAIR_REQUIRED','DAMAGE'),'23514');

    INSERT INTO maintenance_execution(plan_item_id,provider_id,started_by_user_id,attempt_no,started_at)
        VALUES(seed_item,provider,vtyt,1,NOW()) RETURNING id INTO test_execution;
    INSERT INTO maintenance_execution(plan_item_id,provider_id,started_by_user_id,attempt_no,started_at)
        VALUES(seed_item,provider,vtyt,2,NOW());
    PERFORM pg_temp.assert_true('K: multiple execution attempts are supported',
        (SELECT COUNT(*)=2 FROM maintenance_execution WHERE plan_item_id=seed_item));
    PERFORM pg_temp.expect_error('K: duplicate attempt number',
        format('INSERT INTO maintenance_execution(plan_item_id,provider_id,started_by_user_id,attempt_no,started_at) VALUES(%s,%s,%s,1,NOW())',seed_item,provider,vtyt),'23505');
    PERFORM pg_temp.expect_error('K: execution end precedes start',
        format('INSERT INTO maintenance_execution(plan_item_id,provider_id,started_by_user_id,attempt_no,started_at,ended_at) VALUES(%s,%s,%s,3,NOW(),NOW()-INTERVAL %L)',seed_item,provider,vtyt,'1 hour'),'23514');
    PERFORM pg_temp.expect_error('K: nonpositive attempt number',
        format('INSERT INTO maintenance_execution(plan_item_id,provider_id,started_by_user_id,attempt_no,started_at) VALUES(%s,%s,%s,0,NOW())',seed_item,provider,vtyt),'23514');
    INSERT INTO maintenance_progress_log(execution_id,recorded_by_user_id,event_at,work_note)
        VALUES(test_execution,vtyt,NOW(),'Test progress');
    PERFORM pg_temp.assert_true('K: progress log references execution',
        (SELECT COUNT(*)=1 FROM maintenance_progress_log WHERE execution_id=test_execution));
    PERFORM pg_temp.expect_error('K: blank progress note',
        format('INSERT INTO maintenance_progress_log(execution_id,recorded_by_user_id,event_at,work_note) VALUES(%s,%s,NOW(),%L)',test_execution,vtyt,'   '),'23514');
    PERFORM pg_temp.expect_error('K: invalid acceptance type',
        format('INSERT INTO acceptance_record(execution_id,acceptance_type,result,observed_at,conclusion,recorded_by_user_id) VALUES(%s,%L,%L,NOW(),%L,%s)',test_execution,'OTHER','PASS','test',vtyt),'23514');
    PERFORM pg_temp.expect_error('K: invalid acceptance result',
        format('INSERT INTO acceptance_record(execution_id,acceptance_type,result,observed_at,conclusion,recorded_by_user_id) VALUES(%s,%L,%L,NOW(),%L,%s)',test_execution,'TECHNICAL_ACCEPTANCE','OTHER','test',vtyt),'23514');
    INSERT INTO acceptance_record(execution_id,acceptance_type,result,observed_at,conclusion,recorded_by_user_id)
        VALUES(test_execution,'TECHNICAL_ACCEPTANCE','PASS',NOW(),'Technical pass',vtyt);
    PERFORM pg_temp.expect_error('K: one technical acceptance per attempt',
        format('INSERT INTO acceptance_record(execution_id,acceptance_type,result,observed_at,conclusion,recorded_by_user_id) VALUES(%s,%L,%L,NOW(),%L,%s)',test_execution,'TECHNICAL_ACCEPTANCE','FAIL','duplicate',vtyt),'23505');
    PERFORM pg_temp.expect_error('K: handover PASS requires two signers',
        format('INSERT INTO acceptance_record(execution_id,acceptance_type,result,observed_at,conclusion,recorded_by_user_id) VALUES(%s,%L,%L,NOW(),%L,%s)',test_execution,'HANDOVER_ACCEPTANCE','PASS','missing signers',vtyt),'23514');
    PERFORM pg_temp.expect_error('K: signer ID/time must be paired',
        format('INSERT INTO acceptance_record(execution_id,acceptance_type,result,observed_at,conclusion,recorded_by_user_id,department_confirmed_by_user_id) VALUES(%s,%L,%L,NOW(),%L,%s,%s)',test_execution,'HANDOVER_ACCEPTANCE','FAIL','missing time',vtyt,clinical_user),'23514');
    INSERT INTO acceptance_record(execution_id,acceptance_type,result,observed_at,conclusion,recorded_by_user_id,department_confirmed_by_user_id,department_confirmed_at,vtyt_confirmed_by_user_id,vtyt_confirmed_at)
        VALUES(test_execution,'HANDOVER_ACCEPTANCE','PASS',NOW(),'Handover pass',vtyt,clinical_user,NOW(),vtyt,NOW());
    PERFORM pg_temp.assert_true('K: valid technical and signed handover accepted',
        (SELECT COUNT(*)=2 FROM acceptance_record WHERE execution_id=test_execution));

    INSERT INTO maintenance_report(plan_id,created_by_user_id,report_date)
        VALUES(test_plan,vtyt,CURRENT_DATE) RETURNING id INTO test_report;
    PERFORM pg_temp.assert_true('L: draft report accepted',
        (SELECT status='DRAFT' FROM maintenance_report WHERE id=test_report));
    PERFORM pg_temp.expect_error('L: invalid report status',
        format('UPDATE maintenance_report SET status=%L WHERE id=%s','OTHER',test_report),'23514');
    PERFORM pg_temp.expect_error('L: one report per plan',
        format('INSERT INTO maintenance_report(plan_id,created_by_user_id,report_date) VALUES(%s,%s,CURRENT_DATE)',test_plan,vtyt),'23505');
    PERFORM pg_temp.expect_error('L: final report requires work_done and finalized_at',
        format('UPDATE maintenance_report SET status=%L WHERE id=%s','FINAL',test_report),'23514');
    PERFORM pg_temp.expect_error('M: referenced department cannot be deleted',
        format('DELETE FROM department WHERE id=%s',dept),'23503');
    PERFORM pg_temp.expect_error('M: referenced plan cannot be deleted',
        format('DELETE FROM maintenance_plan WHERE id=%s',seed_plan),'23503');
    PERFORM pg_temp.assert_true('indexes: required eight named indexes exist',
        (SELECT COUNT(*)=8 FROM pg_indexes WHERE schemaname='public' AND indexname=ANY(ARRAY[
            'ix_item_equipment_plan','ix_item_plan_status','ix_request_queue',
            'ux_request_pending_plan','ux_request_pending_item','ix_history_plan_time',
            'ix_history_item_time','ix_progress_execution_time'])));
    PERFORM pg_temp.assert_true('indexes: deferred candidates remain absent',
        NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname=ANY(ARRAY[
            'ix_coverage_equipment_dates','ix_equipment_department_serial'])));
END
$tests$;

ROLLBACK; -- All verification fixtures are discarded; seed state remains unchanged.
