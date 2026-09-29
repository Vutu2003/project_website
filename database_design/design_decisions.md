# FINAL FROZEN DECISIONS

The frozen decisions below override Phase 1.1 proposals. **Source** = directly specified in SA/forms; **Digital design** = SA's proposed electronic workflow/NFR; **Project assumption** = simplest V1 policy where source is silent. The source PDFs remain unchanged.

## FP-01 / FD-01 — Editable plan states
**Issue:** Part I BR01 says DRAFT only; UC02, Part II BR01 and Part III lifecycle also allow REVISION_REQUIRED. **Decision:** DRAFT and REVISION_REQUIRED are editable; saving a revision returns to DRAFT before UC03 resubmission. **Rationale:** repeated UC/lifecycle evidence dominates the isolated earlier wording. **Consequence:** SUBMITTED and later stay locked. **Classification:** project design decision resolving conflicting digital design statements.

## FP-02 / FD-02 — Approval rounds and decisions
**Issue:** Rejected plans/vendors may be resubmitted. **Decision:** each submission is a new `approval_request`; each pending request receives one immutable terminal `approval_action`. For vendor UC06, a DRAFT request stores proposed provider/rationale before submission; revision creates a new request. **Rationale:** preserves decision history without a separate proposal entity or content snapshot. **Consequence:** prior request/action rows remain read-only. **Classification:** digital design from UC03/04/06/07 and NFR-AUDIT.

## FP-03 / FD-03 — Coverage classification
**Issue:** Missing coverage cannot imply non-free. **Decision:** absent row or UNKNOWN blocks routing; FREE and NOT_FREE require verifier/time/basis and dated evidence. **Rationale:** UC05 exception is explicit. **Consequence:** no automatic paid-route default. **Classification:** source-supported workflow with minimal project encoding.

## FP-04 / FD-04 — External provider approval
**Issue:** BM02 asks for director opinion on invited partner. **Decision:** `approval_request` stores item, proposed provider, rationale, warranty note; `approval_action` stores BGĐ outcome; plan item stores current approved provider/route. **Rationale:** proposal has no independently required post-approval life. **Consequence:** fewer tables/FKs; reject or revision retains its request row. **Classification:** source BM02 plus digital workflow.

## FP-05 / FD-05 — Rework attempts
**Issue:** REWORK_REQUIRED may loop to actual work again. **Decision:** retain `maintenance_execution` per numbered attempt and `maintenance_progress_log` per note. **Rationale:** plan item start/end alone would overwrite previous attempt and obscure failed acceptance. **Consequence:** each attempt records provider actually used. **Classification:** digital design grounded in UC08/09 and state lifecycle.

## FP-06 / FD-06 — Typed acceptance
**Issue:** UC09 technical and UC10 handover differ in rule/actor but share result, time and conclusion. **Decision:** one `acceptance_record` with TECHNICAL_ACCEPTANCE or HANDOVER_ACCEPTANCE. Max one of each type per execution attempt; failure remains and rework creates another execution. **Rationale:** less duplication than separate tables while preserving BR04. **Classification:** digital design adapting BM06/BM08.

## FP-07 / FD-07 — Explicit signers
**Issue:** Paper forms list variable participants but V1 UC10 requires department and VTYT confirmation. **Decision:** remove general participant table; record these two hospital user IDs and confirmation times on handover acceptance. Other paper witnesses are not structured V1 requirements. **Rationale:** two known signers need no N-row infrastructure. **Classification:** project V1 assumption consistent with UC10/BR04; physical paper remains separate.

## FP-08 / FD-08 — REPAIR_REQUIRED and reporting
**Issue:** UC10 allows all items complete or on a valid branch before AWAITING_REPORT. **Decision:** REPAIR_REQUIRED is a final V1 maintenance outcome and may satisfy plan report-readiness, but must be counted/reported separately from COMPLETED. **Rationale:** repair belongs to V2; blocking a plan forever would contradict report/history goals. **Consequence:** report makes unresolved hand-off visible; no repair table. **Classification:** project policy from SA lifecycle/UC10/11.

## FP-09 / FD-09 — Optional attachments
**Issue:** UC09 says scans may be attached *if supported*. **Decision:** defer upload and metadata table from V1 freeze. Source form facts are stored as columns; no binary or path field is necessary for mandatory UC01–UC12. **Consequence:** a later upload feature requires a reviewed design change. **Classification:** project scope decision based on optional UC wording.

## FP-10 / FD-10 — Historical references and simple RBAC
**Issue:** Phase 1.1 added generic roles and multiple snapshots. **Decision:** one `user_account.role_code`; keep only `maintenance_plan_item.department_id_at_plan` for historical scope and `maintenance_execution.provider_id` for actual performer. Remove name/code/provider/submission snapshots and SHA-256. **Rationale:** NFR-AUDIT requires transition/decision history, not exact cryptographic payload preservation; UC12 needs stable FK-linked history. **Consequence:** old master names may display their current label; this is acceptable V1 policy. **Classification:** project assumption plus NFR-driven minimum references.

## FD-11 — Status and concurrency storage
**Decision:** separate constrained strings for the eight plan and eleven item values, matched by application enums. Keep current status on each row and append typed `status_history`. Keep `version` on plan/item for optimistic locking; decision/state/history commit atomically. **Rationale:** direct and testable; no lookup tables or PostgreSQL enum migration burden. **Classification:** digital design/NFR-REL/AUDIT.

## FD-12 — Retention and identifier policy
**Decision:** generated stable PKs, explicit source-supported/proposed unique keys, no serial uniqueness, no routine hard deletion of submitted evidence; deactivate referenced masters. **Rationale:** UC12/history and NFR-REL-03. **Classification:** technical implementation support with stated project policies.

## Superseded Phase 1.1 decisions

| Earlier decision | Freeze treatment |
| --- | --- |
| DD-01, DD-02, DD-04, DD-09, DD-10, DD-11, DD-12 | Retained in simplified form as FD-11, FP-03, FD-12, FP-08. |
| DD-03 immutable submission snapshot/hash | **SUPERSEDED:** request/action remain; snapshot/hash removed. |
| DD-05 multiple assignment-history rows | **SUPERSEDED:** current choice on item; actual provider on execution. |
| DD-06 N-participant acceptance | **SUPERSEDED IN PART:** attempts/typed record retained; participant table merged into explicit signers. |
| DD-07 generic attachment owner structure | **SUPERSEDED:** optional attachments deferred; typed state-history FKs retained. |
| DD-08 multiple label snapshots | **SUPERSEDED:** historical department FK and execution provider FK only. |

