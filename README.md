## Bảo hành và danh sách bảo trì

VTYT dùng một mục **Thiết bị & bảo trì** tại `/equipment`, gộp danh mục, bảo hành, đề xuất và truy cập lịch sử. Đường dẫn `/maintenance-suggestions` cũ chuyển về mục này. Danh sách có tìm theo mã/tên/serial và lọc thiết bị đang/ngừng hoạt động.
Thiết bị trong biểu mẫu kế hoạch và theo dõi tiến độ dùng bảng. Bảng chọn thiết bị và bảng đã chọn hiển thị nhanh trạng thái, ngày hết bảo hành theo ngày bắt đầu kế hoạch hoặc ngày dự kiến của từng thiết bị.
Mỗi dòng có nút **Thông tin bảo hành** để xem thời hạn, trạng thái, hợp đồng và liên hệ.
VTYT/ADMIN có thể cập nhật nhà sản xuất hoặc đại diện (từ danh mục đơn vị) và ngày hết bảo hành trong hộp thoại này.

Migration V009 thêm `equipment.manufacturer_provider_id`,
`maintenance_coverage.warranty_expires_on` và `maintenance_plan_item.service_choice`.
Với hợp đồng hiện có, ngày kết thúc hợp đồng là ngày hết bảo hành ban đầu; có thể cập nhật lại theo hồ sơ thực tế.
Trạng thái được tính theo ngày tham chiếu, còn hiệu lực đến hết ngày hết bảo hành;
thiếu ngày thì hiển thị **Chưa rõ thời hạn**. Ngày kết thúc hợp đồng và ngày hết bảo hành được lưu riêng.

Ngoài hợp đồng hoặc hết bảo hành có hai phương án: **Liên hệ nhà sản xuất** và **Bảo hành ngoài**.
Phương án nhà sản xuất sử dụng đơn vị đã lưu trong hồ sơ thiết bị; cả hai vẫn cần căn cứ và BGĐ phê duyệt trước khi thực hiện.
Hệ thống lưu phương án và hiển thị thông tin liên hệ để VTYT chủ động liên hệ.
Backend tự áp dụng migration khi khởi động bằng `./scripts/start.sh` sau khi build JAR mới.

# Chạy project trên máy hiện tại

## Nền tảng dữ liệu Version 3

Runtime tiếp tục dùng `medical_maintenance_v2`. Baseline V3 có 10 khoa/phòng, 32 thiết bị,
5 tài khoản và 3 đơn vị bảo trì; không seed kế hoạch, lịch sử hoặc thông báo.

Khởi tạo trên máy mới hoặc bổ sung danh mục trên database hiện có:

```bash
./backend/scripts/setup-v3.sh
./scripts/start.sh
./scripts/status.sh
```

Chạy lại `setup-v3.sh` giữ nguyên kế hoạch và dữ liệu người dùng đã tạo.
Để chủ động xóa **toàn bộ kế hoạch và workflow hiện tại**, chuẩn bị lại cho một lượt E2E:

```bash
./scripts/stop.sh
./backend/scripts/prepare-v3.sh --yes
./scripts/start.sh
```

`prepare-v3.sh` tạo và kiểm tra backup tại `.local-postgres/backups/` trước khi xóa.
Khoa, thiết bị, coverage, đơn vị bảo trì và tài khoản hiện có được giữ nguyên.
Không đặt lại ID, nên kế hoạch mới có thể bắt đầu từ ID lớn hơn 1.
Đăng nhập ADMIN local: `admin / admin`. Tài khoản được tạo mới lấy mật khẩu từ env ignored.
Trên database đã sử dụng, setup và cleanup giữ nguyên mật khẩu hiện có; dùng mật khẩu đang đăng nhập.
Hai tài khoản khoa sẵn có là `khoa_noi` và `khoa_ngoai`; ADMIN có thể tạo tài khoản cho các khoa bổ sung.

Sau cleanup có thể kiểm tra baseline trước khi bắt đầu E2E:

```bash
source scripts/use-toolchain.sh
source .local-postgres/backend-dev.env
export PGPASSWORD="$DB_PASSWORD"
psql -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" \
  -f database/tests/validate_v3_baseline.sql
```

## Môi trường local Version 2

Database chính: `medical_maintenance_v2`. Khởi tạo trên máy mới:

```bash
source scripts/use-toolchain.sh
./backend/scripts/setup-v2.sh
./scripts/start.sh
./scripts/status.sh
```

Seed canonical gồm 5 tài khoản, 8 thiết bị ở nhiều khoa và 2 đợt lịch sử hoàn tất. Thông tin đăng nhập nằm trong `.local-postgres/backend-security.env` (ignored, mode 600). Tài khoản ADMIN local theo yêu cầu: `admin / admin`.

Regression backend dùng cluster tạm và fixture riêng, tự cleanup, không chạy mutation tests trên database chính:

```bash
./backend/scripts/test-v2.sh
npm run build --prefix frontend
npm run lint --prefix frontend
npm run test --prefix frontend
```

Các hướng dẫn Version 1 bên dưới là tài liệu lịch sử; dùng các lệnh Version 2 ở trên cho runtime hiện tại.

## Hướng dẫn Version 1 (lịch sử)

Đã cài và kiểm tra ngày 29/09/2026 trên Ubuntu 24.04.5 LTS, trong thư mục `/home/vutu0809/Desktop/LTNC/project_website`.

## Khởi động

```bash
cd /home/vutu0809/Desktop/LTNC/project_website
./scripts/start.sh
./scripts/status.sh
```

`start.sh` tự chọn toolchain, nạp cấu hình local và khởi động PostgreSQL, backend, frontend. Chạy lại sẽ dùng các tiến trình đúng project đang hoạt động. Không cần bật Conda hoặc nhập mật khẩu sudo.

Mở **http://localhost:5173/login** trên trình duyệt. Giữ địa chỉ `localhost:5173` để khớp CORS của backend.

| Dịch vụ | Địa chỉ |
| --- | --- |
| PostgreSQL 16 | 127.0.0.1:55432 |
| Backend Spring Boot | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Frontend React | http://localhost:5173/login |

## Đăng nhập demo

Mở tệp cục bộ `.local-postgres/backend-security.env` để lấy mật khẩu tương ứng:

| Tài khoản | Biến chứa mật khẩu |
| --- | --- |
| demo_vtyt | DEMO_VTYT_PASSWORD |
| demo_bgd | DEMO_BGD_PASSWORD |
| demo_khoa_noi | DEMO_KHOA_PASSWORD |
| demo_admin | DEMO_ADMIN_PASSWORD |

Tệp chứa mật khẩu và khóa JWT chỉ chủ máy có quyền đọc (mode 600), đã được Git bỏ qua. Các mật khẩu được tạo riêng cho bản clone này.

## Dừng

```bash
./scripts/stop.sh
```

Để dừng cả PostgreSQL của project:

```bash
./scripts/stop.sh --with-db
```

## Công cụ đã sẵn sàng

Java 17.0.20.1, Maven 3.9.11, Node.js 24.21.0, npm 11.19.0 nằm trong `.toolchain`. PostgreSQL 16.15 dùng binary sẵn có của Ubuntu. Curl 8.5.0 (gói Ubuntu có bản vá bảo mật) và Python bcrypt 3.2.2 được giải nén từ kho Ubuntu vào `.toolchain/runtime`; wrapper Python dùng `/usr/bin/python3` 3.12.3.

Dùng các công cụ từ terminal:

```bash
source scripts/use-toolchain.sh
java -version
mvn -version
node --version
npm --version
psql --version
curl --version
python3 -c 'import bcrypt; print(bcrypt.__version__)'
```

Maven lưu dependency trong `.toolchain/maven-repository`; npm đã cài theo `frontend/package-lock.json` và cache trong `.toolchain/npm-cache`.

Database dev riêng của project đã có 6 migration Flyway, 14 bảng nghiệp vụ, 603 dòng demo và 15 tài khoản. Cluster hệ thống trên cổng 5432 vẫn riêng biệt.

## Kiểm tra và build

Đã đạt: frontend build/lint/41 test, backend package/103 test, health UP, đăng nhập 4 tài khoản demo, API thiết bị, frontend và CORS.

```bash
source scripts/use-toolchain.sh
npm run build --prefix frontend
npm run lint --prefix frontend
npm run test --prefix frontend
env -u DEBUG mvn -B -ntp -f backend/pom.xml -DskipTests package
```

Sau khi build backend, chạy stop rồi start để dùng JAR mới.

Bộ test backend yêu cầu fixture demo chuẩn 603 dòng/15 tài khoản. Khi database vẫn ở fixture chuẩn, chạy:

```bash
source .local-postgres/backend-dev.env
source .local-postgres/backend-security.env
env -u DEBUG mvn -B -ntp -f backend/pom.xml test
```

## Log

- `.local-run/backend.log`, `.local-run/frontend.log`: dịch vụ ứng dụng.
- `.local-postgres/server.log`: PostgreSQL của project.
- `.local-run/installation-build.log`: build backend lúc cài đặt.
- `.local-run/installation-backend-tests.log`: 103 test backend lúc cài đặt.

Các thư mục `.toolchain`, `.local-postgres`, `.local-run`, `frontend/node_modules` và `backend/target` được Git bỏ qua.
