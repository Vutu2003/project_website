# Canonical seed local

## Nền tảng Version 3

`v3_canonical.sql` dùng danh mục V2 rồi bổ sung `v3_catalog.sql`: tổng 10 khoa/phòng,
32 thiết bị, 31 coverage (16 FREE / 15 NOT_FREE), 5 tài khoản và 3 đơn vị.
TB-008 giữ nguyên trạng thái không có coverage. Không seed dữ liệu workflow.

`./backend/scripts/setup-v3.sh` tạo baseline này trên database trống; trên database đã có
dữ liệu chỉ bổ sung danh mục, không xóa kế hoạch hoặc đổi tài khoản.
`./backend/scripts/prepare-v3.sh --yes` yêu cầu dừng backend trước, backup được kiểm tra,
rồi xóa toàn bộ workflow trong một transaction và bổ sung danh mục.
Các master rows hiện có được đối chiếu ngay trong transaction để bảo đảm không thay đổi.

`database/tests/validate_v3_baseline.sql` kiểm tra trạng thái trống trước một lượt E2E.
Không dùng validator baseline sau khi đã tạo kế hoạch thủ công.

## Baseline Version 2 (giữ để tái tạo)

`v2_canonical.sql` gồm `v2_catalog.sql` và lịch sử: 5 tài khoản, 5 khoa/phòng, 3 đơn vị, 8 thiết bị, 7 coverage FREE/NOT_FREE và 2 đợt bảo trì hoàn tất. Không có thông báo/yêu cầu phê duyệt đang chờ ban đầu.

Chạy `./backend/scripts/setup-v2.sh` từ project root để tạo DB, chạy Flyway toàn bộ migrations và seed nếu business tables còn trống. `local_credentials.py` đọc ignored security env và truyền BCrypt hashes qua stdin; SQL/source không chứa mật khẩu các tài khoản nghiệp vụ.

Tái chạy setup giữ dữ liệu hiện có. Reset chỉ thực hiện bằng `./database/scripts/reset_database.sh --yes`, có backup local trước khi drop.

Fixture lớn của regression nằm tại `database/tests/fixtures/`, chạy bằng `./backend/scripts/test-v2.sh` trong cluster tạm tự cleanup. Các mã và tài khoản demo ở fixture chỉ phục vụ assertion của test, không được seed vào DB chính.
