# FINAL FROZEN DATA DICTIONARY

Authority for Phase 1.2: 14 tables and 117 columns. A = BUSINESS REQUIRED from forms; B = WORKFLOW REQUIRED from System Analysis; C = TECHNICALLY REQUIRED for stated integrity/audit need. N/Y denote nullability.

## Exact V1 vocabularies

- Plan: `DRAFT`, `SUBMITTED`, `REVISION_REQUIRED`, `APPROVED`, `IN_PROGRESS`, `AWAITING_REPORT`, `REPORTED`, `CLOSED`.
- Item: `PLANNED`, `UNDER_CONTRACT`, `PENDING_PROPOSAL`, `WAITING_VENDOR_APPROVAL`, `ASSIGNED_EXTERNAL`, `IN_MAINTENANCE`, `AWAITING_TECHNICAL_ACCEPTANCE`, `AWAITING_HANDOVER`, `COMPLETED`, `REWORK_REQUIRED`, `REPAIR_REQUIRED`.
- Role: `PHONG_VTYT`, `BAN_GIAM_DOC`, `KHOA_PHONG`, `ADMIN`. Coverage: `UNKNOWN`, `FREE`, `NOT_FREE`. Route: `UNDER_CONTRACT`, `EXTERNAL_APPROVED`.
- Request type: `PLAN_APPROVAL`, `VENDOR_SELECTION`; request status: `DRAFT`, `PENDING`, `DECIDED` (DRAFT vendor only); action: `APPROVE`, `REVISION_REQUIRED`.
- Acceptance type: `TECHNICAL_ACCEPTANCE`, `HANDOVER_ACCEPTANCE`; result: `PASS`, `FAIL`. Report: `DRAFT`, `FINAL`.

## Tables
### department

Hospital unit for ownership and department access. **Group:** Master Data. **Source:** UC01/10/12; BM01/BM08.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Stable department identity | C: FK integrity | Generated |
| code | string | N | UK | Hospital unit code | B: admin catalogue from UC01 | Unique in app |
| name | string | N | - | Current unit name | A: BM01/BM08 | Nonblank |
| active | boolean | N | - | Eligible for new records | B: admin catalogue | Default true; deactivate |

### user_account

Hospital actor with one V1 role. **Group:** Master Data. **Source:** Actor matrix; NFR-SEC-01/02/04.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Stable actor identity | C: audit FKs | Generated |
| department_id | reference | Y | FK | Home/authorized department | B: NFR-SEC-02 | Required for KHOA_PHONG |
| role_code | enum | N | - | One V1 actor role | B: actor matrix | PHONG_VTYT, BAN_GIAM_DOC, KHOA_PHONG, ADMIN |
| username | string | N | UK | Login name | B: user management | Unique |
| password_hash | string | N | - | One-way credential digest | B: NFR-SEC-04 | Never plaintext |
| display_name | string | N | - | Person label | B: actor/sign-off identity | Nonblank |
| active | boolean | N | - | Login availability | B: admin management | Default true; deactivate |

### equipment

Equipment identity and current custodial unit. **Group:** Master Data. **Source:** UC01/12; BM06/BM09.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Stable equipment identity | C: history FKs | Generated |
| department_id | reference | N | FK | Current custodial unit | A: BM01/BM08/BM09 | Existing department |
| equipment_code | string | N | UK | Hospital device code | B: UC12 lookup | Unique project policy |
| name | string | N | - | Device name | A: BM06/BM08 | Nonblank |
| serial_number | string | Y | - | Manufacturer serial | A: BM09/QT01 | Not assumed unique |
| model | string | Y | - | Model/type | A: BM09/QT01 | Optional |
| technical_spec | text | Y | - | Relevant technical details | A: BM06/QT01 | Optional |
| active | boolean | N | - | Selectable for new plans | B: UC01 exception | Default true |

### service_provider

External maintenance organization; no V1 account. **Group:** Master Data. **Source:** UC05–07; BM02.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Stable provider identity | C: FK integrity | Generated |
| name | string | N | - | Provider name | A: BM02/QT02 | Nonblank; not assumed unique |
| contact_details | text | Y | - | Contact for coordination | B: UC05/06 | Restricted visibility |
| active | boolean | N | - | Selectable for new decisions | B: UC05/06 | Default true |

### maintenance_coverage

Dated evidence of free or non-free maintenance entitlement. **Group:** Maintenance Workflow. **Source:** UC05; BR03; BM02/BM05.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Evidence identity | C: historical reference | Generated |
| equipment_id | reference | N | FK | Equipment assessed | B: UC05 | Required |
| provider_id | reference | Y | FK | Contract provider if free | A: BM05/QT01 | Required for verified FREE |
| contract_reference | string | Y | - | Agreement reference | A: BM05/QT01 | Optional |
| coverage_scope | text | Y | - | Maintenance terms considered | A: BM05; B: UC05 | Do not equate warranty to maintenance |
| effective_from | date | Y | - | Evidence validity start | A: BM05/QT01 | Before end |
| effective_to | date | Y | - | Evidence validity end | A: BM05/QT01 | After start |
| classification | enum | N | - | UNKNOWN, FREE or NOT_FREE | B: UC05 exception | UNKNOWN blocks route; default UNKNOWN |
| verified_by_user_id | reference | Y | FK | VTYT verifier | C: decision accountability | Required when FREE/NOT_FREE |
| verified_at | datetime | Y | - | Verification time | C: decision accountability | Required when FREE/NOT_FREE |
| basis_note | text | Y | - | Why classified | B: UC05 | Required when FREE/NOT_FREE |

### maintenance_plan

One campaign with its own eight-state lifecycle. **Group:** Maintenance Workflow. **Source:** UC01–04/08/11; BM01.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Plan identity | C: FK integrity | Generated |
| title | string | N | - | Campaign name | B: UC01 | Nonblank |
| period_start | date | N | - | Planned period start | A: BM01/QT02 | On/before period_end |
| period_end | date | N | - | Planned period end | A: BM01/QT02 | On/after period_start |
| status | enum | N | - | Current plan lifecycle state | B: SA Part III | Eight exact values; default DRAFT |
| created_by_user_id | reference | N | FK | VTYT creator | B: UC01 | Required |
| version | integer | N | - | Optimistic lock | C: NFR-REL-02 | Default 0; nonnegative |
| created_at | datetime | N | - | Creation event time | C: audit chronology | Generated |

### maintenance_plan_item

One equipment in a plan, with independent state and current provider choice. **Group:** Maintenance Workflow. **Source:** UC01–10/12; BM01.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Item identity | C: FK integrity | Generated |
| plan_id | reference | N | FK | Parent plan | B: UC01 | Unique with equipment_id |
| equipment_id | reference | N | FK | Planned equipment | B: UC01 | Required |
| department_id_at_plan | reference | N | FK | Unit at planning for historical scope | B: UC12; NFR-SEC-02 | Set at creation; retain after moves |
| planned_date | date | Y | - | Optional equipment visit date | B: UC01 | Within plan period when supplied |
| status | enum | N | - | Current item lifecycle state | B: SA Part III | Eleven exact values; default PLANNED |
| assigned_provider_id | reference | Y | FK | Current chosen provider | B: UC05/07 | Required before execution |
| assignment_route | enum | Y | - | UNDER_CONTRACT or EXTERNAL_APPROVED | B: BR03 | Must match coverage and approval |
| coverage_id | reference | Y | FK | Verified classification used for route | B: UC05 exception | Required with assignment; same equipment |
| version | integer | N | - | Optimistic lock | C: NFR-REL-02 | Default 0; nonnegative |

### approval_request

One draft/pending/decided plan or vendor decision round. **Group:** Approval / Audit. **Source:** UC03/04/06/07; BM01/BM02.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Approval round identity | C: historical decisions | Generated |
| request_type | enum | N | - | PLAN_APPROVAL or VENDOR_SELECTION | B: UC03/06 | Matches target |
| plan_id | reference | Y | FK | Plan subject | B: UC03 | Only PLAN_APPROVAL |
| plan_item_id | reference | Y | FK | Vendor-choice item subject | B: UC06 | Only VENDOR_SELECTION |
| proposed_provider_id | reference | Y | FK | Provider requested for approval | A: BM02; B: UC06 | Required for submitted vendor request |
| rationale | text | Y | - | Provider-selection grounds | A: BM02/QT02 | Required for submitted vendor request |
| warranty_impact_note | text | Y | - | Impact on purchase terms | B: UC06 | Review before submission |
| status | enum | N | - | DRAFT, PENDING or DECIDED | B: UC03/04/06/07 | DRAFT vendor only; one pending/subject |
| created_by_user_id | reference | N | FK | VTYT author/submitter | B: UC03/06 | Required |
| submitted_at | datetime | Y | - | Submission time | B: UC03/06 | Required when PENDING/DECIDED |
| resolved_at | datetime | Y | - | Decision time | B: UC04/07 | Required when DECIDED |

### approval_action

Immutable director decision on one request. **Group:** Approval / Audit. **Source:** UC04/07; NFR-AUDIT.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Decision identity | C: audit FK | Generated |
| request_id | reference | N | FK/UK | Decided request | B: UC04/07 | One terminal action per request |
| actor_user_id | reference | N | FK | Director deciding | B: UC04/07 | BAN_GIAM_DOC role |
| outcome | enum | N | - | APPROVE or REVISION_REQUIRED | B: UC04/07 | Exact outcomes |
| comment | text | Y | - | Opinion or revision reason | A: BM02; B: UC04 | Required for REVISION_REQUIRED |
| action_at | datetime | N | - | Decision time | B: UC04/07 | Immutable |

### maintenance_execution

One actual work attempt, including rework. **Group:** Maintenance Workflow. **Source:** UC08/09; QT02 summarized in SA.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Attempt identity | C: repeated attempts | Generated |
| plan_item_id | reference | N | FK | Worked item | B: UC08 | Required |
| provider_id | reference | N | FK | Provider used in this attempt | B: UC08/12 | Freeze provider FK at start |
| started_by_user_id | reference | N | FK | VTYT recorder | B: UC08 | Vendors have no login |
| attempt_no | integer | N | - | Ordinal work attempt | C: rework ordering | Positive; unique per item |
| started_at | datetime | N | - | Work start | B: UC08 | Required |
| ended_at | datetime | Y | - | Technical work end | B: UC08 | After start |
| result_note | text | Y | - | Technical outcome summary | B: UC08 | Optional before end |

### maintenance_progress_log

Chronological notes within a work attempt. **Group:** Maintenance Workflow. **Source:** UC08; FR-EXEC-02/03.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Log identity | C: repeated notes | Generated |
| execution_id | reference | N | FK | Parent attempt | B: UC08 | Required |
| recorded_by_user_id | reference | N | FK | VTYT author | B: UC08 | Required |
| event_at | datetime | N | - | Event time | B: UC08 | Required |
| work_note | text | N | - | Performed work/progress | B: UC08 | Nonblank |
| damage_note | text | Y | - | Damage supporting REPAIR_REQUIRED | B: UC08 | Optional |

### acceptance_record

Technical or handover assessment, including explicit V1 signers. **Group:** Maintenance Workflow. **Source:** UC09/10; BM06/BM08.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Assessment identity | C: repeated results | Generated |
| execution_id | reference | N | FK | Assessed work attempt | B: UC09/10 | Required |
| acceptance_type | enum | N | - | TECHNICAL_ACCEPTANCE or HANDOVER_ACCEPTANCE | B: UC09/10 | Exact values |
| result | enum | N | - | PASS or FAIL | B: UC09/10 | Exact values |
| observed_at | datetime | N | - | Assessment time | A: BM06/BM08 | Required |
| conclusion | text | N | - | Technical/handover findings | A: BM06/BM08 | Nonblank |
| recorded_by_user_id | reference | N | FK | VTYT recorder | B: UC09/10 | Required |
| department_confirmed_by_user_id | reference | Y | FK | Receiving department signer | B: UC10; A: BM08 | Required for handover PASS; scoped |
| department_confirmed_at | datetime | Y | - | Department confirmation time | C: sign-off audit | Required with signer |
| vtyt_confirmed_by_user_id | reference | Y | FK | VTYT handover signer | B: UC10 | Required for handover PASS |
| vtyt_confirmed_at | datetime | Y | - | VTYT confirmation time | C: sign-off audit | Required with signer |

### maintenance_report

One draft/final narrative report per plan. **Group:** Reporting. **Source:** UC11; BM03.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | Report identity | C: FK integrity | Generated |
| plan_id | reference | N | FK/UK | Reported plan | B: UC11 | One report per plan |
| created_by_user_id | reference | N | FK | VTYT author | B: UC11 | Required |
| report_number | string | Y | - | Paper-facing number | A: BM03/QT02 | Format not assumed |
| report_date | date | N | - | Report date | A: BM03/QT02 | Required |
| work_done | text | Y | - | Completed work | A: BM03/QT02 | Required when FINAL |
| achieved | text | Y | - | Achieved results | A: BM03/QT02 | Optional |
| not_achieved | text | Y | - | Unachieved results | A: BM03/QT02 | Optional |
| causes | text | Y | - | Causes and comments | A: BM03/QT02 | Optional |
| next_work | text | Y | - | Next planned work | A: BM03/QT02 | Optional |
| resolutions | text | Y | - | Outstanding work solution | A: BM03/QT02 | Optional |
| recommendations | text | Y | - | Recommendations | A: BM03/QT02 | Optional |
| status | enum | N | - | DRAFT or FINAL | B: UC11 | Default DRAFT |
| finalized_at | datetime | Y | - | Finalization time | C: report lifecycle | Required when FINAL |

### status_history

Append-only transitions for plan or item. **Group:** Approval / Audit. **Source:** BR05; NFR-AUDIT-01/02.

| Column | Type Concept | Nullable | Key | Purpose | Source / Design | Constraint |
| --- | --- | --- | --- | --- | --- | --- |
| id | identifier | N | PK | History identity | C: audit FK | Generated |
| plan_id | reference | Y | FK | Plan target | B: BR05 | Exactly one target FK |
| plan_item_id | reference | Y | FK | Item target | B: BR05 | Exactly one target FK |
| actor_user_id | reference | N | FK | Transition actor | B: NFR-AUDIT-01 | Required |
| old_state | string | Y | - | Prior state | B: NFR-AUDIT-01 | Null only on create |
| new_state | string | N | - | Result state | B: NFR-AUDIT-01 | Target-specific vocabulary |
| action | string | N | - | Action/UC event | B: NFR-AUDIT-01 | Nonblank |
| reason | text | Y | - | Reason/remark | B: NFR-AUDIT-01 | Required for revision/failure |
| action_timestamp | datetime | N | - | Transition time | B: NFR-AUDIT-01 | Immutable |

## Cross-row rules

- A plan request has `plan_id` only; vendor selection has `plan_item_id` only. Submitted vendor requests require provider and rationale. Resubmission creates a new request; one terminal action resolves it.
- `maintenance_plan_item.coverage_id` must reference verified coverage for the same equipment. Provider and route must agree with FREE coverage or the latest approved vendor request. Missing/UNKNOWN coverage never implies NOT_FREE.
- A handover PASS requires technical PASS on the current execution and department/VTYT signer IDs and times. Rework creates another execution attempt; failed assessments remain.
- `status_history` has exactly one typed target FK and is append-only. Current status remains on plan/item. State, decision and history writes are atomic.

