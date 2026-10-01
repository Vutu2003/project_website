# V2 ADM-03 — Quản lý danh mục hệ thống

**Trạng thái:** PASS qua tự động và Chrome thật; human UI/UX review còn chờ. ADM-03 là phần mở rộng quản trị ngoài 12 Use Case chi tiết UC01–UC12. Phạm vi chỉ gồm Khoa/Phòng và Đơn vị bảo trì; không mở rộng quy trình bảo trì hoặc quyền nghiệp vụ của ADMIN.

## 1. Kiểm toán CSDL và quyết định

V001 đã có department(id, code, name, active) với unique code, CHECK không trống và FK từ account/equipment. service_provider V001–V006 chỉ có id, name, contact_details, active; **thiếu code** nên không thể đáp ứng mã bắt buộc/duy nhất. Vì vậy **V007 ADDED**: thêm service_provider.code TEXT NOT NULL, unique và CHECK không trống; backfill provider cũ bằng PROVIDER_{id}. Seed mới cấp DEMO_PROVIDER_01–07. Không thêm bảng hoặc FK. Trạng thái sau ADM-03: **14 bảng, 118 cột nghiệp vụ, 31 FK, V001–V007**; baseline V1 trước ADM-03 là 117 cột/V001–V006.

Audit JPA/PostgreSQL: **14/14 entity–table, 118/118 cột, 31/31 FK, 11/11 enum CHECK, 2/2 trường version PASS**. Các FK đến provider trong coverage, approval_request, plan_item và execution vẫn giữ nguyên; khoa vẫn được tham chiếu bởi account, equipment và plan_item. [Schema audit JSON](v2_system_catalog_schema_audit.json) lưu số liệu máy đọc được; mapping audit đầy đủ được sinh lại trong backend/target khi chạy test, để thư mục báo cáo chỉ có một Markdown cho phần này.

## 2. Quy tắc và triển khai

- Mã/tên bắt buộc, trim, tối đa 100 ký tự; mã unique ở DB. Liên hệ provider tùy chọn, tối đa 500 ký tự. Tạo mới mặc định active, đổi trạng thái bằng action riêng; không hard delete.
- List ADMIN cho xem cả active/inactive, phân trang, tìm theo mã/tên không phân biệt hoa/thường và escape wildcard, lọc trạng thái. Public selection API /api/departments và /api/providers chỉ trả active.
- Account KHOA_PHONG mới không thể gắn khoa inactive. Khoa inactive đang gắn vẫn đọc được và được giữ nếu sửa account không đổi vai trò/khoa. Provider inactive không được chọn/gửi trong đề xuất mới. Coverage, proposal, execution, báo cáo và lịch sử cũ vẫn giữ FK và đọc được.
- AdminDepartmentService/AdminServiceProviderService sở hữu validation, search/filter và transaction; controller mỏng. SecurityConfig giới hạn hai nhánh ADMIN, không cấp ADMIN quyền bảo trì. ErrorResponse: 400 invalid, 403 non-admin, 404 missing, 409 duplicate.
- UI dùng adminCatalogsApi typed qua central client, bảng/form/detail theo style navy–teal–gray, loading/empty/error/reload, confirm đổi trạng thái. Sidebar ADMIN có Quản trị → Quản lý tài khoản; Danh mục hệ thống → Khoa / Phòng, Đơn vị bảo trì. RoleGuard chặn truy cập trực tiếp của vai trò khác.

## 3. API và route

| Catalog | List/detail/create/edit/status API | Frontend |
| --- | --- | --- |
| Khoa/Phòng | GET /api/admin/departments; GET /{id}; POST collection; PATCH /{id}; POST /{id}/activate hoặc /deactivate | /admin/catalogs/departments, /new, /:id, /:id/edit |
| Đơn vị bảo trì | GET /api/admin/providers; GET /{id}; POST collection; PATCH /{id}; POST /{id}/activate hoặc /deactivate | /admin/catalogs/providers, /new, /:id, /:id/edit |

Các đoạn /{id} trong bảng nối với collection API cùng hàng. List dùng page, size, sort, search, active. Department DTO gồm id/code/name/active; provider thêm contactDetails.

## 4. Kết quả kiểm chứng và cleanup

- Frontend build/lint PASS, **57/57 tests PASS**; 16 test ADM-03 mới.
- Backend **106 tests, 0 failure, 0 error, 0 skip** trên PostgreSQL cách ly; bao phủ catalog CRUD/status/search/filter/duplicate/404/403 và Department → KHOA account → /api/auth/me. Regression UC01–UC12 và V2 Account Management PASS.
- verify.sql PASS 70 assertion; validate_demo_data.sql PASS 32 assertion; schema audit PASS 14 bảng/118 cột/31 FK.
- Chrome 154 headless: ADMIN tạo smoke department/provider. Department hiện trong form tạo KHOA; account mới đăng nhập và /api/auth/me đúng. Khi khoa inactive, không thể gán mới; account cũ vẫn hiện khoa. Provider mới được VTYT chọn trong NOT_FREE proposal, lưu draft và submit. Khi provider inactive, select đề xuất mới loại bỏ nó; BGD vẫn thấy provider ở proposal cũ. Non-admin gọi ADMIN API nhận 403. Ảnh tại 1366×768 không tràn ngang.

Development DB đã có 16 thay vì 15 account và mật khẩu demo không khớp fixture chuẩn, nên regression được thực hiện trên database medical_maintenance_adm03_test cách ly, không reset dữ liệu người dùng. Browser smoke gồm 1 khoa, 1 account, 1 provider và 2 plan; guarded cleanup theo thứ tự FK xác nhận **0 smoke record**. Database cách ly và access rule tạm đã được gỡ; managed backend/frontend đã restart tại localhost cho human review. Đánh giá UI chủ quan còn chờ người dùng.

## 5. Hướng dẫn kiểm thử thủ công

Dùng môi trường/dữ liệu thử cách ly. Đặt mã duy nhất SMOKE-V2-DEPT-... và SMOKE-V2-PROVIDER-...; không sửa/xóa danh mục chuẩn. Đăng nhập ADMIN trừ khi ca kiểm thử ghi vai trò khác. Mỗi ca ghi **PASS/FAIL: ____ · Kết quả thực tế: ____ · Ghi chú: ____ · UI/UX nhận xét: ____**.

1. **Danh sách Khoa/Phòng:** mở Danh mục hệ thống → Khoa / Phòng; kiểm tra mã/tên/trạng thái, tìm mã/tên, lọc active/inactive, tải lại.
2. **Tạo Khoa/Phòng:** nhập mã/tên smoke; thử thiếu trường và mã trùng; lưu rồi xem chi tiết.
3. **Sửa Khoa/Phòng:** đổi mã/tên smoke; đối chiếu chi tiết và danh sách.
4. **Vô hiệu hóa/kích hoạt Khoa/Phòng:** xác nhận mỗi action; kiểm tra badge và bộ lọc.
5. **Khoa mới trong tạo account KHOA:** khi khoa active, tạo KHOA_PHONG gắn khoa; đăng nhập account mới và đối chiếu /api/auth/me.
6. **Khoa inactive không gán mới:** vô hiệu hóa khoa; xác nhận form tạo account không có lựa chọn và backend từ chối ID inactive.
7. **Danh sách provider:** mở Đơn vị bảo trì; kiểm tra mã/tên/trạng thái, tìm kiếm, lọc và tải lại.
8. **Tạo provider:** nhập mã/tên/liên hệ tùy chọn; thử thiếu trường và mã trùng; xem chi tiết.
9. **Sửa provider:** đổi mã/tên/liên hệ; đối chiếu chi tiết và danh sách.
10. **Vô hiệu hóa/kích hoạt provider:** xác nhận action, kiểm tra badge/filter/API chọn.
11. **Provider mới trong NOT_FREE proposal:** chuẩn bị plan/item NOT_FREE đã duyệt trên dữ liệu cách ly; VTYT chọn provider smoke, lưu draft, gửi duyệt.
12. **Provider inactive không được chọn:** vô hiệu hóa; mở proposal NOT_FREE mới, xác nhận select loại bỏ nó và backend từ chối ID inactive.
13. **Tham chiếu lịch sử:** sau vô hiệu hóa, account cũ vẫn hiện tên khoa; proposal cũ vẫn hiện provider; FK coverage/history không đổi.
14. **Phân quyền:** PHONG_VTYT, BAN_GIAM_DOC, KHOA_PHONG mở trực tiếp route và gọi hai ADMIN API; UI unauthorized, backend 403.

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
| 13 | | | | |
| 14 | | | | |

**Dọn dữ liệu:** chỉ xóa smoke master data khi đã kiểm tra không còn tham chiếu; nếu đã tạo account/plan/proposal thì dọn theo thứ tự FK trong DB cách ly hoặc giữ mã/ID để truy vết. Không hard delete dữ liệu chuẩn.

Bước tiếp theo: human review ADM-03 → freeze ADMIN scope → tiếp tục kiểm thử chức năng UC01–UC12.
