-- Danh mục bổ sung V3. Chỉ INSERT, giữ nguyên mọi bản ghi đã có.
-- Có thể chạy lại; coverage chỉ được thêm cho thiết bị vừa được INSERT.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM user_account WHERE username='vtyt' AND role_code='PHONG_VTYT' AND active)
       OR (SELECT count(*) FROM service_provider WHERE code IN ('AN_PHAT','Y_SINH_VIET') AND active) <> 2 THEN
        RAISE EXCEPTION 'Active canonical VTYT and contractual providers required';
    END IF;
END $$;

WITH additions(code, name) AS (VALUES
    ('KHOA_NHI', 'Khoa Nhi'),
    ('KHOA_SAN', 'Khoa Sản'),
    ('CAP_CUU', 'Khoa Cấp cứu'),
    ('XET_NGHIEM', 'Khoa Xét nghiệm'),
    ('THAN_NHAN_TAO', 'Khoa Thận nhân tạo')
)
INSERT INTO department (code, name, active)
SELECT a.code, a.name, TRUE FROM additions a
WHERE NOT EXISTS (SELECT 1 FROM department d WHERE d.code=a.code)
ORDER BY a.code
ON CONFLICT (code) DO NOTHING;

WITH additions(department_code, equipment_code, name, serial_number, model) AS (VALUES
    ('KHOA_NOI', 'TB-009', 'Máy đo chức năng hô hấp', 'SN-2026-0009', 'SPIRO-01'),
    ('KHOA_NOI', 'TB-010', 'Máy đo huyết áp tự động', 'SN-2026-0010', 'NIBP-01'),
    ('KHOA_NGOAI', 'TB-011', 'Dao mổ điện', 'SN-2026-0011', 'ESU-01'),
    ('KHOA_NGOAI', 'TB-012', 'Máy hút dịch phẫu thuật', 'SN-2026-0012', 'SUCTION-01'),
    ('HOI_SUC', 'TB-013', 'Máy thở hồi sức', 'SN-2026-0013', 'VENT-02'),
    ('HOI_SUC', 'TB-014', 'Bơm truyền dịch', 'SN-2026-0014', 'INF-02'),
    ('CHAN_DOAN_HINH_ANH', 'TB-015', 'Máy X-quang di động', 'SN-2026-0015', 'XR-MOBILE-01'),
    ('CHAN_DOAN_HINH_ANH', 'TB-016', 'Máy siêu âm Doppler', 'SN-2026-0016', 'US-DOPPLER-01'),
    ('KHOA_NHI', 'TB-017', 'Lồng ấp trẻ sơ sinh', 'SN-2026-0017', 'INCUBATOR-01'),
    ('KHOA_NHI', 'TB-018', 'Đèn chiếu điều trị vàng da', 'SN-2026-0018', 'PHOTO-01'),
    ('KHOA_NHI', 'TB-019', 'Máy theo dõi bệnh nhân nhi', 'SN-2026-0019', 'MON-PED-01'),
    ('KHOA_SAN', 'TB-020', 'Máy theo dõi tim thai', 'SN-2026-0020', 'CTG-01'),
    ('KHOA_SAN', 'TB-021', 'Máy siêu âm sản khoa', 'SN-2026-0021', 'US-OB-01'),
    ('KHOA_SAN', 'TB-022', 'Bơm truyền dịch', 'SN-2026-0022', 'INF-03'),
    ('CAP_CUU', 'TB-023', 'Máy khử rung tim', 'SN-2026-0023', 'DEFIB-01'),
    ('CAP_CUU', 'TB-024', 'Máy thở vận chuyển', 'SN-2026-0024', 'VENT-TRANSPORT-01'),
    ('CAP_CUU', 'TB-025', 'Máy theo dõi bệnh nhân cấp cứu', 'SN-2026-0025', 'MON-ER-01'),
    ('XET_NGHIEM', 'TB-026', 'Máy phân tích huyết học', 'SN-2026-0026', 'HEMA-01'),
    ('XET_NGHIEM', 'TB-027', 'Máy phân tích sinh hóa', 'SN-2026-0027', 'BIOCHEM-01'),
    ('XET_NGHIEM', 'TB-028', 'Máy ly tâm xét nghiệm', 'SN-2026-0028', 'CENTRIFUGE-01'),
    ('THAN_NHAN_TAO', 'TB-029', 'Máy chạy thận nhân tạo', 'SN-2026-0029', 'DIALYSIS-01'),
    ('THAN_NHAN_TAO', 'TB-030', 'Hệ thống lọc nước RO', 'SN-2026-0030', 'RO-01'),
    ('THAN_NHAN_TAO', 'TB-031', 'Máy đo huyết áp tự động', 'SN-2026-0031', 'NIBP-02'),
    ('THAN_NHAN_TAO', 'TB-032', 'Bơm truyền dịch', 'SN-2026-0032', 'INF-04')
), inserted AS (
    INSERT INTO equipment (department_id, equipment_code, name, serial_number, model, technical_spec, active)
    SELECT d.id, a.equipment_code, a.name, a.serial_number, a.model,
           'Kiểm tra an toàn, vệ sinh, hiệu chuẩn và bảo dưỡng theo hồ sơ kỹ thuật', TRUE
    FROM additions a JOIN department d ON d.code=a.department_code
    WHERE NOT EXISTS (SELECT 1 FROM equipment e WHERE e.equipment_code=a.equipment_code)
    ORDER BY a.equipment_code
    ON CONFLICT (equipment_code) DO NOTHING
    RETURNING id, equipment_code
)
INSERT INTO maintenance_coverage (equipment_id, provider_id, contract_reference, coverage_scope,
    effective_from, effective_to, classification, verified_by_user_id, verified_at, basis_note, warranty_expires_on)
SELECT e.id,
    CASE WHEN right(e.equipment_code,3)::int % 2=1 THEN
        (SELECT id FROM service_provider WHERE code=CASE WHEN right(e.equipment_code,3)::int % 4=1 THEN 'AN_PHAT' ELSE 'Y_SINH_VIET' END)
    END,
    CASE WHEN right(e.equipment_code,3)::int % 2=1 THEN 'HD-BT-2026-' || right(e.equipment_code,3) END,
    CASE WHEN right(e.equipment_code,3)::int % 2=1 THEN 'Kiểm tra, hiệu chuẩn và bảo dưỡng định kỳ' END,
    DATE '2026-01-01', DATE '2030-12-31',
    CASE WHEN right(e.equipment_code,3)::int % 2=1 THEN 'FREE' ELSE 'NOT_FREE' END,
    (SELECT id FROM user_account WHERE username='vtyt'), TIMESTAMPTZ '2026-01-01 08:00:00+07',
    CASE WHEN right(e.equipment_code,3)::int % 2=1 THEN 'Bảo trì theo hợp đồng còn hiệu lực, đã được Phòng VTYT xác minh'
         ELSE 'Ngoài phạm vi hợp đồng bảo trì; Phòng VTYT cần đề xuất đơn vị thực hiện' END,
    CASE WHEN right(e.equipment_code,3)::int % 2=1 THEN DATE '2030-12-31' END
FROM inserted e
ORDER BY e.equipment_code;
