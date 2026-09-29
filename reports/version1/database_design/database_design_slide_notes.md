# Database Design — Ghi chú thuyết trình

**Bộ slide:** `slides/database_design/database_design_slides.pptx` (8 slide). Nội dung dùng bản Phase 1.1 **PASS / FROZEN**: 14 bảng, 31 FK, 117 cột. Các sơ đồ trong slide là bản trình bày lược giản; ERD đầy đủ nằm ở `database_design/erd.md`.

## Slide 1 — Mục tiêu thiết kế cơ sở dữ liệu

- Dẫn từ quy trình và use case tới yêu cầu dữ liệu, thực thể, ERD và contract triển khai.
- Nhấn mạnh Phase 1.1 chỉ thiết kế; chưa có database hay migration. Mô hình đã freeze để bàn giao Phase 1.2.
- **Nguồn:** `reports/phase_1_1_database_slide_source.md`; `reports/phase_1_1_database_design_freeze_report.md`; `database_design/phase_1_2_implementation_contract.md`.

## Slide 2 — Từ biểu mẫu đến mô hình dữ liệu

- BM01 dẫn tới kế hoạch và các hạng mục thiết bị; BM02 hỗ trợ phân loại coverage và vòng phê duyệt vendor; BM03 là báo cáo; BM06/BM08 là căn cứ cho nghiệm thu và bàn giao.
- Giải thích vì sao mô hình theo nghiệp vụ và quan hệ, không tạo một bảng cho mỗi biểu mẫu.
- **Nguồn:** `reports/phase_1_1_database_slide_source.md`; `reports/phase_1_1_database_design_freeze_report.md`; `database_design/data_dictionary.md`.

## Slide 3 — Kết quả design freeze

- So sánh baseline trước audit (20 bảng, 42 FK, 154 cột) với freeze (14 bảng, 31 FK, 117 cột).
- Phạm vi V1 vẫn bao phủ UC01–UC12 và BR01–BR05. Repair V2, vendor account và document store tổng quát nằm ngoài phạm vi.
- **Nguồn:** `reports/phase_1_1_database_design_freeze_report.md`; `reports/phase_1_1_database_slide_source.md`.

## Slide 4 — Nhóm bảng chính

- Giới thiệu đủ 14 bảng trong bốn nhóm: Master Data, Maintenance Workflow, Approval/Audit, Reporting.
- Nêu trục xử lý là kế hoạch, hạng mục, execution và nghiệm thu; các bảng audit/report đi kèm vòng đời đó.
- **Nguồn:** `database_design/erd.md`; `database_design/data_dictionary.md`; `reports/phase_1_1_database_design_freeze_report.md`.

## Slide 5 — Sơ đồ ERD tổng thể

- Trình bày đủ 14 thực thể. `maintenance_plan` và `maintenance_plan_item` là trung tâm; một thiết bị có thể xuất hiện trong nhiều kế hoạch thông qua nhiều plan item.
- Đường nối là quan hệ tiêu biểu để dễ nhìn khi thuyết trình; ERD chính thức trong `erd.md` có đủ 31 FK và bội số quan hệ.
- **Nguồn:** `database_design/erd.md`; `database_design/data_dictionary.md`.

## Slide 6 — Luồng bảo trì cốt lõi

- `maintenance_plan_item` nối thiết bị vào một kế hoạch; coverage/provider xác định tuyến thực hiện trước khi bắt đầu công việc.
- `maintenance_execution` lưu từng lần thực hiện; `maintenance_progress_log` lưu diễn biến; `acceptance_record` lưu đánh giá kỹ thuật và bàn giao; `maintenance_report` tổng hợp theo kế hoạch.
- Khi phải làm lại, tạo execution mới để giữ dấu vết cũ.
- **Nguồn:** `database_design/erd.md`; `database_design/data_dictionary.md`; `reports/phase_1_1_database_slide_source.md`.

## Slide 7 — Trạng thái, phê duyệt và audit

- Plan có đúng 8 trạng thái; plan item có đúng 11. Chuỗi trên slide chỉ nêu các mốc để thuyết trình, không thay thế danh sách/đồ thị chuyển trạng thái đầy đủ.
- Current status nằm trên plan/item; `status_history` lưu từng lần chuyển. `approval_request` là một vòng gửi duyệt, `approval_action` là quyết định của BGĐ.
- `REWORK_REQUIRED` quay lại một lần thực hiện mới; `REPAIR_REQUIRED` là bàn giao khỏi phạm vi bảo trì V1, không được tính là `COMPLETED`.
- **Nguồn:** `reports/phase_1_1_database_design_freeze_report.md`; `database_design/data_dictionary.md`; `database_design/phase_1_2_implementation_contract.md`.

## Slide 8 — Sẵn sàng cho Phase 1.2

- Kiểm tra độ bao phủ UC01–UC12, BR01–BR05, hai lifecycle, approval và audit.
- Dictionary, ERD, constraints và implementation contract đã freeze. Thay đổi bảng/cột/FK sau freeze phải được review trước khi triển khai.
- **Nguồn:** `reports/phase_1_1_database_design_freeze_report.md`; `database_design/erd.md`; `database_design/data_dictionary.md`; `database_design/phase_1_2_implementation_contract.md`.
