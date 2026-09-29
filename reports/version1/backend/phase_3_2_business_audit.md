# Phase 3.2 Business Audit

**Date:** 2026-09-26  
**Scope:** UC05–UC07, BR02/BR03/BR05 on the frozen V1 schema. Evidence: real-PostgreSQL `ProviderRoutingIntegrationTest`, 57-test full suite, packaged JAR smoke and final SQL metadata/counts.

## UC Coverage

| UC | Command | Result |
| --- | --- | --- |
| UC05 | `POST /api/plan-items/{id}/route` | PASS: explicit verified coverage, FREE or NOT_FREE transition |
| UC06 | create DRAFT and submit vendor proposal | PASS: PENDING request, WAITING item, active provider/rationale |
| UC07 | typed `/api/approvals/{id}/decision` | PASS: immutable action, approval/revision, request/item/history atomic |

## Coverage Classification Audit

| Classification | Expected route | Result |
| --- | --- | --- |
| FREE | UNDER_CONTRACT using coverage provider | PASS; no vendor request |
| NOT_FREE | PENDING_PROPOSAL, then director-approved external route | PASS |
| UNKNOWN | Block, retain PLANNED | PASS |
| NO COVERAGE | Block, retain PLANNED | PASS |

Wrong-equipment, non-VTYT verifier and expired evidence are rejected. The date anchor uses item planned date, falling back to plan period start. Non-null effective bounds are respected.

## Item Transition Audit

| From | Command | To | Result |
| --- | --- | --- | --- |
| PLANNED | verified FREE | UNDER_CONTRACT | PASS |
| PLANNED | verified NOT_FREE | PENDING_PROPOSAL | PASS |
| PENDING_PROPOSAL | submit vendor draft | WAITING_VENDOR_APPROVAL | PASS |
| WAITING_VENDOR_APPROVAL | director APPROVE | ASSIGNED_EXTERNAL | PASS |
| WAITING_VENDOR_APPROVAL | director REVISION_REQUIRED | PENDING_PROPOSAL | PASS with reason |
| UNDER_CONTRACT / WAITING / ASSIGNED_EXTERNAL | wrong command | No transition | PASS: 409 |

## Provider Evidence Audit

| Scenario | Expected | Result |
| --- | --- | --- |
| FREE provider | Item provider equals coverage provider | PASS; derived server-side |
| NOT_FREE before decision | No item provider/route | PASS |
| External approval | Item provider equals approved request provider | PASS |
| Contracted provider inactive before FREE route | No route or history | PASS |
| External provider inactive before decision | No action or assignment | PASS |
| Wrong-equipment / non-VTYT verifier / expired coverage | No route or history | PASS |
| UNKNOWN / absent evidence | Never inferred NOT_FREE | PASS |

## Approval Round Audit

| Round | Request | Action | Final item state |
| --- | --- | --- | --- |
| 1 | New VENDOR_SELECTION, DECIDED | REVISION_REQUIRED with reason | PENDING_PROPOSAL |
| 2 | New VENDOR_SELECTION, DECIDED | APPROVE | ASSIGNED_EXTERNAL |

Both rows/actions remain; the final provider matches the approved second request. Duplicate decision and duplicate active draft/pending attempts return controlled conflicts.

## Role Audit

| Command | PHONG_VTYT | BAN_GIAM_DOC | KHOA_PHONG | ADMIN |
| --- | --- | --- | --- | --- |
| Route | Allowed | 403 | 403 | 403 |
| Create/submit vendor draft | Allowed | 403 | 403 | 403 |
| Decide vendor request | 403 | Allowed | 403 | 403 |

Anonymous access is 401 for all command paths. Direct unauthorized service invocation is rejected. Actor IDs and timestamps come from server context.

## Transaction Audit

| Command | Atomic records | Failure evidence | Result |
| --- | --- | --- | --- |
| FREE/NOT_FREE route | item route/coverage/state + history | Invalid evidence/state leaves PLANNED | PASS |
| Create DRAFT | request | Invalid provider leaves no draft | PASS |
| Submit DRAFT | request PENDING + item WAITING + history | Missing provider/rationale leaves DRAFT/item unchanged | PASS |
| Director decision | action + request DECIDED + assignment/state + history | Test-only history failure after action save rolls all back | PASS |

The rollback test leaves the vendor request PENDING, zero actions, item WAITING without provider, and unchanged history count.

## Optimistic Lock Audit

| Scenario | Expected | Result |
| --- | --- | --- |
| Item changes from version N; stale proposal uses N | 409, no request | PASS |
| Stale director decision | 409, no action or request change | PASS |
| Successful state transition | Response version equals DB version | PASS for FREE and external approval |
| DRAFT request creation | Item version unchanged | PASS |

## Regression Summary

- Phase 3.2: **14/14** new integration tests passed.
- Phase 3.1: **8/8** tests passed with the shared HTTP dispatch expectation updated; plan-service wrong-type guard remains tested.
- Phase 2: **36/36** tests passed.
- Total: **58/58**, zero failures/errors/skips; clean `mvn test` and independent `mvn package` passed.
- Final JAR smoke: health, VTYT/BGĐ login, plan approval, FREE route, NOT_FREE DRAFT/submission/director decision passed.
- Post-cleanup SQL: **14 tables / 117 columns / 31 FKs / V001–V006 / 603 business rows / zero temporary plans**.
- No migration, seed, schema, PDF, frontend or Phase 3.3 execution change.
