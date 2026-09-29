# Coverage, Provider Selection & Assignment

Phase 3.2 implements UC05–UC07 over the frozen 14-table schema. Authorities: `docs/system_analysis_v1.pdf` (UC05–UC07, BR02/BR03/BR05, item lifecycle), the Phase 1.2 implementation contract, data dictionary, constraints and design decisions. Only PHONG_VTYT routes/proposes; only BAN_GIAM_DOC decides a submitted vendor proposal.

## UC05 Route Determination

`POST /api/plan-items/{itemId}/route` requires the expected item `version` and an explicit `coverageId`. It accepts only `PLANNED` items on `APPROVED` plans. The explicit ID avoids arbitrary selection if multiple evidence rows exist. The service verifies equipment match, classification, VTYT verifier/time/basis and date applicability. The System Analysis permits preliminary assessment before approval; this command persists the route only after plan approval, following the Phase 3.2 scope and BR02 gate.

## Coverage Semantics

The coverage date anchor is `maintenance_plan_item.planned_date` when present; otherwise the parent plan's `period_start`. Each non-null effective bound must contain that date. An open bound stays open because the frozen dictionary permits null bounds. A FREE or NOT_FREE decision needs a PHONG_VTYT `verified_by_user`, `verified_at` and nonblank `basis_note`. The source PDF distinguishes maintenance coverage from a general warranty.

## FREE Path

Applicable verified FREE evidence yields `PLANNED → UNDER_CONTRACT`. The service derives `assigned_provider` from the coverage's contracted provider, checks that it remains active, stores `assignment_route=UNDER_CONTRACT` and the selected `coverage`, and appends one history event. The client cannot choose a different provider in this command. No director request is created.

## NOT_FREE Path

Applicable verified NOT_FREE evidence yields `PLANNED → PENDING_PROPOSAL` and stores the selected coverage on the item. Assignment route and provider remain null. An external provider is assigned only after UC06 submission and UC07 director approval. NOT_FREE is an explicit verified classification, never a default for absent evidence.

## UNKNOWN / Missing Coverage

UNKNOWN evidence returns `409 COVERAGE_UNKNOWN`; omitted coverage ID returns `409 COVERAGE_REQUIRED`. An unknown or missing row causes no item state, provider, request or history change. Wrong-equipment, expired/future or unverified evidence is also rejected.

## UC06 External Provider Proposal

`POST /api/plan-items/{itemId}/vendor-proposals` creates a `DRAFT` `VENDOR_SELECTION` `ApprovalRequest` for a `PENDING_PROPOSAL` item. This implements the UC06 save-draft alternative and FD-02 frozen decision. Provider/rationale can be supplied now or completed when submitting; an optional warranty-impact note records the source's purchase-term consideration. A draft causes no item transition and no false status history.

`POST /api/vendor-proposals/{requestId}/submit` requires the expected item version. It can complete the draft's provider, rationale and note. Submission requires an active provider and nonblank rationale, changes request to `PENDING` with server timestamp, changes item `PENDING_PROPOSAL → WAITING_VENDOR_APPROVAL`, and appends one history event atomically. One active draft and one pending request per item are enforced by service rules; the frozen partial unique index also protects pending requests. A revision creates a new request rather than reopening a decided one.

## UC07 Director Decision

The existing `POST /api/approvals/{requestId}/decision` dispatches PLAN_APPROVAL to `PlanApprovalService` and VENDOR_SELECTION to `MaintenanceAssignmentService`. Both services reload and validate the request type/target before writing. The command's `version` means plan version for a plan request and item version for a vendor request.

For a pending vendor request on a `WAITING_VENDOR_APPROVAL` item of an `APPROVED` plan, `APPROVE` creates an immutable action, resolves the request and sets `ASSIGNED_EXTERNAL`, `assignment_route=EXTERNAL_APPROVED`, and `assigned_provider` equal to the request's proposed provider. Coverage must still be valid NOT_FREE. `REVISION_REQUIRED` requires a nonblank comment, resolves the request and returns the item to `PENDING_PROPOSAL` with no provider assignment. Both paths append exactly one item transition with the trusted director actor.

## Vendor Approval Rounds

Each resubmission starts a new DRAFT request. Earlier DECIDED requests and their terminal actions are retained. A test covers revision in round 1 and approval with another provider in round 2; the final provider comes only from the approved second request.

## Item State Transitions

```text
PLANNED ──verified FREE──▶ UNDER_CONTRACT
   │
   └──verified NOT_FREE──▶ PENDING_PROPOSAL ──submit draft──▶ WAITING_VENDOR_APPROVAL
                                  ▲                              │
                                  └────director revision─────────┤
                                                                 └──director approve──▶ ASSIGNED_EXTERNAL
UNKNOWN or no coverage ──▶ BLOCKED (no transition)
```

No Phase 3.3 execution state is reachable through these commands.

## Role Authorization

| Command | PHONG_VTYT | BAN_GIAM_DOC | KHOA_PHONG | ADMIN |
| --- | --- | --- | --- | --- |
| Determine route, create/submit vendor draft | Allowed | Denied | Denied | Denied |
| Decide vendor request | Denied | Allowed | Denied | Denied |

Spring Security restricts paths, and command services repeat role checks using `CurrentUser`. Request JSON cannot supply actor IDs or timestamps.

## Transaction Boundaries

Each item-changing command is service-level `@Transactional`. Route, vendor submission and director decision write item/request/action/history as one unit. A test-only history failure after the director action is saved proves the request remains PENDING, action disappears and item assignment/history roll back. Draft creation is also transactional; it leaves the item in PENDING_PROPOSAL.

## Optimistic Locking

Item-changing commands require the current `MaintenancePlanItem.version`; successful state changes increment it. Stale inputs return `409 OPTIMISTIC_LOCK_CONFLICT` without partial writes. Hibernate `@Version` also guards concurrent commits. Responses contain the committed resulting version; draft creation leaves the version unchanged because it does not edit the item.

## Business Errors

| HTTP | Codes | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR`, `PROVIDER_REQUIRED`, `RATIONALE_REQUIRED`, `REVISION_COMMENT_REQUIRED` | Invalid command data |
| 401 / 403 | `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`, `BUSINESS_ACCESS_DENIED` | Missing identity or wrong actor |
| 404 | `PLAN_ITEM_NOT_FOUND`, `COVERAGE_NOT_FOUND`, `PROVIDER_NOT_FOUND`, `APPROVAL_REQUEST_NOT_FOUND` | Missing referenced record |
| 409 | `PLAN_NOT_APPROVED`, `PLAN_ITEM_STATE_CONFLICT`, `COVERAGE_REQUIRED`, `COVERAGE_UNKNOWN`, `COVERAGE_EQUIPMENT_MISMATCH`, `COVERAGE_UNVERIFIED`, `COVERAGE_NOT_APPLICABLE`, `COVERAGE_PROVIDER_MISSING`, `PROVIDER_INACTIVE`, `VENDOR_DRAFT_EXISTS`, `PENDING_VENDOR_APPROVAL_EXISTS`, `VENDOR_PROPOSAL_NOT_DRAFT`, `APPROVAL_REQUEST_WRONG_TYPE`, `APPROVAL_REQUEST_NOT_PENDING`, `OPTIMISTIC_LOCK_CONFLICT` | Current workflow/evidence conflicts with command |

All errors use the established safe `ErrorResponse` shape. Command responses are flat `ItemWorkflowResponse` DTOs; no entity graph, credential, SQL or stack trace is exposed.

## What Is Deferred to Phase 3.3

UC08–UC10 will verify the approved plan and the persisted provider/coverage/request evidence again before starting work. They will create execution attempts, progress and acceptance records. This phase creates none of those rows and does not move an item to IN_MAINTENANCE.
