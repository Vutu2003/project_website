# Database Design Freeze — Before vs After

## 1. Summary

| Metric | Before | After | Reduction |
| --- | ---: | ---: | ---: |
| Tables | 20 | 14 | 6 (30.0%) |
| FK relationships | 42 | 31 | 11 (26.2%) |
| Columns | 154 | 117 | 37 (24.0%) |

Counts are from the previous dictionary/ERD audit and the final dictionary/ERD audit. The 14-table model is chosen for requirement sufficiency, not to hit a numeric target.

## 2. Every current table audited

| Current Table | Decision | Final Destination | Reason |
| --- | --- | --- | --- |
| department | KEEP | department | Department scope and form terminology. |
| role | MERGE | user_account.role_code | One role per V1 account; no simultaneous roles required. |
| user_account | KEEP | user_account | Actor identity, authorization and audit. |
| user_role | MERGE | user_account.role_code | Junction unnecessary for one-role policy. |
| equipment | KEEP | equipment | Core equipment identity and history. |
| service_provider | KEEP | service_provider | Partner details shared across rounds and attempts. |
| maintenance_coverage | KEEP | maintenance_coverage | Dated unknown/free/non-free evidence. |
| maintenance_plan | KEEP | maintenance_plan | Distinct campaign lifecycle. |
| maintenance_plan_item | KEEP | maintenance_plan_item | Distinct device lifecycle and current choice. |
| vendor_proposal | MERGE | approval_request | UC06 draft/proposal fields live on the approval round. |
| approval_request | KEEP | approval_request | Pending/repeated submissions. |
| approval_action | KEEP | approval_action | Immutable director decisions. |
| maintenance_assignment | MERGE | maintenance_plan_item + maintenance_execution | Current choice on item; actual provider per work attempt. |
| maintenance_execution | KEEP | maintenance_execution | Repeated work attempts on rework. |
| maintenance_progress_log | KEEP | maintenance_progress_log | Multiple chronological updates per attempt. |
| acceptance_record | KEEP | acceptance_record | Technical/handover result attempts. |
| acceptance_participant | MERGE | acceptance_record | Known department/VTYT signer IDs/times. |
| maintenance_report | KEEP | maintenance_report | BM03 narrative and UC11 finalization. |
| attachment | OPTIONAL / DEFER | none in V1 | Upload optional in UC09; no mandatory demo need. |
| status_history | KEEP | status_history | BR05/NFR-AUDIT transition evidence. |

## 3. Tables Removed

| Table absent from final schema | Reason |
| --- | --- |
| role | One fixed V1 role code is stored on account. |
| user_role | No simultaneous multi-role requirement. |
| vendor_proposal | Draft/submitted proposal content fits one vendor approval request. |
| maintenance_assignment | Current choice is on item; actual provider is on each execution. |
| acceptance_participant | Known two digital signers fit explicit acceptance columns. |
| attachment | Optional upload deferred from V1. |

## 4. Tables Merged

| Old Tables | Final Table | Reason |
| --- | --- | --- |
| role + user_role | user_account | One role code per account, no grant junction. |
| vendor_proposal | approval_request | Proposal has no independent required lifecycle after its approval round. |
| maintenance_assignment | maintenance_plan_item + maintenance_execution | Current route and historical performer have direct owners. |
| acceptance_participant | acceptance_record | Known department/VTYT confirmations are explicit. |

## 5. Tables Retained

| Table | Why required |
| --- | --- |
| department | Hospital unit for ownership and department access. |
| user_account | Hospital actor with one V1 role. |
| equipment | Equipment identity and current custodial unit. |
| service_provider | External maintenance organization; no V1 account. |
| maintenance_coverage | Dated evidence of free or non-free maintenance entitlement. |
| maintenance_plan | One campaign with its own eight-state lifecycle. |
| maintenance_plan_item | One equipment in a plan, with independent state and current provider choice. |
| approval_request | One draft/pending/decided plan or vendor decision round. |
| approval_action | Immutable director decision on one request. |
| maintenance_execution | One actual work attempt, including rework. |
| maintenance_progress_log | Chronological notes within a work attempt. |
| acceptance_record | Technical or handover assessment, including explicit V1 signers. |
| maintenance_report | One draft/final narrative report per plan. |
| status_history | Append-only transitions for plan or item. |

## 6. Major fields removed

- `equipment_code_snapshot`, `equipment_name_snapshot`, `department_name_snapshot`, `provider_name_snapshot`: no source requires immutable old labels. `department_id_at_plan` and execution.provider_id retain essential historic scope/performer.
- `submission_snapshot` and `snapshot_sha256`: BR05/NFR-AUDIT ask for decision and state trace, not cryptographic payload evidence; request/action rows retain rounds/outcomes.
- `verification_status`: FREE/NOT_FREE require verifier/time/basis; UNKNOWN represents unverified/insufficient evidence.
- Assignment `assigned_at`/`superseded_at` and source-entity FKs: no independent assignment lifecycle is needed. Current route resides on item.
- Generic attachment storage key, digest, MIME/size/upload metadata: optional upload deferred.
- Arbitrary participant party/name/role rows: only two V1 digital confirmations remain.
- Redundant `updated_at` plan and multiple created timestamps that do not serve a cited rule were removed; creation/decision/event times with concrete audit roles remain.

## 7. Relationships simplified

The model removes eleven FKs net: role grants and role master, proposal-to-request chain, assignment-to-coverage/proposal/provider/actor chain, participant-to-acceptance/user chain and generic attachment-owner links. Direct item→coverage/provider and request→item/provider links replace longer chains. Execution retains provider FK to preserve the actual performer after current choice changes. Typed StatusHistory FKs remain for integrity.

## 8. Complexity reduction without requirement loss

**Implementation:** fewer tables, fewer joins and fewer mutually exclusive owner patterns. **Comprehension:** each remaining table has a direct UC/BR/source reason. **Presentation:** one 14-entity ERD plus two focused views. **Testing:** fewer lifecycle combinations; all 12 UCs, five BRs, both state machines, department scope, optimistic locking, rework and V2 hand-off remain. The normalized plan/item, provider, coverage, execution/log and request/action splits prevent the concrete update anomalies identified in `normalization.md`.

