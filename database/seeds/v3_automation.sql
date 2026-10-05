-- Canonical demonstration dates from equipment commissioning records.
-- Only previously unconfigured baseline equipment is changed; user settings survive reruns.
UPDATE equipment SET maintenance_enabled=TRUE,maintenance_interval_value=6,maintenance_interval_unit='MONTH',
 commissioning_date=CASE right(equipment_code,3)::integer % 4
 WHEN 0 THEN DATE '2026-01-05' WHEN 1 THEN DATE '2026-04-20'
 WHEN 2 THEN DATE '2026-04-05' ELSE DATE '2026-07-05' END
WHERE equipment_code ~ '^TB-[0-9]{3}$' AND commissioning_date IS NULL AND maintenance_interval_value IS NULL
 AND equipment_code NOT IN ('TB-008','TB-032');
-- Group existing per-equipment evidence into shared contracts with the same provider/terms.
INSERT INTO maintenance_contract(contract_code,contract_name,provider_id,start_date,end_date,notes)
SELECT 'HD-DK-2026-'||p.code,'Hợp đồng bảo trì định kỳ — '||p.name,p.id,DATE '2026-01-01',DATE '2030-12-31',
 'Bảo dưỡng và hiệu chuẩn theo danh mục thiết bị thuộc hợp đồng'
FROM service_provider p WHERE p.code IN ('AN_PHAT','Y_SINH_VIET')
ON CONFLICT(contract_code) DO NOTHING;
UPDATE maintenance_coverage c SET contract_id=k.id
FROM maintenance_contract k,equipment e
WHERE e.id=c.equipment_id AND e.equipment_code ~ '^TB-[0-9]{3}$' AND c.classification='FREE'
 AND c.contract_id IS NULL AND k.contract_code LIKE 'HD-DK-2026-%' AND k.provider_id=c.provider_id;
