# Planning & Approval Workflow

Phase 3.1 implements UC01–UC04 on the frozen 14-table V1 schema. Source authority: `docs/system_analysis_v1.pdf` (UC01–UC04, BR01/BR05, plan lifecycle), `database_design/phase_1_2_implementation_contract.md`, `database_design/data_dictionary.md`, and the Phase 1.3 DS-03 seed scenario.

## UC01 Create Plan

`POST /api/plans` accepts a title, period and at least one equipment item with an optional planned date. A `PHONG_VTYT` actor creates a `DRAFT` plan and `PLANNED` items in one transaction. Equipment must exist and be active; the same equipment may occur in different plans, but only once in this plan. The server copies the equipment's current department into each item's historical snapshot and derives the creator from `CurrentUser`. A plan and each item receive a creation history event with `old_state = null`. Response: 201 with ID, status and version.

## UC02 Edit Plan

`PATCH /api/plans/{id}` accepts expected plan version, title, period and optional item additions/date updates. It is additive: omitted items stay in the plan. Editing is allowed only for `DRAFT` and `REVISION_REQUIRED`; saving the latter records `REVISION_REQUIRED → DRAFT`. Period and all resulting planned dates must agree. A successful draft edit increments the plan version even when only items changed; it also records a `DRAFT → DRAFT` edit event. Requests to remove audited items return `409 PLAN_ITEM_RETENTION_CONFLICT`. This follows the agreed audit-preserving policy: the frozen item has a creation `status_history` FK and no removed state, so hard deletion would erase audit evidence. No remove endpoint is exposed.

## UC03 Submit Plan

`POST /api/plans/{id}/submit` requires expected version, `DRAFT` state and at least one valid `PLANNED` item. It sets `SUBMITTED`, creates a new `PENDING` `PLAN_APPROVAL` request for this round, and appends `DRAFT → SUBMITTED` history atomically. A duplicate pending request or second submit returns 409; `REVISION_REQUIRED` must be edited back to `DRAFT` first.

## UC04 Approve / Revision

`POST /api/approvals/{requestId}/decision` requires director role, expected plan version and `APPROVE` or `REVISION_REQUIRED`; the latter requires a nonblank comment. It accepts only a pending plan request whose plan is `SUBMITTED`. In one transaction it inserts one immutable `approval_action`, resolves the request, changes the plan to `APPROVED` or `REVISION_REQUIRED`, and appends history. The revision reason is copied into the history row. A repeated decision returns 409.

## Allowed Actors

| Command | PHONG_VTYT | BAN_GIAM_DOC | KHOA_PHONG | ADMIN |
| --- | --- | --- | --- | --- |
| Create, edit, submit | Yes | No | No | No |
| Plan decision | No | Yes | No | No |

Spring Security restricts routes; services repeat the role check so internal calls cannot bypass it. Actor IDs come from `CurrentUser`, never request JSON.

## Plan State Transitions

```text
DRAFT ──submit──▶ SUBMITTED ──approve──▶ APPROVED
  ▲                  │
  │                  └──revision──▶ REVISION_REQUIRED
  └──────────────────────edit/save──────────────┘
```

Only these four transitions are implemented in Phase 3.1. There is no generic status mutation endpoint.

## Approval Rounds

A revision leaves the first `approval_request` as `DECIDED` and its `approval_action` intact. Resubmission creates a second request; a second decision creates a second action. The Phase 1.3 DS-03 scenario provides the reference chain.

## Transaction Boundaries

`PlanningService.create`, `edit`, `submit`, and `PlanApprovalService.decide` are `@Transactional`. Controllers validate HTTP syntax and delegate; services validate business preconditions and coordinate repositories. Create, submit and decision cannot commit a partial plan, request, action or history. The duplicate-equipment integration case fails after the first item was inserted and verifies full rollback.

## Audit History

`status_history` is append-only in these commands. Creation records use null old state; subsequent plan history rows use exact old/new states and the trusted actor. Draft edits use a same-state `EDIT_PLAN` event to trace UC02 changes. Actions use the seed's `CREATE`, `SUBMIT_PLAN`, `EDIT_PLAN`, `SAVE_REVISION`, `RECORD_APPROVAL`, and `RECORD_REVISION` names. No history update/delete command exists.

## Optimistic Locking

Edit, submit and decision require an expected plan `version`. A stale value returns `409 OPTIMISTIC_LOCK_CONFLICT` before a write; Hibernate `@Version` also catches concurrent writes during flush/commit. The exception handler translates framework optimistic failures into the same safe response. Clients should reload and review before retrying.

## Business Error Codes

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR`, `INVALID_PLAN_PERIOD`, `INVALID_PLANNED_DATE`, `REVISION_COMMENT_REQUIRED` | Invalid command input |
| 403 | `ACCESS_DENIED` / `BUSINESS_ACCESS_DENIED` | Wrong role at route/service |
| 404 | `PLAN_NOT_FOUND`, `EQUIPMENT_NOT_FOUND`, `APPROVAL_REQUEST_NOT_FOUND` | Missing target |
| 409 | `PLAN_NOT_EDITABLE`, `PLAN_NOT_SUBMITTABLE`, `PLAN_ALREADY_PENDING_APPROVAL`, `APPROVAL_REQUEST_NOT_PENDING`, `APPROVAL_REQUEST_WRONG_TYPE`, `PLAN_STATE_CONFLICT`, `PLAN_ITEM_RETENTION_CONFLICT`, `OPTIMISTIC_LOCK_CONFLICT` | Current workflow conflicts with command |

All use the existing `ErrorResponse` envelope. No entity, stack trace, SQL or credential is returned.

## What Is Deferred to Phase 3.2

UC05–UC07 coverage assessment, provider proposal, external selection and assignment are not implemented here. Later execution, acceptance, repair hand-off and reporting remain outside Phase 3.1. The item removal policy requires a reviewed design change if product requirements later demand auditable removal.
