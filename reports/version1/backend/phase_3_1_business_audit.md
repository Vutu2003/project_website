# Phase 3.1 Business Audit

**Date:** 2026-09-26  
**Scope:** UC01–UC04 on the frozen PostgreSQL V1 schema. Evidence: `PlanningApprovalIntegrationTest`, full Maven suite, packaged JAR smoke and post-test SQL counts.

## UC Coverage

| UC | Main command | Result |
| --- | --- | --- |
| UC01 | `POST /api/plans` | PASS: DRAFT plan, PLANNED items, trusted creator/snapshots, creation history |
| UC02 | `PATCH /api/plans/{id}` | PASS: DRAFT/REVISION_REQUIRED edits; additive items/dates; revision returns DRAFT. Audited removal deliberately rejected. |
| UC03 | `POST /api/plans/{id}/submit` | PASS: SUBMITTED, new pending request, history, duplicate rejection |
| UC04 | `POST /api/approvals/{requestId}/decision` | PASS: director action, decision, APPROVED/revision, history, duplicate rejection |

## State Transition Audit

| From | Command | To | Allowed | Tested |
| --- | --- | --- | --- | --- |
| null | create | DRAFT | Yes | PASS |
| DRAFT | submit | SUBMITTED | Yes | PASS |
| SUBMITTED | approve | APPROVED | Yes | PASS |
| SUBMITTED | revision | REVISION_REQUIRED | Yes | PASS |
| REVISION_REQUIRED | edit/save | DRAFT | Yes | PASS |
| REVISION_REQUIRED | direct submit | SUBMITTED | No | PASS: rejected |
| SUBMITTED / APPROVED | edit | any | No | PASS: rejected |
| DRAFT | edit | DRAFT | Yes | PASS: same-state audit event |

## Role Audit

| Command | PHONG_VTYT | BAN_GIAM_DOC | KHOA_PHONG | ADMIN |
| --- | --- | --- | --- | --- |
| Create | PASS allowed | PASS denied | PASS denied | PASS denied |
| Edit | PASS allowed | Denied by same route rule | PASS denied | Denied by same route rule |
| Submit | PASS allowed | Denied by same route rule | Denied by same route rule | PASS denied |
| Decision | PASS denied | PASS allowed | PASS denied | PASS denied |

Route security and service role checks are both present. Direct service call with a KHOA_PHONG principal was rejected. Actor IDs in history and actions are server derived.

## Transaction Audit

| Command | Atomic records | Rollback tested | Result |
| --- | --- | --- | --- |
| Create | plan + items + creation history | Duplicate equipment after first item insert | PASS: no plan survives |
| Edit | plan/items + edit or revision history | Invalid planned date before commit | PASS: no partial edit |
| Submit | plan + pending request + history | Stale version and duplicate pending cases | PASS: no partial request/history |
| Decision | action + resolved request + plan + history | Missing reason, stale version, wrong state | PASS: no partial action/decision |

## Audit History

| Transition/event | History written | Actor | Result |
| --- | --- | --- | --- |
| null → DRAFT / null → PLANNED | One each | PHONG_VTYT | PASS |
| DRAFT → DRAFT edit | One event | PHONG_VTYT | PASS |
| DRAFT → SUBMITTED | One | PHONG_VTYT | PASS |
| SUBMITTED → REVISION_REQUIRED | One with reason | BAN_GIAM_DOC | PASS |
| REVISION_REQUIRED → DRAFT | One | PHONG_VTYT | PASS |
| SUBMITTED → APPROVED | One | BAN_GIAM_DOC | PASS |

The full revision test checks the exact six-state transition chain and two preserved request/action rounds.

## Optimistic Lock Audit

| Scenario | Expected | Result |
| --- | --- | --- |
| Edit succeeds, stale submit uses old version | 409, no request/transition | PASS |
| Item-only edit with unchanged plan metadata | Plan version advances; response matches DB | PASS |
| Stale director decision | 409, no action | PASS |
| Hibernate concurrent commit failure | Safe `OPTIMISTIC_LOCK_CONFLICT` handler | Handler implemented; simultaneous two-writer race not exercised |

## Database / Regression Audit

Clean rebuild used the guarded backend dev reset, original Flyway V001–V006, original six Phase 1.3 seed files and demo login bootstrap. Full suite: **44 tests, 0 failures/errors/skips**; eight new tests. Packaged JAR smoke passed health, login, create, submit and approve. Post-cleanup SQL: **14 tables, 117 columns, 31 FKs, six successful migrations, 603 business rows, zero TEST/SMOKE plans**. No schema, migration or seed change.

## Retention Decision

The user chose to retain audit evidence and reject UC02 item removal. `removeEquipmentIds` produces `409 PLAN_ITEM_RETENTION_CONFLICT`; omission in PATCH leaves items in place. The frozen schema has a status history FK for each created item and no removed state. A future auditable removal feature needs design review.
