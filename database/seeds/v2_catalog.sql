-- Dữ liệu mô phỏng cho môi trường local V2; không phải dữ liệu bệnh viện thật.

INSERT INTO department (code, name, active) VALUES ('KHOA_NOI', 'Khoa Nội tổng hợp', TRUE);

INSERT INTO department (code, name, active) VALUES ('KHOA_NGOAI', 'Khoa Ngoại', TRUE);

INSERT INTO department (code, name, active) VALUES ('PHONG_VTYT', 'Phòng Vật tư - Thiết bị y tế', TRUE);

INSERT INTO department (code, name, active) VALUES ('HOI_SUC', 'Khoa Hồi sức tích cực', TRUE);

INSERT INTO department (code, name, active) VALUES ('CHAN_DOAN_HINH_ANH', 'Khoa Chẩn đoán hình ảnh', TRUE);

INSERT INTO service_provider (code, name, contact_details, active) VALUES ('AN_PHAT', 'Công ty TNHH Thiết bị Y tế An Phát', 'Bộ phận dịch vụ kỹ thuật', TRUE);

INSERT INTO service_provider (code, name, contact_details, active) VALUES ('Y_SINH_VIET', 'Công ty Cổ phần Kỹ thuật Y sinh Việt', 'Bộ phận dịch vụ kỹ thuật', TRUE);

INSERT INTO service_provider (code, name, contact_details, active) VALUES ('DV_KY_THUAT', 'Trung tâm Dịch vụ Kỹ thuật Y tế', 'Bộ phận dịch vụ kỹ thuật', TRUE);

INSERT INTO user_account (username,password_hash,role_code,department_id,display_name,active) VALUES ('admin', :'hash_admin', 'ADMIN', NULL, 'Quản trị hệ thống', TRUE);

INSERT INTO user_account (username,password_hash,role_code,department_id,display_name,active) VALUES ('vtyt', :'hash_vtyt', 'PHONG_VTYT', 3, 'Nhân viên Phòng Vật tư - Thiết bị y tế', TRUE);

INSERT INTO user_account (username,password_hash,role_code,department_id,display_name,active) VALUES ('bgd', :'hash_bgd', 'BAN_GIAM_DOC', NULL, 'Ban Giám đốc', TRUE);

INSERT INTO user_account (username,password_hash,role_code,department_id,display_name,active) VALUES ('khoa_noi', :'hash_khoa_noi', 'KHOA_PHONG', 1, 'Nhân viên Khoa Nội tổng hợp', TRUE);

INSERT INTO user_account (username,password_hash,role_code,department_id,display_name,active) VALUES ('khoa_ngoai', :'hash_khoa_ngoai', 'KHOA_PHONG', 2, 'Nhân viên Khoa Ngoại', TRUE);

INSERT INTO equipment (department_id, equipment_code, name, serial_number, model, technical_spec, active) VALUES (1, 'TB-001', 'Máy theo dõi bệnh nhân', 'SN-2026-0001', 'MON-01', 'Theo hồ sơ kỹ thuật và hướng dẫn vận hành của thiết bị', TRUE);

INSERT INTO maintenance_coverage (equipment_id, provider_id, contract_reference, coverage_scope, effective_from, effective_to, classification, verified_by_user_id, verified_at, basis_note) VALUES (1, 1, 'HD-BT-2026-001', 'Kiểm tra, hiệu chuẩn và bảo dưỡng định kỳ', '2026-01-01', '2030-12-31', 'FREE', 2, '2026-01-01T08:00:00+07:00', 'Áp dụng điều khoản bảo trì theo hợp đồng còn hiệu lực');

INSERT INTO equipment (department_id, equipment_code, name, serial_number, model, technical_spec, active) VALUES (1, 'TB-002', 'Bơm tiêm điện', 'SN-2026-0002', 'SYR-01', 'Theo hồ sơ kỹ thuật và hướng dẫn vận hành của thiết bị', TRUE);

INSERT INTO maintenance_coverage (equipment_id, provider_id, contract_reference, coverage_scope, effective_from, effective_to, classification, verified_by_user_id, verified_at, basis_note) VALUES (2, NULL, NULL, NULL, '2026-01-01', '2030-12-31', 'NOT_FREE', 2, '2026-01-01T08:00:00+07:00', 'Thiết bị ngoài phạm vi hợp đồng bảo trì; cần đề xuất đơn vị thực hiện');

INSERT INTO equipment (department_id, equipment_code, name, serial_number, model, technical_spec, active) VALUES (2, 'TB-003', 'Máy điện tim', 'SN-2026-0003', 'ECG-01', 'Theo hồ sơ kỹ thuật và hướng dẫn vận hành của thiết bị', TRUE);

INSERT INTO maintenance_coverage (equipment_id, provider_id, contract_reference, coverage_scope, effective_from, effective_to, classification, verified_by_user_id, verified_at, basis_note) VALUES (3, 1, 'HD-BT-2026-003', 'Kiểm tra, hiệu chuẩn và bảo dưỡng định kỳ', '2026-01-01', '2030-12-31', 'FREE', 2, '2026-01-01T08:00:00+07:00', 'Áp dụng điều khoản bảo trì theo hợp đồng còn hiệu lực');

INSERT INTO equipment (department_id, equipment_code, name, serial_number, model, technical_spec, active) VALUES (2, 'TB-004', 'Bơm truyền dịch', 'SN-2026-0004', 'INF-01', 'Theo hồ sơ kỹ thuật và hướng dẫn vận hành của thiết bị', TRUE);

INSERT INTO maintenance_coverage (equipment_id, provider_id, contract_reference, coverage_scope, effective_from, effective_to, classification, verified_by_user_id, verified_at, basis_note) VALUES (4, NULL, NULL, NULL, '2026-01-01', '2030-12-31', 'NOT_FREE', 2, '2026-01-01T08:00:00+07:00', 'Thiết bị ngoài phạm vi hợp đồng bảo trì; cần đề xuất đơn vị thực hiện');

INSERT INTO equipment (department_id, equipment_code, name, serial_number, model, technical_spec, active) VALUES (4, 'TB-005', 'Máy thở', 'SN-2026-0005', 'VENT-01', 'Theo hồ sơ kỹ thuật và hướng dẫn vận hành của thiết bị', TRUE);

INSERT INTO maintenance_coverage (equipment_id, provider_id, contract_reference, coverage_scope, effective_from, effective_to, classification, verified_by_user_id, verified_at, basis_note) VALUES (5, 1, 'HD-BT-2026-005', 'Kiểm tra, hiệu chuẩn và bảo dưỡng định kỳ', '2026-01-01', '2030-12-31', 'FREE', 2, '2026-01-01T08:00:00+07:00', 'Áp dụng điều khoản bảo trì theo hợp đồng còn hiệu lực');

INSERT INTO equipment (department_id, equipment_code, name, serial_number, model, technical_spec, active) VALUES (4, 'TB-006', 'Máy theo dõi bệnh nhân', 'SN-2026-0006', 'MON-02', 'Theo hồ sơ kỹ thuật và hướng dẫn vận hành của thiết bị', TRUE);

INSERT INTO maintenance_coverage (equipment_id, provider_id, contract_reference, coverage_scope, effective_from, effective_to, classification, verified_by_user_id, verified_at, basis_note) VALUES (6, NULL, NULL, NULL, '2026-01-01', '2030-12-31', 'NOT_FREE', 2, '2026-01-01T08:00:00+07:00', 'Thiết bị ngoài phạm vi hợp đồng bảo trì; cần đề xuất đơn vị thực hiện');

INSERT INTO equipment (department_id, equipment_code, name, serial_number, model, technical_spec, active) VALUES (5, 'TB-007', 'Máy siêu âm', 'SN-2026-0007', 'US-01', 'Theo hồ sơ kỹ thuật và hướng dẫn vận hành của thiết bị', TRUE);

INSERT INTO maintenance_coverage (equipment_id, provider_id, contract_reference, coverage_scope, effective_from, effective_to, classification, verified_by_user_id, verified_at, basis_note) VALUES (7, 1, 'HD-BT-2026-007', 'Kiểm tra, hiệu chuẩn và bảo dưỡng định kỳ', '2026-01-01', '2030-12-31', 'FREE', 2, '2026-01-01T08:00:00+07:00', 'Áp dụng điều khoản bảo trì theo hợp đồng còn hiệu lực');

INSERT INTO equipment (department_id, equipment_code, name, serial_number, model, technical_spec, active) VALUES (5, 'TB-008', 'Máy X-quang', 'SN-2026-0008', 'XR-01', 'Theo hồ sơ kỹ thuật và hướng dẫn vận hành của thiết bị', TRUE);

