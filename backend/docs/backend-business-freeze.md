# Backend Business Freeze

**Project:** Medical Equipment Maintenance Management System  
**Baseline:** Phase 3.4, 2026-09-26. This is the reviewed V1 backend business contract for frontend work. See the [Phase 3.4 report](../../reports/backend/phase_3_4_reporting_history_final_audit_report.md) for validation evidence.

## 1. Implemented Use Cases

| UC | Backend capability | Primary role |
| --- | --- | --- |
| UC01 | Create maintenance plan with items | PHONG_VTYT |
| UC02 | Edit eligible draft/revision plan; preserve audited items | PHONG_VTYT |
| UC03 | Submit plan for director decision | PHONG_VTYT |
| UC04 | Decide plan approval/revision | BAN_GIAM_DOC |
| UC05 | Verify coverage and route item | PHONG_VTYT |
| UC06 | Draft/submit external provider proposal | PHONG_VTYT |
| UC07 | Decide external provider | BAN_GIAM_DOC |
| UC08 | Numbered work attempts and progress | PHONG_VTYT |
| UC09 | Technical acceptance | PHONG_VTYT |
| UC10 | Scoped handover with authenticated VTYT confirmer | KHOA_PHONG + PHONG_VTYT |
| UC11 | Draft/final maintenance report | PHONG_VTYT |
| UC12 | Read equipment maintenance history | KHOA_PHONG, PHONG_VTYT, BAN_GIAM_DOC |

## 2. Implemented Business Rules

- **BR01:** only DRAFT/REVISION_REQUIRED plans can be edited; revision save returns to DRAFT. Audited item removal is rejected under the V1 retention decision.
- **BR02:** work begins only with an approved/in-progress plan and valid routed item.
- **BR03:** FREE uses verified coverage provider; NOT_FREE requires director-approved external provider. UNKNOWN/missing coverage blocks routing. Actual provider is stored on each attempt.
- **BR04:** technical PASS on the latest attempt precedes successful handover; two authenticated scoped signers are required for COMPLETED. Failed attempts remain.
- **BR05:** every plan/item transition inserts append-only `StatusHistory` in the same transaction as its state/evidence changes.

## 3. Plan State Machine

```text
DRAFT → SUBMITTED → APPROVED → IN_PROGRESS → AWAITING_REPORT → REPORTED
              └→ REVISION_REQUIRED → DRAFT
```

`CLOSED` exists in the frozen enum and source state diagram, but no UC11 actor/condition defines an API command to close a reported plan. The implemented backend lifecycle ends at `REPORTED`.

## 4. PlanItem State Machine

```text
PLANNED → UNDER_CONTRACT ───────────────────────────┐
        → PENDING_PROPOSAL → WAITING_VENDOR_APPROVAL → ASSIGNED_EXTERNAL
                  ↑                 │ revision        │
                  └─────────────────┘                 │
UNDER_CONTRACT / ASSIGNED_EXTERNAL / REWORK_REQUIRED → IN_MAINTENANCE
IN_MAINTENANCE → AWAITING_TECHNICAL_ACCEPTANCE or REPAIR_REQUIRED
AWAITING_TECHNICAL_ACCEPTANCE → AWAITING_HANDOVER / REWORK_REQUIRED / REPAIR_REQUIRED
AWAITING_HANDOVER → COMPLETED / REWORK_REQUIRED / REPAIR_REQUIRED
```

`REPAIR_REQUIRED` is terminal in V1 and remains distinct from `COMPLETED`.

## 5. Approval Model

Each plan/vendor submission creates an `ApprovalRequest`. Each decided round has one immutable `ApprovalAction`; revision and resubmission create another request, preserving prior rounds. No generic status PATCH or direct approval-table edit/delete API exists.

## 6. Execution / Acceptance Model

One `MaintenanceExecution` is one numbered attempt with a frozen actual-provider FK. Progress logs are append-only. Each attempt has at most one technical and one handover acceptance. Rework creates a new attempt; failed evidence remains. Handover PASS checks technical PASS on that same current attempt, historical department scope and a second authenticated PHONG_VTYT session.

## 7. Reporting Model

One report per plan, with `DRAFT` and `FINAL` statuses. Draft create/edit advances plan version without changing plan status. Finalization requires all items `COMPLETED` or `REPAIR_REQUIRED`, nonblank `workDone`, and moves plan `AWAITING_REPORT → REPORTED` with history. Outcome counts are computed, not stored.

## 8. Security / Role Model

JWT Bearer login reloads the active account on each request. Four exact role strings are `PHONG_VTYT`, `BAN_GIAM_DOC`, `KHOA_PHONG`, and `ADMIN`. Commands check role in both URL security and service code. UC12 deliberately denies ADMIN; prior Phase 2 generic authenticated read endpoints retain their accepted role behavior. A second Bearer token in `X-VTYT-Authorization` authenticates the VTYT confirmer for handover PASS.

## 9. Department Isolation

KHOA_PHONG handover and history use `maintenance_plan_item.department_id_at_plan`. Equipment's current department governs current custody, while an old campaign remains visible only to the historical department. Equipment and plan GET paths scope KHOA_PHONG list/detail/item responses; a former department's equipment DTO masks current custody. UC12 does not expose plan-wide audit, coverage IDs, pending report references or another department's campaigns to a KHOA_PHONG viewer.

## 10. Transaction Boundaries

Transactional services own planning, edit, submission, director decisions, route/proposal, start/progress/finish, technical/handover assessment, repair hand-off and report draft/finalization. Multi-row state/evidence/history changes commit or roll back together. Controllers parse/validate and map responses; they do not orchestrate workflow decisions.

## 11. Optimistic Locking

`MaintenancePlan` and `MaintenancePlanItem` are the only versioned business entities. Plan edit/submit/decision and report mutations use plan version; item routing, approval, execution, acceptance and handover use item version; first execution also requires plan version. Stale commands return `409 OPTIMISTIC_LOCK_CONFLICT`. Plan/item row locks serialize concurrent attempt numbering and report readiness checks.

## 12. Error Contract

HTTP success uses flat DTOs, `PageResponse<T>` for pages and ordinary JSON lists for references. Failure uses `ErrorResponse(timestamp,status,error,code,message,path,fieldErrors)`. 400 means malformed input, 401 unauthenticated, 403 forbidden role/scope, 404 missing record, 405 unsupported method, 409 workflow/version/unique conflict, and 500 safe unexpected error. No entity, SQL or JWT internals appear in client errors.

## 13. API Inventory

| Area | Paths |
| --- | --- |
| Authentication | `POST /api/auth/login`, `GET /api/auth/me` |
| References | `GET /api/departments`, `GET /api/providers` |
| Equipment | `GET /api/equipment`, `GET /api/equipment/{id}`, `GET /api/equipment/{id}/maintenance-history` |
| Plans | `POST /api/plans`, `PATCH /api/plans/{id}`, `POST /api/plans/{id}/submit`, `GET /api/plans`, `GET /api/plans/{id}`, `GET /api/plans/{id}/items` |
| Routing | `POST /api/plan-items/{id}/route`, `POST /api/plan-items/{id}/vendor-proposals`, `POST /api/vendor-proposals/{id}/submit` |
| Approvals | `GET /api/approvals/pending`, `POST /api/approvals/{id}/decision` |
| Execution | `POST /api/plan-items/{id}/executions`, `POST /api/executions/{id}/progress`, `POST /api/executions/{id}/complete-work`, `POST /api/executions/{id}/repair-required` |
| Acceptance | `POST /api/executions/{id}/technical-acceptance`, `POST /api/executions/{id}/handover` |
| Reporting | `POST/PUT/GET /api/plans/{id}/report`, `POST /api/plans/{id}/report/finalize` |

There is no `/api/v1` prefix or generic status mutation endpoint.

## 14. Known V1 Limitations

- Audited item removal is rejected because the frozen schema has no valid retained-item removal state.
- `REPORTED → CLOSED` remains a documented lifecycle edge without a source-defined actor/command.
- No refresh token, frontend, V2 repair, attachments, notification or inventory module exists.
- No formal production-scale load/latency proof exists; 603 synthetic rows validate function and bounded query shapes only.
- Phase 2 generic authenticated reads preserve their prior ADMIN behavior; UC12 and UC11 use explicit narrower business roles. Production access review should revisit generic reference/read routes if hospital policy requires tighter ADMIN visibility.
- The VTYT handover co-signer header requires protected transport outside the local demo environment.

## 15. Frontend Handoff

Frontend may rely on JWT login, `/api/auth/me`, the exact four role strings, 401/403 semantics, `ErrorResponse` and `PageResponse` shapes, enum names, named command/read endpoints, version fields, and the documented workflow states. After every successful write, use returned versions or refresh GET before the next command. Use the two authenticated sessions for handover PASS. Render failed attempts and `REPAIR_REQUIRED` distinctly. Do not set status directly, invent transitions, submit actor IDs, or infer privileges from UI state alone. See [reporting/history guide](reporting-history-workflow.md) and earlier workflow guides for request/response details.
