BEGIN;

ALTER TABLE equipment ADD COLUMN manufacturer_provider_id BIGINT
    REFERENCES service_provider(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE maintenance_coverage ADD COLUMN warranty_expires_on DATE;
-- Existing contractual records use the contract end date as their initial warranty deadline.
UPDATE maintenance_coverage SET warranty_expires_on = effective_to
WHERE classification = 'FREE' AND contract_reference IS NOT NULL;
ALTER TABLE maintenance_coverage ADD CONSTRAINT ck_coverage_warranty_date
    CHECK (warranty_expires_on IS NULL OR effective_from IS NULL OR warranty_expires_on >= effective_from);

ALTER TABLE maintenance_plan_item ADD COLUMN service_choice TEXT;
ALTER TABLE maintenance_plan_item ADD CONSTRAINT ck_item_service_choice
    CHECK (service_choice IS NULL OR service_choice IN ('MANUFACTURER', 'EXTERNAL'));
UPDATE maintenance_plan_item SET service_choice = 'EXTERNAL'
WHERE status <> 'PLANNED' AND (assignment_route IS NULL OR assignment_route <> 'UNDER_CONTRACT');
COMMIT;
