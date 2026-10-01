# V2 — Quản lý tài khoản ADMIN

**Trạng thái:** chức năng PASS qua kiểm thử tự động và Chrome; đánh giá UI/UX của người dùng còn chờ. Tài liệu này gộp thiết kế, triển khai, bằng chứng và hướng dẫn kiểm thử thủ công của Account Management, gồm cải tiến hiện/ẩn mật khẩu.

## 1. Phạm vi và dữ liệu

ADMIN quản lý tài khoản của bốn vai trò PHONG_VTYT, BAN_GIAM_DOC, KHOA_PHONG, ADMIN: xem danh sách/chi tiết, tìm và lọc, tạo, sửa vai trò/khoa, kích hoạt/vô hiệu hóa, đặt lại mật khẩu. Không có hard delete, email, MFA hoặc quyền bảo trì mới cho ADMIN.

**Mốc Account Management:** không thay đổi schema hay seed. V001–V006 đã có user_account với username duy nhất (phân biệt hoa/thường), password_hash, role_code, department_id, display_name và active; KHOA_PHONG bắt buộc có khoa. Tại mốc này baseline là 14 bảng, 117 cột, 31 FK, 603 dòng demo. **Trạng thái hiện tại sau ADM-03 Catalog Management:** V007 đã thêm mã provider, nên schema chung là 14 bảng, 118 cột, 31 FK. V007 không được tính là thay đổi của Account Management.

## 2. Quy tắc và bảo mật

- Username bắt buộc, trim hai đầu, tối đa 100 ký tự, duy nhất theo đúng chữ hoa/thường; không sửa sau khi tạo. Tìm kiếm là chuỗi con không phân biệt hoa/thường, escape wildcard.
- Mật khẩu bắt buộc, không blank, giữ đúng nội dung người dùng nhập; BCrypt cost 12 trước khi lưu. Giới hạn 72 byte UTF-8 do BCrypt; không đặt yêu cầu độ phức tạp hay độ dài tối thiểu. display_name mặc định bằng username.
- KHOA_PHONG phải có khoa. Các vai trò khác có thể không gắn khoa. Khoa mới được chọn phải tồn tại và active. Sau ADM-03, form chỉ liệt kê khoa active cho gán mới; khoa inactive đã gắn vẫn hiện trong chi tiết và có thể giữ nguyên khi sửa account không đổi vai trò/khoa.
- Trạng thái đặt khi tạo, sau đó đổi bằng action riêng. ADMIN không thể tự vô hiệu hóa account đang đăng nhập (409). Sửa account chỉ đổi role/department; đặt lại mật khẩu là action riêng.
- SecurityConfig và service đều kiểm tra ADMIN. AccountResponse chỉ có id, username, role, departmentId, departmentCode, departmentName, active; không trả hash, password hoặc token. Đọc/ghi dùng transaction, row lock cho các thay đổi account và unique constraint cho create đồng thời.
- Account inactive bị chặn cả fresh login và request tiếp theo với JWT cũ. Role/khoa đổi được backend áp dụng ở request tiếp theo; frontend cập nhật menu khi refresh hoặc đăng nhập lại. Reset password đổi thông tin đăng nhập về sau; JWT đã phát vẫn hợp lệ đến khi hết hạn hoặc account bị vô hiệu hóa, theo mô hình V1.

## 3. API và giao diện

Tất cả endpoint sau đều chỉ dành cho ADMIN, dùng ErrorResponse hiện có (400 dữ liệu không hợp lệ, 403 sai vai trò, 404 không tìm thấy, 409 xung đột):

| Method | Endpoint | Chức năng |
| --- | --- | --- |
| GET | /api/admin/accounts | Phân trang, search, role, departmentId, active; phối hợp bộ lọc |
| GET | /api/admin/accounts/{id} | Chi tiết an toàn |
| POST | /api/admin/accounts | Tạo account, trả 201 |
| PATCH | /api/admin/accounts/{id} | Sửa role/department |
| POST | /api/admin/accounts/{id}/activate | Kích hoạt |
| POST | /api/admin/accounts/{id}/deactivate | Vô hiệu hóa, có self-protection |
| POST | /api/admin/accounts/{id}/reset-password | Đặt lại mật khẩu do ADMIN chọn |

Trang /admin/accounts, /admin/accounts/new, /admin/accounts/:id, /admin/accounts/:id/edit đều có RoleGuard ADMIN. Danh sách có tìm kiếm, lọc vai trò/khoa/trạng thái, phân trang và tải lại. Form tạo có username, mật khẩu, vai trò, khoa, trạng thái; chi tiết có chỉnh sửa, xác nhận đổi trạng thái và reset password với xác nhận khớp. UI dùng central API client, component/status/style navy–teal–gray của ứng dụng; dữ liệu tải/trống/lỗi đều có phản hồi.

**Hiện/ẩn mật khẩu:** PasswordInput áp dụng cho 5 ô: login (1), tạo account (1), reset và xác nhận (2), mật khẩu đồng ký VTYT ở handover (1). Mặc định che, nút Hiện/Ẩn độc lập giữ nguyên giá trị, không submit form; có nhãn accessible, aria-controls, aria-pressed và focus. Giá trị chỉ ở state của component, không lưu URL/storage/log hay ảnh bằng chứng. Đây là thay đổi frontend, không đổi API/BCrypt/JWT/schema.

## 4. Kiểm chứng và bằng chứng

| Mốc kiểm chứng | Kết quả |
| --- | --- |
| Account Management trước ADM-03 | Frontend build/lint PASS, 36/36 tests; backend 103 tests, 0 failure/error/skip |
| Cải tiến hiện/ẩn mật khẩu | Frontend build/lint PASS, 41/41 tests; Chrome thật kiểm tra login/create/reset/co-sign và 1366/760/390/320 px |
| Regression chung sau ADM-03 | Frontend build/lint PASS, 57/57 tests; backend 106 tests, 0 failure/error/skip trên DB cách ly |

Chrome 154 đã chạy **76 kiểm tra trực tiếp** cho Account Management: tạo bốn account smoke bằng UI, đăng nhập từng vai trò, kiểm tra /api/auth/me và menu, phân trang/tìm/lọc, KHOA đọc thiết bị đúng phạm vi, từ chối duplicate và thiếu khoa, vô hiệu hóa phiên đang dùng, kích hoạt, reset mật khẩu cũ/mới, đổi khoa/vai trò trên token hiện có, chặn self-deactivation và chặn non-admin. Tài khoản ADMIN mới vẫn bị 403 với lệnh bảo trì. Không thấy lỗi JS/CORS bất ngờ hoặc dữ liệu mật khẩu trong evidence.

[Bằng chứng đã che thông tin nhạy cảm](v2_admin_account_management_evidence.json), [driver Chrome](verify_v2_admin_accounts.py), [ảnh danh sách](screens/v2_admin_account_list.png), [ảnh chi tiết](screens/v2_admin_account_detail.png), [manifest nguồn](v2_admin_source_change_manifest.json) và [patch V1→V2 ở mốc account](v1_to_v2_product.patch) được giữ riêng vì chúng là bằng chứng/kỹ thuật, không phải báo cáo Markdown khác. Patch này ghi nhận mốc Account Management, không bao gồm thay đổi ADM-03 về sau.

Hai lỗi runtime V1 được sửa tối thiểu trong giai đoạn account: tiến trình PostgreSQL kế thừa FD 9 làm kẹt lock start/stop; Vite HMR thêm query vào main.tsx khiến readiness báo DOWN dù trang trả 200. Cả hai được kiểm tra lại với cold start/restart/status; không đổi nghiệp vụ. Bốn smoke account Chrome được xóa bằng transaction có guard ID/username và kiểm tra FK; fingerprint 14 bảng trở về baseline của mốc account.

## 5. Hướng dẫn kiểm thử thủ công

**Chuẩn bị:** chạy ./scripts/start.sh rồi ./scripts/status.sh; mở http://localhost:5173/login và đăng nhập ADMIN bằng mật khẩu local theo [runtime guide V1](../../version1/local_dev/local_v1_runtime_guide.md#7-how-to-get-current-demo-passwords). Không ghi mật khẩu trong biên bản. Tạo account riêng với tiền tố SMOKE-V2-ADMIN-MANUAL- và hậu tố duy nhất; không sửa account demo. Dùng tab/profile riêng cho người dùng thử, đăng xuất nếu tab đang giữ session cũ. KHOA chọn khoa active; fixture Khoa Nội có thiết bị 001, thiết bị 004 thuộc khoa khác. Chỉ ghi username/ID; giữ mật khẩu riêng. Helper start/stop không xóa dữ liệu manual.

Mỗi ca dưới đây để người kiểm thử tự ghi: **PASS/FAIL: ____ · Kết quả thực tế: ____ · Ghi chú: ____ · UI/UX nhận xét: ____**.

1. **Danh sách:** mở Quản lý tài khoản; kiểm tra cột, Sau/Trước, tổng số, Tải lại; tìm username bằng chữ hoa/thường, phối hợp role/khoa/status, thử kết quả rỗng và Xóa bộ lọc. Mong đợi phân trang và bộ lọc đúng.
2. **Tạo PHONG_VTYT:** tạo account active, khoa tùy chọn; kiểm tra chi tiết sau F5, đăng nhập bằng mật khẩu vừa cấp, thấy Kế hoạch/Thực hiện/Báo cáo/Thiết bị. Mật khẩu không hiện lại.
3. **Tạo BAN_GIAM_DOC:** tạo account active không cần khoa; đăng nhập, thấy Báo cáo/Thiết bị/Phê duyệt, đọc hàng chờ mà không quyết định dữ liệu chuẩn; không thấy quản lý tài khoản.
4. **Tạo KHOA_PHONG:** gắn Khoa Nội; đăng nhập, xem lịch sử thiết bị 001; truy cập /equipment/4/history phải bị 403, không lộ dữ liệu ngoài khoa.
5. **Tạo ADMIN:** account ADMIN mới vào được quản lý tài khoản; tự bấm Vô hiệu hóa phải bị 409 và vẫn active; không có quyền bảo trì UC01–UC12.
6. **Trùng username:** thử tạo lại username đã có, kể cả thêm khoảng trắng hai đầu; backend trả 409 USERNAME_ALREADY_EXISTS, không thêm dòng.
7. **KHOA thiếu khoa:** chọn KHOA_PHONG nhưng để trống khoa; UI/backend từ chối, không lưu account.
8. **Vô hiệu hóa:** account KHOA smoke đang đăng nhập ở tab khác; ADMIN vô hiệu hóa; request tiếp theo của tab đó bị 401, fresh login thất bại.
9. **Kích hoạt:** kích hoạt lại account smoke; trạng thái còn sau refresh và mật khẩu cũ đăng nhập được.
10. **Reset password:** thử xác nhận không khớp rồi nhập khớp; lưu thành công, ô mật khẩu được xóa; fresh login bằng mật khẩu cũ thất bại, bằng mật khẩu mới thành công.
11. **Sửa role/khoa:** username chỉ đọc; đổi KHOA từ Khoa Nội sang Khoa Ngoại, sau refresh thiết bị 001 bị chặn còn 038 được xem; đổi tiếp sang BGD, menu/hàng chờ đúng. Username/status/password không đổi bởi form sửa.
12. **Phân quyền:** VTYT/BGD/KHOA không thấy menu; mở trực tiếp /admin/accounts và /new phải hiện Không có quyền xem trang. ADMIN được phép; backend độc lập trả 403 cho non-admin.

| Ca | PASS/FAIL | Kết quả thực tế | Ghi chú | UI/UX |
| --- | --- | --- | --- | --- |
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |
| 4 | | | | |
| 5 | | | | |
| 6 | | | | |
| 7 | | | | |
| 8 | | | | |
| 9 | | | | |
| 10 | | | | |
| 11 | | | | |
| 12 | | | | |

**Tổng hợp người kiểm thử:** PASS ____ / FAIL ____ / Cần kiểm tra lại ____ / UI/UX chung ____ / Quyết định chấp nhận V2 ____.

Không tự xóa account manual đã có lịch sử. Nếu chỉ là account thử không còn dùng, có thể vô hiệu hóa; cleanup SQL chỉ áp dụng cho smoke tự động đã được chứng minh không có tham chiếu. Human review Account Management vẫn là bước quyết định tiếp theo.
