# UC05 — Báo cáo tổng hợp: Xác định hình thức & đối tác bảo trì

**Trạng thái: PASS kỹ thuật — chờ human review UC05.** Báo cáo gồm yêu cầu nguồn, kiểm toán trước triển khai, quyết định thiết kế, thay đổi, kết quả kiểm chứng và hướng dẫn kiểm thử thủ công.

## 1. Nguồn yêu cầu và phạm vi

Hoàn thiện UC05 để PHONG_VTYT thực hiện được từ UI theo [tài liệu nguồn](../../../docs/system_analysis_v1.pdf), dừng ở UNDER_CONTRACT/PENDING_PROPOSAL hoặc chặn có giải thích. ADMIN Account Management/ADM-03 và UC01–UC03 được người dùng xác nhận/freeze theo context đầu việc; thay đổi hiện tại chỉ xử lý UC05 và các điểm tích hợp cần thiết.

Nguồn đã đọc trước khi sửa code: `docs/system_analysis_v1.pdf`, UC05 trang in 18–19, BR01–BR05 và bảng chuyển trạng thái; đối chiếu `docs/use_case.pdf` (cùng đặc tả UC05). UC05 do PHONG_VTYT thực hiện trước **hoặc** sau phê duyệt kế hoạch. FREE xác nhận đơn vị từ hợp đồng → UNDER_CONTRACT; NOT_FREE → PENDING_PROPOSAL; thiếu/UNKNOWN/không đủ căn cứ phải chặn. BR03 quy định đơn vị, BR05 quy định truy vết; BR02 chỉ cho bắt đầu thực hiện sau phê duyệt.

<a id="audit"></a>

## 2. Kiểm toán trước triển khai và khoảng thiếu

### 2.1. Triển khai đã có

- PostgreSQL V001–V007: `maintenance_coverage` đã có equipment/provider, classification, thời hạn, hợp đồng/phạm vi, người/thời điểm xác minh và căn cứ. Không có cột active cho coverage; tính hợp lệ được xác định bằng evidence và ngày áp dụng. `maintenance_plan_item` giữ coverage_id, assigned_provider_id, assignment_route và version; MaintenanceAssignment đã được gộp vào item theo kiến trúc V1. `status_history` giữ actor/time/action/reason/old/new. Provider có active. Baseline V2: 14 bảng / 118 cột / 31 FK.
- Backend: `MaintenanceAssignmentService.route`, `POST /api/plan-items/{itemId}/route`, request `{version, coverageId}`. Đã có validation thiết bị/ngày/căn cứ/VTYT verifier/active provider, chặn UNKNOWN/thiếu evidence, FREE và NOT_FREE, transaction/history và item optimistic version. Coverage đọc bằng `GET /api/equipment/{id}/coverages` với DTO an toàn. Route chỉ cho item PLANNED và parent APPROVED.
- Frontend: `PlanDetailPage.ItemWorkflowPanel` đã đọc coverage và gọi route, nhưng entry “Chọn coverage” chỉ hiện dưới parent APPROVED. UNKNOWN disabled; hồ sơ hết hạn/chưa xác minh vẫn chọn được, chỉ biết lỗi sau submit. Cùng panel có UC06 đã tồn tại từ V1.
- Integration: `PlanningService.submit` chỉ nhận item PLANNED. `edit` từ chối mọi item đã route ngay cả khi ngày không đổi. `ExecutionAcceptanceService.start` đã kiểm tra APPROVED/IN_PROGRESS, item route và version.
- Test/evidence: `ProviderRoutingIntegrationTest` đã kiểm tra FREE/NOT_FREE/evidence/auth/rollback/version, nhưng một test cố ý chặn DRAFT/SUBMITTED/REVISION_REQUIRED. Báo cáo Phase 3.2, Phase 4.2 và Phase 5.1/5.2 xác nhận luồng cũ chạy sau phê duyệt. Frontend chưa có test chức năng riêng cho UC05.

### 2.2. Vì sao người review không thấy UC05

UC05 **triển khai một phần, bị ẩn theo trạng thái và bị chặn sai điều kiện**, không phải chỉ có backend. Người đang kiểm thử UC01–UC03 không thấy action vì kế hoạch chưa APPROVED; tên coverage không nói rõ nghiệp vụ UC05. Hiển thị thiếu giải thích căn cứ không hợp lệ, kết quả FREE/NOT_FREE và khác biệt định tuyến với quyền thực hiện.

### 2.3. Đối chiếu tài liệu nguồn

| Nội dung | Nguồn | Trước thay đổi |
| --- | --- | --- |
| Thời điểm UC05 | Trước hoặc sau phê duyệt | Chỉ APPROVED ở cả backend/UI |
| FREE | Provider từ coverage, UNDER_CONTRACT | Đã đúng |
| NOT_FREE | PENDING_PROPOSAL, chưa phân công ngoài | Đã đúng |
| Thiếu/UNKNOWN | Chặn, không suy diễn NOT_FREE | Backend đúng; UX chưa rõ |
| Thực hiện UC08 | Sau phê duyệt | Backend đúng; trang execution chưa giới hạn nút start theo plan |

Test APPROVED-only cũ thể hiện sai lệch thiết kế; phải thay bằng kiểm tra các trạng thái được phép và giữ các test nghiệp vụ khác.

## 3. Quyết định nghiệp vụ và chuyển trạng thái

Cho phép UC05 khi parent thuộc **DRAFT, SUBMITTED, REVISION_REQUIRED, APPROVED** và item **PLANNED** chưa có assignment. Chặn IN_PROGRESS/AWAITING_REPORT/REPORTED/CLOSED và mọi item đã route hoặc đã bước sang workflow sau. Không hỗ trợ reroute.

SUBMITTED chỉ thêm quyết định UC05 và history cho item; không đổi title, kỳ kế hoạch, danh sách thiết bị, khoa hoặc ngày dự kiến, không đổi parent status/version hoặc yêu cầu phê duyệt. Đây là quyết định nghiệp vụ riêng được UC05 cho phép, không mở UC02 khi đã gửi duyệt (BR01). Gửi duyệt DRAFT nhận các trạng thái trước thực hiện PLANNED/UNDER_CONTRACT/PENDING_PROPOSAL. Sửa DRAFT/REVISION_REQUIRED vẫn cho giữ hạng mục đã route với ngày không đổi, nhưng chặn đổi ngày tham chiếu của coverage (kể cả periodStart dùng làm fallback). Không xóa/đặt lại route một cách ngầm định.

| Item | Evidence | Kết quả | Provider/route |
| --- | --- | --- | --- |
| PLANNED | FREE hợp lệ | UNDER_CONTRACT | Provider evidence / UNDER_CONTRACT |
| PLANNED | NOT_FREE hợp lệ | PENDING_PROPOSAL | null / null |
| PLANNED | Thiếu/UNKNOWN/sai equipment/ngày/verification/provider | Giữ nguyên | Không đổi |
| Đã route / đã thực hiện / đã duyệt vendor | Bất kỳ | 409, giữ nguyên | Không đổi |

Test APPROVED-only cũ đã được thay bằng 8 ca FREE/NOT_FREE trên 4 trạng thái kế hoạch được phép, theo tài liệu nguồn. Các test nghiệp vụ khác được giữ nguyên.

## 4. Ảnh hưởng database

**NO SCHEMA CHANGE / NO V008.** Flyway V001–V007; **14 business tables / 118 business columns / 31 FK**. Assignment tiếp tục nằm trong maintenance_plan_item. Không thêm audit model, coverage.active hoặc editor coverage. Seed/migration chuẩn không đổi. Regression và browser chạy trên cluster thử riêng `127.0.0.1:55433`, database `medical_maintenance_uc05_test`; backend 8081/frontend 5174/CDP 9335. Dữ liệu development không được dùng làm fixture cho các test phá trạng thái.

## 5. Thiết kế và thay đổi backend

- MaintenanceAssignmentService.route: parent được DRAFT/SUBMITTED/REVISION_REQUIRED/APPROVED; item phải PLANNED và chưa assignment. Giữ toàn bộ kiểm tra coverage thuộc thiết bị, VTYT verification/time/basis, ngày áp dụng inclusive và active contractual provider. Ngày xét là plannedDate, fallback periodStart. Không reroute sau quyết định.
- Route tiếp tục `POST /api/plan-items/{itemId}/route`, body `{version, coverageId}`. Trả thêm planId/planStatus/planVersion/providerName trong ItemWorkflowResponse hiện có; các field cũ giữ nguyên. Không thêm endpoint trùng.
- CoverageEvidenceResponse thêm verifiedByRole để UI có thể giải thích/chặn verifier không hợp lệ; DTO không chứa password/hash/token/JPA graph.
- History có actor/time/old/new/action cùng reason chứa coverage ID/classification/ngày/căn cứ; mutation và history trong một transaction.
- PlanningService tích hợp tối thiểu: submit nhận PLANNED/UNDER_CONTRACT/PENDING_PROPOSAL; edit giữ hạng mục đã quyết định nếu ngày không đổi, từ chối đổi ngày và fallback periodStart làm mất căn cứ. Giữ điều kiện chỉnh sửa DRAFT/REVISION_REQUIRED và không xóa audited items.

Biên `effectiveFrom`/`effectiveTo` được tính inclusive; null là không giới hạn theo V1. Chỉ ghi quyết định sau khi toàn bộ validation thành công. Coverage không có cột active; tính hợp lệ dựa trên căn cứ, xác minh và ngày áp dụng.

## 6. Thiết kế và thay đổi frontend

- Plan Detail có action **“Xác định hình thức & đối tác”** trên item PLANNED trong bốn trạng thái parent được phép, chỉ VTYT.
- CoverageRoutingPanel riêng hiển thị thiết bị, khoa, ngày xét hợp đồng, kế hoạch, loại hồ sơ tiếng Việt/enum, hợp đồng/phạm vi/thời hạn/provider/xác minh/căn cứ. Radio invalid disabled, có lý do; không hợp lệ thì không có nút xác nhận.
- FREE/NOT_FREE có nhãn xác nhận riêng, hộp xác nhận, busy guard và thông báo kết quả; reload dữ liệu thật từ backend. Bảng giữ provider/tuyến và hướng dẫn UC06 hoặc thông báo chờ duyệt.
- ExecutionItemPage giới hạn nút start theo APPROVED/IN_PROGRESS ngay cả khi mở URL trực tiếp. PlanFormPage giữ/khóa ngày của item đã route; periodStart được khóa nếu đang là fallback cho item đã quyết định.
- Error messages UC05 được diễn đạt tiếng Việt. Conflict reload đóng panel cũ, bỏ lựa chọn trước đó.
- ItemWorkflowResponse nay có planStatus cả ở vendor result: ApprovalDetailPage dùng discriminant itemId để vẫn hiển thị đúng item outcome. Đây là sửa tương thích DTO một dòng, không triển khai UC06/UC07 mới. Form/endpoint vendor có sẵn tiếp tục giữ logic cũ.

Nhãn xác nhận: FREE → **“Xác nhận bảo trì theo hợp đồng”**; NOT_FREE → **“Xác nhận ngoài hợp đồng”**. UNKNOWN hoặc thiếu hồ sơ hợp lệ không có nút xác nhận.

## 7. Kết quả các luồng UC05

### 7.1. FREE

Coverage FREE hợp lệ → provider chỉ được lấy từ coverage → persist coverage_id/assigned_provider_id/assignment_route=UNDER_CONTRACT/status=UNDER_CONTRACT, tăng item version và ghi VERIFY_FREE_COVERAGE. Browser xác nhận EQ-001 ngay khi plan DRAFT; SQL/API/UI và refresh thống nhất provider “Đơn vị bảo trì hợp đồng demo”. Không phát sinh vendor proposal.

### 7.2. NOT_FREE

Coverage NOT_FREE hợp lệ → PENDING_PROPOSAL, coverage được lưu, provider và assignmentRoute **null**, ghi CLASSIFY_NOT_FREE. UI thông báo chưa phân công ngoài và bước tiếp theo UC06. Browser xác nhận EQ-002 ở DRAFT rồi refresh, chưa có provider và không mở/gửi form UC06. Entry proposal cũ chỉ ở APPROVED, không sửa nghiệp vụ đó trong đầu việc này.

### 7.3. UNKNOWN và thiếu hồ sơ

UNKNOWN EQ-003: cảnh báo chưa đủ căn cứ, radio disabled, không xác nhận. Missing EQ-036: cảnh báo chưa có coverage phù hợp, không xác nhận. Item vẫn PLANNED, chưa coverage/provider/route/history decision. Hồ sơ sai equipment/ngày/verification/provider cũng bị chặn bởi backend và được giải thích ở UI. Thiếu thông tin không được suy diễn NOT_FREE.

### 7.4. Định tuyến trước phê duyệt và điều kiện thực hiện

SUBMITTED routing không sửa title/kỳ/equipment/khoa/plannedDate, parent status/version hoặc pending plan request. BR01 vẫn khóa UC02. Full integration kiểm tra quyết định DRAFT → submit → approve, đồng thời giữ route/provider; revision edit giữ route và chặn thay ngày. UC05 không cho phép bắt đầu execution. API UC08 trước approve vẫn trả PLAN_NOT_EXECUTABLE; sau approve, preconditions UC08 cũ vẫn hoạt động.

## 8. Phân quyền, truy vết và optimistic locking

### 8.1. Phân quyền

PHONG_VTYT được route/read coverage. Anonymous 401; BGD/KHOA/ADMIN command 403 ở security và service. Chrome đăng nhập cả bốn vai trò, kiểm tra entry và API. Không thêm quyền bảo trì cho ADMIN; read plan theo policy hiện có.

### 8.2. Kiểm soát cạnh tranh và rollback

Giữ request item version hiện có, không thêm planVersion bắt buộc. Item @Version chặn stale command 409. Parent OPTIMISTIC lock kiểm tra snapshot đến commit mà không tăng parent version; edit kế hoạch cũng kiểm tra optimistic version của các item đang giữ để không đè quyết định UC05 đồng thời.

Tests xác nhận stale item không mutate; fault injection history lỗi rollback route; independent parent version change trong transaction gây 409 và rollback cả decision/history. Chrome hai client xác nhận 409, không auto-retry; tải lại thấy quyết định hiện tại và không còn action cũ. Contract cũ kiểm tra client item version; parent snapshot được lấy ở server, không có client parent version trong route command.

## 9. Kiểm thử tự động

| Check | Kết quả thực tế |
| --- | --- |
| Frontend build | PASS (`npm run build --prefix frontend`) |
| Frontend lint | PASS (`npm run lint --prefix frontend`) |
| Frontend full tests | **89/89 PASS**, 12 files; UC05 mới **32 tests** |
| Backend focused ProviderRoutingIntegrationTest | **39 tests / 0 failure / 0 error / 0 skip** |
| Backend full suite | **131 tests / 0 failure / 0 error / 0 skip** (`env -u DEBUG mvn -f backend/pom.xml test`) |
| verify.sql | PASS, **70 assertions**, transaction ROLLBACK |
| validate_demo_data.sql | PASS, **32 assertions**, transaction ROLLBACK |
| JPA/database mapping | Full suite PASS: 14/118/31, V001–V007 |

Frontend tests bao phủ state/role entry, hồ sơ/provider/evidence, FREE/NOT_FREE confirmation/result, cancel, UNKNOWN/missing/expired/future/inactive/missing provider/unverified/wrong verifier/blank basis/wrong equipment, date fallback/bounds, backend error/conflict reload, UC08 direct URL trước/sau duyệt. Backend giữ toàn bộ test cũ ngoài test precondition bị sửa theo nguồn và thêm state matrix, no reroute, atomic rollback, parent race, submit/approve/execution, revision date preservation. Error log 500 chỉ thuộc fault injection đã chủ động kiểm tra; không có failure của suite.

## 10. Kiểm chứng Chrome thật và cleanup

**PASS — Chrome 154.0.8037.92 thật, 36 checks**, không mock API/backend. [Driver](verify_uc05_chrome.py), [evidence JSON](uc05_chrome_evidence.json), [FREE trước phê duyệt](screens/uc05_free_before_approval.png), [NOT_FREE](screens/uc05_not_free.png), [UNKNOWN](screens/uc05_unknown.png).

UI tạo kế hoạch thử 4 items EQ-001/002/003/036; FREE/NOT_FREE route ngay DRAFT, refresh giữ kết quả; UNKNOWN/missing vẫn PLANNED. Direct execution URL và API bị chặn trước approve. UI gửi duyệt được kế hoạch có hai quyết định UC05; BGD approve giữ trạng thái item; sau approve nút UC08 khả dụng theo điều kiện cũ, không bắt đầu execution trong browser smoke. Hai client stale trả 409; reload bỏ panel cũ. BGD/KHOA/ADMIN không thấy action và nhận 403. History có actor/time/evidence. Không có JS/CORS/console error và không có POST vendor-proposals. Kiểm tra 760px không tràn ngang; ảnh 1366px đã xem trực tiếp, không tự chấm điểm UX.

Hai smoke plans/5 items được cleanup có guard ID/title/FK; **0 smoke plan**, fingerprint của **14 bảng** và migrations bằng baseline trước browser, tổng 603 dòng canonical. Evidence chỉ giữ metadata an toàn, không password/token/header. Chrome/backend/frontend/PostgreSQL thử nghiệm riêng đã được dừng sau cleanup; localhost:8080/5173 chính vẫn chạy. Các lần đầu driver chưa chạy được vì thiếu Python websocket-client/venv; dùng pip target trong `/tmp` rồi chạy driver thành công. Lỗi đó thuộc tooling, chưa tạo dữ liệu smoke.

[Tổng hợp kết quả kiểm chứng máy đọc được](uc05_verification_summary.json) lưu số liệu test, schema, Chrome, cleanup và trạng thái môi trường local.

## 11. Regression và lỗi đã sửa

### 11.1. Regression

Full suite giữ PASS UC01–UC03, phê duyệt, provider workflows hiện có, execution/acceptance/report/history, auth/CORS/repository/schema, ADMIN Account Management và ADM-03. Chrome kiểm tra thêm UC01 tạo 4 item, UC03 gửi duyệt sau UC05 và UC04 phê duyệt giữ quyết định. Frontend ADMIN 19 account tests + 16 catalog tests PASS. Không đổi file ADMIN, seed hoặc migration.

Môi trường local localhost:8080/5173 được nạp backend mới bằng helper stop/start, PostgreSQL development giữ nguyên. Đối chiếu fingerprint trước/sau restart xác nhận dữ liệu development không đổi. Trạng thái cuối health/status ghi ở evidence completion.

### 11.2. Lỗi đã sửa

1. Parent APPROVED-only sai source → sửa 4 allowed states, reject later states.
2. Action coverage ẩn/khó nhận diện → tên UC05 tiếng Việt và panel giải thích.
3. Submit/edit bị kẹt khi đã route trước duyệt → nhận pre-execution routed states, giữ immutable date evidence.
4. Trang execution hiện start khi parent chưa approve → UI gate, backend gate giữ nguyên.
5. Evidence invalid chỉ có lỗi sau command → disabled với lý do trên UI, backend vẫn quyết định cuối.
6. History reason trống → lưu selected evidence/date/basis trong model có sẵn.
7. DTO thêm planStatus làm mất discriminant approval result → dùng itemId, giữ vendor item outcome.

## 12. Danh sách file thay đổi

**Backend (6):**

- `backend/src/main/java/vn/edu/medmaintenance/api/dto/response/CoverageEvidenceResponse.java`
- `backend/src/main/java/vn/edu/medmaintenance/api/dto/response/ItemWorkflowResponse.java`
- `backend/src/main/java/vn/edu/medmaintenance/service/MaintenanceAssignmentService.java`
- `backend/src/main/java/vn/edu/medmaintenance/service/PlanningService.java`
- `backend/src/main/java/vn/edu/medmaintenance/service/WorkflowReadService.java`
- `backend/src/test/java/vn/edu/medmaintenance/service/ProviderRoutingIntegrationTest.java`

**Frontend (8):**

- `frontend/src/components/CoverageRoutingPanel.tsx` (new)
- `frontend/src/pages/PlanDetailPage.tsx`
- `frontend/src/pages/PlanFormPage.tsx`
- `frontend/src/pages/ExecutionItemPage.tsx`
- `frontend/src/pages/ApprovalDetailPage.tsx`
- `frontend/src/pages/Uc05Routing.test.tsx` (new)
- `frontend/src/types/workflow.ts`
- `frontend/src/utils/errorMessages.ts`

**Reports/evidence:** một báo cáo Markdown duy nhất `uc05_report.md`, `verify_uc05_chrome.py`, `uc05_chrome_evidence.json`, `uc05_verification_summary.json`, 3 PNG trong thư mục này. PDF nguồn của người dùng không sửa; các file PDF untracked có sẵn/phát sinh từ người dùng không tính là thay đổi UC05.

## 13. Giới hạn và trạng thái hoàn thành

Human review UI/UX vẫn chờ; không tự tuyên bố HUMAN VERIFIED/FROZEN. UC06 mới không được triển khai; workflow vendor có sẵn chỉ giữ regression. Không có editor coverage vì không thuộc chức năng ADMIN hiện có/phạm vi đầu việc. Ngày của item đã route không được đổi hoặc reroute; cần xử lý theo thiết kế riêng nếu sau này muốn thay đổi căn cứ. Không bắt đầu thực hiện bảo trì trong browser smoke; backend integration đã kiểm tra execution sau phê duyệt thành công và trước phê duyệt bị chặn. Credential fixtures nằm ở env local riêng, không ghi trong report.

**PASS kỹ thuật — WAIT FOR HUMAN REVIEW UC05.** Source audit, UI discoverability, FREE/NOT_FREE/blockers/auth/history/version/date/pre-approval rule, toàn bộ regression và Chrome thật đều đã kiểm chứng. Database/migrations giữ baseline V2. Hướng dẫn: [12 ca kiểm thử thủ công bên dưới](#manual-tests). Human review UC05 → freeze nếu chấp nhận → tiếp tục UC06 validation/implementation.

<a id="manual-tests"></a>

## 14. Hướng dẫn kiểm thử thủ công tiếng Việt

Dành cho người review Phòng VTYT. Chỉ kiểm thử **Xác định hình thức & đối tác bảo trì**, dừng tại UNDER_CONTRACT/PENDING_PROPOSAL hoặc thông báo chặn. Không lập/gửi tờ trình UC06 trong hướng dẫn này.

### Chuẩn bị

1. Chạy `./scripts/start.sh`, kiểm tra `./scripts/status.sh`; mở `http://localhost:5173/login`. Dùng thông tin đăng nhập local theo [runtime guide](../../version1/local_dev/local_v1_runtime_guide.md#7-how-to-get-current-demo-passwords), không đưa mật khẩu vào biên bản.
2. Dùng kế hoạch thử mới có tiền tố `SMOKE-V2-UC05-MANUAL-` và hậu tố riêng. Ghi ID kế hoạch/hạng mục. Không sửa kế hoạch, hợp đồng hay danh mục chuẩn đã có lịch sử.
3. Với seed chuẩn: `DEMO-EQ-001` FREE, provider “Đơn vị bảo trì hợp đồng demo”; `DEMO-EQ-002` NOT_FREE; `DEMO-EQ-003` UNKNOWN; `DEMO-EQ-036` không có coverage. Kiểm tra hồ sơ trên UI trước khi dùng vì dữ liệu local của người review có thể đã thay đổi.
4. Chọn kỳ kế hoạch `01/11/2026–30/11/2026`, ngày dự kiến `15/11/2026`, nằm trong hợp đồng seed năm 2026. Có thể để ngày dự kiến trống để kiểm tra fallback ngày bắt đầu kế hoạch. Phân trang danh sách thiết bị khi thêm EQ-036.
5. Mỗi hạng mục chỉ quyết định UC05 một lần. Dùng hạng mục/kế hoạch mới cho ca cần quyết định lại. Khi đổi vai trò, dùng profile/tab độc lập hoặc đăng xuất rồi đăng nhập lại.
6. Giữ các trường kết quả bên dưới trống cho đến khi người review thực hiện. PASS kỹ thuật tự động không thay thế nhận xét UI/UX của người dùng.

### Test 1 — VTYT tìm thấy UC05

- Đăng nhập PHONG_VTYT → Kế hoạch bảo trì → tạo kế hoạch thử có EQ-001, EQ-002, EQ-003, EQ-036 → mở chi tiết khi còn Nháp.
- Tại từng hạng mục Dự kiến, bấm **“Xác định hình thức & đối tác”**.
- Mong đợi: mở panel UC05, hiện mã/tên thiết bị, khoa tại kế hoạch, ngày xét hiệu lực, tiêu đề/trạng thái kế hoạch. Không cần BGĐ phê duyệt để nhìn thấy action.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Test 2 — FREE → UNDER_CONTRACT

- Mở UC05 của EQ-001; xem hồ sơ “Theo hợp đồng (FREE)”, chọn radio của hồ sơ hợp lệ.
- Bấm **“Xác nhận bảo trì theo hợp đồng”**, xem hộp xác nhận; thử hủy một lần rồi xác nhận.
- Mong đợi: hủy không đổi dữ liệu; xác nhận thành công chuyển hạng mục sang **Theo hợp đồng (UNDER_CONTRACT)**, có feedback thành công. Action UC05 không còn trên hạng mục đã quyết định.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Test 3 — Provider hợp đồng đúng

- Trước Test 2, đối chiếu tên đơn vị, hiệu lực, hợp đồng, phạm vi, người/thời điểm xác minh và căn cứ trên hồ sơ EQ-001.
- Sau xác nhận, đối chiếu cột Đơn vị / tuyến trên chi tiết kế hoạch.
- Mong đợi: provider đúng hồ sơ FREE đã chọn; không có dropdown để chọn provider bất kỳ. Tuyến là Theo hợp đồng. Nếu provider không hoạt động, radio disabled và có lý do, không có nút xác nhận.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Test 4 — NOT_FREE → PENDING_PROPOSAL

- Mở UC05 của EQ-002, xem “Ngoài hợp đồng (NOT_FREE)”, chọn hồ sơ hợp lệ.
- Bấm **“Xác nhận ngoài hợp đồng”** và xác nhận.
- Mong đợi: trạng thái **Chờ đề xuất đơn vị (PENDING_PROPOSAL)**; chưa có đơn vị ngoài được phân công, không có tuyến EXTERNAL_APPROVED.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Test 5 — NOT_FREE chỉ rõ bước UC06

- Đọc giải thích trước xác nhận EQ-002 và feedback/cột thao tác sau Test 4.
- Mong đợi: “Thiết bị không thuộc diện bảo trì miễn phí theo hợp đồng”, bước tiếp theo **UC06 — Lập tờ trình chọn đơn vị bảo trì**. Nếu kế hoạch còn Nháp, chỉ có hướng dẫn bước tiếp theo; không mở form tờ trình.
- Dừng tại đây. Nút Đề xuất đơn vị đã có từ V1 chỉ xuất hiện khi kế hoạch APPROVED; việc review UC06 sẽ thực hiện sau khi chấp nhận UC05.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Test 6 — UNKNOWN và căn cứ không hợp lệ bị chặn

- Mở UC05 của EQ-003.
- Mong đợi: “Chưa đủ căn cứ để xác định hình thức bảo trì”, radio UNKNOWN disabled, không có nút xác nhận; hạng mục vẫn Dự kiến.
- Để kiểm tra ngày không áp dụng mà không sửa hợp đồng, tạo kế hoạch riêng cho EQ-001 vào `01/02/2027–28/02/2027`. Mong đợi: hồ sơ hết hiệu lực bị disabled và giải thích rõ. Không tự xem là ngoài hợp đồng.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Test 7 — Thiếu coverage bị chặn

- Mở UC05 của EQ-036 (hoặc thiết bị đã xác nhận không có hồ sơ phù hợp).
- Mong đợi: “Thiết bị chưa có hồ sơ coverage phù hợp”, yêu cầu VTYT xác minh/bổ sung thông tin; không có nút xác nhận và không chuyển sang PENDING_PROPOSAL.
- Không tạo giả hồ sơ coverage hoặc thêm chức năng biên tập ngoài phạm vi.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Test 8 — Sai vai trò bị chặn

- Ghi URL kế hoạch thử; lần lượt đăng nhập BGD, KHOA_PHONG, ADMIN rồi mở URL đó theo quyền đọc hiện có.
- Mong đợi: không có action UC05, không có khả năng xác nhận hình thức. Quyền đọc KHOA vẫn bị giới hạn theo khoa như V1.
- Nếu người kiểm thử API gọi `POST /api/plan-items/{id}/route` bằng token ba vai trò này: mong đợi 403, không đổi item/history. Không đưa token vào biên bản.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Test 9 — UC05 trước/sau phê duyệt

- **DRAFT:** thực hiện Test 2 hoặc 4 trước gửi duyệt; sau đó gửi kế hoạch. Mong đợi gửi duyệt thành công, quyết định UC05 vẫn giữ.
- **SUBMITTED:** dùng kế hoạch mới với một hạng mục PLANNED chưa quyết định, gửi duyệt trước rồi mở UC05. Mong đợi xác nhận được, parent vẫn Đã gửi duyệt; không mở khóa sửa title/kỳ/danh sách/ngày.
- **REVISION_REQUIRED:** BGĐ yêu cầu chỉnh sửa một kế hoạch thử có hạng mục PLANNED; VTYT mở UC05. Mong đợi action khả dụng. Khi lưu chỉnh sửa title với hạng mục đã route giữ ngày, quyết định vẫn giữ. UI khóa ngày đã dùng xác định hình thức; backend cũng chặn đổi ngày tham chiếu.
- **APPROVED:** BGĐ phê duyệt kế hoạch thử còn item PLANNED, VTYT thực hiện UC05 như bình thường.
- Không định tuyến lại hạng mục đã quyết định hoặc kế hoạch IN_PROGRESS/AWAITING_REPORT/REPORTED/CLOSED.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Test 10 — Chưa phê duyệt thì chưa được thực hiện

- Với EQ-001 đã UNDER_CONTRACT nhưng parent còn DRAFT/SUBMITTED/REVISION_REQUIRED, kiểm tra cột thao tác.
- Mong đợi: thông báo **“Hình thức bảo trì đã được xác định. Chỉ có thể bắt đầu thực hiện sau khi kế hoạch được phê duyệt.”**, không có link thực hiện.
- Mở trực tiếp `/plans/{planId}/items/{itemId}/execution`: không có nút Bắt đầu thực hiện. API UC08 vẫn từ chối 409 PLAN_NOT_EXECUTABLE.
- Cho BGĐ phê duyệt kế hoạch thử; tải lại. Với FREE hợp lệ, link/nút thực hiện trở nên khả dụng theo UC08. Chỉ kiểm tra điều kiện, không bắt đầu thực hiện trong đợt review UC05.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Test 11 — Giữ kết quả sau refresh

- Sau Test 2 và 4, nhấn F5; đăng xuất/đăng nhập lại và mở cùng kế hoạch.
- Mong đợi: FREE vẫn UNDER_CONTRACT với đúng provider/tuyến; NOT_FREE vẫn PENDING_PROPOSAL với hướng dẫn UC06 và chưa phân công ngoài. UNKNOWN/missing vẫn PLANNED.
- Đối chiếu trạng thái API/history nếu dùng công cụ kiểm tra. Quyết định có actor, thời điểm, action, trạng thái trước/sau và coverage/date/basis.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Test 12 — Xung đột phiên bản

- Tạo kế hoạch thử mới với EQ-001; mở cùng kế hoạch bằng hai tab VTYT.
- Tab A mở UC05 và chọn hồ sơ nhưng chưa xác nhận. Tab B xác nhận FREE thành công.
- Quay Tab A, bấm xác nhận với dữ liệu cũ.
- Mong đợi: 409 và **“Xung đột phiên bản”**; không tự retry và không thêm quyết định/history thứ hai.
- Bấm **“Tải lại dữ liệu”**. Mong đợi thấy UNDER_CONTRACT/provider hiện tại; panel/lựa chọn cũ được bỏ và không còn action UC05 cho item này.

PASS / FAIL: ____

Kết quả thực tế: ____

Ghi chú: ____

UI/UX nhận xét: ____

### Tổng hợp review

Người kiểm thử: ____ · Thời điểm: ____ · URL môi trường: ____

Kế hoạch/hạng mục thử: ____

PASS: ____ / FAIL: ____ / Cần kiểm tra lại: ____

UI/UX chung: ____

Quyết định chấp nhận/freeze UC05: ____

Giữ ID dữ liệu manual để truy vết. Chỉ cleanup dữ liệu smoke đã kiểm tra đúng tiền tố và thứ tự FK trong môi trường cách ly; không xóa dữ liệu chuẩn hoặc lịch sử người dùng. Tiếp theo: human review UC05 → freeze nếu chấp nhận → review UC06.
