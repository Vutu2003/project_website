## Tổng quan theo vai trò

Sau đăng nhập, `/dashboard` mở tổng quan phù hợp với VTYT, BGD, khoa/phòng hoặc ADMIN. Menu **Tổng quan** đứng đầu cho mọi vai trò. Một yêu cầu `GET /api/dashboard` trả các DTO đọc theo vai trò đăng nhập; dữ liệu khoa/phòng được lọc tại backend theo phạm vi khoa trong kế hoạch. Không có thao tác thay đổi nghiệp vụ từ tổng quan.

VTYT có việc cần xử lý, kế hoạch quý hiện tại, tiến độ, hợp đồng và báo cáo. BGD có hồ sơ chờ duyệt, quyết định gần đây và báo cáo đã được gửi. Khoa/phòng có bàn giao, bảo trì đang thực hiện và lịch sử riêng. ADMIN có tài khoản, danh mục và các cảnh báo thiếu dữ liệu. Nút tạo kế hoạch truyền năm/quý; các thẻ phê duyệt và tài khoản truyền bộ lọc vào màn hình hiện có. Đường dẫn được bảo vệ trước đăng nhập vẫn được giữ lại.

Chrome smoke ngắn cho bốn vai trò: `./backend/scripts/test-v3-browser.sh --dashboard` (1366×768, database tạm).

## Version 3 — kế hoạch bảo trì theo quý

VTYT dùng **Danh sách hợp đồng → Danh sách thiết bị → Kế hoạch bảo trì → Theo dõi tiến độ bảo trì → Báo cáo bảo trì**.
Hợp đồng được tổ chức theo công ty → hợp đồng → thiết bị. Trang công ty có danh sách thiết bị dưới hợp đồng, kèm khoa/phòng, lịch quý và ngày bảo hành cụ thể. Ngày chưa có trong hồ sơ hiển thị “Chưa cập nhật”, không lấy ngày hết hạn hợp đồng làm ngày bảo hành.
VTYT cập nhật ba mức: **Đang bảo trì / Bảo trì xong / Có hỏng hóc**; hỏng hóc cần mô tả. Khi tất cả thiết bị có kết quả, VTYT xác nhận hoàn thành để tạo báo cáo nháp từ dữ liệu thực hiện. Sau khi kiểm tra và hoàn tất, VTYT gửi báo cáo cho BGĐ và các khoa/phòng có thiết bị trong kế hoạch. Khoa/phòng chỉ xem thiết bị của mình; hệ thống lưu người gửi, thời điểm và thông báo nhận báo cáo.
Tạo kế hoạch chỉ chọn năm/quý: backend sinh tên, thời gian và toàn bộ thiết bị có lịch trong quý.
Hợp đồng còn hạn tại ngày đầu quý tự xác định đơn vị; ngoài hợp đồng cần đề xuất đơn vị và căn cứ trước khi gửi duyệt.
BGĐ dùng **Phê duyệt → Báo cáo**, xem tất cả thiết bị trong một bảng. Ý kiến chỉnh sửa được giữ nguyên và hiện cho VTYT.

Migration **V011** thêm lịch quý, quan hệ hợp đồng–thiết bị và năm/quý kế hoạch; **V012** lưu nơi nhận báo cáo. Các migration giữ dữ liệu lịch sử.
Setup không xóa kế hoạch hoặc đổi mật khẩu. Áp dụng local:

```bash
source scripts/use-toolchain.sh
env -u DEBUG mvn -f backend/pom.xml -DskipTests package
./backend/scripts/setup-v3.sh
./scripts/stop.sh
./scripts/start.sh
```

Kiểm tra: `npm run build --prefix frontend`, `npm run lint --prefix frontend`, `npm run test --prefix frontend`,
`./backend/scripts/test-v2.sh`. Smoke Chrome ngắn: `./backend/scripts/test-v3-browser.sh` (database tạm, không thay đổi dữ liệu đang sử dụng).

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
