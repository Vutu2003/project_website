# Hướng dẫn kiểm thử thủ công — Quản lý tài khoản ADMIN, Version 2

Tài liệu dùng cho người kiểm thử thao tác website thực tế; kết quả và nhận xét UI/UX đều để trống. Không thay đổi các tài khoản canonical `demo_*` để thử vô hiệu hóa, reset mật khẩu hoặc đổi vai trò. Dùng tài khoản mới có tiền tố `SMOKE-V2-ADMIN-MANUAL-` và hậu tố duy nhất.

## Chuẩn bị

1. Mở Terminal tại repository, chạy `./scripts/start.sh`, rồi `./scripts/status.sh`; chờ backend health UP và frontend sẵn sàng.
2. Mở **http://localhost:5173/login**. Lấy mật khẩu ADMIN local theo [runtime guide V1, mục 7](../../version1/local_dev/local_v1_runtime_guide.md#7-how-to-get-current-demo-passwords); đăng nhập `demo_admin`. Không ghi mật khẩu vào phiếu/ảnh/chát.
3. Dùng profile/cửa sổ riêng cho ADMIN và tài khoản mới. Ứng dụng dùng sessionStorage theo tab; có thể tự mở tab mới và gõ URL đăng nhập. Nếu tab nhân bản mang phiên cũ, **Đăng xuất** tại tab đó rồi đăng nhập đúng người. Refresh giữ phiên nếu token còn hiệu lực.
4. Đặt tên riêng cho bốn tài khoản thử (VTYT, BGD, KHOA, ADMIN), ví dụ `SMOKE-V2-ADMIN-MANUAL-KHOA-<timestamp>`. Tự chọn mật khẩu, giữ riêng để nhập lại chính xác; chỉ ghi username/ID vào biên bản. Password không được trống, không yêu cầu độ phức tạp, tối đa 72 byte UTF-8. Username trim hai đầu, tối đa 100 ký tự, phân biệt hoa/thường và cố định sau khi tạo.
5. KHOA phải chọn khoa đang hoạt động; chọn **Khoa Nội** để thử thiết bị 001 được phép và 004 ngoài khoa. Với VTYT/ADMIN có thể chọn **Phòng Vật tư Y tế** hoặc để trống; BGD có thể để trống. Các vai trò khác cũng được chọn khoa hợp lệ nếu cần.

Menu mới **Quản lý tài khoản** chỉ dành cho ADMIN. Trang chi tiết có **Chỉnh sửa**, **Vô hiệu hóa**/**Kích hoạt**, **Đặt lại mật khẩu**, **Tải lại**; không có Xóa. Trạng thái **Hoạt động** cho phép đăng nhập, **Không hoạt động** chặn đăng nhập/request.

Các test dưới đây thao tác dữ liệu lưu thật. Helper start/stop không xóa dữ liệu manual. Sau kiểm thử, có thể vô hiệu hóa tài khoản manual không dùng; không tự xóa account đã có lịch sử. SQL cleanup trong báo cáo chỉ áp dụng cho các smoke account tự động đã được kiểm tra không có tham chiếu.

## Test 1 — Danh sách tài khoản

**Điều kiện:** ADMIN đã đăng nhập; dữ liệu canonical đủ để có nhiều trang.

1. Nhấn **Quản lý tài khoản**, kiểm tra **Danh sách tài khoản**, các cột **Tên đăng nhập**, **Vai trò**, **Khoa/Phòng**, **Trạng thái**, **Thao tác**.
2. Nhấn **Sau**, rồi **Trước**; kiểm tra dòng khác nhau, số trang và tổng bản ghi. Nhấn **Tải lại**.
3. Nhập một phần username vào **Tìm theo tên đăng nhập**, nhấn **Tìm kiếm**; thử từ khóa hoa/thường khác nhau.
4. Chọn **Vai trò**, **Khoa/Phòng**, **Trạng thái**; các dropdown áp dụng ngay. Kiểm tra các dòng đáp ứng đồng thời mọi bộ lọc.
5. Nhập từ khóa không có account, nhấn **Tìm kiếm**; kiểm tra thông báo rỗng. Nhấn **Xóa bộ lọc** để về danh sách.

**Kết quả mong đợi:**

- [ ] Danh sách và phân trang đúng, không lặp dòng giữa hai trang liền nhau trong dữ liệu ổn định.
- [ ] Search không phân biệt hoa/thường; lọc phối hợp đúng; empty state rõ; reload hoạt động.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Test 2 — Tạo PHONG_VTYT

**Điều kiện:** ADMIN đang ở danh sách; có username VTYT manual duy nhất và mật khẩu tự chọn.

1. Nhấn **Tạo tài khoản**, nhập **Tên đăng nhập**, **Mật khẩu**; chọn **Vai trò → Phòng Vật tư Y tế**.
2. Chọn **Khoa/Phòng → Phòng Vật tư Y tế** hoặc để trống; **Trạng thái → Hoạt động**; nhấn **Tạo tài khoản**.
3. Đọc thông báo thành công và **Chi tiết tài khoản**; ghi username/ID, nhấn F5.
4. Tại profile/tab mới, đăng nhập đúng username và chính mật khẩu ADMIN vừa chọn. Kiểm tra username/vai trò, menu **Kế hoạch bảo trì**, **Thực hiện bảo trì**, **Báo cáo**, **Thiết bị & lịch sử**.

**Kết quả mong đợi:**

- [ ] Tạo thành công, dữ liệu còn sau F5, không hiển thị lại mật khẩu.
- [ ] Tài khoản mới đăng nhập thật được, vai trò PHONG_VTYT và menu đúng.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Test 3 — Tạo BAN_GIAM_DOC

**Điều kiện:** ADMIN và một phiên người dùng riêng; username BGD mới.

1. Tại ADMIN, **Quản lý tài khoản → Tạo tài khoản**, nhập username/mật khẩu riêng.
2. Chọn **Vai trò → Ban Giám đốc**, để trống **Khoa/Phòng**, chọn **Hoạt động**, nhấn **Tạo tài khoản**.
3. Tại phiên riêng, **Đăng xuất** nếu đang dùng người khác; đăng nhập BGD mới bằng đúng mật khẩu đã cấp.
4. Kiểm tra **Báo cáo**, **Thiết bị & lịch sử**, **Phê duyệt**; mở **Phê duyệt** để đọc hàng chờ, chưa thay đổi quyết định canonical.

**Kết quả mong đợi:**

- [ ] Vai trò BAN_GIAM_DOC, department trống đúng lựa chọn, login và hàng chờ được phép.
- [ ] Không có menu quản lý tài khoản.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Test 4 — Tạo KHOA_PHONG

**Điều kiện:** Username KHOA mới; fixture 001 thuộc Khoa Nội, 004 thuộc Khoa Cấp cứu.

1. Tại ADMIN, mở **Tạo tài khoản**; nhập username/mật khẩu; chọn **Vai trò → Khoa/Phòng**.
2. Chọn **Khoa/Phòng → Khoa Nội**, **Hoạt động**, nhấn **Tạo tài khoản**; kiểm tra chi tiết.
3. Đăng nhập account mới ở phiên riêng; kiểm tra vai trò Khoa/Phòng, menu **Thiết bị & lịch sử**, **Bàn giao**.
4. Mở **Thiết bị & lịch sử**, tìm 001, nhấn **Xem lịch sử**; kiểm tra dữ liệu trong khoa.
5. Gõ `http://localhost:5173/equipment/4/history`: kiểm tra thông báo không có quyền, không có tên/lịch sử thiết bị ngoài khoa.

**Kết quả mong đợi:**

- [ ] KHOA có department Khoa Nội và login thành công.
- [ ] 001 được đọc; 004 bị 403 theo fixture hiện tại; không lộ dữ liệu ngoài khoa.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Test 5 — Tạo ADMIN

**Điều kiện:** Username ADMIN manual mới, giữ demo_admin làm phiên quản lý gốc.

1. Tại demo_admin, tạo account mới với **Vai trò → Quản trị hệ thống**, khoa tùy chọn hợp lệ, **Hoạt động**.
2. Đăng nhập ADMIN mới ở profile riêng, nhấn **Quản lý tài khoản**, kiểm tra danh sách.
3. Mở chi tiết chính ADMIN manual đang đăng nhập ở profile này, nhấn **Vô hiệu hóa**, xác nhận.
4. Kiểm tra lỗi không được vô hiệu hóa tài khoản đang đăng nhập của chính mình; refresh vẫn hoạt động. Không đổi vai trò/vô hiệu hóa demo_admin.

**Kết quả mong đợi:**

- [ ] ADMIN mới quản lý account được, chỉ có menu Tổng quan/Quản lý tài khoản.
- [ ] Self-deactivation bị 409, active vẫn true; không có quyền bảo trì UC01–UC12 mới cho ADMIN.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Test 6 — Duplicate username

**Điều kiện:** Có một username manual đã tạo; phiên ADMIN gốc.

1. Nhấn **Tạo tài khoản**, nhập lại chính username manual đã tồn tại (có thể thêm khoảng trắng ở hai đầu).
2. Nhập mật khẩu thử riêng, chọn vai trò/khoa hợp lệ và **Hoạt động**, nhấn **Tạo tài khoản**.
3. Kiểm tra **Tên đăng nhập đã tồn tại.**; về danh sách và tìm exact username để đối chiếu chỉ có một dòng.

**Kết quả mong đợi:**

- [ ] Trùng username sau trim bị 409 USERNAME_ALREADY_EXISTS; không tạo thêm account.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Test 7 — KHOA không chọn khoa

**Điều kiện:** Phiên ADMIN, username mới chưa dùng.

1. Mở **Tạo tài khoản**, nhập username/mật khẩu, chọn **Vai trò → Khoa/Phòng**.
2. Giữ **Khoa/Phòng → Không chọn khoa/phòng**, nhấn **Tạo tài khoản**.
3. Kiểm tra thông báo bắt buộc chọn khoa và chưa chuyển sang chi tiết; chọn Khoa Nội, nhấn **Tạo tài khoản** nếu muốn tiếp tục ca hợp lệ.

**Kết quả mong đợi:**

- [ ] Giao diện chặn thiếu khoa; backend cũng yêu cầu department cho KHOA_PHONG.
- [ ] Không lưu một account KHOA thiếu khoa.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Test 8 — Disable account

**Điều kiện:** Account KHOA manual đang active và đăng nhập ở cửa sổ thứ hai; không dùng account canonical.

1. Tại phiên người dùng, mở lịch sử 001 và giữ trang đang đăng nhập.
2. Tại ADMIN gốc, tìm account KHOA manual, **Xem** để mở chi tiết; nhấn **Vô hiệu hóa**, xác nhận.
3. Kiểm tra **Không hoạt động**; tại phiên người dùng, nhấn **Tải lại** trên lịch sử hoặc F5 để phát sinh request.
4. Kiểm tra phiên bị đưa về đăng nhập; thử login lại bằng đúng thông tin cũ.

**Kết quả mong đợi:**

- [ ] Active false lưu thật; request với JWT đang có bị 401 và phiên tab bị xóa.
- [ ] Fresh login bị từ chối với thông báo tên đăng nhập/mật khẩu không đúng; không coi đó là reset password.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Test 9 — Enable account

**Điều kiện:** Account manual vừa bị disable ở Test 8; biết mật khẩu hiện tại.

1. Tại ADMIN gốc, mở chi tiết account không hoạt động, nhấn **Kích hoạt**, xác nhận.
2. Kiểm tra **Hoạt động** và refresh giữ trạng thái.
3. Tại phiên người dùng, đăng nhập bằng username/mật khẩu trước disable, mở màn hình được phép.

**Kết quả mong đợi:**

- [ ] Active true; đăng nhập trở lại thành công với mật khẩu chưa đổi.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Test 10 — Reset password

**Điều kiện:** Account manual active; biết mật khẩu cũ và chọn mật khẩu mới khác, giữ riêng.

1. Tại ADMIN gốc, mở chi tiết account, nhấn **Đặt lại mật khẩu**.
2. Nhập **Mật khẩu mới**, nhập **Xác nhận mật khẩu mới** khác lần đầu, nhấn **Lưu mật khẩu mới**; kiểm tra bị chặn không khớp.
3. Nhập xác nhận đúng, nhấn **Lưu mật khẩu mới**, chấp nhận hộp xác nhận; kiểm tra thông báo thành công và các trường mật khẩu không còn.
4. Tại phiên người dùng, **Đăng xuất**, thử mật khẩu cũ: phải thất bại. Sau đó nhập mật khẩu mới: phải thành công.
5. Refresh trang chi tiết ADMIN, kiểm tra role/khoa/status không đổi và không hiện mật khẩu.

**Kết quả mong đợi:**

- [ ] Hai trường bắt buộc và khớp nhau; password được thay bằng BCrypt, không lộ trên UI/API.
- [ ] Old password không login được; new password login được. JWT cấp trước reset không tự bị thu hồi theo mô hình V1; thử login mới bằng cách Đăng xuất.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Test 11 — Edit role/department

**Điều kiện:** Account KHOA manual active; phiên người dùng còn đăng nhập. Không sửa role/khoa canonical.

1. Tại ADMIN, mở chi tiết account, nhấn **Chỉnh sửa**; kiểm tra **Tên đăng nhập** chỉ đọc.
2. Giữ **Vai trò → Khoa/Phòng**, đổi **Khoa/Phòng → Khoa Ngoại**, nhấn **Lưu chỉnh sửa**.
3. Tại người dùng, F5: Tổng quan phải cập nhật mã khoa 8 theo fixture. Thử lại `/equipment/1/history`: bị từ chối; `/equipment/38/history`: được phép (có thể chưa có đợt).
4. Tại ADMIN, **Chỉnh sửa** cùng account, chọn **Vai trò → Ban Giám đốc**, để trống khoa, **Lưu chỉnh sửa**.
5. Tại người dùng, F5 hoặc đăng xuất/login lại bằng cùng mật khẩu: thấy menu BGD, mở **Phê duyệt** được. Username/status/password không bị đổi bởi form edit.

**Kết quả mong đợi:**

- [ ] Role/khoa mới áp dụng ở backend ngay request tiếp theo; nav frontend cập nhật sau refresh/login.
- [ ] KHOA đổi khoa có scope mới; đổi BGD cho phép hàng chờ, không có ADMIN management.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Test 12 — Authorization

**Điều kiện:** Có phiên VTYT, BGD, KHOA hoặc dùng demo tương ứng chỉ đọc; phiên ADMIN riêng.

1. Đăng nhập từng vai trò VTYT, BGD, KHOA; kiểm tra không có menu **Quản lý tài khoản**.
2. Gõ `http://localhost:5173/admin/accounts`, rồi thử `/admin/accounts/new`: phải hiện **Không có quyền xem trang**.
3. Quay lại **Tổng quan** hoặc menu được phép để xác nhận phiên vẫn dùng được.
4. Tại ADMIN, mở cùng trang: được phép. Các kiểm tra backend 403 cho cả bảy endpoint đã có trong bằng chứng tự động; không cần tự sao chép JWT vào biên bản.

**Kết quả mong đợi:**

- [ ] Ba vai trò non-admin bị chặn route; backend bảo vệ độc lập, không chỉ ẩn menu.
- [ ] ADMIN quản lý account được, không phát sinh quyền phê duyệt/bảo trì/báo cáo/history nghiệp vụ UC01–UC12.

PASS / FAIL:
[ ]

Kết quả thực tế (Actual result):
[ ]

Ghi chú (Notes):
[ ]

Nhận xét UI/UX:
[ ]

## Tổng hợp của người kiểm thử

Tổng số test PASS:
[ ]

Tổng số test FAIL:
[ ]

Cần kiểm tra lại / blocker:
[ ]

Nhận xét UI/UX chung:
[ ]

Có thể chọn Version 2 làm phiên bản cuối:
[ ]

Sau review, quyết định bước tiếp theo; tài liệu này không yêu cầu triển khai Repair Workflow, email, MFA, deployment hoặc NFR tổng quát.
