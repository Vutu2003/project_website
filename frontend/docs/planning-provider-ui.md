# Planning, Approval & Provider UI

**Phase 4.2 — UC01–UC07, 2026-09-27.** The Spring backend remains the authority for roles, versions, state transitions and provider assignment. The UI only validates basic input, sends named commands and reloads stored results.

## Routes

| Browser route | Purpose | Access |
| --- | --- | --- |
| `/plans` | Paged plan list and exact status filter | Authenticated read; create link only for PHONG_VTYT |
| `/plans/new` | UC01 create | PHONG_VTYT |
| `/plans/:planId` | Plan, item, routing and proposal context | Authenticated read; commands only for PHONG_VTYT |
| `/plans/:planId/edit` | UC02 edit | PHONG_VTYT; backend allows DRAFT/REVISION_REQUIRED |
| `/approvals` | Pending director queue | BAN_GIAM_DOC |
| `/approvals/:requestId` | Review and decide | BAN_GIAM_DOC |

The sidebar shows role-relevant entry points. Direct navigation to a forbidden command route shows the unauthorized page; backend role checks remain authoritative. UC08–UC12 links are clearly marked as future work.

## Role Access

| Feature | PHONG_VTYT | BAN_GIAM_DOC | KHOA_PHONG | ADMIN |
| --- | --- | --- | --- | --- |
| Plan list/detail | Read | Read | Read | Read |
| Create/edit/submit plan | Command | — | — | — |
| Approval queue/review/decision | — | Command | — | — |
| Route coverage | Command | — | — | — |
| Create/submit vendor proposal | Command | — | — | — |
| Vendor decision | — | Command | — | — |

Authenticated plan read follows the existing backend GET permission. No new Phase 4.2 mutation is offered to KHOA_PHONG or ADMIN.

## Plan List

`GET /api/plans?page&size&sort=createdAt,desc&status` supplies ten rows per page. The table shows title, period, current state, creator, creation time and version. It has loading, empty, retry and previous/next states. Status filter uses only exact backend enum names.

## Create Plan — UC01

`/plans/new` submits title, ISO business dates and one or more equipment items to `POST /api/plans`. The active equipment selector uses backend pagination (`GET /api/equipment`) and shows code, model and department. The form checks required fields, date order, selected item count and duplicate selections. Backend checks equipment/business eligibility. Success opens the ID returned by the server.

## Edit Plan — UC02

The edit page loads plan and all item pages, then sends `PATCH /api/plans/{id}` with the loaded `version`. Existing items remain in the form; V1 item removal is unavailable to preserve audit evidence. The user can update dates or add equipment. A REVISION_REQUIRED save returns to DRAFT according to the backend; the UI reloads the response. Invalid states are visible but cannot be submitted from this form.

## Submit Plan — UC03

The DRAFT plan page confirms before `POST /api/plans/{id}/submit` with the loaded version. The button is disabled while waiting. The page reloads stored plan and item state after success.

## Approval Queue and Plan Decision — UC04

`GET /api/approvals/pending` supplies actual PENDING PLAN_APPROVAL and VENDOR_SELECTION requests, with pagination/filtering. The director opens `/approvals/:requestId`; `GET /api/approvals/{id}` supplies the complete pending review. Plan review also loads the plan and first 100 items as context, with the actual total shown. `POST /api/approvals/{id}/decision` sends the current plan/item version, `APPROVE` or `REVISION_REQUIRED`, and optional/required comment. Revision requires a nonblank reason. Important decisions use a confirmation dialog. After success, navigation refreshes the pending queue.

## Coverage Routing — UC05

An APPROVED plan exposes routing for PLANNED items. `GET /api/equipment/{id}/coverages` displays available evidence, provider, effective dates, verification, basis and contract reference. The user explicitly selects a coverage row; `POST /api/plan-items/{id}/route` sends `{version, coverageId}`. The backend validates row ownership, evidence, effective dates and plan/item state. The frontend disables UNKNOWN; an absent row leaves no selectable route. It does not convert UNKNOWN to NOT_FREE.

A valid FREE route returns `UNDER_CONTRACT` with provider assigned by the backend. A valid NOT_FREE route returns `PENDING_PROPOSAL`. The plan detail reloads item state, route and provider from storage.

## External Provider Proposal — UC06

For PENDING_PROPOSAL, the panel loads active providers from `GET /api/providers` and any saved draft from `GET /api/plan-items/{id}/vendor-proposals/draft`. A missing draft (404) is a normal empty case. `POST /api/plan-items/{id}/vendor-proposals` creates a DRAFT request; `POST /api/vendor-proposals/{requestId}/submit` sends provider, rationale and warranty note using the current item version. A draft survives refresh because its ID and fields are read from the backend. A completed submission moves the item to WAITING_VENDOR_APPROVAL through the backend response.

## Vendor Approval — UC07

The director review shows equipment, proposed provider, coverage, rationale and warranty impact before the decision. APPROVE leads to ASSIGNED_EXTERNAL and the chosen provider on the stored item. REVISION_REQUIRED returns the item to PENDING_PROPOSAL; VTYT can create a new round. Old request/action history is retained by the backend. The UI does not synthesize previous rounds when no history endpoint is present.

## Optimistic Conflict UX

Every versioned command sends the version just loaded from the server. A 409 with code `OPTIMISTIC_LOCK_CONFLICT` shows a dedicated concurrency message and a reload action; the UI never retries a write silently. Other 409 business conflicts retain the backend message. After reload the user must review current data and choose the action again.

## Error States and Accessibility

Loading and busy states prevent duplicate submissions. Missing records show not-found feedback; 403 shows authorization feedback; 401 follows the existing session expiry flow; network errors remain distinct. Forms have visible labels and text status badges. Tables scroll within their panels at narrower widths. At 760px, plan list, create form and approval queue were checked in Chrome with no page-level overflow. Native confirmation dialogs support cancel/Escape. This is a basic accessibility check, not a WCAG certification.

## Backend Contract Notes

Three confirmed read gaps were documented before backend implementation:

| Need | Existing limitation | Minimal read-only addition |
| --- | --- | --- |
| Choose `coverageId` explicitly | Item/equipment DTO had no coverage choices | `GET /api/equipment/{id}/coverages` for PHONG_VTYT |
| Inspect all vendor evidence and version before decision | Pending queue omitted rationale/warranty and item version | `GET /api/approvals/{id}` for BAN_GIAM_DOC |
| Resume a saved DRAFT after refresh | Create response ID was transient; queue contains only PENDING | `GET /api/plan-items/{id}/vendor-proposals/draft` for PHONG_VTYT |

These endpoints project existing rows through explicit DTOs and follow the frozen role permissions. They add no write, state transition, table, column, migration, seed change or new business rule. Frontend API calls stay in `src/api`; pages do not call `fetch` directly. Wire enums remain exact; Vietnamese labels are presentation only.

## Local Validation

Start PostgreSQL/backend as described in [frontend/README.md](../README.md), then `npm run dev --prefix frontend` and open `http://localhost:5173/login`. The real-browser smoke covered create/edit/submit, both approval decisions and revision loops, FREE and NOT_FREE routing, UNKNOWN/absent coverage, draft refresh, stale-version 409 and all four roles. Isolated smoke rows were removed after the run; the 603-row canonical fixture remains.
