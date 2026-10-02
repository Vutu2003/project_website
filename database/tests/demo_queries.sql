-- PHASE 1.3 DEMO QUERIES. All codes and records are synthetic.
-- Run: psql -X -d medical_maintenance_db -f database/tests/demo_queries.sql

-- 1. Equipment held by an example clinical department.
SELECT e.equipment_code, e.name, e.model, d.name AS department
FROM equipment e JOIN department d ON d.id=e.department_id
WHERE d.code='HOI_SUC'
ORDER BY e.equipment_code;

-- 2. Current plans and their periods.
SELECT title, period_start, period_end, status
FROM maintenance_plan ORDER BY period_start, title;

-- 3. Item counts by plan and current state.
SELECT p.title, i.status, COUNT(*) AS item_count
FROM maintenance_plan p JOIN maintenance_plan_item i ON i.plan_id=p.id
GROUP BY p.title,i.status ORDER BY p.title,i.status;

-- 4. Pending director queue: plan and vendor subjects.
SELECT r.request_type, r.status, p.title AS plan_title, e.equipment_code,
       sp.name AS proposed_provider, r.submitted_at
FROM approval_request r
LEFT JOIN maintenance_plan p ON p.id=r.plan_id
LEFT JOIN maintenance_plan_item i ON i.id=r.plan_item_id
LEFT JOIN equipment e ON e.id=i.equipment_id
LEFT JOIN service_provider sp ON sp.id=r.proposed_provider_id
WHERE r.status='PENDING'
ORDER BY r.submitted_at;

-- 5. UC12: one device in two plans, including current state and attempts.
SELECT e.equipment_code, p.title, i.status AS item_status,
       COALESCE(sp.name,'Chưa phân công') AS provider,
       COUNT(x.id) AS attempt_count
FROM equipment e JOIN maintenance_plan_item i ON i.equipment_id=e.id
JOIN maintenance_plan p ON p.id=i.plan_id
LEFT JOIN service_provider sp ON sp.id=i.assigned_provider_id
LEFT JOIN maintenance_execution x ON x.plan_item_id=i.id
WHERE e.equipment_code='DEMO-EQ-004'
GROUP BY e.equipment_code,p.title,i.status,sp.name,p.period_start
ORDER BY p.period_start;

-- 6. Execution work notes for the same device.
SELECT p.title, x.attempt_no, l.event_at, l.work_note, l.damage_note
FROM equipment e JOIN maintenance_plan_item i ON i.equipment_id=e.id
JOIN maintenance_plan p ON p.id=i.plan_id
JOIN maintenance_execution x ON x.plan_item_id=i.id
JOIN maintenance_progress_log l ON l.execution_id=x.id
WHERE e.equipment_code='DEMO-EQ-004'
ORDER BY x.started_at,x.attempt_no,l.event_at;

-- 7. Technical and handover assessments for that device.
SELECT p.title, x.attempt_no, a.acceptance_type, a.result, a.observed_at, a.conclusion
FROM equipment e JOIN maintenance_plan_item i ON i.equipment_id=e.id
JOIN maintenance_plan p ON p.id=i.plan_id
JOIN maintenance_execution x ON x.plan_item_id=i.id
JOIN acceptance_record a ON a.execution_id=x.id
WHERE e.equipment_code='DEMO-EQ-004'
ORDER BY x.started_at,x.attempt_no,a.observed_at;

-- 8. Current rework queue with preserved failed assessment.
SELECT p.title, e.equipment_code, i.status, x.attempt_no,
       a.acceptance_type, a.result, a.conclusion
FROM maintenance_plan_item i JOIN maintenance_plan p ON p.id=i.plan_id
JOIN equipment e ON e.id=i.equipment_id
JOIN maintenance_execution x ON x.plan_item_id=i.id
JOIN acceptance_record a ON a.execution_id=x.id AND a.result='FAIL'
WHERE i.status='REWORK_REQUIRED'
ORDER BY p.title,e.equipment_code;

-- 9. Repair hand-offs with damage evidence; never count as completed.
SELECT p.title, e.equipment_code, i.status, l.damage_note, l.event_at
FROM maintenance_plan_item i JOIN maintenance_plan p ON p.id=i.plan_id
JOIN equipment e ON e.id=i.equipment_id
JOIN maintenance_execution x ON x.plan_item_id=i.id
JOIN maintenance_progress_log l ON l.execution_id=x.id
WHERE i.status='REPAIR_REQUIRED' AND l.damage_note IS NOT NULL
ORDER BY p.title,e.equipment_code;

-- 10. Reportable plan outcome summary.
SELECT p.title, p.status AS plan_status,
       COUNT(*) FILTER (WHERE i.status='COMPLETED') AS completed,
       COUNT(*) FILTER (WHERE i.status='REPAIR_REQUIRED') AS repair_handoff,
       COUNT(*) FILTER (WHERE i.status NOT IN ('COMPLETED','REPAIR_REQUIRED')) AS still_open,
       COALESCE(r.status,'NO_REPORT') AS report_status
FROM maintenance_plan p JOIN maintenance_plan_item i ON i.plan_id=p.id
LEFT JOIN maintenance_report r ON r.plan_id=p.id
GROUP BY p.id,p.title,p.status,r.status,p.period_start
ORDER BY p.period_start;

-- 11. Incomplete legacy evidence is outside contract; proposal required before new submission.
SELECT p.title, e.equipment_code, c.classification, i.status,
       i.assignment_route, i.assigned_provider_id
FROM maintenance_plan_item i JOIN equipment e ON e.id=i.equipment_id
JOIN maintenance_plan p ON p.id=i.plan_id
JOIN maintenance_coverage c ON c.id=i.coverage_id
WHERE c.classification='NOT_FREE' AND c.verified_at IS NULL
ORDER BY p.title,e.equipment_code;
