BEGIN;
ALTER TABLE maintenance_coverage DROP CONSTRAINT ck_coverage_verified;
UPDATE maintenance_coverage SET classification='NOT_FREE' WHERE classification='UNKNOWN';
ALTER TABLE maintenance_coverage ALTER COLUMN classification SET DEFAULT 'NOT_FREE';
ALTER TABLE maintenance_coverage DROP CONSTRAINT ck_coverage_classification;
ALTER TABLE maintenance_coverage ADD CONSTRAINT ck_coverage_classification CHECK (classification IN ('FREE','NOT_FREE'));
ALTER TABLE maintenance_coverage ADD CONSTRAINT ck_coverage_verified CHECK (
 classification <> 'FREE' OR (verified_by_user_id IS NOT NULL AND verified_at IS NOT NULL AND NULLIF(BTRIM(basis_note),'') IS NOT NULL));
ALTER TABLE maintenance_plan_item DROP CONSTRAINT ck_item_assignment_fields;
ALTER TABLE maintenance_plan_item ADD CONSTRAINT ck_item_assignment_fields CHECK (
 (assignment_route IS NULL AND assigned_provider_id IS NULL) OR
 (assigned_provider_id IS NOT NULL AND assignment_route IS NOT NULL AND (assignment_route='EXTERNAL_APPROVED' OR (assignment_route='UNDER_CONTRACT' AND coverage_id IS NOT NULL))));
ALTER TABLE approval_request DROP CONSTRAINT ck_request_status;
ALTER TABLE approval_request ADD CONSTRAINT ck_request_status CHECK (status IN ('DRAFT','PENDING','DECIDED','CANCELLED'));
ALTER TABLE approval_request DROP CONSTRAINT ck_request_submission;
ALTER TABLE approval_request ADD CONSTRAINT ck_request_submission CHECK (status IN ('DRAFT','CANCELLED') OR submitted_at IS NOT NULL);
ALTER TABLE approval_request DROP CONSTRAINT ck_request_resolution;
ALTER TABLE approval_request ADD CONSTRAINT ck_request_resolution CHECK (status NOT IN ('DECIDED','CANCELLED') OR resolved_at IS NOT NULL);
ALTER TABLE approval_request DROP CONSTRAINT ck_request_vendor_proposal;
ALTER TABLE approval_request ADD CONSTRAINT ck_request_vendor_proposal CHECK (
 request_type <> 'VENDOR_SELECTION' OR status IN ('DRAFT','CANCELLED') OR
 (proposed_provider_id IS NOT NULL AND NULLIF(BTRIM(rationale),'') IS NOT NULL));
-- Preserve older duplicate drafts; keep the newest active content.
UPDATE approval_request r SET status='CANCELLED',resolved_at=CURRENT_TIMESTAMP
 WHERE r.request_type='VENDOR_SELECTION' AND r.status='DRAFT' AND EXISTS (
 SELECT 1 FROM approval_request n WHERE n.plan_item_id=r.plan_item_id AND n.request_type='VENDOR_SELECTION'
 AND (n.status='PENDING' OR (n.status='DRAFT' AND n.id>r.id)));
CREATE UNIQUE INDEX uq_vendor_active_content ON approval_request(plan_item_id)
 WHERE request_type='VENDOR_SELECTION' AND status IN ('DRAFT','PENDING');
CREATE TABLE user_notification (
 id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_user_notification PRIMARY KEY,
 user_account_id BIGINT NOT NULL,
 notification_type TEXT NOT NULL,
 title TEXT NOT NULL,
 message TEXT NOT NULL,
 target_url TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 read_at TIMESTAMPTZ,
 CONSTRAINT fk_notification_recipient FOREIGN KEY(user_account_id) REFERENCES user_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
 CONSTRAINT ck_notification_title CHECK (BTRIM(title)<>''),
 CONSTRAINT ck_notification_target CHECK (target_url LIKE '/%' AND target_url NOT LIKE '//%'),
 CONSTRAINT ck_notification_read_time CHECK (read_at IS NULL OR read_at>=created_at)
);
CREATE INDEX ix_notification_user_time ON user_notification(user_account_id,created_at DESC,id DESC);
CREATE INDEX ix_notification_unread ON user_notification(user_account_id,created_at DESC) WHERE read_at IS NULL;
COMMIT;
