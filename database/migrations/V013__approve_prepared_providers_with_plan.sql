BEGIN;

-- Convert only proposals activated by the recorded approval of their plan.
-- A proposal submitted later, an inactive provider, or a plan returned for
-- revision is not covered by that decision. Keep the original audit entries.
CREATE TEMP TABLE plan_approved_providers ON COMMIT DROP AS
SELECT q.id request_id, i.id item_id, i.plan_id, q.proposed_provider_id,
       a.actor_user_id, a.action_at, pr.id plan_approval_request_id
FROM approval_request q
JOIN maintenance_plan_item i ON i.id=q.plan_item_id
JOIN maintenance_plan p ON p.id=i.plan_id
JOIN service_provider s ON s.id=q.proposed_provider_id AND s.active
JOIN approval_request pr ON pr.id=(
    SELECT r.id FROM approval_request r JOIN approval_action d ON d.request_id=r.id
    WHERE r.plan_id=p.id AND r.request_type='PLAN_APPROVAL'
      AND r.status='DECIDED' AND d.outcome='APPROVE'
    ORDER BY d.action_at DESC,r.id DESC LIMIT 1
)
JOIN approval_action a ON a.request_id=pr.id
WHERE p.status IN ('APPROVED','IN_PROGRESS')
  AND i.status='WAITING_VENDOR_APPROVAL'
  AND q.request_type='VENDOR_SELECTION' AND q.status='PENDING'
  AND NULLIF(BTRIM(q.rationale),'') IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM approval_action d WHERE d.request_id=q.id)
  AND EXISTS (
      SELECT 1 FROM status_history h WHERE h.plan_item_id=i.id
        AND h.action='ACTIVATE_PREPARED_VENDOR'
        AND h.reason='request='||q.id
        AND h.action_timestamp=a.action_at AND q.submitted_at=a.action_at
  );

-- These entries inherit the actual plan decision, rather than recording a new
-- independent decision by a director. The comment links their source explicitly.
INSERT INTO approval_action(request_id,actor_user_id,outcome,comment,action_at)
SELECT request_id,actor_user_id,'APPROVE',
       'Chấp thuận cùng kế hoạch theo luồng duyệt một lần (V013); planApprovalRequest='||plan_approval_request_id,
       action_at FROM plan_approved_providers;

UPDATE approval_request q SET status='DECIDED',resolved_at=t.action_at
FROM plan_approved_providers t WHERE q.id=t.request_id;

UPDATE maintenance_plan_item i
SET status='ASSIGNED_EXTERNAL',assignment_route='EXTERNAL_APPROVED',
    assigned_provider_id=t.proposed_provider_id,version=i.version+1
FROM plan_approved_providers t WHERE i.id=t.item_id;

UPDATE maintenance_plan p SET version=p.version+1
WHERE p.id IN (SELECT plan_id FROM plan_approved_providers);

INSERT INTO status_history(plan_item_id,actor_user_id,old_state,new_state,action,reason,action_timestamp)
SELECT item_id,actor_user_id,'WAITING_VENDOR_APPROVAL','ASSIGNED_EXTERNAL',
       'APPLY_PLAN_APPROVAL_TO_PROVIDER',
       'V013; request='||request_id||'; planApprovalRequest='||plan_approval_request_id,
       now() FROM plan_approved_providers;

UPDATE user_notification n SET read_at=now()
WHERE n.notification_type='VENDOR_PENDING' AND n.read_at IS NULL
  AND n.target_url IN (SELECT '/approvals/'||request_id FROM plan_approved_providers);

COMMIT;
