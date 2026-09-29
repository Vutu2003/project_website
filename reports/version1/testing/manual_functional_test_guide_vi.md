# Hướng dẫn kiểm thử thủ công chức năng — Version 1

## 1. Mục tiêu tài liệu

Dùng tài liệu này bên cạnh trình duyệt để **người kiểm thử tự thao tác UC01–UC12** trên Version 1 hiện có. Kiểm tra hành vi nghiệp vụ, chuyển trạng thái, quyền theo vai trò/khoa và dữ liệu còn nguyên sau khi tải lại trang (refresh). Các ô kết quả chưa được điền; đây là hướng dẫn, chưa phải biên bản đã chạy test.

Chất lượng UI/UX do người kiểm thử đánh giá riêng tại mục I của từng UC và mục 16. Các checklist “Kết quả mong đợi” là tiêu chí chức năng, không phải điểm thiết kế hay kết luận PASS có sẵn.

Tên in **đậm** là nhãn đang có trên giao diện. Trạng thái ghi bằng nhãn tiếng Việt kèm mã nội bộ, ví dụ **Nháp (DRAFT)**. Kết quả nghiệm thu **Đạt (PASS)** khác với kết luận PASS của một ca kiểm thử.

Tài liệu đối chiếu:

- [Phân tích hệ thống V1](../../../docs/system_analysis_v1.pdf).
- [Backend business freeze](../../../backend/docs/backend-business-freeze.md) và [Frontend V1 freeze](../../../frontend/docs/frontend-v1-freeze.md).
- [Integration freeze](../integration/version1_integration_freeze.md).
- [Báo cáo Phase 5.1](../integration/phase_5_1_full_system_integration_report.md) và [Báo cáo Phase 5.2](../integration/phase_5_2_integration_regression_v1_freeze_report.md).
- [Hướng dẫn chạy local hiện có](../local_dev/local_v1_runtime_guide.md), [routes hiện tại](../../../frontend/src/App.tsx) và các trang frontend/API nghiệp vụ hiện tại.

Thư mục kiểm thử đã có trong repository là `reports/version1/testing/`; tài liệu dùng thư mục này, không tạo thêm thư mục song song `testing_v1/`. Hướng dẫn runtime thực tế nằm trong `local_dev/`.

## 2. Chuẩn bị trước khi test

### 2.1 Khởi động hệ thống

1. Mở Terminal trong VS Code tại thư mục repository. Nếu cần, chạy:

   ```bash
   cd /home/vutu0809/Desktop/LTNC
   ```

2. Khởi động ba lớp Frontend ↔ Backend ↔ PostgreSQL:

   ```bash
   ./scripts/start.sh
   ```

3. Kiểm tra trạng thái:

   ```bash
   ./scripts/status.sh
   ```

4. Chờ các dịch vụ sẵn sàng; backend cần **health UP**. Mở **http://localhost:5173/login**. Nếu dịch vụ chưa chạy, xem phần Troubleshooting trong [runtime guide](../local_dev/local_v1_runtime_guide.md#15-troubleshooting).
5. Trên trang **Đăng nhập**, nhập **Tên đăng nhập**, **Mật khẩu**, rồi nhấn **Đăng nhập**. Kiểm tra username/vai trò ở góc phải trước mỗi thao tác. Khi cần đổi người dùng trong cùng tab, nhấn **Đăng xuất** rồi đăng nhập lại.

### 2.2 Tài khoản test

| Username | Role | Mục đích sử dụng |
|---|---|---|
| `demo_vtyt` | `PHONG_VTYT` — Phòng Vật tư Y tế | Tạo/sửa/gửi kế hoạch; coverage/tuyến; đề xuất đơn vị; thực hiện; nghiệm thu kỹ thuật; đồng ký bàn giao Đạt; lập báo cáo; đọc lịch sử rộng. |
| `demo_bgd` | `BAN_GIAM_DOC` — Ban Giám đốc | Quyết định duyệt kế hoạch/đơn vị; đọc báo cáo và lịch sử rộng. |
| `demo_khoa_noi` | `KHOA_PHONG` — Khoa/Phòng, Khoa Nội | Phiên chính bàn giao; đọc thiết bị/lịch sử và hạng mục trong phạm vi Khoa Nội. |
| `demo_admin` | `ADMIN` — Quản trị hệ thống | Kiểm tra đăng nhập, **Tổng quan**, đăng xuất và rào quyền; V1 chưa có quản lý tài khoản hay các lệnh nghiệp vụ UC01–UC12 dành cho ADMIN. |

**Không có mật khẩu thật trong tài liệu này.** Lấy mật khẩu local hiện tại theo [mục 7 của runtime guide](../local_dev/local_v1_runtime_guide.md#7-how-to-get-current-demo-passwords). Chỉ nhập vào trường mật khẩu của trình duyệt; không ghi giá trị vào phiếu test/ảnh chụp. Hash trong database không phải mật khẩu đăng nhập.

Menu theo tài khoản:

| Tài khoản | Menu ngoài Tổng quan |
|---|---|
| VTYT | **Kế hoạch bảo trì**, **Thực hiện bảo trì**, **Báo cáo**, **Thiết bị & lịch sử** |
| BGD | **Báo cáo**, **Thiết bị & lịch sử**, **Phê duyệt** |
| KHOA | **Thiết bị & lịch sử**, **Bàn giao** |
| ADMIN | Không có menu nghiệp vụ trên thanh điều hướng. |

Đăng nhập thử từng tài khoản, kiểm tra đúng menu và **Đăng xuất**. Với ADMIN, mở trực tiếp `/approvals`, `/reports` hoặc `/equipment`: route guard phải hiện **Không có quyền xem trang**. Không suy từ việc ẩn menu rằng tất cả API đọc chung đều cấm ADMIN; hướng dẫn chỉ kiểm tra các route nghiệp vụ được bảo vệ hiện có.

### 2.3 Cách mở nhiều phiên đăng nhập

- Nên dùng ba browser profile đặt tên **VTYT**, **BGD**, **KHOA**; thêm profile **ADMIN** khi kiểm tra quyền.
- Một cửa sổ thường + một cửa sổ ẩn danh (incognito) thuận tiện cho hai vai trò. Nhiều cửa sổ ẩn danh cùng dùng một profile riêng tư, không phải mỗi cửa sổ một profile.
- Có thể dùng tab/cửa sổ mới trong cùng profile: mở tab mới, tự gõ URL đăng nhập, rồi đăng nhập tài khoản cần dùng.

Ứng dụng lưu phiên chính trong **sessionStorage**, riêng theo tab/cửa sổ; F5 giữ phiên của tab đó nếu token còn hiệu lực. Tab nhân bản hoặc cửa sổ có opener có thể ban đầu sao chép phiên của tab gốc. Nếu thấy sai username, **Đăng xuất trong tab mới** rồi đăng nhập đúng vai trò. Khi chuyển cửa sổ, luôn nhìn username ở góc phải. Không mở công cụ để sao chép token giữa các phiên.

### 2.4 Quy ước ghi kết quả

Đánh dấu checklist sau khi thực sự quan sát. Ghi PASS nếu đạt các tiêu chí đã thực hiện; FAIL nếu kết quả khác mong đợi; NOTE nếu nhánh chưa có dữ liệu hoặc cần kiểm tra lại. Ghi riêng kết quả luồng chính và từng nhánh/negative để không bỏ sót lỗi. Không tự coi nhánh chưa chạy là PASS. Đọc mục G trước khi làm mục D: những ca bỏ trống/sai dữ liệu phải thử lúc biểu mẫu còn mở, rồi sửa dữ liệu để tiếp tục; nhánh đổi trạng thái dùng kế hoạch riêng như mục 2.5.

Dùng mẫu sau (mục H dùng nhãn “Ảnh chụp” tương đương):

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp màn hình:
[ ]

Ảnh nên ghi rõ username, tên/ID kế hoạch, trạng thái và kết quả; che thông tin đăng nhập. Với lỗi phiên bản/quyền, chỉ cần ghi mã HTTP nếu đã xem được, không sao chép header hoặc token. Không cần dùng SQL để thực hiện các UC.

### 2.5 Dữ liệu mẫu và thứ tự thao tác

Các dữ liệu dưới đây là fixture demo hiện có. Xác nhận lại mã, khoa, coverage và hiệu lực trên màn hình trước khi dùng; không sửa schema, seed hoặc trạng thái trực tiếp trong database.

| Mã thiết bị | Dữ liệu mẫu hiện có | Dùng để |
|---|---|---|
| `DEMO-EQ-001` | Máy theo dõi bệnh nhân, Khoa Nội; coverage **Theo hợp đồng (FREE)**, đơn vị **Đơn vị bảo trì hợp đồng demo**, hiệu lực 01/01/2026–31/12/2026. | Luồng FREE và bàn giao với `demo_khoa_noi`. |
| `DEMO-EQ-002` | Bơm tiêm điện, Khoa Nội; coverage **Ngoài hợp đồng (NOT_FREE)**, cần duyệt đơn vị ngoài. | UC06–UC07; sau duyệt có thể thực hiện/bàn giao tại Khoa Nội. |
| `DEMO-EQ-003` | Máy điện tim, Khoa Nội; coverage **Chưa xác định (UNKNOWN)**. | Kiểm tra chặn xác định tuyến. |
| `DEMO-EQ-036` | Bơm truyền dịch, Khoa Nội; fixture không có coverage. | Kiểm tra thiếu coverage; dùng **Sau** trong bảng chọn thiết bị để tìm. |
| `DEMO-EQ-004` | Máy sốc điện, Khoa Cấp cứu; FREE với **Giải pháp Trang thiết bị Hòa Bình (Demo)**. | Kiểm tra ngoài khoa và lịch sử nhiều đợt/lần của VTYT/BGD. |

Kế hoạch thao tác mới nên có tên riêng, ví dụ `MANUAL-V1-20260928-MAIN-01`; thay hậu tố cho mỗi lượt chạy. Luồng UC01–UC12 chính bên dưới dùng **MAIN có đúng hai thiết bị 001 và 002**, kỳ 01/10/2026–31/10/2026, ngày dự kiến 05/10/2026 cho cả hai. Đây là ngày demo trong hiệu lực coverage; giao diện ngày có thể hiển thị theo định dạng của máy. Nếu chọn ngày khác, phải nằm trong kỳ kế hoạch và hiệu lực coverage.

- Ghi tên, ID kế hoạch và URL hạng mục từ thanh địa chỉ vào mục H. Các màn hình liên tiếp phải dùng cùng MAIN, không dùng ID smoke trong báo cáo cũ vì dữ liệu smoke đã được dọn.
- **Hoàn tất UC05, UC06 và UC07 cho cả hai hạng mục trước UC08.** UI và backend chỉ cho coverage/đề xuất/duyệt đơn vị khi kế hoạch còn **Đã phê duyệt (APPROVED)**. Bắt đầu hạng mục đầu tiên chuyển kế hoạch sang **Đang thực hiện (IN_PROGRESS)**.
- Mỗi nhánh đổi trạng thái dùng kế hoạch/hạng mục riêng: **REVISION** cho yêu cầu sửa; **REWORK** cho nghiệm thu không đạt rồi làm lại; **MIX** cho báo cáo có một Hoàn tất và một Chuyển sửa chữa; **COVERAGE-BLOCK** cho 003/036; **FOREIGN** chỉ có 004 cho kiểm tra bàn giao ngoài khoa. Tạo các kế hoạch này bằng UC01–UC04; đi tiếp theo UC cần kiểm tra. Không đưa 003/036 vào MAIN vì sẽ không thể hoàn tất mọi hạng mục để lập báo cáo.
- MAIN kết thúc với hai hạng mục **Hoàn tất (COMPLETED)**. MIX dùng 001 bàn giao Đạt và 002 nghiệm thu Không đạt + chuyển sửa chữa; phải duyệt tuyến/đơn vị cho cả hai trước khi bắt đầu. Dữ liệu đã có nhiều đợt dùng đọc lại cho UC12.
- Test thủ công sẽ tạo dữ liệu lưu thật trong database local. Refresh/start/stop không xóa dữ liệu; tài liệu không yêu cầu reset fixture hay xóa dữ liệu đã audit.

## UC01 — Tạo kế hoạch bảo trì

### A. Mục tiêu

Tạo kế hoạch mới và các hạng mục thiết bị, lưu ở **Nháp (DRAFT)**.

### B. Vai trò thực hiện

`demo_vtyt` — `PHONG_VTYT`. `demo_bgd` dùng kiểm tra quyền.

### C. Điều kiện trước khi test

Hệ thống đang chạy, VTYT đã đăng nhập; thiết bị 001 và 002 đang hoạt động và có khoa. Chưa có kế hoạch MAIN của lượt test này; chuẩn bị tiêu đề và kỳ/ngày dự kiến theo mục 2.5.

### D. Luồng kiểm thử chính

1. Đăng nhập `demo_vtyt`; mở **Kế hoạch bảo trì**.
2. Nhấn **Tạo kế hoạch** để mở **Kế hoạch bảo trì mới**.
3. Nhập **Tiêu đề** MAIN; chọn **Ngày bắt đầu** 01/10/2026 và **Ngày kết thúc** 31/10/2026.
4. Trong **Chọn thêm thiết bị**, nhấn **Thêm** ở dòng `DEMO-EQ-001`, rồi `DEMO-EQ-002`. Dùng **Sau**/**Trước** nếu thiết bị không ở trang hiện tại.
5. Trong **Thiết bị trong kế hoạch**, đặt **Ngày dự kiến** cho mỗi dòng là 05/10/2026. Kiểm tra đúng hai mã thiết bị và khoa trước khi tạo.
6. Nhấn **Tạo kế hoạch**; chờ trang chi tiết và thông báo **Đã tạo kế hoạch.**
7. Ghi ID/URL kế hoạch. Nhấn F5, rồi về **Kế hoạch bảo trì**, chọn bộ lọc **Trạng thái → Nháp**, mở **Xem** ở đúng tiêu đề MAIN.

### E. Kết quả mong đợi

- [ ] Có một kế hoạch mới **Nháp (DRAFT)** và đúng hai hạng mục **Dự kiến (PLANNED)**.
- [ ] Tiêu đề, kỳ, ngày dự kiến và **Khoa tại kế hoạch** đúng dữ liệu đã chọn.
- [ ] Dòng đã thêm đổi thành **Đã chọn**, không thêm trùng thiết bị vào cùng kế hoạch.
- [ ] Refresh/mở lại vẫn giữ kế hoạch và hạng mục; luồng hợp lệ không có thông báo lỗi.

### F. Kiểm tra dữ liệu sau thao tác

Trên chi tiết, đọc **TRẠNG THÁI**, **PHIÊN BẢN**, **Hạng mục thiết bị**. Ghi ID từ dòng **KẾ HOẠCH #…** và thanh địa chỉ; đối chiếu hai dòng, ngày và khoa với bước 5. Giữ MAIN để làm UC02.

### G. Kiểm tra negative / permission

1. Trước khi tạo kế hoạch hợp lệ, thử bỏ trống **Tiêu đề**, hoặc để ngày kết thúc trước ngày bắt đầu, rồi nhấn **Tạo kế hoạch**: phải báo thiếu dữ liệu/khoảng ngày không hợp lệ, không chuyển sang một kế hoạch mới. Nhập lại dữ liệu đúng để tiếp tục.
2. Trong cửa sổ BGD, đăng nhập `demo_bgd`, gõ `http://localhost:5173/plans/new`: phải hiện **Không có quyền xem trang**, không có biểu mẫu tạo được phép sử dụng. BGD không có menu **Kế hoạch bảo trì** dành cho lập kế hoạch.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## UC02 — Chỉnh sửa kế hoạch bảo trì

### A. Mục tiêu

Sửa kế hoạch khi **Nháp (DRAFT)** hoặc **Yêu cầu chỉnh sửa (REVISION_REQUIRED)**, giữ hạng mục đã tạo để bảo toàn lịch sử.

### B. Vai trò thực hiện

`demo_vtyt` — `PHONG_VTYT`.

### C. Điều kiện trước khi test

MAIN ở DRAFT từ UC01, hai hạng mục còn PLANNED. Để thử REVISION_REQUIRED, dùng kế hoạch REVISION đã bị BGD yêu cầu chỉnh sửa ở UC04. Chưa có coverage/tuyến thực hiện.

### D. Luồng kiểm thử chính

1. Trong cửa sổ VTYT, mở **Kế hoạch bảo trì**, chọn **Nháp**, mở MAIN bằng **Xem**.
2. Nhấn **Chỉnh sửa**; đối chiếu dữ liệu cũ trên **Chỉnh sửa kế hoạch**.
3. Thêm `-DA-SUA` vào **Tiêu đề**; đổi **Ngày dự kiến** của 001 thành 06/10/2026, vẫn trong kỳ. Giữ hai thiết bị của MAIN.
4. Nhấn **Lưu chỉnh sửa**; quan sát **Đã lưu chỉnh sửa kế hoạch.**
5. Nhấn F5 và **Chỉnh sửa** một lần nữa để đối chiếu tiêu đề/ngày vừa lưu; nhấn **Hủy** để về chi tiết.
6. Với REVISION sau UC04: chọn bộ lọc **Yêu cầu chỉnh sửa**, mở đúng kế hoạch, nhấn **Chỉnh sửa**, sửa tiêu đề/ngày theo lý do đã ghi, nhấn **Lưu chỉnh sửa**. Quan sát kế hoạch trở về **Nháp (DRAFT)** trước khi gửi lại ở UC03.

### E. Kết quả mong đợi

- [ ] DRAFT lưu sửa vẫn là **Nháp (DRAFT)**; phiên bản kế hoạch tăng.
- [ ] Lưu sửa từ REVISION_REQUIRED đưa kế hoạch về **Nháp (DRAFT)**.
- [ ] Tiêu đề/ngày mới còn sau refresh; hai hạng mục cũ vẫn còn.
- [ ] Có ghi chú giữ hạng mục V1; không có thao tác loại bỏ thiết bị đã tạo.

### F. Kiểm tra dữ liệu sau thao tác

Đọc tiêu đề, kỳ/ngày dự kiến, mã thiết bị và phiên bản trước/sau. Nếu muốn thử thêm thiết bị, dùng một kế hoạch riêng, nhấn **Thêm** rồi **Lưu chỉnh sửa**, kiểm tra xuất hiện hạng mục mới; không thêm vào MAIN nếu chưa chuẩn bị chạy hết hạng mục đó.

### G. Kiểm tra negative / permission

Sau UC03, mở lại MAIN **Đã gửi duyệt (SUBMITTED)**: không thấy **Chỉnh sửa**. Gõ URL cũ `/plans/<ID>/edit` của chính MAIN: phải có **Trạng thái hiện tại không cho phép chỉnh sửa.**, nút **Lưu chỉnh sửa** bị vô hiệu hóa. Tương tự với APPROVED/IN_PROGRESS. Không yêu cầu xóa hạng mục để kiểm thử UC02.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## UC03 — Gửi kế hoạch bảo trì để phê duyệt

### A. Mục tiêu

Gửi bản nháp đến BGD: **Nháp (DRAFT) → Đã gửi duyệt (SUBMITTED)** và có yêu cầu chờ quyết định.

### B. Vai trò thực hiện

`demo_vtyt` — `PHONG_VTYT`; `demo_bgd` — `BAN_GIAM_DOC` quan sát hàng chờ.

### C. Điều kiện trước khi test

MAIN đã lưu UC02, DRAFT, có hai hạng mục PLANNED và ngày hợp lệ. Nếu kế hoạch bị yêu cầu sửa, hoàn thành UC02 để về DRAFT trước.

### D. Luồng kiểm thử chính

1. Trong VTYT, mở chi tiết MAIN và kiểm tra tiêu đề, ngày, thiết bị.
2. Nhấn **Gửi phê duyệt**, chấp nhận hộp xác nhận của trình duyệt.
3. Chờ thông báo **Đã gửi kế hoạch để phê duyệt.** và nhãn **Đã gửi duyệt (SUBMITTED)**.
4. Nhấn F5; kiểm tra không còn nút **Chỉnh sửa** hoặc **Gửi phê duyệt**.
5. Trong cửa sổ BGD, mở **Phê duyệt**; chọn **Loại yêu cầu → Duyệt kế hoạch**. Nếu trang đã mở từ trước, nhấn F5 để nhận hàng chờ mới.
6. Tìm dòng MAIN bằng tiêu đề/ID kế hoạch, ghi số **Yêu cầu #…** và kiểm tra **Chờ quyết định (PENDING)**. Chưa quyết định ở UC này.

### E. Kết quả mong đợi

- [ ] Kế hoạch chuyển **DRAFT → SUBMITTED**, giữ nguyên thiết bị/ngày.
- [ ] BGD thấy yêu cầu **Duyệt kế hoạch (PLAN_APPROVAL)** chờ quyết định đúng MAIN.
- [ ] Refresh giữ SUBMITTED; không còn sửa/gửi bản nháp ở chi tiết.

### F. Kiểm tra dữ liệu sau thao tác

Đối chiếu ID/tiêu đề của kế hoạch giữa hai cửa sổ; kiểm tra người gửi là tài khoản VTYT và hàng chờ đúng loại, không nhầm yêu cầu duyệt đơn vị.

### G. Kiểm tra negative / permission

Trên một DRAFT trước bước 2, nhấn **Gửi phê duyệt** rồi hủy hộp xác nhận: kế hoạch vẫn **Nháp**, chưa có yêu cầu mới của kế hoạch đó trong hàng chờ BGD. Sau gửi thành công, refresh không xuất hiện nút gửi lần nữa; không có yêu cầu PENDING trùng cho cùng lượt gửi.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## UC04 — Phê duyệt kế hoạch bảo trì

### A. Mục tiêu

BGD duyệt **SUBMITTED → APPROVED** hoặc yêu cầu chỉnh sửa **SUBMITTED → REVISION_REQUIRED**.

### B. Vai trò thực hiện

`demo_bgd` — `BAN_GIAM_DOC`; VTYT đọc lại và sửa khi được yêu cầu.

### C. Điều kiện trước khi test

MAIN đang **Đã gửi duyệt (SUBMITTED)**, yêu cầu PLAN_APPROVAL **Chờ quyết định (PENDING)** từ UC03. Nhánh chỉnh sửa dùng kế hoạch REVISION riêng đã tạo/gửi theo UC01–UC03.

### D. Luồng kiểm thử chính

1. Đăng nhập BGD, mở **Phê duyệt**, chọn **Loại yêu cầu → Duyệt kế hoạch**.
2. Nhấn **Xem & quyết định** trên dòng MAIN. Kiểm tra trang **Duyệt kế hoạch**, tiêu đề, kỳ và bảng hạng mục.
3. Chọn **Phê duyệt**; nhập **Nhận xét / lý do** nếu muốn, ví dụ `Đồng ý kế hoạch kiểm thử`.
4. Nhấn **Xác nhận quyết định**, chấp nhận hộp xác nhận.
5. Tại hàng chờ, kiểm tra thông báo đã ghi quyết định và dòng yêu cầu MAIN không còn PENDING.
6. Trong VTYT, mở MAIN, nhấn **Tải lại** hoặc F5; kiểm tra **Đã phê duyệt (APPROVED)**, hạng mục vẫn **Dự kiến (PLANNED)**.
7. Nhánh sửa: BGD mở yêu cầu của REVISION, chọn **Yêu cầu chỉnh sửa**, nhập **Nhận xét / lý do** `Điều chỉnh ngày dự kiến`, nhấn **Xác nhận quyết định** và xác nhận. VTYT tải lại REVISION, kiểm tra **Yêu cầu chỉnh sửa (REVISION_REQUIRED)**, làm UC02 → UC03 rồi BGD duyệt yêu cầu mới.

### E. Kết quả mong đợi

- [ ] Luồng duyệt đưa MAIN **SUBMITTED → APPROVED**; chưa bắt đầu thực hiện.
- [ ] Yêu cầu đã quyết định không còn trong hàng chờ PENDING.
- [ ] Nhánh sửa đưa REVISION về REVISION_REQUIRED; lưu sửa về DRAFT, gửi lại có lượt yêu cầu mới.
- [ ] Refresh vẫn giữ quyết định/trạng thái tương ứng.

### F. Kiểm tra dữ liệu sau thao tác

Ở VTYT, đọc nhãn và phiên bản MAIN; ở BGD, đối chiếu số yêu cầu trước/sau. Sau này trong UC12, mở dòng trạng thái kế hoạch để xem chuyển trạng thái và lý do yêu cầu sửa nếu đã chạy nhánh đó; không cần một màn hình lịch sử quyết định riêng.

### G. Kiểm tra negative / permission

1. Trên yêu cầu REVISION còn PENDING, chọn **Yêu cầu chỉnh sửa**, để trống **Nhận xét / lý do**, nhấn **Xác nhận quyết định**: phải hiện **Vui lòng ghi lý do yêu cầu chỉnh sửa.** và không ghi quyết định. Sau đó nhập lý do để tiếp tục.
2. Trong phiên VTYT, mở trực tiếp `http://localhost:5173/approvals`: phải hiện **Không có quyền xem trang**. VTYT không được tự phê duyệt kế hoạch.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## UC05 — Xác định hình thức / tuyến bảo trì

### A. Mục tiêu

Dùng coverage đã xác minh để phân tuyến: FREE → UNDER_CONTRACT; NOT_FREE → PENDING_PROPOSAL; chặn UNKNOWN/thiếu coverage.

### B. Vai trò thực hiện

`demo_vtyt` — `PHONG_VTYT`.

### C. Điều kiện trước khi test

MAIN ở **Đã phê duyệt (APPROVED)**, cả hai hạng mục **Dự kiến (PLANNED)**; coverage của 001/002 có xác minh và áp dụng cho ngày dự kiến. Chưa bắt đầu UC08. FREE phải có đơn vị hợp đồng hoạt động.

### D. Luồng kiểm thử chính

1. Trong VTYT, mở MAIN; nhấn **Tải lại** nếu BGD vừa duyệt.
2. Ở dòng `DEMO-EQ-001`, nhấn **Chọn coverage**.
3. Đọc **Hồ sơ #… · Theo hợp đồng**, **Đơn vị hợp đồng**, **Hiệu lực**, **Xác minh**, **Căn cứ**. Chọn radio đúng hồ sơ FREE của 001; ghi tên đơn vị.
4. Nhấn **Xác định tuyến**, chấp nhận hộp xác nhận. Quan sát dòng 001 chuyển **Theo hợp đồng (UNDER_CONTRACT)**; **Đơn vị / tuyến** là **Đơn vị bảo trì hợp đồng demo** / **Theo hợp đồng**.
5. Ở dòng `DEMO-EQ-002`, nhấn **Chọn coverage**, đọc/chọn hồ sơ **Ngoài hợp đồng (NOT_FREE)** của 002.
6. Nhấn **Xác định tuyến** và xác nhận. Quan sát 002 chuyển **Chờ đề xuất đơn vị (PENDING_PROPOSAL)**, chưa có đơn vị được phân công.
7. Nhấn F5; đọc lại hai dòng. Tiếp tục UC06–UC07 trước khi bắt đầu thực hiện bất kỳ dòng nào.

### E. Kết quả mong đợi

- [ ] FREE: **Dự kiến → Theo hợp đồng (UNDER_CONTRACT)**, đơn vị lấy từ coverage đã chọn.
- [ ] NOT_FREE: **Dự kiến → Chờ đề xuất đơn vị (PENDING_PROPOSAL)**, chưa tự gán đơn vị ngoài.
- [ ] Kế hoạch vẫn **Đã phê duyệt (APPROVED)**; dữ liệu tuyến còn sau refresh.

### F. Kiểm tra dữ liệu sau thao tác

Đối chiếu trạng thái và cột **Đơn vị / tuyến** từng dòng. Ở UC08, tên **Đơn vị thực tế** của lần thực hiện FREE phải trùng đơn vị coverage đã ghi ở bước 3; không chọn thủ công đơn vị thực tế lúc bắt đầu.

### G. Kiểm tra negative / permission

1. Dùng COVERAGE-BLOCK chỉ có 003 và 036, đã duyệt, chưa thực hiện. Mở **Chọn coverage** của 003: radio **Chưa xác định (UNKNOWN)** bị vô hiệu hóa; khi không có hồ sơ chọn được, **Xác định tuyến** bị vô hiệu hóa. Không được tự đổi UNKNOWN thành NOT_FREE.
2. Mở **Chọn coverage** của 036: phải hiện **Thiết bị chưa có hồ sơ coverage. Không thể xác định tuyến bảo trì.**; hạng mục giữ **Dự kiến (PLANNED)**.
3. Mở một DRAFT/SUBMITTED: không có **Chọn coverage**. Không thêm/cập nhật coverage để ép ca này đi tiếp; ghi NOTE nếu fixture local đã khác dữ liệu mẫu.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## UC06 — Lập đề xuất đơn vị bảo trì ngoài

### A. Mục tiêu

Lưu nháp đề xuất đơn vị cho NOT_FREE, mở lại dữ liệu và gửi đến BGD.

### B. Vai trò thực hiện

`demo_vtyt` — `PHONG_VTYT`; BGD quan sát yêu cầu sau khi gửi.

### C. Điều kiện trước khi test

MAIN vẫn **APPROVED**, dòng 002 **Chờ đề xuất đơn vị (PENDING_PROPOSAL)** từ UC05, coverage NOT_FREE hợp lệ. Chưa có nháp/yêu cầu chờ cho dòng này. Có đơn vị đang hoạt động, ví dụ **Đơn vị bảo trì ngoài demo**.

### D. Luồng kiểm thử chính

1. Trong chi tiết MAIN của VTYT, nhấn **Đề xuất đơn vị** ở dòng 002.
2. Chọn **Đơn vị bảo trì đề xuất → Đơn vị bảo trì ngoài demo**.
3. Nhập **Lý do đề xuất** `Đơn vị phù hợp thiết bị kiểm thử`; nhập **Ảnh hưởng bảo hành (nếu có)** `Ghi chú kiểm thử về bảo hành`.
4. Nhấn **Lưu bản nháp**; ghi số nháp trong **Đang tiếp tục bản nháp đề xuất #…**. Hạng mục vẫn **Chờ đề xuất đơn vị**.
5. Nhấn F5, mở lại **Đề xuất đơn vị** của 002. Đối chiếu đơn vị, lý do, ghi chú đã lưu.
6. Bổ sung `- đã bổ sung` vào **Lý do đề xuất**. Nháp đã có chỉ hiện **Gửi đề xuất duyệt**; sửa bổ sung sẽ được gửi/lưu cùng thao tác này, không có nút lưu sửa nháp riêng. Không refresh giữa lúc bổ sung và gửi nếu muốn giữ phần vừa nhập.
7. Nhấn **Gửi đề xuất duyệt**, xác nhận hộp thoại; kiểm tra **Chờ phê duyệt đơn vị (WAITING_VENDOR_APPROVAL)**.
8. Trong BGD, mở **Phê duyệt**, chọn **Loại yêu cầu → Duyệt đơn vị bảo trì**, F5 nếu cần; tìm dòng 002 và đơn vị đề xuất. Mở **Xem & quyết định** để đọc lý do đã bổ sung, chưa quyết định.

### E. Kết quả mong đợi

- [ ] Nháp đề xuất DRAFT lưu trên backend và mở lại sau refresh được; item còn PENDING_PROPOSAL.
- [ ] Gửi nháp đưa item **PENDING_PROPOSAL → WAITING_VENDOR_APPROVAL**, yêu cầu **Duyệt đơn vị bảo trì (VENDOR_SELECTION)** thành **Chờ quyết định (PENDING)**.
- [ ] BGD đọc đúng đơn vị/lý do/ảnh hưởng bảo hành đã gửi; chưa có đơn vị phân công trước UC07.
- [ ] Phần bổ sung ở bước 6 có trong yêu cầu đã gửi; dữ liệu còn sau refresh.

### F. Kiểm tra dữ liệu sau thao tác

Phân biệt trạng thái nháp/yêu cầu với trạng thái hạng mục. Ghi số nháp/yêu cầu, mã 002 và đơn vị; cột **Đơn vị / tuyến** chưa được gán khi mới gửi đề xuất. Nháp chưa gửi không hiện trong hàng chờ PENDING của BGD.

### G. Kiểm tra negative / permission

Trước bước 7, bỏ chọn đơn vị hoặc xóa **Lý do đề xuất**, nhấn **Gửi đề xuất duyệt**: phải hiện **Vui lòng chọn đơn vị và nhập lý do đề xuất.**, chưa chuyển WAITING_VENDOR_APPROVAL. Nhập lại dữ liệu rồi gửi hợp lệ. Dòng FREE/UNDER_CONTRACT không có **Đề xuất đơn vị**.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## UC07 — Phê duyệt đề xuất đơn vị bảo trì ngoài

### A. Mục tiêu

BGD duyệt đúng đơn vị cho item, hoặc trả về để đề xuất lại; đơn vị duyệt phải được dùng ở lần thực hiện sau đó.

### B. Vai trò thực hiện

`demo_bgd` — `BAN_GIAM_DOC`; VTYT đọc lại tuyến và bắt đầu ở UC08.

### C. Điều kiện trước khi test

MAIN còn APPROVED, 002 **Chờ phê duyệt đơn vị (WAITING_VENDOR_APPROVAL)**; yêu cầu VENDOR_SELECTION PENDING từ UC06. Nhánh trả về dùng kế hoạch riêng đã làm UC01–UC06, chưa bắt đầu bảo trì.

### D. Luồng kiểm thử chính

1. Trong BGD, mở **Phê duyệt**, chọn **Loại yêu cầu → Duyệt đơn vị bảo trì**.
2. Nhấn **Xem & quyết định** ở đúng dòng 002/MAIN.
3. Trên **Duyệt đơn vị bảo trì**, đọc **Thiết bị**, **Đơn vị được đề xuất**, **Coverage**, **Căn cứ coverage**, **Lý do đề xuất**, **Ảnh hưởng bảo hành**. Đối chiếu với UC06.
4. Chọn **Phê duyệt**, nhập **Nhận xét / lý do** nếu cần, nhấn **Xác nhận quyết định** và xác nhận hộp thoại.
5. Trong VTYT, mở MAIN và **Tải lại**. Đọc dòng 002: **Đã phân công đơn vị ngoài (ASSIGNED_EXTERNAL)**; đơn vị là **Đơn vị bảo trì ngoài demo**, tuyến **Đơn vị ngoài đã duyệt (EXTERNAL_APPROVED)**.
6. Nhấn F5 để xác nhận còn dữ liệu. Khi làm UC08 cho 002, kiểm tra **Đơn vị thực tế** trùng chính đơn vị được BGD duyệt.
7. Nhánh trả về: BGD mở yêu cầu của kế hoạch riêng, chọn **Yêu cầu chỉnh sửa**, ghi lý do `Cần điều chỉnh đề xuất`, nhấn **Xác nhận quyết định** và xác nhận. VTYT tải lại: item về **Chờ đề xuất đơn vị (PENDING_PROPOSAL)**. Làm lại UC06 để lưu một nháp mới/gửi lại; BGD xử lý lượt yêu cầu mới, không sửa quyết định cũ.

### E. Kết quả mong đợi

- [ ] Duyệt: item **WAITING_VENDOR_APPROVAL → ASSIGNED_EXTERNAL**, đúng provider/tuyến đã duyệt.
- [ ] Yêu cầu cũ biến khỏi hàng chờ PENDING; refresh giữ kết quả.
- [ ] Trả về: item **WAITING_VENDOR_APPROVAL → PENDING_PROPOSAL**; kế hoạch vẫn APPROVED, chưa tự được phép thực hiện theo đơn vị ngoài.
- [ ] Đơn vị thực tế khi bắt đầu UC08 trùng đơn vị phê duyệt, không phải một đơn vị khác.

### F. Kiểm tra dữ liệu sau thao tác

Ghi số yêu cầu, tên đơn vị, mã thiết bị và trạng thái sau quyết định. MAIN phải có 001 UNDER_CONTRACT và 002 ASSIGNED_EXTERNAL trước khi bắt đầu UC08. Sau chạy nhánh trả về, UC12 có dòng trạng thái item và lý do tương ứng.

### G. Kiểm tra negative / permission

1. Trong VTYT, mở URL `/approvals/<ID yêu cầu đơn vị>` đã ghi: phải hiện **Không có quyền xem trang**.
2. Trên yêu cầu riêng còn PENDING, chọn **Yêu cầu chỉnh sửa** mà không nhập lý do: không ghi được quyết định, xuất hiện yêu cầu bổ sung lý do. Hoàn thiện lý do mới tiếp tục nhánh trả về.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## UC08 — Theo dõi thực hiện bảo trì

### A. Mục tiêu

Bắt đầu lần thực hiện, nối thêm nhật ký tiến độ và kết thúc công việc để chờ nghiệm thu; làm lại phải tạo lần mới và giữ lần thất bại.

### B. Vai trò thực hiện

`demo_vtyt` — `PHONG_VTYT`. KHOA chỉ đọc hạng mục được phép và bàn giao, không cập nhật tiến độ/nghiệm thu kỹ thuật.

### C. Điều kiện trước khi test

MAIN đã hoàn tất tuyến/duyệt đơn vị cả hai dòng: 001 UNDER_CONTRACT, 002 ASSIGNED_EXTERNAL. Kế hoạch APPROVED (hoặc IN_PROGRESS khi làm dòng thứ hai), provider hợp lệ. Nhánh làm lại cần item REWORK_REQUIRED từ UC09/UC10 và lần trước đã kết thúc.

### D. Luồng kiểm thử chính

1. Trong VTYT, mở **Thực hiện bảo trì**, tìm MAIN, nhấn **Xem hạng mục**, rồi **Xem thực hiện** ở 001. Cũng có thể dùng **Thực hiện / bàn giao** tại chi tiết kế hoạch.
2. Kiểm tra mã thiết bị, **Khoa tại kế hoạch**, **TUYẾN / ĐƠN VỊ**; ghi URL hạng mục.
3. Nhấn **Bắt đầu thực hiện**, chấp nhận hộp xác nhận. Quan sát **Đang bảo trì (IN_MAINTENANCE)**, kế hoạch **Đang thực hiện (IN_PROGRESS)** và **Lần thực hiện 1**.
4. Trong **Cập nhật tiến độ**, nhập **Nội dung công việc** `Nhật ký 1: kiểm tra ban đầu`; nhập **Ghi nhận hư hỏng (nếu có)** nếu cần; nhấn **Lưu tiến độ**.
5. Chờ tải lại, nhập **Nội dung công việc** `Nhật ký 2: vệ sinh và kiểm tra hoạt động`, nhấn **Lưu tiến độ**. Cuộn đến **Lịch sử các lần thực hiện → Tiến độ**, kiểm tra cả hai dòng cùng còn.
6. Nhập **Ghi chú kết quả (nếu có)** `Đã hoàn thành phần việc bảo trì`; nhấn **Kết thúc công việc**, chấp nhận xác nhận.
7. Kiểm tra **Chờ nghiệm thu kỹ thuật (AWAITING_TECHNICAL_ACCEPTANCE)**; F5 và đọc lại số lần, đơn vị thực tế, thời gian bắt đầu/kết thúc, hai nhật ký và ghi chú.
8. Làm bước 1–7 với 002; **Đơn vị thực tế** phải là đơn vị ngoài đã được UC07 duyệt. Sau đó làm UC09/UC10 cho cả hai dòng MAIN.
9. Nhánh làm lại: với REWORK sau UC09 Không đạt, mở hạng mục, thấy **Bắt đầu lần thực hiện lại**, nhấn **Bắt đầu thực hiện** và xác nhận; kiểm tra lần N+1. Làm bước 4–7 và UC09 Đạt trên lần mới; lần N vẫn giữ nhật ký/nghiệm thu Không đạt.

### E. Kết quả mong đợi

- [ ] Bắt đầu chuyển item **UNDER_CONTRACT/ASSIGNED_EXTERNAL → IN_MAINTENANCE**; lần đầu chuyển plan **APPROVED → IN_PROGRESS**.
- [ ] Lần đầu là 1, lần làm lại tăng N+1; mỗi lần có **Đơn vị thực tế** đúng tuyến.
- [ ] Hai lần **Lưu tiến độ** nối hai nhật ký, không ghi đè nhật ký đầu; lúc ghi tiến độ item vẫn IN_MAINTENANCE.
- [ ] Kết thúc chuyển **IN_MAINTENANCE → AWAITING_TECHNICAL_ACCEPTANCE**, có thời gian kết thúc.
- [ ] Rework tạo lần mới; bằng chứng thất bại cũ còn và các lựa chọn nghiệm thu/bàn giao của lần mới trở về **Đạt** mặc định.
- [ ] Refresh giữ dữ liệu thực hiện và tiến độ.

### F. Kiểm tra dữ liệu sau thao tác

Đọc **Lịch sử các lần thực hiện**, **Lần thực hiện N**, **Đơn vị thực tế**, **Thời gian**, **Tiến độ** và **Ghi chú kết quả**. Không dùng nhãn Đạt của lần cũ để kết luận lần hiện tại đã nghiệm thu.

### G. Kiểm tra negative / permission

1. Khi đang IN_MAINTENANCE, để trống **Nội dung công việc**, nhấn **Lưu tiến độ**: phải hiện **Vui lòng nhập nội dung công việc.**, không thêm nhật ký rỗng.
2. Đăng nhập KHOA, dùng menu **Bàn giao → Xem hạng mục → Xem thực hiện** cho hạng mục Khoa Nội đang thực hiện: không có **Bắt đầu thực hiện**, **Lưu tiến độ**, **Kết thúc công việc** hoặc biểu mẫu nghiệm thu kỹ thuật.
3. Nhánh chuyển sửa chữa trực tiếp, dùng một item riêng đang IN_MAINTENANCE: nhập **Lý do bắt buộc** trong **Chuyển sửa chữa**, nhấn **Chuyển sang sửa chữa** và xác nhận. Item thành **Chuyển sửa chữa (REPAIR_REQUIRED)**, giữ lý do/lần thực hiện đã kết thúc; không có bắt đầu làm lại trên item này. Để trống lý do phải bị chặn. Đây là kết quả cuối phạm vi bảo trì V1, không phải Hoàn tất.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## UC09 — Nghiệm thu kỹ thuật

### A. Mục tiêu

Ghi kết quả kỹ thuật của lần hiện tại: Đạt để chờ bàn giao; Không đạt để làm lại hoặc chuyển sửa chữa.

### B. Vai trò thực hiện

`demo_vtyt` — `PHONG_VTYT`.

### C. Điều kiện trước khi test

Item MAIN **Chờ nghiệm thu kỹ thuật (AWAITING_TECHNICAL_ACCEPTANCE)**, lần mới nhất đã kết thúc UC08, chưa có nghiệm thu kỹ thuật cho lần đó. Nhánh FAIL dùng REWORK/MIX riêng đã hoàn tất chuẩn bị tuyến và thực hiện.

### D. Luồng kiểm thử chính

1. Trong VTYT, mở **Thực hiện bảo trì → Xem hạng mục → Xem thực hiện** của MAIN/001.
2. Kiểm tra trạng thái, **Lần thực hiện N** và phần **Nghiệm thu kỹ thuật · lần N** cùng đúng lần hiện tại.
3. Chọn **Đạt (PASS)**; nhập **Kết luận bắt buộc** `Kiểm tra kỹ thuật đạt, đủ điều kiện bàn giao`.
4. Nhấn **Ghi nghiệm thu kỹ thuật**, chấp nhận hộp xác nhận; kiểm tra **Chờ bàn giao (AWAITING_HANDOVER)**.
5. Nhấn F5; cuộn đến **Lịch sử các lần thực hiện → Nghiệm thu kỹ thuật**, đọc **Đạt**, kết luận, thời điểm/người ghi; bàn giao chưa có kết quả.
6. Lặp lại cho 002 của MAIN. Không ghi thêm lần nghiệm thu thứ hai lên cùng lần đã có kết quả.

### E. Kết quả mong đợi

- [ ] Technical **Đạt (PASS)** đưa item **AWAITING_TECHNICAL_ACCEPTANCE → AWAITING_HANDOVER**.
- [ ] Kết luận và bằng chứng gắn đúng lần thực hiện; refresh giữ kết quả.
- [ ] Technical PASS chưa đưa item thành COMPLETED; cần UC10.
- [ ] Nhánh FAIL giữ bằng chứng **Không đạt** và chuyển đúng lựa chọn REWORK_REQUIRED hoặc REPAIR_REQUIRED.

### F. Kiểm tra dữ liệu sau thao tác

Đọc số lần, kết luận kỹ thuật, thời điểm và nhãn item. **Yêu cầu thực hiện lại (REWORK_REQUIRED)** cho phép bắt đầu một lần bảo trì mới tại UC08. **Chuyển sửa chữa (REPAIR_REQUIRED)** là chuyển sang phạm vi sửa chữa, kết thúc xử lý item trong V1; không phải rework và không tính vào Hoàn tất bảo trì.

### G. Kiểm tra negative / permission

1. Trước khi ghi Đạt, để trống **Kết luận bắt buộc**, nhấn **Ghi nghiệm thu kỹ thuật**: phải hiện **Vui lòng nhập kết luận nghiệm thu kỹ thuật.**, chưa có kết quả kỹ thuật.
2. REWORK: trên item riêng đang chờ nghiệm thu, chọn **Không đạt**, **không tích** **Chuyển sửa chữa thay vì thực hiện lại**, nhập kết luận `Cần xử lý lại phần kiểm tra`, nhấn **Ghi nghiệm thu kỹ thuật** và xác nhận. Item thành **Yêu cầu thực hiện lại (REWORK_REQUIRED)**; làm UC08 để có lần N+1, nghiệm thu Đạt lần mới; xác nhận Không đạt lần cũ vẫn còn.
3. MIX/002: chọn **Không đạt**, **tích** **Chuyển sửa chữa thay vì thực hiện lại**, nhập kết luận `Hư hỏng cần chuyển xử lý sửa chữa`, ghi/xác nhận. Item thành **Chuyển sửa chữa (REPAIR_REQUIRED)**; không có biểu mẫu bàn giao Đạt hoặc bắt đầu làm lại cho item đó. MIX/001 vẫn làm UC09 Đạt → UC10 Đạt để chuẩn bị báo cáo 1/1.
4. Mở item còn IN_MAINTENANCE hoặc đã AWAITING_HANDOVER: không có biểu mẫu ghi nghiệm thu kỹ thuật mới. KHOA không có quyền ghi kỹ thuật ngay cả khi đọc được hạng mục.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## UC10 — Bàn giao thiết bị

### A. Mục tiêu

KHOA là phiên chính, VTYT đồng ký tạm thời: technical Đạt → bàn giao Đạt → Hoàn tất; phiên chính không bị đổi người dùng.

### B. Vai trò thực hiện

Chính: `demo_khoa_noi` — `KHOA_PHONG`, Khoa Nội. Thứ hai: `demo_vtyt` — `PHONG_VTYT`, nhập trong biểu mẫu đồng ký, không đăng xuất KHOA.

### C. Điều kiện trước khi test

MAIN/001 và 002 **Chờ bàn giao (AWAITING_HANDOVER)**, nghiệm thu kỹ thuật **Đạt (PASS)** trên lần mới nhất từ UC09; **Khoa tại kế hoạch** là Khoa Nội. Chuẩn bị mật khẩu VTYT từ runtime guide, không ghi vào biên bản. Nhánh FAIL và ngoài khoa dùng hạng mục riêng.

### D. Luồng kiểm thử chính

1. Trong cửa sổ KHOA, đăng nhập `demo_khoa_noi`; kiểm tra username ở góc phải.
2. Mở **Bàn giao**, tìm MAIN, nhấn **Xem hạng mục**, rồi **Xem thực hiện** ở 001; kiểm tra **Khoa tại kế hoạch** và technical Đạt của lần hiện tại.
3. Trong **Bàn giao · lần N**, chọn **Đạt (PASS)**; nhập **Kết luận bắt buộc** `Khoa Nội tiếp nhận thiết bị sau bảo trì`.
4. Trong **Xác nhận thứ hai · Phòng VTYT**, nhập **Tên đăng nhập VTYT** `demo_vtyt` và **Mật khẩu VTYT** hiện tại.
5. Nhấn **Ghi kết quả bàn giao**, chấp nhận hộp xác nhận; chờ **Hoàn tất (COMPLETED)**.
6. Đọc **Lịch sử các lần thực hiện → Bàn giao**: Đạt, kết luận, xác nhận **Khoa/Phòng #…** và **Phòng VTYT #…** kèm thời điểm. Hai danh tính phải tương ứng tài khoản KHOA và VTYT.
7. Kiểm tra góc phải vẫn là `demo_khoa_noi`, menu vẫn có **Bàn giao**, không đổi thành VTYT; F5 vẫn giữ phiên KHOA. Các trường đồng ký đã được xóa/không còn vì item hoàn tất.
8. Lặp lại cho 002 của MAIN. Sau bàn giao cả hai, VTYT tải lại MAIN; kế hoạch phải **Chờ báo cáo (AWAITING_REPORT)**. Nếu còn item chưa có kết quả cuối thì chưa chuyển sang Chờ báo cáo.

### E. Kết quả mong đợi

- [ ] Bàn giao **Đạt (PASS)** đưa item **AWAITING_HANDOVER → COMPLETED**.
- [ ] Bằng chứng cùng lần có xác nhận của KHOA và VTYT; technical PASS vẫn còn.
- [ ] Đồng ký tạm thời không thay phiên chính KHOA; refresh giữ username KHOA và dữ liệu bàn giao.
- [ ] Khi mọi item là COMPLETED hoặc REPAIR_REQUIRED, kế hoạch chuyển **IN_PROGRESS → AWAITING_REPORT**.
- [ ] Không có bàn giao thành công cho hạng mục ngoài khoa hoặc thiếu signer VTYT hợp lệ.

### F. Kiểm tra dữ liệu sau thao tác

Đọc trạng thái item, số lần, cả hai xác nhận và trạng thái plan sau item cuối. Không cần xem token. Với MIX, chỉ bàn giao 001; 002 REPAIR_REQUIRED không bàn giao và không đổi thành COMPLETED.

### G. Kiểm tra negative / permission

1. Trước khi ghi thành công, nhập kết luận nhưng để trống trường đồng ký, nhấn **Ghi kết quả bàn giao**: phải báo cần tài khoản/mật khẩu VTYT, item còn AWAITING_HANDOVER. Có thể dùng tài khoản BGD đúng mật khẩu trong trường đồng ký: phải hiện **Người xác nhận phải có vai trò Phòng VTYT.**, không ghi bàn giao, KHOA vẫn là phiên chính. Sau đó nhập lại signer VTYT hợp lệ.
2. Nhánh ngoài khoa: tạo FOREIGN chỉ có 004 theo UC01–UC05, làm UC08–UC09 Đạt bằng VTYT; sao chép URL trang thực hiện của item. Trong cửa sổ KHOA, dán URL này: phải bị chặn phạm vi (403), không thấy dữ liệu/biểu mẫu bàn giao của Khoa Cấp cứu. Không tự thay department hoặc tạo tài khoản để vượt phạm vi.
3. Nhánh Không đạt, dùng item riêng đã technical PASS: chọn **Không đạt**, nhập **Kết luận bắt buộc**, không tích chuyển sửa chữa, nhấn **Ghi kết quả bàn giao** và xác nhận → **Yêu cầu thực hiện lại (REWORK_REQUIRED)**. Nếu tích **Chuyển sửa chữa thay vì thực hiện lại** trên một item riêng khác → **Chuyển sửa chữa (REPAIR_REQUIRED)**. FAIL không yêu cầu trường đồng ký VTYT; bằng chứng Không đạt vẫn được giữ. Làm lại bắt đầu tại UC08 và cần technical PASS mới trước bàn giao mới.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## UC11 — Lập báo cáo bảo trì

### A. Mục tiêu

Tạo/sửa báo cáo nháp rồi hoàn tất; kiểm tra số Hoàn tất/Chuyển sửa chữa riêng, báo cáo chính thức chỉ đọc và chống ghi đè từ phiên cũ.

### B. Vai trò thực hiện

`demo_vtyt` — `PHONG_VTYT` lập/sửa/hoàn tất. `demo_bgd` — `BAN_GIAM_DOC` chỉ đọc báo cáo.

### C. Điều kiện trước khi test

MAIN **Chờ báo cáo (AWAITING_REPORT)**, mọi item COMPLETED hoặc REPAIR_REQUIRED, chưa có báo cáo. MAIN có hai COMPLETED → dự kiến 2/0. Chuẩn bị MIX theo UC09/UC10: một COMPLETED, một REPAIR_REQUIRED → dự kiến 1/1. Test stale dùng hai tab VTYT của cùng báo cáo còn DRAFT, trước khi hoàn tất.

### D. Luồng kiểm thử chính

1. Trong VTYT, mở **Báo cáo**, chọn **Trạng thái → Chờ báo cáo**, nhấn **Mở báo cáo** ở MAIN. Cũng có thể nhấn **Báo cáo** tại chi tiết MAIN.
2. Đọc **Chưa có báo cáo**, trạng thái kế hoạch và **Kết quả hạng mục**; đối chiếu số **Hoàn tất bảo trì**/**Chuyển sửa chữa** với hai item MAIN.
3. Nhập các trường: **Số báo cáo (nếu có)**, **Công việc đã thực hiện**, **Kết quả đạt được**, **Kết quả chưa đạt**, **Nguyên nhân**, **Công việc tiếp theo**, **Biện pháp xử lý**, **Kiến nghị**. Dùng nội dung kiểm thử dễ nhận biết; **Công việc đã thực hiện** cần có nội dung để hoàn tất.
4. Nhấn **Tạo bản nháp**. Kiểm tra **Bản nháp (DRAFT)** và plan vẫn **Chờ báo cáo (AWAITING_REPORT)**. Nhấn F5, đối chiếu các nội dung đã nhập.
5. Bổ sung `- nội dung đã cập nhật` vào **Kiến nghị**, nhấn **Lưu bản nháp**; chờ tải lại và đọc phần vừa sửa. Thực hiện test stale ở mục G trước bước 6 nếu cần.
6. Khi nội dung đã lưu, nhấn **Hoàn tất báo cáo**, chấp nhận hộp xác nhận.
7. Kiểm tra **Báo cáo chính thức (FINAL)**, kế hoạch **Đã báo cáo (REPORTED)**; nội dung chỉ đọc, có thời điểm hoàn tất. Nhấn F5 và đọc lại số liệu/nội dung.
8. Trong BGD, mở **Báo cáo**, chọn **Đã báo cáo**, **Mở báo cáo** của MAIN; kiểm tra cùng dữ liệu, không có lệnh tạo/sửa/hoàn tất.
9. Làm bước 1–7 cho MIX khi đã chuẩn bị: đối chiếu **Hoàn tất bảo trì = 1**, **Chuyển sửa chữa = 1**. Nội dung chưa đạt/nguyên nhân/công việc tiếp theo phản ánh item chuyển sửa chữa, không mô tả đã sửa chữa xong.

### E. Kết quả mong đợi

- [ ] Tạo/sửa nháp giữ plan AWAITING_REPORT; một kế hoạch có một báo cáo, dữ liệu nháp còn sau refresh.
- [ ] Hoàn tất đưa report **DRAFT → FINAL**, plan **AWAITING_REPORT → REPORTED**.
- [ ] MAIN: Hoàn tất = 2, Chuyển sửa chữa = 0; MIX: 1 và 1; REPAIR_REQUIRED không cộng vào Hoàn tất.
- [ ] Nội dung đã lưu và thời điểm hoàn tất còn sau refresh; FINAL chỉ đọc, không còn nút lưu/hoàn tất.
- [ ] BGD đọc được, không sửa báo cáo; stale write bị chặn với **Xung đột phiên bản**, không ghi đè dữ liệu mới.

### F. Kiểm tra dữ liệu sau thao tác

Đối chiếu số liệu với từng trạng thái item, không tự nhập số đếm vào báo cáo. Trước tạo báo cáo, số đếm là xem tạm từ item; khi có báo cáo, số liệu do backend trả về. Đọc đủ tám trường, ngày báo cáo, thời điểm hoàn tất và phiên bản kế hoạch; tại UC12 kiểm tra tham chiếu báo cáo chính thức. V1 kết thúc ở REPORTED; **không yêu cầu thao tác đóng kế hoạch (CLOSED)**.

### G. Kiểm tra negative / permission

1. Trước tạo/hoàn tất, mở báo cáo một plan còn APPROVED/IN_PROGRESS bằng URL `/plans/<ID>/report`: không có biểu mẫu lập báo cáo được phép, vì chưa đủ kết quả cuối của tất cả item. BGD ở plan AWAITING_REPORT chỉ đọc, không có nút tạo/sửa.
2. Trên DRAFT, sửa một trường nhưng chưa lưu, nhấn **Hoàn tất báo cáo**: phải hiện **Vui lòng lưu thay đổi bản nháp trước khi hoàn tất.** Nếu đã lưu nháp nhưng **Công việc đã thực hiện** trống: phải báo cần nội dung công việc, chưa thành FINAL. Bổ sung/lưu lại để tiếp tục.
3. Test stale (409 conflict), thực hiện khi report còn DRAFT:
   1. Mở cùng URL báo cáo trong tab A và tab B, cả hai đăng nhập VTYT. Chờ tải xong; ghi cùng phiên bản kế hoạch vN.
   2. Ở A, đổi **Kiến nghị** thành `Cập nhật từ A`, nhấn **Lưu bản nháp**, chờ tải lại và phiên bản mới.
   3. Ở B, **không refresh/Tải lại**, đổi **Kiến nghị** thành `Nội dung cũ từ B`, nhấn **Lưu bản nháp**.
   4. B phải hiện **Xung đột phiên bản** và hướng dẫn dữ liệu đã thay đổi ở phiên khác (HTTP **409 OPTIMISTIC_LOCK_CONFLICT**). A vẫn giữ `Cập nhật từ A`; B chưa ghi đè thành công. Có thể F5 ở A để kiểm tra.
   5. Nhấn **Tải lại dữ liệu** trong khung lỗi của B; thấy `Cập nhật từ A` và phiên bản hiện tại. Nếu cần sửa, nhập lại sau khi đối chiếu rồi lưu; hoàn tất bằng dữ liệu/phiên bản mới.

Chỉ ghi mã lỗi vào biên bản; không lưu request headers/token nếu dùng DevTools để xem status 409.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## UC12 — Xem lịch sử bảo trì thiết bị

### A. Mục tiêu

Đọc các đợt/lần thực hiện, tiến độ, nghiệm thu, bàn giao, đơn vị và báo cáo; giữ thất bại cũ và phân biệt Chuyển sửa chữa; kiểm tra phạm vi khoa.

### B. Vai trò thực hiện

`demo_vtyt` — `PHONG_VTYT` và `demo_bgd` — `BAN_GIAM_DOC` đọc rộng; `demo_khoa_noi` — `KHOA_PHONG` đọc trong phạm vi khoa. ADMIN dùng kiểm tra route bị chặn.

### C. Điều kiện trước khi test

MAIN đã hoàn tất báo cáo UC11; có mã thiết bị 001/002 và bằng chứng đã ghi. REWORK/MIX tạo thêm bằng chứng nhánh nếu đã chạy. Fixture 004 có lịch sử nhiều đợt và lần thất bại/làm lại để đọc bằng VTYT/BGD; đây là thiết bị ngoài Khoa Nội.

### D. Luồng kiểm thử chính

1. Trong VTYT, mở **Thiết bị & lịch sử**; tại **Danh sách thiết bị**, tìm 001 (dùng **Sau**/**Trước** khi cần), nhấn **Xem lịch sử**.
2. Kiểm tra tiêu đề đúng mã/tên thiết bị, số đợt trong phạm vi; tìm thẻ MAIN theo tiêu đề/ID kế hoạch. Mỗi thẻ là một đợt/hạng mục, không phải một lần thực hiện.
3. Trên thẻ MAIN, đọc trạng thái item/plan, **Tuyến bảo trì**, **Đơn vị được phân công**. Phần này hiển thị ID đơn vị; tên đơn vị thực tế có ở từng lần bên dưới.
4. Nhấn dòng mở/đóng **Lần thực hiện (N) và bằng chứng** nếu đang thu gọn. Đọc từng **Lần thực hiện N**, **Đơn vị thực tế**, **Thời gian**, **Tiến độ**, **Nghiệm thu kỹ thuật**, **Bàn giao** và hai xác nhận trên bàn giao Đạt.
5. Nhấn **Dòng trạng thái hạng mục (…) và kế hoạch (…)** để mở; đọc chuỗi chuyển trạng thái, thời điểm, người thực hiện/lý do. Đối chiếu các thao tác vừa làm UC01–UC11.
6. Đọc **Báo cáo: Chính thức (FINAL)** và thời điểm; nhấn **Mở báo cáo**, kiểm tra đúng MAIN và số liệu/nội dung UC11. Quay lại lịch sử bằng nút Back của trình duyệt, nhấn **Tải lại** hoặc F5.
7. Trong BGD, mở **Thiết bị & lịch sử**, **Xem lịch sử** của 001; đối chiếu bằng chứng và tham chiếu báo cáo. Có thể mở báo cáo để đọc.
8. Trong VTYT/BGD, mở lịch sử 004; mở các thẻ đợt cũ nếu đang thu gọn. Đọc đợt có lần 1 kỹ thuật **Không đạt (FAIL)** và lần 2 **Đạt (PASS)**, kiểm tra lần cũ không mất. Đợt/item **Chuyển sửa chữa (REPAIR_REQUIRED)** phải có nhãn riêng với **Hoàn tất (COMPLETED)**; có ghi chú đây là điểm cuối bảo trì V1, chưa có kết quả sửa chữa V2.
9. Trong KHOA, mở **Thiết bị & lịch sử → Xem lịch sử** của 001. Chỉ thấy đợt thuộc khoa được backend cho phép; đọc tiến độ/kỹ thuật/bàn giao được trả về. Không yêu cầu dữ liệu audit toàn kế hoạch/coverage đầy đủ như VTYT và không có link **Mở báo cáo** cho KHOA.

### E. Kết quả mong đợi

- [ ] Mã/tên thiết bị và các đợt đúng dữ liệu; mỗi đợt giữ toàn bộ lần thực hiện trong phạm vi được phép.
- [ ] Nhật ký nối thêm, đơn vị thực tế, kỹ thuật/bàn giao và thất bại/lần làm lại cũ còn sau refresh.
- [ ] MAIN có item COMPLETED, plan REPORTED và tham chiếu FINAL đúng; báo cáo mở được bằng VTYT/BGD.
- [ ] REPAIR_REQUIRED có nhãn riêng, không biến thành Hoàn tất hoặc bị mô tả là lần làm lại.
- [ ] VTYT/BGD đọc rộng; KHOA đọc theo khoa tại kế hoạch, không lộ đợt/dữ liệu ngoài phạm vi.
- [ ] Trang lịch sử chỉ đọc, không có nút sửa/xóa bằng chứng.

### F. Kiểm tra dữ liệu sau thao tác

Dùng ghi chép ở UC08–UC11 để đối chiếu tên/ID kế hoạch, số lần, hai nhật ký, kết luận, signer và báo cáo. Với REWORK, xem cả FAIL lần trước lẫn PASS lần mới. Với MIX, 001 COMPLETED và 002 REPAIR_REQUIRED cùng tham chiếu báo cáo có đếm 1/1. KHOA có thể thấy ít đợt/trường hơn; thiếu dữ liệu bị giới hạn đúng quyền không tự xem là lỗi.

### G. Kiểm tra negative / permission

1. Trong phiên KHOA, gõ `http://localhost:5173/equipment/4/history` (fixture 004 của Khoa Cấp cứu): phải hiện **Không thể hoàn tất** / **Bạn không có quyền thực hiện thao tác này.** (HTTP 403). Không hiển thị tên, các đợt hoặc bằng chứng của thiết bị ngoài khoa. Về **Thiết bị & lịch sử**, thử 001 vẫn đọc được.
2. Trong phiên ADMIN, gõ `http://localhost:5173/equipment`: phải hiện **Không có quyền xem trang**, không có màn hình UC12 được phép.
3. Nếu fixture đã thay đổi khoa hoặc có chuyển giao lịch sử hợp lệ, ghi NOTE và chọn một thiết bị hiện không có bất kỳ đợt hợp lệ trong khoa tài khoản; không đổi dữ liệu để ép kết quả 403. Quyền lịch sử/bàn giao dựa trên **khoa tại kế hoạch**, không chỉ khoa hiện tại.

### H. Ghi nhận kết quả

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp:
[ ]

### I. Đánh giá UI/UX của người kiểm thử

Mức dễ hiểu:
[ ]

Mức thuận tiện:
[ ]

Điểm gây khó hiểu:
[ ]

Đề xuất cải thiện:
[ ]

## 15. Kịch bản kiểm thử xuyên suốt UC01–UC12

Chuẩn bị ba cửa sổ theo mục 2.3: **1 — VTYT (`demo_vtyt`)**, **2 — BGD (`demo_bgd`)**, **3 — KHOA (`demo_khoa_noi`)**. Dùng kế hoạch mới `MANUAL-V1-20260928-E2E-01`, **chỉ có 001**, kỳ/ngày trong hiệu lực FREE theo mục 2.5. Để các cửa sổ mở cùng lúc; sau thao tác ở cửa sổ khác, F5 hoặc **Tải lại** trước khi tiếp tục.

| Bước | Cửa sổ | Thao tác trên giao diện | Mốc cần kiểm tra |
|---|---|---|---|
| 1 | 1 — VTYT | **Kế hoạch bảo trì → Tạo kế hoạch**; nhập tiêu đề/kỳ, **Thêm** 001, đặt ngày dự kiến, **Tạo kế hoạch**. | Plan **Nháp (DRAFT)**, item **Dự kiến (PLANNED)**. |
| 2 | 1 — VTYT | **Chỉnh sửa**, bổ sung tiêu đề, **Lưu chỉnh sửa**; **Gửi phê duyệt** và xác nhận. | Dữ liệu sửa được lưu; plan **Đã gửi duyệt (SUBMITTED)**. |
| 3 | 2 — BGD | **Phê duyệt → Duyệt kế hoạch → Xem & quyết định** đúng E2E; chọn **Phê duyệt**, **Xác nhận quyết định** và xác nhận. | Plan **Đã phê duyệt (APPROVED)**. |
| 4 | 1 — VTYT | Tải lại E2E, **Chọn coverage**, chọn FREE của 001, **Xác định tuyến** và xác nhận. | Item **Theo hợp đồng (UNDER_CONTRACT)**, đúng đơn vị coverage. |
| 5 | 1 — VTYT | **Thực hiện / bàn giao → Bắt đầu thực hiện** và xác nhận; nhập **Nội dung công việc**, **Lưu tiến độ**; nhập ghi chú, **Kết thúc công việc** và xác nhận. | Attempt 1, provider đúng; plan **Đang thực hiện (IN_PROGRESS)**; item **Chờ nghiệm thu kỹ thuật**. |
| 6 | 1 — VTYT | Chọn kỹ thuật **Đạt**, nhập **Kết luận bắt buộc**, **Ghi nghiệm thu kỹ thuật** và xác nhận. | Item **Chờ bàn giao (AWAITING_HANDOVER)**. |
| 7 | 3 — KHOA | **Bàn giao → Xem hạng mục → Xem thực hiện** đúng E2E; chọn **Đạt**, nhập kết luận và hai trường VTYT đồng ký; **Ghi kết quả bàn giao** và xác nhận. | Item **Hoàn tất (COMPLETED)**; KHOA vẫn đăng nhập; plan **Chờ báo cáo (AWAITING_REPORT)**. |
| 8 | 1 — VTYT | **Báo cáo → Chờ báo cáo → Mở báo cáo**; nhập nội dung, **Tạo bản nháp**, sửa **Kiến nghị**, **Lưu bản nháp**, **Hoàn tất báo cáo** và xác nhận. | Report **Báo cáo chính thức (FINAL)**; plan **Đã báo cáo (REPORTED)**; đếm 1 Hoàn tất / 0 Chuyển sửa chữa. |
| 9 | 1, rồi 2 và 3 | **Thiết bị & lịch sử → Xem lịch sử** 001; tìm E2E, mở bằng chứng; F5 và đối chiếu từng vai trò. | Tiến độ, technical/handover PASS và tham chiếu FINAL còn; quyền KHOA đúng phạm vi. |

Luồng FREE trên đi qua UC01–UC05 và UC08–UC12. **UC06–UC07 là nhánh NOT_FREE**, cần thực hiện riêng bằng MAIN/002 như các mục trên; không đánh dấu đã chạy hai UC đó chỉ vì E2E FREE thành công. Nếu chạy E2E với hai item, phải gửi/duyệt đơn vị ngoài trước bước bắt đầu đầu tiên và đưa cả hai đến kết quả cuối trước lập báo cáo.

- [ ] Kết thúc luồng một item: **COMPLETED / REPORTED / FINAL**, không yêu cầu CLOSED.
- [ ] Ba cửa sổ vẫn đúng người dùng; co-signer không thay phiên KHOA.
- [ ] Dữ liệu/trạng thái/bằng chứng còn sau F5 và mở lại lịch sử.
- [ ] UC06–UC07 đã được kiểm tra ở nhánh ngoài hợp đồng riêng.

Kết quả thực tế:
[ ]

PASS / FAIL:
[ ]

Ghi chú:
[ ]

Ảnh chụp màn hình:
[ ]

## 16. Phiếu đánh giá UI/UX thủ công

### Navigation

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

### Dashboard

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

### Plan list

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

### Plan create/edit

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

### Approval

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

### Routing / provider

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

### Execution

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

### Technical acceptance

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

### Handover

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

### Report

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

### Equipment history

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

### Multi-role workflow

Điểm tốt:
[ ]

Điểm khó hiểu:
[ ]

Thao tác thừa:
[ ]

Thông tin còn thiếu:
[ ]

Đề xuất cải thiện V2:
[ ]

## 17. Ghi chú tính năng còn thiếu / ứng viên Version 2

**Ranh giới phạm vi V1 đã chấp nhận, không phải bug:** chưa có quản lý tài khoản ADMIN; chưa có quy trình sửa chữa tiếp nối sau **Chuyển sửa chữa (REPAIR_REQUIRED)**. Ghi đề xuất bên dưới nếu người kiểm thử có nhận xét; tài liệu này không bắt đầu triển khai V2.

| ID | Role | Màn hình / quy trình | Vấn đề phát hiện | Đề xuất | Mức ưu tiên |
|---|---|---|---|---|---|
|  |  |  |  |  |  |
|  |  |  |  |  |  |
|  |  |  |  |  |  |

## 18. Tổng hợp kết quả kiểm thử

| UC | Tên chức năng | PASS | FAIL | Cần kiểm tra lại | Ghi chú |
|---|---|---|---|---|---|
| UC01 | Tạo kế hoạch bảo trì | [ ] | [ ] | [ ] |  |
| UC02 | Chỉnh sửa kế hoạch bảo trì | [ ] | [ ] | [ ] |  |
| UC03 | Gửi kế hoạch bảo trì để phê duyệt | [ ] | [ ] | [ ] |  |
| UC04 | Phê duyệt kế hoạch bảo trì | [ ] | [ ] | [ ] |  |
| UC05 | Xác định hình thức / tuyến bảo trì | [ ] | [ ] | [ ] |  |
| UC06 | Lập đề xuất đơn vị bảo trì ngoài | [ ] | [ ] | [ ] |  |
| UC07 | Phê duyệt đề xuất đơn vị bảo trì ngoài | [ ] | [ ] | [ ] |  |
| UC08 | Theo dõi thực hiện bảo trì | [ ] | [ ] | [ ] |  |
| UC09 | Nghiệm thu kỹ thuật | [ ] | [ ] | [ ] |  |
| UC10 | Bàn giao thiết bị | [ ] | [ ] | [ ] |  |
| UC11 | Lập báo cáo bảo trì | [ ] | [ ] | [ ] |  |
| UC12 | Xem lịch sử bảo trì thiết bị | [ ] | [ ] | [ ] |  |

Tổng số UC PASS:
[ ]

Tổng số UC FAIL:
[ ]

Blocker:
[ ]

Có thể bắt đầu Version 2:
[ ]
