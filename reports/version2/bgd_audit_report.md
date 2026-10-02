# Version 2 — BAN_GIAM_DOC Audit

## 1. Scope

UC04, UC07, UC11 (quyền xem), UC12 và thông báo liên quan. Đối chiếu `docs/system_analysis_v1.pdf`: UC04 trang in 16–17, UC07 21–22, UC11 28–29, UC12 30–31; đọc thêm `docs/use_case.pdf`. Giữ thiết kế V2 đã chấp nhận: chuẩn bị FREE/NOT_FREE trước gửi duyệt, tự kích hoạt đề xuất ngoài khi duyệt kế hoạch, trả kế hoạch về hiệu chỉnh khi yêu cầu chọn lại đơn vị.

## 2. Audit Result

| UC | Mong đợi / triển khai hiện tại | Kết luận và sửa tối thiểu |
| --- | --- | --- |
| UC04 | SUBMITTED + PLAN_APPROVAL PENDING; APPROVE → APPROVED, yêu cầu chỉnh sửa có lý do → REVISION_REQUIRED; ApprovalAction/history trong transaction, phiên bản và quyết định trùng bị chặn. FREE giữ provider hợp đồng; NOT_FREE kích hoạt đúng một yêu cầu vendor đã chuẩn bị. | Backend **MATCH**. UI **GAP → MATCH**: bảng duyệt chưa hiện hình thức/provider/căn cứ và chỉ lấy 100 item đầu. Bổ sung các thông tin đã có trong DTO, ghi chú bảo hành và phân trang 20 item để đọc toàn bộ hồ sơ. |
| UC07 | Hiện kế hoạch, thiết bị, provider đề xuất, căn cứ/ghi chú. Chỉ duyệt provider trong request → ASSIGNED_EXTERNAL/EXTERNAL_APPROVED; yêu cầu chọn lại phải có lý do, trả kế hoạch APPROVED về REVISION_REQUIRED, hủy yêu cầu sibling đang chờ và giữ quyết định cũ. | **MATCH**, giữ nguyên service/UI quyết định. Test xác nhận VTYT hiệu chỉnh và gửi lại được; không ghi đè nội dung đã duyệt hoặc tạo yêu cầu active trùng. |
| UC11 | VTYT lập/chốt; BGĐ xem narrative, kết quả tổng hợp, các lần thực hiện, tiến độ, nghiệm thu và bàn giao. Refresh vẫn giữ FINAL; BGĐ không có form ghi và API ghi bị từ chối. | **MATCH**, không sửa chức năng báo cáo. |
| UC12 | BGĐ đọc rộng nhiều khoa; lịch sử có kế hoạch, route/provider, execution/progress, acceptance và tham chiếu báo cáo; không có command ghi lịch sử. | Phạm vi/backend lịch sử **MATCH**. Tra cứu **GAP → MATCH**: thêm ô tìm mã/tên/serial và tham số `search` trên GET equipment hiện có. Tìm không phân biệt hoa/thường, `%`, `_`, `!` được coi là ký tự literal; giữ phân trang và quyền truy cập. |

## 3. Authorization

UC04/UC07 chỉ BAN_GIAM_DOC tại security và service. VTYT/KHOA/ADMIN không quyết định được. UC11 chỉ VTYT ghi; BGĐ đọc. UC12 BGĐ/VTYT đọc rộng, KHOA theo khoa hiện tại hoặc snapshot của đợt lịch sử được phép; ADMIN nhận 403 khi đọc maintenance-history. Không thêm quyền hoặc endpoint mutation.

## 4. Notification Integration

**MATCH**: BGĐ active nhận PLAN_SUBMITTED, VENDOR_PENDING và REPORT_FINALIZED; VTYT nhận PLAN_APPROVED/PLAN_REVISION, VENDOR_APPROVED/VENDOR_REVISION và kết quả bàn giao. Kích hoạt vendor vẫn thông báo cho BGĐ vừa duyệt kế hoạch. Cơ chế hiện có ghi thông báo trong transaction; test inactive/ownership/rollback giữ PASS. Không sửa subsystem.

## 5. Tests

- **Backend focused: 77/77 PASS**, bốn suite hiện có: PlanningApproval, ProviderRouting, ExecutionAcceptance, BackendBusinessFinal. Bao phủ approve/revision/lý do/trùng/sai quyền, FREE/vendor activation, báo cáo chỉ đọc, scope và thông báo.
- **Backend full: 137 tests, 0 failures / 0 errors / 0 skips**, `env -u DEBUG mvn -f backend/pom.xml test`; regression VTYT, ADMIN, ADM-03, notification, auth/CORS, JPA/schema giữ PASS.
- **Frontend build/lint PASS; 130/130 tests PASS, 14 files**. Chạy đủ ba lệnh yêu cầu; thêm bốn ca vào file test reporting/history hiện có, không nhân đôi suite nghiệp vụ.
- **Chrome 154.0.8037.92 thật: 22 checks PASS** chung BGĐ/KHOA. VTYT gửi một plan NOT_FREE → BGĐ mở thông báo/hàng chờ, đọc provider/căn cứ, duyệt plan rồi vendor → KHOA bàn giao → API VTYT chốt report → BGĐ xem FINAL/evidence/history, refresh và tìm thiết bị. BGĐ finalize API trả 403. Nhánh revision/FREE kiểm chứng bằng integration tests, không mở rộng browser E2E.
- Lần đầu focused test dùng nhầm kỳ vọng 36 thiết bị; đổi sang count fixture thực tế 40 rồi focused/full đều PASS. Hai lần driver dừng do selector nút/mật khẩu, đã sửa tooling và chạy lại thành công; cả các lần dừng đều cleanup đúng baseline.
- Database cách ly `medical_maintenance_bgd_khoa_test`, PostgreSQL 55434 / backend 8081 / frontend 5174 / CDP 9335. Smoke thành công plan #272, item #350 đã xóa cùng tài khoản khoa thử và thông báo liên quan. Fingerprint **15 bảng, 603 dòng và schema** sau cleanup bằng trước smoke. Chỉ giữ metadata an toàn; không ghi credential trong report. Không có uncaught JS/CORS/secret log; 403 trình duyệt là hai kiểm tra sai khoa chủ động.

## 6. Files Changed

Các file liên quan BGĐ/tra cứu và kiểm chứng chung:

- `frontend/src/pages/ApprovalDetailPage.tsx`
- `frontend/src/pages/EquipmentListPage.tsx`
- `frontend/src/api/equipmentApi.ts`
- `backend/src/main/java/vn/edu/medmaintenance/api/controller/EquipmentController.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/repository/EquipmentRepository.java`
- `backend/src/test/java/vn/edu/medmaintenance/service/BackendBusinessFinalIntegrationTest.java`
- `frontend/src/pages/ReportingHistoryPage.test.tsx`
- `reports/version2/bgd_audit_report.md`

Các file lọc bàn giao được liệt kê trong [báo cáo Khoa/Phòng](khoa_phong_audit_report.md). Tổng đầu việc: **12 file code/test sửa, 2 report mới**.

## 7. Remaining Limitations

Chờ người dùng review các thay đổi UI; chưa tuyên bố freeze. Smoke ngắn dùng API chuẩn bị execution/technical/report và UI thật cho submit/approve/handover. Không kiểm thử browser mọi nhánh revision/rework; backend suite bao phủ. Không thay database/migration/seed: giữ **V001–V008, 15 bảng / 126 cột / 32 FK**. Các dịch vụ test được dừng; runtime development ban đầu đang dừng, dữ liệu và credential development không thay đổi.

## 8. Final Status

**PASS kỹ thuật** — UC04/UC07/UC11/UC12, phân quyền, thông báo và regression đã kiểm chứng; ba khoảng thiếu UI/tra cứu/bàn giao được sửa tối thiểu. Dừng đầu việc để human review.
