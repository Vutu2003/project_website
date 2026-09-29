# FINAL LOGICAL DATA MODEL

The frozen names and columns are in `data_dictionary.md`; all 31 FKs are in `erd.md`. This text states lifecycle and ownership without SQL.

| Table | Separate purpose | Referenced parents |
| --- | --- | --- |
| department | Hospital unit for ownership and department access. | none |
| user_account | Hospital actor with one V1 role. | department |
| equipment | Equipment identity and current custodial unit. | department |
| service_provider | External maintenance organization; no V1 account. | none |
| maintenance_coverage | Dated evidence of free or non-free maintenance entitlement. | user_account, equipment, service_provider |
| maintenance_plan | One campaign with its own eight-state lifecycle. | user_account |
| maintenance_plan_item | One equipment in a plan, with independent state and current provider choice. | department, equipment, service_provider, maintenance_plan, maintenance_coverage |
| approval_request | One draft/pending/decided plan or vendor decision round. | user_account, service_provider, maintenance_plan, maintenance_plan_item |
| approval_action | Immutable director decision on one request. | user_account, approval_request |
| maintenance_execution | One actual work attempt, including rework. | user_account, service_provider, maintenance_plan_item |
| maintenance_progress_log | Chronological notes within a work attempt. | user_account, maintenance_execution |
| acceptance_record | Technical or handover assessment, including explicit V1 signers. | user_account, user_account, user_account, maintenance_execution |
| maintenance_report | One draft/final narrative report per plan. | user_account, maintenance_plan |
| status_history | Append-only transitions for plan or item. | user_account, maintenance_plan, maintenance_plan_item |

## Frozen design policies

- One `role_code` per account meets the four-actor V1 matrix; no simultaneous multi-role requirement is stated.
- Coverage `classification=UNKNOWN` or no record blocks routing. FREE/NOT_FREE require verifier, time and basis. The item retains the coverage record used.
- `approval_request` directly stores plan or item target, proposed provider/rationale for vendor selection, and DRAFT/PENDING/DECIDED. A new request represents every resubmission; `approval_action` retains the decision.
- The item holds current provider choice and route. Each execution freezes the provider actually used, so UC12 can show provider history without an assignment table.
- Technical and handover acceptance share a type discriminator. Explicit department/VTYT signer IDs and times support the known V1 actors. Failed results remain; rework creates another execution.
- Attachments are deferred. No mandatory V1 UC requires upload; form data is persisted in business columns.
- `department_id_at_plan` preserves historical department scope. No code/name/provider/submission snapshots or hash are required by the supplied audit NFR.

## Plan transitions

new→DRAFT; DRAFT→SUBMITTED; SUBMITTED→APPROVED or REVISION_REQUIRED; REVISION_REQUIRED→DRAFT; APPROVED→IN_PROGRESS; IN_PROGRESS→AWAITING_REPORT; AWAITING_REPORT→REPORTED; REPORTED→CLOSED.

## Item transitions

new→PLANNED; PLANNED→UNDER_CONTRACT or PENDING_PROPOSAL; PENDING_PROPOSAL→WAITING_VENDOR_APPROVAL; WAITING_VENDOR_APPROVAL→ASSIGNED_EXTERNAL or PENDING_PROPOSAL; UNDER_CONTRACT/ASSIGNED_EXTERNAL→IN_MAINTENANCE; IN_MAINTENANCE→AWAITING_TECHNICAL_ACCEPTANCE or REPAIR_REQUIRED; AWAITING_TECHNICAL_ACCEPTANCE→AWAITING_HANDOVER/REWORK_REQUIRED/REPAIR_REQUIRED; AWAITING_HANDOVER→COMPLETED/REWORK_REQUIRED/REPAIR_REQUIRED; REWORK_REQUIRED→IN_MAINTENANCE. COMPLETED and REPAIR_REQUIRED are terminal V1 outcomes.

StatusHistory records transitions but does not replace current status. Application transactions enforce the transition graph and append history.

