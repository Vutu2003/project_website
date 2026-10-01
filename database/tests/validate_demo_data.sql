\set ON_ERROR_STOP on
BEGIN;
CREATE FUNCTION pg_temp.assert_demo(label TEXT, condition BOOLEAN) RETURNS VOID
LANGUAGE plpgsql AS $fn$
BEGIN
    IF condition IS DISTINCT FROM TRUE THEN RAISE EXCEPTION 'FAIL %', label; END IF;
    RAISE NOTICE 'PASS %', label;
END
$fn$;

DO $validate$
DECLARE
    expected_count INTEGER;
    actual_count INTEGER;
BEGIN
    -- 1. Dataset size and inherited schema remain exact.
    FOR expected_count, actual_count IN
        SELECT expected, actual FROM (VALUES
            (8,(SELECT COUNT(*) FROM department)),
            (15,(SELECT COUNT(*) FROM user_account)),
            (40,(SELECT COUNT(*) FROM equipment)),
            (7,(SELECT COUNT(*) FROM service_provider)),
            (35,(SELECT COUNT(*) FROM maintenance_coverage)),
            (8,(SELECT COUNT(*) FROM maintenance_plan)),
            (52,(SELECT COUNT(*) FROM maintenance_plan_item)),
            (19,(SELECT COUNT(*) FROM approval_request)),
            (15,(SELECT COUNT(*) FROM approval_action)),
            (30,(SELECT COUNT(*) FROM maintenance_execution)),
            (90,(SELECT COUNT(*) FROM maintenance_progress_log)),
            (40,(SELECT COUNT(*) FROM acceptance_record)),
            (4,(SELECT COUNT(*) FROM maintenance_report)),
            (240,(SELECT COUNT(*) FROM status_history))
        ) AS counts(expected,actual)
    LOOP
        IF expected_count <> actual_count THEN RAISE EXCEPTION 'FAIL dataset count: expected %, actual %', expected_count, actual_count; END IF;
    END LOOP;
    PERFORM pg_temp.assert_demo('01 exact medium dataset counts', TRUE);
    PERFORM pg_temp.assert_demo('02 14 tables / 118 columns / 31 validated FKs',
        (SELECT COUNT(*)=14 FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history') AND
        (SELECT COUNT(*)=118 FROM information_schema.columns WHERE table_schema='public' AND table_name<>'flyway_schema_history') AND
        (SELECT COUNT(*)=31 FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public' AND c.contype='f' AND c.convalidated));

    -- 3. All synthetic business keys and department scopes are coherent.
    PERFORM pg_temp.assert_demo('03 no duplicate frozen business keys',
        NOT EXISTS (SELECT 1 FROM department GROUP BY code HAVING COUNT(*)>1) AND
        NOT EXISTS (SELECT 1 FROM user_account GROUP BY username HAVING COUNT(*)>1) AND
        NOT EXISTS (SELECT 1 FROM equipment GROUP BY equipment_code HAVING COUNT(*)>1) AND
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item GROUP BY plan_id,equipment_id HAVING COUNT(*)>1) AND
        NOT EXISTS (SELECT 1 FROM maintenance_execution GROUP BY plan_item_id,attempt_no HAVING COUNT(*)>1));
    PERFORM pg_temp.assert_demo('04 one clinical user per department and correct item scope',
        (SELECT COUNT(*)=7 FROM user_account WHERE role_code='KHOA_PHONG') AND
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item i JOIN equipment e ON e.id=i.equipment_id
                    WHERE i.department_id_at_plan<>e.department_id));
    PERFORM pg_temp.assert_demo('05 equipment/model labels use public list or DEMO marker',
        NOT EXISTS (SELECT 1 FROM equipment WHERE model NOT LIKE 'DEMO-%' AND model NOT IN (
            'Nihon Kohden Cardiolife TEC-8300','Philips IntelliVue MX450',
            'B. Braun Spaceplus Perfusor','B. Braun Spaceplus Infusomat',
            'Dräger Evita V600','GE HealthCare Venue Go',
            'Roche cobas c 311','Sysmex XN-1000')));

    -- 6. Coverage distinguishes missing/unknown from verified free/non-free.
    PERFORM pg_temp.assert_demo('06 FREE/NOT_FREE/UNKNOWN distribution',
        (SELECT COUNT(*)=16 FROM maintenance_coverage WHERE classification='FREE') AND
        (SELECT COUNT(*)=14 FROM maintenance_coverage WHERE classification='NOT_FREE') AND
        (SELECT COUNT(*)=5 FROM maintenance_coverage WHERE classification='UNKNOWN'));
    PERFORM pg_temp.assert_demo('07 verified FREE and NOT_FREE have evidence',
        NOT EXISTS (SELECT 1 FROM maintenance_coverage c WHERE c.classification IN ('FREE','NOT_FREE')
                    AND (c.verified_by_user_id IS NULL OR c.verified_at IS NULL OR NULLIF(BTRIM(c.basis_note),'') IS NULL
                         OR (c.classification='FREE' AND c.provider_id IS NULL))));
    PERFORM pg_temp.assert_demo('08 UNKNOWN coverage never creates an assignment route',
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item i JOIN maintenance_coverage c ON c.id=i.coverage_id
                    WHERE c.classification='UNKNOWN' AND (i.assignment_route IS NOT NULL OR i.assigned_provider_id IS NOT NULL)) AND
        EXISTS (SELECT 1 FROM maintenance_plan_item i JOIN maintenance_coverage c ON c.id=i.coverage_id
                WHERE c.classification='UNKNOWN' AND i.status='PLANNED'));
    PERFORM pg_temp.assert_demo('09 coverage matches item equipment and provider route',
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item i JOIN maintenance_coverage c ON c.id=i.coverage_id
                    WHERE c.equipment_id<>i.equipment_id OR
                          (i.assignment_route='UNDER_CONTRACT' AND (c.classification<>'FREE' OR c.provider_id<>i.assigned_provider_id)) OR
                          (i.assignment_route='EXTERNAL_APPROVED' AND c.classification<>'NOT_FREE')));
    PERFORM pg_temp.assert_demo('10 verified coverage predates route decision',
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item i JOIN maintenance_coverage c ON c.id=i.coverage_id
                    JOIN status_history h ON h.plan_item_id=i.id AND h.new_state IN ('UNDER_CONTRACT','PENDING_PROPOSAL')
                    WHERE c.verified_at IS NULL OR c.verified_at>h.action_timestamp OR
                          (c.effective_from IS NOT NULL AND c.effective_from>h.action_timestamp::date) OR
                          (c.effective_to IS NOT NULL AND c.effective_to<h.action_timestamp::date)));

    -- 11. Plan periods and status distribution.
    PERFORM pg_temp.assert_demo('11 all eight plan states represented once',
        (SELECT COUNT(DISTINCT status)=8 FROM maintenance_plan) AND
        NOT EXISTS (SELECT 1 FROM maintenance_plan GROUP BY status HAVING COUNT(*)<>1));
    PERFORM pg_temp.assert_demo('12 all eleven item states represented',
        (SELECT COUNT(DISTINCT status)=11 FROM maintenance_plan_item));
    PERFORM pg_temp.assert_demo('13 planned visit date lies inside plan period',
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item i JOIN maintenance_plan p ON p.id=i.plan_id
                    WHERE i.planned_date IS NOT NULL AND i.planned_date NOT BETWEEN p.period_start AND p.period_end));

    -- 14. Approval round completeness and timestamps.
    PERFORM pg_temp.assert_demo('14 request subject/type and action completeness',
        NOT EXISTS (SELECT 1 FROM approval_request r LEFT JOIN approval_action a ON a.request_id=r.id
                    WHERE (r.request_type='PLAN_APPROVAL' AND (r.plan_id IS NULL OR r.plan_item_id IS NOT NULL)) OR
                          (r.request_type='VENDOR_SELECTION' AND (r.plan_id IS NOT NULL OR r.plan_item_id IS NULL)) OR
                          (r.status='DECIDED' AND a.id IS NULL) OR
                          (r.status<>'DECIDED' AND a.id IS NOT NULL)));
    PERFORM pg_temp.assert_demo('15 submitted request precedes BGĐ action/resolution',
        NOT EXISTS (SELECT 1 FROM approval_request r JOIN approval_action a ON a.request_id=r.id
                    JOIN user_account u ON u.id=a.actor_user_id
                    WHERE r.submitted_at>=a.action_at OR a.action_at<>r.resolved_at OR u.role_code<>'BAN_GIAM_DOC'));
    PERFORM pg_temp.assert_demo('16 pending vendor item has matching pending request',
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item i WHERE i.status='WAITING_VENDOR_APPROVAL'
                    AND NOT EXISTS (SELECT 1 FROM approval_request r WHERE r.plan_item_id=i.id
                                    AND r.request_type='VENDOR_SELECTION' AND r.status='PENDING'
                                    AND r.proposed_provider_id IS NOT NULL AND NULLIF(BTRIM(r.rationale),'') IS NOT NULL)));
    PERFORM pg_temp.assert_demo('17 assigned external item has prior approved same-provider request',
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item i WHERE i.assignment_route='EXTERNAL_APPROVED'
                    AND NOT EXISTS (SELECT 1 FROM approval_request r JOIN approval_action a ON a.request_id=r.id
                                    JOIN status_history h ON h.plan_item_id=i.id AND h.new_state='ASSIGNED_EXTERNAL'
                                    WHERE r.plan_item_id=i.id AND r.proposed_provider_id=i.assigned_provider_id
                                      AND r.request_type='VENDOR_SELECTION' AND r.status='DECIDED'
                                      AND a.outcome='APPROVE' AND a.action_at<h.action_timestamp)));
    PERFORM pg_temp.assert_demo('18 revision scenario has two plan rounds',
        EXISTS (SELECT 1 FROM maintenance_plan p JOIN approval_request r ON r.plan_id=p.id
                WHERE p.title='DEMO — Kế hoạch tháng 10/2026 đã duyệt'
                GROUP BY p.id HAVING COUNT(*)=2));

    -- 19. Execution, log and acceptance chronology.
    PERFORM pg_temp.assert_demo('19 execution provider and plan approval prerequisite',
        NOT EXISTS (SELECT 1 FROM maintenance_execution x JOIN maintenance_plan_item i ON i.id=x.plan_item_id
                    WHERE x.provider_id<>i.assigned_provider_id OR
                          NOT EXISTS (SELECT 1 FROM status_history h WHERE h.plan_id=i.plan_id
                                      AND h.new_state='APPROVED' AND h.action_timestamp<x.started_at)));
    PERFORM pg_temp.assert_demo('20 attempt numbering and log chronology',
        NOT EXISTS (SELECT 1 FROM maintenance_execution x
                    WHERE x.attempt_no<1 OR NOT EXISTS (SELECT 1 FROM maintenance_progress_log l WHERE l.execution_id=x.id)
                          OR (SELECT COUNT(*) FROM maintenance_progress_log l WHERE l.execution_id=x.id) NOT BETWEEN 2 AND 4) AND
        NOT EXISTS (SELECT 1 FROM maintenance_progress_log l JOIN maintenance_execution x ON x.id=l.execution_id
                    WHERE l.event_at<=x.started_at OR (x.ended_at IS NOT NULL AND l.event_at>=x.ended_at)));
    PERFORM pg_temp.assert_demo('21 acceptance follows execution end',
        NOT EXISTS (SELECT 1 FROM acceptance_record a JOIN maintenance_execution x ON x.id=a.execution_id
                    WHERE x.ended_at IS NULL OR a.observed_at<=x.ended_at));
    PERFORM pg_temp.assert_demo('22 handover follows technical PASS for same attempt',
        NOT EXISTS (SELECT 1 FROM acceptance_record h WHERE h.acceptance_type='HANDOVER_ACCEPTANCE'
                    AND NOT EXISTS (SELECT 1 FROM acceptance_record t WHERE t.execution_id=h.execution_id
                                    AND t.acceptance_type='TECHNICAL_ACCEPTANCE' AND t.result='PASS'
                                    AND t.observed_at<h.observed_at)));
    PERFORM pg_temp.assert_demo('23 successful handover has scoped department and VTYT signers',
        NOT EXISTS (SELECT 1 FROM acceptance_record a JOIN maintenance_execution x ON x.id=a.execution_id
                    JOIN maintenance_plan_item i ON i.id=x.plan_item_id
                    LEFT JOIN user_account du ON du.id=a.department_confirmed_by_user_id
                    LEFT JOIN user_account vu ON vu.id=a.vtyt_confirmed_by_user_id
                    WHERE a.acceptance_type='HANDOVER_ACCEPTANCE' AND a.result='PASS'
                      AND (du.id IS NULL OR du.role_code<>'KHOA_PHONG' OR du.department_id<>i.department_id_at_plan
                           OR vu.id IS NULL OR vu.role_code<>'PHONG_VTYT'
                           OR a.department_confirmed_at<a.observed_at OR a.vtyt_confirmed_at<a.observed_at)));
    PERFORM pg_temp.assert_demo('24 COMPLETED item has latest-attempt technical/handover PASS',
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item i WHERE i.status='COMPLETED' AND NOT EXISTS (
                    SELECT 1 FROM maintenance_execution x JOIN acceptance_record t ON t.execution_id=x.id
                    JOIN acceptance_record h ON h.execution_id=x.id
                    WHERE x.plan_item_id=i.id
                      AND x.attempt_no=(SELECT MAX(x2.attempt_no) FROM maintenance_execution x2 WHERE x2.plan_item_id=i.id)
                      AND t.acceptance_type='TECHNICAL_ACCEPTANCE' AND t.result='PASS'
                      AND h.acceptance_type='HANDOVER_ACCEPTANCE' AND h.result='PASS'
                      AND t.observed_at<h.observed_at)));
    PERFORM pg_temp.assert_demo('25 REWORK evidence retained with numbered second attempt',
        (SELECT COUNT(*)=7 FROM maintenance_plan_item i WHERE (SELECT COUNT(*) FROM maintenance_execution x WHERE x.plan_item_id=i.id)=2) AND
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item i WHERE i.status='REWORK_REQUIRED'
                    AND NOT EXISTS (SELECT 1 FROM maintenance_execution x JOIN acceptance_record a ON a.execution_id=x.id
                                    WHERE x.plan_item_id=i.id AND a.result='FAIL')));
    PERFORM pg_temp.assert_demo('26 REPAIR_REQUIRED has damage note and reason, never COMPLETED',
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item i WHERE i.status='REPAIR_REQUIRED'
                    AND (NOT EXISTS (SELECT 1 FROM maintenance_execution x JOIN maintenance_progress_log l ON l.execution_id=x.id
                                     WHERE x.plan_item_id=i.id AND NULLIF(BTRIM(l.damage_note),'') IS NOT NULL)
                         OR NOT EXISTS (SELECT 1 FROM status_history h WHERE h.plan_item_id=i.id
                                        AND h.new_state='REPAIR_REQUIRED' AND NULLIF(BTRIM(h.reason),'') IS NOT NULL))) AND
        (SELECT COUNT(*)=4 FROM maintenance_plan_item WHERE status='REPAIR_REQUIRED'));

    -- 27. Reports and history must agree with the current business state.
    PERFORM pg_temp.assert_demo('27 reports follow final item outcomes',
        NOT EXISTS (SELECT 1 FROM maintenance_report r JOIN maintenance_plan_item i ON i.plan_id=r.plan_id
                    JOIN status_history h ON h.plan_item_id=i.id AND h.new_state IN ('COMPLETED','REPAIR_REQUIRED')
                    WHERE r.report_date<h.action_timestamp::date) AND
        (SELECT COUNT(*)=2 FROM maintenance_report WHERE status='FINAL') AND
        (SELECT COUNT(*)=2 FROM maintenance_report WHERE status='DRAFT'));
    PERFORM pg_temp.assert_demo('28 one creation and final history matches each plan',
        NOT EXISTS (SELECT 1 FROM maintenance_plan p WHERE
                    (SELECT COUNT(*) FROM status_history h WHERE h.plan_id=p.id AND h.old_state IS NULL)<>1 OR
                    (SELECT h.new_state FROM status_history h WHERE h.plan_id=p.id ORDER BY h.action_timestamp DESC,h.id DESC LIMIT 1)<>p.status));
    PERFORM pg_temp.assert_demo('29 one creation and final history matches each item',
        NOT EXISTS (SELECT 1 FROM maintenance_plan_item i WHERE
                    (SELECT COUNT(*) FROM status_history h WHERE h.plan_item_id=i.id AND h.old_state IS NULL)<>1 OR
                    (SELECT h.new_state FROM status_history h WHERE h.plan_item_id=i.id ORDER BY h.action_timestamp DESC,h.id DESC LIMIT 1)<>i.status));
    PERFORM pg_temp.assert_demo('30 plan history chain and allowed transitions',
        NOT EXISTS (
          WITH seq AS (SELECT h.*,LAG(new_state) OVER(PARTITION BY plan_id ORDER BY action_timestamp,id) AS previous
                       FROM status_history h WHERE plan_id IS NOT NULL),
               allowed(old_state,new_state) AS (VALUES
                   ('DRAFT','SUBMITTED'),('SUBMITTED','APPROVED'),('SUBMITTED','REVISION_REQUIRED'),
                   ('REVISION_REQUIRED','DRAFT'),('APPROVED','IN_PROGRESS'),('IN_PROGRESS','AWAITING_REPORT'),
                   ('AWAITING_REPORT','REPORTED'),('REPORTED','CLOSED'))
          SELECT 1 FROM seq s WHERE (s.previous IS NULL AND (s.old_state IS NOT NULL OR s.new_state<>'DRAFT')) OR
              (s.previous IS NOT NULL AND (s.old_state IS DISTINCT FROM s.previous OR
                  NOT EXISTS (SELECT 1 FROM allowed a WHERE a.old_state=s.old_state AND a.new_state=s.new_state)))));
    PERFORM pg_temp.assert_demo('31 item history chain and allowed transitions',
        NOT EXISTS (
          WITH seq AS (SELECT h.*,LAG(new_state) OVER(PARTITION BY plan_item_id ORDER BY action_timestamp,id) AS previous
                       FROM status_history h WHERE plan_item_id IS NOT NULL),
               allowed(old_state,new_state) AS (VALUES
                   ('PLANNED','UNDER_CONTRACT'),('PLANNED','PENDING_PROPOSAL'),
                   ('PENDING_PROPOSAL','WAITING_VENDOR_APPROVAL'),
                   ('WAITING_VENDOR_APPROVAL','ASSIGNED_EXTERNAL'),('WAITING_VENDOR_APPROVAL','PENDING_PROPOSAL'),
                   ('UNDER_CONTRACT','IN_MAINTENANCE'),('ASSIGNED_EXTERNAL','IN_MAINTENANCE'),
                   ('IN_MAINTENANCE','AWAITING_TECHNICAL_ACCEPTANCE'),('IN_MAINTENANCE','REPAIR_REQUIRED'),
                   ('AWAITING_TECHNICAL_ACCEPTANCE','AWAITING_HANDOVER'),('AWAITING_TECHNICAL_ACCEPTANCE','REWORK_REQUIRED'),
                   ('AWAITING_TECHNICAL_ACCEPTANCE','REPAIR_REQUIRED'),('AWAITING_HANDOVER','COMPLETED'),
                   ('AWAITING_HANDOVER','REWORK_REQUIRED'),('AWAITING_HANDOVER','REPAIR_REQUIRED'),
                   ('REWORK_REQUIRED','IN_MAINTENANCE'))
          SELECT 1 FROM seq s WHERE (s.previous IS NULL AND (s.old_state IS NOT NULL OR s.new_state<>'PLANNED')) OR
              (s.previous IS NOT NULL AND (s.old_state IS DISTINCT FROM s.previous OR
                  NOT EXISTS (SELECT 1 FROM allowed a WHERE a.old_state=s.old_state AND a.new_state=s.new_state)))));
    PERFORM pg_temp.assert_demo('32 UC12 history has repeated equipment across plans with evidence',
        (SELECT COUNT(*)>=4 FROM equipment e WHERE (SELECT COUNT(DISTINCT i.plan_id) FROM maintenance_plan_item i WHERE i.equipment_id=e.id)>=2) AND
        EXISTS (SELECT 1 FROM equipment e JOIN maintenance_plan_item i ON i.equipment_id=e.id
                JOIN maintenance_execution x ON x.plan_item_id=i.id
                JOIN maintenance_progress_log l ON l.execution_id=x.id
                JOIN acceptance_record a ON a.execution_id=x.id
                JOIN status_history h ON h.plan_item_id=i.id
                WHERE e.equipment_code='DEMO-EQ-004'));
END
$validate$;
ROLLBACK;
