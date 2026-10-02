\ir v2_catalog.sql

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
