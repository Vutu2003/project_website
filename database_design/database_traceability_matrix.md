# FINAL FROZEN DATABASE TRACEABILITY

## Final table → requirement/source

| Final Table | Purpose | UC | BR | NFR | Form/Source |
| --- | --- | --- | --- | --- | --- |
| department | Hospital unit identity and scope | UC01, UC10, UC12 | BR04 | NFR-SEC-02 | BM01, BM08 |
| user_account | One-role hospital actor, approvals, signers, audit | UC01–UC12 | BR05 | NFR-SEC-01/02/04, AUDIT-01 | SA actor matrix |
| equipment | Device master and current custody | UC01, UC05, UC08–UC12 | BR03/04 | NFR-SEC-02, PERF-01 | BM06, BM09 |
| service_provider | External maintenance partner | UC05–UC08, UC12 | BR03 | NFR-SEC-03 | BM02 |
| maintenance_coverage | Unknown/free/non-free dated evidence | UC05, UC08 | BR03 | NFR-SEC-03 | BM02, BM05 |
| maintenance_plan | Campaign and eight-state lifecycle | UC01–UC04, UC08, UC10–UC12 | BR01/02/05 | NFR-REL-01/02/03 | BM01, BM03 |
| maintenance_plan_item | Device participation, eleven-state lifecycle, current choice | UC01–UC12 | BR01–BR05 | NFR-SEC-02, REL-02, PERF-02 | BM01, BM06, BM08 |
| approval_request | Plan/vendor request round, proposed partner and grounds | UC03/04/06/07 | BR02/03/05 | NFR-AUDIT-01/02 | BM01, BM02 |
| approval_action | Immutable director decision | UC04/07 | BR02/03/05 | NFR-AUDIT-02 | BM01, BM02 |
| maintenance_execution | Actual provider and repeated work attempts | UC08/09/12 | BR02/04 | NFR-REL-03 | QT02 summarized in SA |
| maintenance_progress_log | Chronological work/damage details | UC08/11/12 | BR05 | NFR-AUDIT-01, PERF-01 | QT02 summarized in SA |
| acceptance_record | Technical and handover result with known signers | UC09/10/11/12 | BR04/05 | NFR-SEC-02, REL-03 | BM06, BM08 |
| maintenance_report | Draft/final campaign narrative | UC11/12 | BR05 | NFR-PERF-02 | BM03 |
| status_history | Immutable plan/item transitions | UC01–UC11 | BR05 | NFR-AUDIT-01/02 | SA state lifecycle |

## Reverse UC → required tables

| UC | Final tables |
| --- | --- |
| UC01 | department, user_account, equipment, maintenance_plan, maintenance_plan_item, status_history |
| UC02 | maintenance_plan, maintenance_plan_item, equipment, status_history |
| UC03 | maintenance_plan, maintenance_plan_item, approval_request, status_history |
| UC04 | maintenance_plan, approval_request, approval_action, status_history |
| UC05 | equipment, maintenance_coverage, service_provider, maintenance_plan_item, status_history |
| UC06 | maintenance_plan_item, approval_request, service_provider, status_history |
| UC07 | approval_request, approval_action, maintenance_plan_item, service_provider, status_history |
| UC08 | maintenance_plan, maintenance_plan_item, service_provider, maintenance_execution, maintenance_progress_log, status_history |
| UC09 | maintenance_execution, acceptance_record, maintenance_plan_item, status_history |
| UC10 | department, user_account, maintenance_plan_item, acceptance_record, maintenance_plan, status_history |
| UC11 | maintenance_plan, maintenance_plan_item, maintenance_execution, acceptance_record, maintenance_report, status_history |
| UC12 | department, equipment, maintenance_plan_item, service_provider, maintenance_execution, maintenance_progress_log, acceptance_record, maintenance_report, status_history |

All UC01–UC12 and BR01–BR05 have direct storage support. No table exists solely for optional scan upload, generic enterprise RBAC or speculative future repair.

