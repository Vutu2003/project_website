# Chạy project trên máy hiện tại

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
