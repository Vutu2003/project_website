# FINAL DATA REQUIREMENTS

Requirements below bridge the System Analysis/form evidence to the frozen model. M = mandatory for the relevant workflow; C = conditional. Source codes: SA = `docs/system_analysis_v1.pdf`; forms = `docs/temple.pdf`. A field marked technical in the dictionary is a project design choice, not a hospital form requirement.

| ID | Domain | Data requirement | Source / FR / UC | M/C | Final table(s) |
| --- | --- | --- | --- | --- | --- |
| DR-ID-001 | Identity | One authenticated account has one V1 actor role and persistent identity. | SA actor matrix; NFR-SEC-01; UC04/07 | M | user_account |
| DR-ID-002 | Scope | Department users have a home scope; password stored only as digest. | NFR-SEC-02/04; UC10/12 | M | user_account, department |
| DR-EQP-001 | Equipment | Identify equipment, its current department, code, name, model/serial if available. | UC01/12; BM06/BM09 | M | equipment, department |
| DR-EQP-002 | History | Preserve the department associated with a planned device even after a move. | UC12; NFR-SEC-02 | M | maintenance_plan_item |
| DR-COV-001 | Coverage | Store dated maintenance-specific evidence and contract/provider reference. | FR-ASSIGN-01; UC05; BM02/BM05 | C | maintenance_coverage |
| DR-COV-002 | Coverage | Missing/UNKNOWN differs from verified FREE and verified NOT_FREE. | UC05 exception; BR03 | M for route | maintenance_coverage, maintenance_plan_item |
| DR-PLAN-001 | Planning | Store campaign period/title and independent plan state. | UC01/03; BM01 | M | maintenance_plan |
| DR-PLAN-002 | Planning | Store each device in a plan, optional planned date, and independent item state. | UC01; BM01; SA lifecycle | M | maintenance_plan_item |
| DR-PLAN-003 | Planning | Detect stale plan/item edits. | NFR-REL-02; UC02/08 | M | maintenance_plan, maintenance_plan_item |
| DR-APP-001 | Approval | Persist each submitted/draft vendor or plan request round. | UC03/04/06/07; BM01/BM02 | M | approval_request |
| DR-APP-002 | Approval | Persist director's outcome, actor, time, opinion/revision reason. | FR-APP-04; UC04/07 | M | approval_action |
| DR-PROV-001 | Provider | Keep provider identity independent of each proposal or work attempt. | UC05–08; BM02 | C | service_provider |
| DR-PROV-002 | Provider | Vendor request stores proposed provider, grounds and warranty consideration. | UC06/07; BM02 | C | approval_request |
| DR-PROV-003 | Provider | Item stores current choice and route; execution stores provider actually used. | UC05/07/08/12; BR03 | C | maintenance_plan_item, maintenance_execution |
| DR-EXEC-001 | Execution | Rework may produce multiple numbered work attempts and many progress notes. | UC08/09; SA lifecycle | C | maintenance_execution, maintenance_progress_log |
| DR-EXEC-002 | Repair hand-off | Damage note and REPAIR_REQUIRED state remain linked to equipment/history. | UC08–10; NFR-MAINT-01 | C | maintenance_progress_log, maintenance_plan_item, status_history |
| DR-ACPT-001 | Acceptance | Store technical/handover results separately by type, including failures. | UC09/10; BM06/BM08 | C | acceptance_record |
| DR-ACPT-002 | Handover | Record department and VTYT confirmations before COMPLETED. | UC10; BR04 | C | acceptance_record |
| DR-REP-001 | Reporting | Store BM03 narrative draft/final per plan; derive counts from items. | UC11/12; BM03 | C | maintenance_report, maintenance_plan_item |
| DR-AUD-001 | Audit | Append every plan/item transition with old/new state, actor, time, action and reason. | BR05; NFR-AUDIT-01/02 | M | status_history |
| DR-NFR-001 | Reliability | Decision/acceptance, current state and audit change atomically. | NFR-REL-01; UC04/07/10 | M | approval_action, acceptance_record, status_history |
| DR-NFR-002 | Retention | Retain submitted official records and deactivate referenced masters. | NFR-REL-03 | M | all final workflow/master tables |

Optional source scans are **deferred** under FP-09. No mandatory UC depends on their metadata or binary storage.

