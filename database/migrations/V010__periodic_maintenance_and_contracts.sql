BEGIN;
ALTER TABLE equipment
 ADD COLUMN maintenance_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN maintenance_interval_value INTEGER,
 ADD COLUMN maintenance_interval_unit TEXT,
 ADD COLUMN commissioning_date DATE,
 ADD CONSTRAINT ck_equipment_interval CHECK (
  (maintenance_interval_value IS NULL AND maintenance_interval_unit IS NULL AND NOT maintenance_enabled)
  OR (maintenance_interval_value IS NOT NULL AND maintenance_interval_unit IS NOT NULL AND maintenance_interval_value BETWEEN 1 AND 1200 AND maintenance_interval_unit IN ('DAY','MONTH','YEAR')));
CREATE TABLE maintenance_contract (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 contract_code TEXT NOT NULL UNIQUE CHECK (btrim(contract_code)<>''),
 contract_name TEXT NOT NULL CHECK (btrim(contract_name)<>''),
 provider_id BIGINT NOT NULL REFERENCES service_provider(id) ON DELETE RESTRICT,
 start_date DATE NOT NULL, end_date DATE NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, notes TEXT,
 CHECK (end_date>=start_date));
ALTER TABLE maintenance_coverage ADD COLUMN contract_id BIGINT REFERENCES maintenance_contract(id) ON DELETE RESTRICT;
-- Different legacy terms remain separate contracts, including same-code ambiguity.
INSERT INTO maintenance_contract(contract_code,contract_name,provider_id,start_date,end_date,notes)
SELECT coalesce(nullif(btrim(contract_reference),''),'HD-CU') || '-M' || min(id),
 coalesce(nullif(btrim(contract_reference),''),'Hợp đồng bảo trì hiện có'),provider_id,
 coalesce(effective_from,DATE '0001-01-01'),coalesce(effective_to,DATE '9999-12-31'),
 'Chuyển từ coverage V2; giữ nguyên điều khoản hiện có'
FROM maintenance_coverage WHERE classification='FREE' AND provider_id IS NOT NULL
GROUP BY contract_reference,provider_id,effective_from,effective_to;
UPDATE maintenance_coverage c SET contract_id=k.id FROM maintenance_contract k
WHERE c.classification='FREE' AND k.contract_code=
 coalesce(nullif(btrim(c.contract_reference),''),'HD-CU') || '-M' ||
 (SELECT min(original.id) FROM maintenance_coverage original WHERE original.classification='FREE'
  AND original.contract_reference IS NOT DISTINCT FROM c.contract_reference
  AND original.provider_id=c.provider_id
  AND original.effective_from IS NOT DISTINCT FROM c.effective_from
  AND original.effective_to IS NOT DISTINCT FROM c.effective_to);
-- Coverage evidence can have multiple historical versions; keep every referenced row.
CREATE INDEX ix_coverage_contract_equipment ON maintenance_coverage(contract_id,equipment_id) WHERE contract_id IS NOT NULL;
CREATE INDEX ix_contract_provider_dates ON maintenance_contract(provider_id,start_date,end_date);
CREATE INDEX ix_coverage_equipment_contract ON maintenance_coverage(equipment_id,contract_id);
CREATE INDEX ix_execution_completed_date ON maintenance_execution(plan_item_id,ended_at DESC) WHERE ended_at IS NOT NULL;
ALTER TABLE maintenance_plan_item ADD COLUMN last_maintenance_date DATE, ADD COLUMN maintenance_due_date DATE;
-- Compatibility mirrors for old reporting/assignment code; contract owns these values.
CREATE FUNCTION sync_coverage_contract() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.contract_id IS NOT NULL THEN
  SELECT provider_id,contract_code,start_date,end_date INTO NEW.provider_id,NEW.contract_reference,NEW.effective_from,NEW.effective_to
   FROM maintenance_contract WHERE id=NEW.contract_id;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER coverage_contract_mirror BEFORE INSERT OR UPDATE ON maintenance_coverage FOR EACH ROW EXECUTE FUNCTION sync_coverage_contract();
CREATE FUNCTION sync_contract_coverages() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 UPDATE maintenance_coverage SET provider_id=NEW.provider_id,contract_reference=NEW.contract_code,
 effective_from=NEW.start_date,effective_to=NEW.end_date WHERE contract_id=NEW.id;
 RETURN NEW;
END $$;
CREATE TRIGGER contract_coverage_mirror AFTER UPDATE ON maintenance_contract FOR EACH ROW EXECUTE FUNCTION sync_contract_coverages();
UPDATE maintenance_coverage SET contract_id=contract_id WHERE contract_id IS NOT NULL;
COMMIT;
