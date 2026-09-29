# Chạy project trên máy hiện tại

Đã cài và kiểm tra ngày 29/09/2026 trên Ubuntu 26.04.1. Database, backend và frontend đang chạy sau khi thiết lập.

## Mở ứng dụng

Truy cập **http://localhost:5173/login** bằng trình duyệt.

Tài khoản demo: `demo_vtyt`, `demo_bgd`, `demo_khoa_noi`, `demo_admin`.
Mật khẩu tương ứng nằm trong tệp cục bộ `.local-postgres/backend-security.env`, theo các biến `DEMO_VTYT_PASSWORD`, `DEMO_BGD_PASSWORD`, `DEMO_KHOA_PASSWORD`, `DEMO_ADMIN_PASSWORD`. Mật khẩu hiện có được giữ nguyên.

## Khởi động lại sau khi bật máy

```bash
cd /home/vutu/Desktop/LTNC/project
./scripts/start.sh
./scripts/status.sh
```

Script tự chọn công cụ trong `.toolchain`, nạp cấu hình local và khởi động cả ba dịch vụ. Chạy lại start khi đang chạy sẽ dùng lại các tiến trình đúng project.

| Dịch vụ | Địa chỉ |
| --- | --- |
| PostgreSQL 16 | 127.0.0.1:55432 |
| Backend Spring Boot | http://localhost:8080 |
| Backend health | http://localhost:8080/actuator/health — status UP |
| Frontend React | http://localhost:5173/login |

Giữ URL frontend là `localhost:5173` để khớp cấu hình CORS của backend.

## Dừng ứng dụng

```bash
cd /home/vutu/Desktop/LTNC/project
./scripts/stop.sh
```

Lệnh này dừng backend/frontend và giữ PostgreSQL chạy. Để dừng cả ba:

```bash
./scripts/stop.sh --with-db
```

## Dùng công cụ trong terminal

```bash
cd /home/vutu/Desktop/LTNC/project
source scripts/use-toolchain.sh
java -version
mvn -version
node --version
npm --version
psql --version
curl --version
```

Java 17.0.20.1, Maven 3.9.11, Node 24.21.0, npm 11.19.0, PostgreSQL 16.15 và curl 8.18.0 đã hoạt động. PostgreSQL/curl được giải nén từ gói chính thức vào `.toolchain/runtime`; không cần sudo khi chạy project.

## Build và test

```bash
source scripts/use-toolchain.sh
npm run build --prefix frontend
npm run lint --prefix frontend
npm run test --prefix frontend
env -u DEBUG mvn -B -ntp -f backend/pom.xml -Dmaven.repo.local=.toolchain/maven-repository -DskipTests package
```

Để thay JAR đang chạy bằng bản vừa build, chạy stop rồi start.

Toàn bộ 103 test backend đã đạt trên cluster thử riêng ở cổng 55433. Cluster thử có fixture chuẩn 603 dòng, 15 tài khoản; database bạn đang dùng có 604 dòng, 16 tài khoản nên các assertion yêu cầu fixture chuẩn cần chạy trên cluster thử. Không reset database chính để chạy test.

Chạy lại bộ test backend bằng cluster thử đã được chuẩn bị:

```bash
cd /home/vutu/Desktop/LTNC/project
source scripts/use-toolchain.sh
"${LTNC_PG_BIN}/pg_ctl" -D "$PWD/.local-postgres/test-data"   -l "$PWD/.local-postgres/test-server.log"   -o "-p 55433 -c listen_addresses=127.0.0.1 -k $PWD/.local-postgres/test-run" start
source .local-postgres/backend-dev.env
source .local-postgres/backend-security.env
DB_PORT=55433 env -u DEBUG mvn -B -ntp -f backend/pom.xml   -Dmaven.repo.local=.toolchain/maven-repository test
"${LTNC_PG_BIN}/pg_ctl" -D "$PWD/.local-postgres/test-data" -m fast stop
```

Cluster thử hiện đã dừng. PostgreSQL chính ở 55432 vẫn đang chạy.

## Log và bản sao lưu

- `.local-run/backend.log`, `.local-run/frontend.log`: log ứng dụng.
- `.local-postgres/server.log`: log database.
- `.local-run/environment-backend-full-test.log`: kết quả 103 test backend.
- `.local-postgres/backups/pre-runtime-install-20260929.tar.gz`: bản sao lưu offline dữ liệu/env trước khi sửa cấu trúc cluster.
- `.local-postgres/admin.env`: tên role quản trị cũ của cluster để các script chạy được sau khi chuyển máy.

Các thư mục `.toolchain`, `.local-postgres`, `.local-run` đã nằm trong gitignore.
