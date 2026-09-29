# FINAL CONCEPTUAL DATA MODEL

The frozen V1 model is technology independent. Each entity exists for a cited use case, rule, source concept, or NFR. A paper form presents several normalized entities.

| Entity | Group | Meaning / why separate | Main relationship | Source |
| --- | --- | --- | --- | --- |
| department | Master Data | Hospital unit for ownership and department access. | Parents: none | UC01/10/12; BM01/BM08 |
| user_account | Master Data | Hospital actor with one V1 role. | Parents: department | Actor matrix; NFR-SEC-01/02/04 |
| equipment | Master Data | Equipment identity and current custodial unit. | Parents: department | UC01/12; BM06/BM09 |
| service_provider | Master Data | External maintenance organization; no V1 account. | Parents: none | UC05–07; BM02 |
| maintenance_coverage | Maintenance Workflow | Dated evidence of free or non-free maintenance entitlement. | Parents: user_account, equipment, service_provider | UC05; BR03; BM02/BM05 |
| maintenance_plan | Maintenance Workflow | One campaign with its own eight-state lifecycle. | Parents: user_account | UC01–04/08/11; BM01 |
| maintenance_plan_item | Maintenance Workflow | One equipment in a plan, with independent state and current provider choice. | Parents: department, equipment, service_provider, maintenance_plan, maintenance_coverage | UC01–10/12; BM01 |
| approval_request | Approval / Audit | One draft/pending/decided plan or vendor decision round. | Parents: user_account, service_provider, maintenance_plan, maintenance_plan_item | UC03/04/06/07; BM01/BM02 |
| approval_action | Approval / Audit | Immutable director decision on one request. | Parents: user_account, approval_request | UC04/07; NFR-AUDIT |
| maintenance_execution | Maintenance Workflow | One actual work attempt, including rework. | Parents: user_account, service_provider, maintenance_plan_item | UC08/09; QT02 summarized in SA |
| maintenance_progress_log | Maintenance Workflow | Chronological notes within a work attempt. | Parents: user_account, maintenance_execution | UC08; FR-EXEC-02/03 |
| acceptance_record | Maintenance Workflow | Technical or handover assessment, including explicit V1 signers. | Parents: user_account, user_account, user_account, maintenance_execution | UC09/10; BM06/BM08 |
| maintenance_report | Reporting | One draft/final narrative report per plan. | Parents: user_account, maintenance_plan | UC11; BM03 |
| status_history | Approval / Audit | Append-only transitions for plan or item. | Parents: user_account, maintenance_plan, maintenance_plan_item | BR05; NFR-AUDIT-01/02 |

## Business cardinalities

- A draft plan may have zero items; submission requires at least one. Equipment may appear in many plans over time but once per plan.
- Equipment has historical dated coverage; UNKNOWN/absent evidence never means verified NOT_FREE.
- A plan or item may have many approval request rounds. One request has at most one terminal action.
- An item may have many execution attempts; an attempt has many progress notes and at most one technical and one handover result. A plan has at most one report.
- StatusHistory targets one plan or item. Typed FKs preserve referential integrity.

