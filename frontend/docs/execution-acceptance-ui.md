# Execution, Acceptance & Handover UI

**Phase 4.3 — UC08–UC10 (2026-09-28).** The frontend displays stored evidence and sends the frozen named commands. Spring owns authorization, eligibility, attempt numbering, actual provider, state transitions, timestamps and signer identity.

## Routes

| Route | Purpose | Browser roles |
| --- | --- | --- |
| `/execution` | Paged plans visible to the current account | PHONG_VTYT, KHOA_PHONG |
| `/execution/plans/:planId` | Paged plan items; KHOA_PHONG sees only its historical department items | PHONG_VTYT, KHOA_PHONG |
| `/plans/:planId/items/:itemId/execution` | Item summary, all attempts, current commands and evidence | PHONG_VTYT, KHOA_PHONG |

Plan detail also links routed and later-state items to their execution page. UC11 reports and UC12 full equipment history remain future pages.

## Role Access

| Feature | PHONG_VTYT | BAN_GIAM_DOC | KHOA_PHONG | ADMIN |
| --- | --- | --- | --- | --- |
| Execution context in Phase 4.3 UI | Read | — | Scoped read | — |
| Start, progress, finish, repair hand-off | Command | — | — | — |
| Technical acceptance | Command | — | — | — |
| Handover context | Read | — | Scoped read | — |
| Handover decision | — | — | Scoped command | — |
| Handover PASS second signer | Authenticate as confirmer | — | — | — |

The backend history endpoint also supports BAN_GIAM_DOC read for UC12, but no director execution page is added in this phase. The browser guards are usability controls; backend URL rules and service checks decide final access.

## Execution Read Model

No backend addition was needed. `GET /api/equipment/{equipmentId}/maintenance-history` already returns campaigns and complete nested attempt/progress/technical/handover evidence, including actual provider and signer IDs/times. The execution page first loads plan/item through existing scoped plan reads, then selects exactly the matching `planId` and `itemId` campaign from the history response. It does not present the full UC12 equipment history UI or fabricate a missing attempt. KHOA_PHONG history campaigns are filtered to `department_id_at_plan` by the backend, and the item lookup uses its department-scoped plan items endpoint.

## Start Attempt — UC08

For UNDER_CONTRACT, ASSIGNED_EXTERNAL or REWORK_REQUIRED, VTYT confirms `POST /api/plan-items/{id}/executions` with the latest item `version` and plan `planVersion`. The backend derives provider and `attemptNo`; the page reloads both plan and history. A first start may move APPROVED to IN_PROGRESS. REPAIR_REQUIRED never shows a start control.

## Progress Logging

The current active attempt accepts a nonblank work note and optional damage note through `POST /api/executions/{executionId}/progress`. Logs are append-only, shown in server order with timestamp and recorder ID. An empty attempt says “Chưa có cập nhật tiến độ.” Old attempts expose no edit/delete action.

## Complete Work

VTYT may send `POST /api/executions/{executionId}/complete-work` with item version and optional result note. Confirmation explains that work ends and technical acceptance still follows. After success the item reloads as AWAITING_TECHNICAL_ACCEPTANCE.

## Repair Hand-off

While work is active, `POST /api/executions/{executionId}/repair-required` requires item version and nonblank reason. The page shows REPAIR_REQUIRED as a terminal V1 hand-off with retained progress/reason; it offers no rework, technical or handover command. Technical or handover FAIL can also explicitly choose `repairRequired=true` in the frozen assessment command.

## Technical Acceptance — UC09

VTYT acts only on the latest ended attempt while the item is AWAITING_TECHNICAL_ACCEPTANCE. `POST /api/executions/{id}/technical-acceptance` sends item version, PASS/FAIL, nonblank conclusion and a repair flag only for FAIL. PASS leads to AWAITING_HANDOVER. FAIL leads to REWORK_REQUIRED or REPAIR_REQUIRED. The saved assessment is shown on its original attempt after reload.

## Rework and Multi-attempt Presentation

REWORK_REQUIRED offers a new start command. Each attempt card shows the server-assigned number, actual provider, start/end, progress, result note, technical assessment and handover assessment. Cards are ordered by attempt number; the latest is labelled “Lần thực hiện hiện tại”. Old cards remain read-only. Handover eligibility uses current item state **and** the latest attempt's technical PASS; an old PASS never unlocks a new attempt.

## Handover — UC10

KHOA_PHONG may assess only an AWAITING_HANDOVER item whose current attempt has technical PASS. The receiving account must match the historical `department_id_at_plan`; the backend enforces this. `POST /api/executions/{id}/handover` sends item version, PASS/FAIL, conclusion and optional FAIL repair flag. PASS becomes COMPLETED; FAIL becomes REWORK_REQUIRED or REPAIR_REQUIRED. Stored signer IDs/times and result remain visible on the attempt. A plan whose items are all COMPLETED or REPAIR_REQUIRED may become AWAITING_REPORT, shown as a neutral note.

## Two-signer Authentication

A PASS requires the primary KHOA_PHONG session and a second active PHONG_VTYT account. The form requests VTYT username/password using a password input with autofill disabled. `POST /api/auth/login` is called **without** replacing the primary session; its returned role is checked, and the temporary token is passed only as `X-VTYT-Authorization: Bearer …` on the handover call. The normal `Authorization` header still carries the KHOA token. The secondary token is a local function value, never rendered, logged or stored; form credentials are cleared after success/failure and when switching to FAIL. FAIL does not ask for a second signer. The backend validates both sessions and derives signer IDs.

## Optimistic Conflict UX and Errors

Versioned commands use current server values. A 409 `OPTIMISTIC_LOCK_CONFLICT` shows a specific reload control; no mutation is silently retried. Other backend business 409 responses keep their message. Only intentional `UserInputError` messages are exposed for local validation; unexpected JavaScript errors receive a generic message. Buttons disable during mutations, and a synchronous in-flight guard prevents repeated clicks before React redraw. Important transitions ask for confirmation.

## Responsive and Accessibility Basics

The execution board is paged; the item page uses labelled forms, explicit PASS/FAIL radios, text badges and keyboard-usable native buttons/confirmations. Attempt cards become a single column around 760px. Chrome checks at desktop and 760px found no page-level horizontal overflow. This is not a full WCAG review.

## Backend Contract Notes

The existing history read includes attempts, progress, both acceptance types, current actual provider and safe signer identifiers. It is already role and department scoped, so Phase 4.3 made **no backend code, endpoint, security, schema, migration or seed change**. Command methods/bodies are in `src/api/executionsApi.ts` and `src/api/acceptancesApi.ts`; pages do not call `fetch` directly.

## Validation and Cleanup

Real Chrome with the packaged backend completed contract and external happy paths, technical and handover rework, repair hand-off, stale concurrent start, wrong department, missing/wrong co-signer, primary-session isolation, refresh, duplicate-click and 760px checks. Five guarded `SMOKE-P43` plans were deleted after recording results; canonical data returned to 603 rows. See the [Phase 4.3 report](../../reports/frontend/phase_4_3_execution_acceptance_handover_report.md) and [audit](../../reports/frontend/phase_4_3_frontend_audit.md).
