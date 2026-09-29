# Speaker notes — Database Design (updated)

Bộ 12 slide thay thế phần Database cũ. Phong cách được đối chiếu với `slides/database_design/database_design_slides.pptx` và `docs/temp_slide.png`: nền trắng, Times New Roman, xanh đậm/teal, card viền mảnh.

## Slide 01 — Vai trò của cơ sở dữ liệu

- **Mục tiêu / ý cần nói:** Giải thích CSDL giữ mối nối từ thiết bị tới kế hoạch, thực hiện, nghiệm thu và báo cáo. Phân biệt trạng thái hiện tại với bằng chứng quá khứ. REPAIR_REQUIRED chỉ là điểm bàn giao của V1.
- **Nguồn:** `reports/phase_1_1_database_design_freeze_report.md`; `database_design/erd.md`; `database_design/data_dictionary.md`.

## Slide 02 — Từ biểu mẫu đến mô hình dữ liệu

- **Mục tiêu / ý cần nói:** Dẫn BM01, BM02, BM03, BM06/BM08 tới nhóm dữ liệu tương ứng. Nhấn mạnh biểu mẫu xác định thông tin cần lưu, còn UC và BR quyết định quan hệ, trạng thái và lịch sử.
- **Nguồn:** `reports/phase_1_1_database_design_freeze_report.md`; `database_design/data_dictionary.md`.

## Slide 03 — Vì sao không mỗi biểu mẫu một bảng?

- **Mục tiêu / ý cần nói:** Nêu ví dụ tên thiết bị bị lặp trong nhiều form. Giải thích equipment có định danh riêng, PlanItem nối kế hoạch với thiết bị, execution/acceptance giữ từng lần xử lý; nhờ vậy UC12 có lịch sử đúng.
- **Nguồn:** `reports/phase_1_1_database_design_freeze_report.md`; `database_design/erd.md`; `database_design/data_dictionary.md`.

## Slide 04 — Thiết kế được cô đọng như thế nào?

- **Mục tiêu / ý cần nói:** Đọc before/after đúng phạm vi design audit: 20→14 bảng, 42→31 FK, 154→117 cột. Nêu vendor_proposal, maintenance_assignment và attachment được gộp hoặc lùi; các vòng đời bắt buộc vẫn còn.
- **Nguồn:** `reports/phase_1_1_database_design_freeze_report.md`.

## Slide 05 — 14 bảng theo 4 nhóm chức năng

- **Mục tiêu / ý cần nói:** Đi qua bốn nhóm theo thứ tự: dữ liệu nền, luồng bảo trì, duyệt/audit, báo cáo. Đây là danh mục cuối của mô hình V1, không gồm bảng Repair V2.
- **Nguồn:** `database_design/erd.md`; `database_design/data_dictionary.md`; `reports/phase_1_1_database_design_freeze_report.md`.

## Slide 06 — ERD tổng thể sau design freeze

- **Mục tiêu / ý cần nói:** Chỉ vào Plan/PlanItem là trung tâm, equipment là điểm truy vết và các nhánh execution, approval, report, history. Sơ đồ được giản lược từ ERD nguồn: đủ 14 bảng nhưng chỉ vẽ các liên kết tiêu biểu; ERD nguồn ghi đủ 31 FK.
- **Nguồn:** `database_design/erd.md`; `database_design/data_dictionary.md`.

## Slide 07 — Plan và PlanItem: quyết định cốt lõi

- **Mục tiêu / ý cần nói:** Một plan là một đợt gửi duyệt và tổng hợp; PlanItem là một thiết bị trong đợt với trạng thái độc lập. Một thiết bị có thể tham gia nhiều đợt; đó là nền tảng của truy vấn UC12.
- **Nguồn:** `reports/phase_1_1_database_design_freeze_report.md`; `database_design/erd.md`; `database_design/data_dictionary.md`; `database/seeds/dataset_catalog.md`.

## Slide 08 — Trạng thái, phê duyệt và truy vết

- **Mục tiêu / ý cần nói:** Plan có 8, PlanItem có 11 trạng thái theo từ điển. ApprovalRequest giữ từng vòng gửi, ApprovalAction giữ quyết định BGĐ, StatusHistory ghi ai đổi trạng thái lúc nào và vì sao. Gửi lại tạo vòng mới.
- **Nguồn:** `database_design/data_dictionary.md`; `database_design/erd.md`; `reports/phase_1_1_database_design_freeze_report.md`.

## Slide 09 — Triển khai bằng PostgreSQL và SQL migration

- **Mục tiêu / ý cần nói:** PostgreSQL 16 thực thi FK, CHECK, UNIQUE và transaction. Sáu SQL migration versioned tạo schema theo thứ tự; sáu seed file chạy trong một transaction; reset/migrate/seed/validate có thể lặp. Flyway là điểm tích hợp dự kiến, chưa được triển khai.
- **Nguồn:** `reports/phase_1_2_database_implementation_report.md`; `reports/phase_1_3_demo_data_report.md`.

## Slide 10 — Demo data được tạo như thế nào?

- **Mục tiêu / ý cần nói:** Nêu 603 dòng, 40 thiết bị, 8 plan, 52 item. Chỉ tên nhà sản xuất/model là tham khảo công khai được xác minh. Mã thiết bị, serial, nhân sự, provider, coverage và toàn bộ hoạt động là dữ liệu tổng hợp; không phải dữ liệu bệnh viện thật.
- **Nguồn:** `reports/phase_1_3_demo_data_report.md`; `database/seeds/dataset_catalog.md`; `database/seeds/data_sources.md`.

## Slide 11 — Dataset chứng minh những luồng nào?

- **Mục tiêu / ý cần nói:** Dẫn người nghe qua chín kịch bản DS01–DS09. Phân biệt đường bình thường, chờ duyệt và các ngoại lệ; UC12 cho thấy một thiết bị xuất hiện trong nhiều kế hoạch. Demo queries đã có các truy vấn phục vụ màn hình/API tương lai.
- **Nguồn:** `database/seeds/demo_scenarios.md`; `database/seeds/dataset_catalog.md`; `database/tests/demo_queries.sql`; `reports/phase_1_3_demo_data_report.md`.

## Slide 12 — Cơ sở dữ liệu sẵn sàng cho backend

- **Mục tiêu / ý cần nói:** Tóm tắt ranh giới: DB giữ toàn vẹn cục bộ và dữ liệu truy vết; backend sẽ kiểm soát transition, RBAC và transaction nghiệp vụ. Clean rebuild/validation là bằng chứng tái dựng, không phải tuyên bố hiệu năng. Phase tiếp theo là Spring Boot Backend Foundation.
- **Nguồn:** `reports/phase_1_2_database_implementation_report.md`; `reports/phase_1_3_demo_data_report.md`; `database_design/data_dictionary.md`; `database/tests/demo_queries.sql`.
