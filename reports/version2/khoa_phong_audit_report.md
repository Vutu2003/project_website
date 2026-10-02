# Version 2 — KHOA_PHONG Audit

## 1. Scope

UC10, UC12 và thông báo chờ/kết quả bàn giao. Nguồn: `docs/system_analysis_v1.pdf` UC10 trang in 26–28, UC12 30–31; đối chiếu `docs/use_case.pdf`. Giữ cơ chế xác nhận thứ hai của VTYT và nhánh REPAIR_REQUIRED hiện có.

## 2. Audit Result

| UC | Mong đợi / triển khai hiện tại | Kết luận và sửa tối thiểu |
| --- | --- | --- |
| UC10 | Item AWAITING_HANDOVER và technical PASS của lần thực hiện hiện tại. KHOA đúng departmentAtPlan xác nhận; PASS cần VTYT đồng ký → COMPLETED; toàn bộ item final hợp lệ → AWAITING_REPORT. FAIL → REWORK_REQUIRED hoặc nhánh sửa chữa hiện có; history/acceptance atomic, trùng/version/sai khoa bị chặn. | Service/form **MATCH**. Hàng chờ **GAP → MATCH**: trước đây liệt kê mọi kế hoạch/hạng mục trong scope. Giờ chỉ trả kế hoạch có item AWAITING_HANDOVER của chính khoa và chỉ liệt kê item chờ bàn giao. Dùng tham số tùy chọn `itemStatus`/`status` trên GET plan/items; scope và pagination thực hiện ở database, không lọc sau khi lấy trang. |
| UC12 | Tìm thiết bị, đọc lịch sử các đợt thuộc khoa, execution/progress và acceptance, không mutate; khoa khác không được nhận hồ sơ. | Scope/history **MATCH**. Tìm kiếm **GAP → MATCH**: bổ sung mã/tên/serial trên UI và GET equipment. Kết hợp tìm kiếm với scope trước phân trang; ký tự wildcard được escape. |

## 3. Department Scope / Authorization

- Handover dùng **departmentAtPlan**, chỉ KHOA_PHONG đúng khoa. Technical PASS và current attempt là điều kiện bắt buộc. PASS xác thực VTYT qua header đồng ký có sẵn; phiên KHOA giữ nguyên, thông tin VTYT tạm không lưu như phiên đăng nhập mới.
- Scope lịch sử đã chấp nhận gồm khoa đang giữ thiết bị hoặc đợt có snapshot khoa tương ứng. Sau chuyển khoa, khoa cũ chỉ đọc đợt thuộc mình; currentDepartmentId, coverage/provider assignment ID và plan history được hạn chế theo policy hiện có. Không mở lịch sử của khoa khác.
- GET plan/items vẫn kiểm tra scope; lọc chờ bàn giao xét trạng thái và khoa trên **cùng item**, tránh kế hoạch lẫn nhiều khoa xuất hiện do item khoa khác. KHOA không có department bị từ chối.
- KHOA không quyết định UC04/UC07, không ghi/chốt báo cáo UC11; báo cáo FINAL được tham chiếu trong lịch sử, không cấp quyền đọc toàn bộ báo cáo đa khoa. ADMIN không được đọc maintenance-history.

## 4. Notification Integration

**MATCH**: technical PASS → HANDOVER_PENDING cho tài khoản KHOA active đúng departmentAtPlan; khoa không liên quan/inactive không nhận. Bàn giao đạt → HANDOVER_COMPLETED cho VTYT; FAIL/rework/repair → HANDOVER_REWORK theo implementation hiện có. Giữ transaction/ownership/rollback, không thay notification model.

## 5. Tests

- **Backend focused 77/77 PASS**; **full 137 tests / 0 failure / 0 error / 0 skip**. Reuse ExecutionAcceptance và BackendBusinessFinal cùng planning/provider suite. Kiểm tra chưa technical PASS, sai khoa, signer thiếu/sai, duplicate, rollback, PASS/FAIL/rework/repair, plan AWAITING_REPORT, lịch sử snapshot sau chuyển khoa và API reads scoped.
- Bổ sung assertion trong test scope sẵn có: BGĐ tìm toàn bộ fixture; KHOA tìm scoped; serial/literal wildcard; plan có item khoa khác chờ bàn giao không vào queue, thêm item đúng khoa thì xuất hiện và items chỉ trả đúng khoa.
- **Frontend build/lint PASS; 130/130 tests, 14 files PASS**. Bốn ca mới trong ReportingHistoryPage.test: search và hai cấp queue, thông tin duyệt kế hoạch/lý do chỉnh sửa. Các form UC10/đồng ký đã có giữ nguyên.
- **Chrome thật 154.0.8037.92, 22 checks PASS** chung hai actor. API đưa một item test tới AWAITING_HANDOVER; KHOA đăng nhập thật → queue → item → nhập kết luận và VTYT đồng ký → COMPLETED/AWAITING_REPORT. Item biến mất khỏi queue, lịch sử vẫn đọc được. Tài khoản khoa khác mở URL item/history bị 403, tìm mã thiết bị không có kết quả và không nhận HANDOVER_PENDING.
- BGĐ smoke kiểm tra submit/plan approval/vendor approval, report FINAL/evidence/history chỉ đọc và refresh. Không mock API. Hai selector driver được sửa trước lần smoke PASS; mỗi lần dừng đều cleanup.
- Cluster riêng 55434, DB `medical_maintenance_bgd_khoa_test`, backend 8081 / frontend 5174 / CDP 9335. Plan #272/item #350, tài khoản khoa thử và thông báo được cleanup có guard ID/prefix/FK; **15 bảng/603 dòng/schema** fingerprint bằng baseline. Không có uncaught JS/CORS/secret log. Test runtime đã dừng, không sửa dữ liệu hoặc mật khẩu development.

## 6. Files Changed

- `frontend/src/pages/ExecutionQueuePage.tsx`
- `frontend/src/pages/EquipmentListPage.tsx`
- `frontend/src/api/plansApi.ts`
- `frontend/src/api/equipmentApi.ts`
- `backend/src/main/java/vn/edu/medmaintenance/api/controller/PlanController.java`
- `backend/src/main/java/vn/edu/medmaintenance/api/controller/EquipmentController.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/repository/MaintenancePlanRepository.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/repository/MaintenancePlanItemRepository.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/repository/EquipmentRepository.java`
- `backend/src/test/java/vn/edu/medmaintenance/service/BackendBusinessFinalIntegrationTest.java`
- `frontend/src/pages/ReportingHistoryPage.test.tsx`
- `reports/version2/khoa_phong_audit_report.md`

Thay đổi trang duyệt BGĐ nằm trong [báo cáo BGĐ](bgd_audit_report.md). Tổng đầu việc: **12 file code/test sửa, 2 report mới**.

## 7. Remaining Limitations

Chờ human review UI, không freeze tự động. Browser smoke kiểm tra bàn giao đạt và sai khoa; nhánh FAIL/rework/repair dùng integration tests hiện có. Tra cứu đợt sau chuyển khoa tuân theo snapshot đã chấp nhận, không diễn giải quyền lịch sử thành chỉ khoa hiện tại. Không schema/migration/seed mới: baseline **V001–V008, 15 bảng / 126 cột / 32 FK**. REPAIR_REQUIRED là điểm bàn giao sang quy trình riêng; không triển khai sửa chữa trong đầu việc này.

## 8. Final Status

**PASS kỹ thuật** — UC10/UC12, scope, đồng ký VTYT, notification và regression đã kiểm chứng. Hàng chờ bàn giao và tra cứu được sửa tối thiểu; dừng đầu việc để human review.
