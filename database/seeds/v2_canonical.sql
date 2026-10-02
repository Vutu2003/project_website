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

INSERT INTO maintenance_plan (title, period_start, period_end, status, created_by_user_id, version, created_at) VALUES ('Bảo trì định kỳ Khoa Nội tổng hợp tháng 06/2026', '2026-06-01', '2026-06-30', 'REPORTED', 2, 6, '2026-06-02T08:00:00+07:00');

INSERT INTO maintenance_plan_item (plan_id, equipment_id, department_id_at_plan, planned_date, status, assigned_provider_id, assignment_route, coverage_id, version) VALUES (1, 1, 1, '2026-06-09', 'COMPLETED', 1, 'UNDER_CONTRACT', 1, 6);

INSERT INTO approval_request (request_type, plan_id, status, created_by_user_id, submitted_at, resolved_at) VALUES ('PLAN_APPROVAL', 1, 'DECIDED', 2, '2026-06-03T08:00:00+07:00', '2026-06-04T08:00:00+07:00');

INSERT INTO approval_action (request_id, actor_user_id, outcome, comment, action_at) VALUES (1, 3, 'APPROVE', 'Thống nhất kế hoạch bảo trì định kỳ', '2026-06-04T08:00:00+07:00');

INSERT INTO maintenance_execution (plan_item_id, provider_id, started_by_user_id, attempt_no, started_at, ended_at, result_note) VALUES (1, 1, 2, 1, '2026-06-09T08:00:00+07:00', '2026-06-09T11:00:00+07:00', 'Đã hoàn tất kiểm tra, vệ sinh và hiệu chuẩn các thông số vận hành');

INSERT INTO maintenance_progress_log (execution_id, recorded_by_user_id, event_at, work_note) VALUES (1, 2, '2026-06-09T09:00:00+07:00', 'Đang thực hiện
Kiểm tra tình trạng và vệ sinh thiết bị');

INSERT INTO maintenance_progress_log (execution_id, recorded_by_user_id, event_at, work_note) VALUES (1, 2, '2026-06-09T10:00:00+07:00', 'Đã xử lý xong
Hoàn tất hiệu chuẩn và kiểm tra thông số vận hành');

INSERT INTO acceptance_record (execution_id, acceptance_type, result, observed_at, conclusion, recorded_by_user_id) VALUES (1, 'TECHNICAL_ACCEPTANCE', 'PASS', '2026-06-09T11:30:00+07:00', 'Thông số kỹ thuật đạt yêu cầu vận hành', 2);

INSERT INTO acceptance_record (execution_id, acceptance_type, result, observed_at, conclusion, recorded_by_user_id, department_confirmed_by_user_id, department_confirmed_at, vtyt_confirmed_by_user_id, vtyt_confirmed_at) VALUES (1, 'HANDOVER_ACCEPTANCE', 'PASS', '2026-06-10T11:00:00+07:00', 'Thiết bị hoạt động ổn định, được bàn giao lại cho khoa sử dụng', 2, 4, '2026-06-10T11:00:00+07:00', 2, '2026-06-10T11:00:00+07:00');

INSERT INTO maintenance_report (plan_id, created_by_user_id, report_number, report_date, work_done, achieved, not_achieved, causes, next_work, resolutions, recommendations, status, finalized_at) VALUES (1, 2, 'BC-BT-2026-06', '2026-06-11', 'Kiểm tra, vệ sinh và hiệu chuẩn thiết bị theo kế hoạch', 'Thiết bị đã được nghiệm thu kỹ thuật và bàn giao đạt yêu cầu', NULL, NULL, 'Theo dõi vận hành và bố trí bảo dưỡng định kỳ tiếp theo', 'Tiếp tục lưu hồ sơ và kết quả nghiệm thu', 'Tuân thủ hướng dẫn sử dụng và lịch bảo dưỡng', 'FINAL', '2026-06-11T11:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (1, 2, NULL, 'DRAFT', 'CREATE', '2026-06-02T08:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (1, 2, 'DRAFT', 'SUBMITTED', 'SUBMIT_PLAN', '2026-06-03T08:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (1, 3, 'SUBMITTED', 'APPROVED', 'RECORD_APPROVAL', '2026-06-04T08:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (1, 2, 'APPROVED', 'IN_PROGRESS', 'START_MAINTENANCE', '2026-06-09T08:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (1, 2, 'IN_PROGRESS', 'AWAITING_REPORT', 'ALL_ITEMS_TERMINAL', '2026-06-10T11:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (1, 2, 'AWAITING_REPORT', 'REPORTED', 'FINALIZE_REPORT', '2026-06-11T11:00:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (1, 2, NULL, 'PLANNED', 'CREATE_ITEM', NULL, '2026-06-02T08:00:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (1, 2, 'PLANNED', 'UNDER_CONTRACT', 'VERIFY_FREE_COVERAGE', 'Hồ sơ hợp đồng đã được VTYT xác minh', '2026-06-02T08:01:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (1, 2, 'UNDER_CONTRACT', 'IN_MAINTENANCE', 'START_MAINTENANCE', NULL, '2026-06-09T08:00:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (1, 2, 'IN_MAINTENANCE', 'AWAITING_TECHNICAL_ACCEPTANCE', 'FINISH_MAINTENANCE', NULL, '2026-06-09T11:00:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (1, 2, 'AWAITING_TECHNICAL_ACCEPTANCE', 'AWAITING_HANDOVER', 'TECHNICAL_ACCEPTANCE', NULL, '2026-06-09T11:30:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (1, 4, 'AWAITING_HANDOVER', 'COMPLETED', 'HANDOVER_ACCEPTANCE', NULL, '2026-06-10T11:00:00+07:00');

INSERT INTO maintenance_plan (title, period_start, period_end, status, created_by_user_id, version, created_at) VALUES ('Bảo trì định kỳ Khoa Ngoại tháng 08/2026', '2026-08-01', '2026-08-31', 'REPORTED', 2, 6, '2026-08-02T08:00:00+07:00');

INSERT INTO maintenance_plan_item (plan_id, equipment_id, department_id_at_plan, planned_date, status, assigned_provider_id, assignment_route, coverage_id, version) VALUES (2, 4, 2, '2026-08-09', 'COMPLETED', 2, 'EXTERNAL_APPROVED', 4, 8);

INSERT INTO approval_request (request_type, plan_id, status, created_by_user_id, submitted_at, resolved_at) VALUES ('PLAN_APPROVAL', 2, 'DECIDED', 2, '2026-08-03T08:00:00+07:00', '2026-08-04T08:00:00+07:00');

INSERT INTO approval_action (request_id, actor_user_id, outcome, comment, action_at) VALUES (2, 3, 'APPROVE', 'Thống nhất kế hoạch bảo trì định kỳ', '2026-08-04T08:00:00+07:00');

INSERT INTO approval_request (request_type, plan_item_id, proposed_provider_id, rationale, warranty_impact_note, status, created_by_user_id, submitted_at, resolved_at) VALUES ('VENDOR_SELECTION', 2, 2, 'Đơn vị có năng lực bảo dưỡng và hiệu chuẩn phù hợp với thiết bị', 'Phương án không ảnh hưởng điều kiện bảo hành', 'DECIDED', 2, '2026-08-04T08:00:00+07:00', '2026-08-05T08:00:00+07:00');

INSERT INTO approval_action (request_id, actor_user_id, outcome, comment, action_at) VALUES (3, 3, 'APPROVE', 'Thống nhất đơn vị đề xuất', '2026-08-05T08:00:00+07:00');

INSERT INTO maintenance_execution (plan_item_id, provider_id, started_by_user_id, attempt_no, started_at, ended_at, result_note) VALUES (2, 2, 2, 1, '2026-08-09T08:00:00+07:00', '2026-08-09T11:00:00+07:00', 'Đã hoàn tất kiểm tra, vệ sinh và hiệu chuẩn các thông số vận hành');

INSERT INTO maintenance_progress_log (execution_id, recorded_by_user_id, event_at, work_note) VALUES (2, 2, '2026-08-09T09:00:00+07:00', 'Đang thực hiện
Kiểm tra tình trạng và vệ sinh thiết bị');

INSERT INTO maintenance_progress_log (execution_id, recorded_by_user_id, event_at, work_note) VALUES (2, 2, '2026-08-09T10:00:00+07:00', 'Đã xử lý xong
Hoàn tất hiệu chuẩn và kiểm tra thông số vận hành');

INSERT INTO acceptance_record (execution_id, acceptance_type, result, observed_at, conclusion, recorded_by_user_id) VALUES (2, 'TECHNICAL_ACCEPTANCE', 'PASS', '2026-08-09T11:30:00+07:00', 'Thông số kỹ thuật đạt yêu cầu vận hành', 2);

INSERT INTO acceptance_record (execution_id, acceptance_type, result, observed_at, conclusion, recorded_by_user_id, department_confirmed_by_user_id, department_confirmed_at, vtyt_confirmed_by_user_id, vtyt_confirmed_at) VALUES (2, 'HANDOVER_ACCEPTANCE', 'PASS', '2026-08-10T11:00:00+07:00', 'Thiết bị hoạt động ổn định, được bàn giao lại cho khoa sử dụng', 2, 5, '2026-08-10T11:00:00+07:00', 2, '2026-08-10T11:00:00+07:00');

INSERT INTO maintenance_report (plan_id, created_by_user_id, report_number, report_date, work_done, achieved, not_achieved, causes, next_work, resolutions, recommendations, status, finalized_at) VALUES (2, 2, 'BC-BT-2026-08', '2026-08-11', 'Kiểm tra, vệ sinh và hiệu chuẩn thiết bị theo kế hoạch', 'Thiết bị đã được nghiệm thu kỹ thuật và bàn giao đạt yêu cầu', NULL, NULL, 'Theo dõi vận hành và bố trí bảo dưỡng định kỳ tiếp theo', 'Tiếp tục lưu hồ sơ và kết quả nghiệm thu', 'Tuân thủ hướng dẫn sử dụng và lịch bảo dưỡng', 'FINAL', '2026-08-11T11:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (2, 2, NULL, 'DRAFT', 'CREATE', '2026-08-02T08:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (2, 2, 'DRAFT', 'SUBMITTED', 'SUBMIT_PLAN', '2026-08-03T08:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (2, 3, 'SUBMITTED', 'APPROVED', 'RECORD_APPROVAL', '2026-08-04T08:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (2, 2, 'APPROVED', 'IN_PROGRESS', 'START_MAINTENANCE', '2026-08-09T08:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (2, 2, 'IN_PROGRESS', 'AWAITING_REPORT', 'ALL_ITEMS_TERMINAL', '2026-08-10T11:00:00+07:00');

INSERT INTO status_history (plan_id, actor_user_id, old_state, new_state, action, action_timestamp) VALUES (2, 2, 'AWAITING_REPORT', 'REPORTED', 'FINALIZE_REPORT', '2026-08-11T11:00:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (2, 2, NULL, 'PLANNED', 'CREATE_ITEM', NULL, '2026-08-02T08:00:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (2, 2, 'PLANNED', 'PENDING_PROPOSAL', 'PREPARE_VENDOR', NULL, '2026-08-02T08:01:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (2, 3, 'PENDING_PROPOSAL', 'WAITING_VENDOR_APPROVAL', 'ACTIVATE_PREPARED_VENDOR', NULL, '2026-08-04T08:00:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (2, 3, 'WAITING_VENDOR_APPROVAL', 'ASSIGNED_EXTERNAL', 'RECORD_VENDOR_APPROVAL', NULL, '2026-08-05T08:00:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (2, 2, 'ASSIGNED_EXTERNAL', 'IN_MAINTENANCE', 'START_MAINTENANCE', NULL, '2026-08-09T08:00:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (2, 2, 'IN_MAINTENANCE', 'AWAITING_TECHNICAL_ACCEPTANCE', 'FINISH_MAINTENANCE', NULL, '2026-08-09T11:00:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (2, 2, 'AWAITING_TECHNICAL_ACCEPTANCE', 'AWAITING_HANDOVER', 'TECHNICAL_ACCEPTANCE', NULL, '2026-08-09T11:30:00+07:00');

INSERT INTO status_history (plan_item_id, actor_user_id, old_state, new_state, action, reason, action_timestamp) VALUES (2, 5, 'AWAITING_HANDOVER', 'COMPLETED', 'HANDOVER_ACCEPTANCE', NULL, '2026-08-10T11:00:00+07:00');
