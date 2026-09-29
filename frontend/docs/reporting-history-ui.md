# Reporting & Equipment History UI

## Routes

`/reports` lists plans by the backend's `AWAITING_REPORT` or `REPORTED` status. `/plans/:planId/report` reads and, where permitted, writes BM03. `/equipment` lists backend-visible equipment; `/equipment/:equipmentId/history` renders the single UC12 projection. All four routes require authentication and role guards. The backend remains the access authority.

## Role Access

| Role | Report | Equipment history |
| --- | --- | --- |
| PHONG_VTYT | Create/edit DRAFT, finalize, read | Broad read |
| BAN_GIAM_DOC | Read | Broad read |
| KHOA_PHONG | No route | Historical-department scoped read |
| ADMIN | No route | No UC12 privilege |

## UC11 Reporting

The report entry queries existing plans with `status=AWAITING_REPORT` or `REPORTED`; it does not create a second eligibility algorithm. The page reads the plan, existing report and plan items. Before creation, item counts are labelled a provisional view. Once a report exists, `completedCount` and `repairRequiredCount` come from the backend response. They are separate and are never merged.

## Draft / Final

VTYT sends one `POST /api/plans/{id}/report` for a new draft and `PUT` for its edits, with the loaded plan version and narrative fields. It sends no actor ID, status or count. After each write the page reloads both plan and report. `POST /api/plans/{id}/report/finalize` uses `{version}` and asks for confirmation. Unsaved form edits must be saved before finalization. FINAL displays narrative text read-only; no edit, delete, reopen or `REPORTED → CLOSED` control exists.

## Outcome Counts

The two labels are **Hoàn tất bảo trì** (`COMPLETED`) and **Chuyển sửa chữa** (`REPAIR_REQUIRED`). The isolated mixed browser plan proved `1 + 1`, rather than two completed items. Backend decides whether every item is terminal and whether finalization is valid.

## Report Conflict Handling

A stale plan version receives `OPTIMISTIC_LOCK_CONFLICT` 409 and the shared reload action; the UI never retries automatically. A duplicate create receives the backend's 409 and leaves one report. Validation, 403, 404 and network failures use `WorkflowError`. A report GET 404 is treated as “no report yet” only after plan detail is independently loaded.

## UC12 Equipment History

The equipment list uses the existing scoped GET `/api/equipment` without an `active` filter, so inactive equipment with history remains reachable. The history page makes exactly one `GET /api/equipment/{id}/maintenance-history` read per load. It renders the returned campaigns in backend order, newest first. No per-attempt or per-progress HTTP loop is used.

## Multi-campaign View

Each card shows the plan title and period, historical department ID, item and plan status, route, assigned-provider reference where allowed, and report reference. A report link appears only for VTYT/BGD. The current department is shown only when the backend supplies it; former-department masking is preserved. A device with no campaigns shows an empty state.

## Multi-attempt View

The shared `AttemptHistory` component renders server-numbered attempts, actual provider, time, progress, technical acceptance, handover acceptance and signers. Both the Phase 4.3 execution page and UC12 page use it. Failed earlier attempts remain visible after later success. Each campaign has native `<details>` controls for attempts and state events; the newest campaign starts expanded.

## Failed Evidence / Repair Outcome

Technical FAIL and handover FAIL appear on their own attempts. `REWORK_REQUIRED` events and reasons stay in the status chronology. `REPAIR_REQUIRED` has a separate amber outcome and explicitly marks the V1 repair hand-off boundary; no V2 repair completion is implied.

## Department Scope

KHOA_PHONG receives only campaigns for its department at the time of the plan. The backend may expose equipment to a former department through historical custody and masks current custody where appropriate. The UI renders only returned campaigns, uses no current-department shortcut for access, and displays 403 without revealing equipment identity from a denied response. KHOA_PHONG has no plan-wide history, coverage/provider reference or unfinished report reference when the backend omits them.

## Shared Components / Error States

`AttemptHistory`, `StatusBadge`, `WorkflowError`, `WorkflowSuccess` and `Pagination` are reused. 404 missing equipment, 403 scope, 409 conflict and offline failures keep the shared safe error presentation. History has no mutation control. Form labels, visible status text, keyboard-operable native details and focus outlines provide the V1 accessibility baseline.

## Backend Contract Notes

The report contract is `GET/POST/PUT /api/plans/{planId}/report` and `POST /api/plans/{planId}/report/finalize`. The history DTO includes campaigns, attempts, progress, acceptance, state events and a concise report reference. Types are in `src/types/report.ts` and `src/types/execution.ts`; API calls stay in `src/api`. The backend owns eligibility, counts, versions, role and department checks. The frozen schema and backend workflows were not changed.
