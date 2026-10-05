-- Reproducible annual membership, also used after a non-destructive upgrade.
INSERT INTO equipment_maintenance_schedule(equipment_id,quarter)
SELECT e.id,'Q'||(((e.id-1)%4)+1) FROM equipment e
WHERE NOT EXISTS(SELECT 1 FROM equipment_maintenance_schedule s WHERE s.equipment_id=e.id)
ON CONFLICT DO NOTHING;
-- Group the canonical catalog under shared company contracts. Old coverage remains execution evidence.
INSERT INTO maintenance_contract(contract_code,contract_name,provider_id,start_date,end_date,notes)
SELECT 'HD-QUY-'||p.code||'-'||v.kind,
 CASE v.kind WHEN '2026' THEN 'Bảo dưỡng thiết bị y tế 2026–2030' ELSE 'Bảo dưỡng thiết bị y tế 2024–2025' END,
 p.id,v.start_date,v.end_date,'Bảo dưỡng và hiệu chuẩn định kỳ theo danh mục thiết bị'
FROM service_provider p CROSS JOIN (VALUES ('2026',DATE '2026-01-01',DATE '2030-12-31'),('2024',DATE '2024-01-01',DATE '2025-12-31')) v(kind,start_date,end_date)
WHERE p.code IN ('AN_PHAT','Y_SINH_VIET','DV_KY_THUAT') ON CONFLICT(contract_code) DO NOTHING;
-- Replace only unused canonical mappings; never alter the evidence of an existing plan.
DELETE FROM maintenance_contract_equipment m USING equipment e
WHERE m.equipment_id=e.id AND e.equipment_code ~ '^TB-[0-9]{3}$'
 AND NOT EXISTS(SELECT 1 FROM maintenance_plan_item i WHERE i.equipment_id=e.id);
INSERT INTO maintenance_contract_equipment(contract_id,equipment_id)
SELECT k.id,e.id FROM equipment e
JOIN service_provider p ON p.code=CASE ((right(e.equipment_code,3)::integer-1)%3) WHEN 0 THEN 'AN_PHAT' WHEN 1 THEN 'Y_SINH_VIET' ELSE 'DV_KY_THUAT' END
JOIN maintenance_contract k ON k.contract_code='HD-QUY-'||p.code||'-'||CASE WHEN right(e.equipment_code,3)::integer%3=0 THEN '2024' ELSE '2026' END
WHERE e.equipment_code ~ '^TB-[0-9]{3}$'
 AND NOT EXISTS(SELECT 1 FROM maintenance_plan_item i WHERE i.equipment_id=e.id)
ON CONFLICT DO NOTHING;
