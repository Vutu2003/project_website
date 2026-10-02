# Canonical seed Version 2

`v2_canonical.sql` là baseline business local: 5 tài khoản, 5 khoa/phòng, 3 đơn vị, 8 thiết bị, 7 coverage FREE/NOT_FREE và 2 đợt bảo trì hoàn tất. Không có thông báo/yêu cầu phê duyệt đang chờ ban đầu.

Chạy `./backend/scripts/setup-v2.sh` từ project root để tạo DB, chạy Flyway toàn bộ migrations và seed nếu business tables còn trống. `local_credentials.py` đọc ignored security env và truyền BCrypt hashes qua stdin; SQL/source không chứa mật khẩu các tài khoản nghiệp vụ.

Tái chạy setup giữ dữ liệu hiện có. Reset chỉ thực hiện bằng `./database/scripts/reset_database.sh --yes`, có backup local trước khi drop.

Fixture lớn của regression nằm tại `database/tests/fixtures/`, chạy bằng `./backend/scripts/test-v2.sh` trong cluster tạm tự cleanup. Các mã và tài khoản demo ở fixture chỉ phục vụ assertion của test, không được seed vào DB chính.
