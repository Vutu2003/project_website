# FINAL FROZEN ERD

## 1. Final Design Scope

Frozen V1: **14 tables, 31 FK relationships, 117 columns**. Maintenance only.

## 2. Final Entity Inventory

| Group | Tables |
| --- | --- |
| Master Data | department, user_account, equipment, service_provider |
| Maintenance Workflow | maintenance_coverage, maintenance_plan, maintenance_plan_item, maintenance_execution, maintenance_progress_log, acceptance_record |
| Approval / Audit | approval_request, approval_action, status_history |
| Reporting | maintenance_report |

## 3. FINAL ERD

```mermaid
erDiagram
    department {
        bigint id PK
    }
    user_account {
        bigint id PK
        bigint department_id FK
    }
    equipment {
        bigint id PK
        bigint department_id FK
    }
    service_provider {
        bigint id PK
    }
    maintenance_coverage {
        bigint id PK
        bigint equipment_id FK
        bigint provider_id FK
        string classification
        bigint verified_by_user_id FK
    }
    maintenance_plan {
        bigint id PK
        string status
        bigint created_by_user_id FK
    }
    maintenance_plan_item {
        bigint id PK
        bigint plan_id FK
        bigint equipment_id FK
        bigint department_id_at_plan FK
        string status
        bigint assigned_provider_id FK
        string assignment_route
        bigint coverage_id FK
    }
    approval_request {
        bigint id PK
        string request_type
        bigint plan_id FK
        bigint plan_item_id FK
        bigint proposed_provider_id FK
        string status
        bigint created_by_user_id FK
    }
    approval_action {
        bigint id PK
        bigint request_id FK
        bigint actor_user_id FK
        string outcome
    }
    maintenance_execution {
        bigint id PK
        bigint plan_item_id FK
        bigint provider_id FK
        bigint started_by_user_id FK
    }
    maintenance_progress_log {
        bigint id PK
        bigint execution_id FK
        bigint recorded_by_user_id FK
    }
    acceptance_record {
        bigint id PK
        bigint execution_id FK
        string acceptance_type
        string result
        bigint recorded_by_user_id FK
        bigint department_confirmed_by_user_id FK
        bigint vtyt_confirmed_by_user_id FK
    }
    maintenance_report {
        bigint id PK
        bigint plan_id FK
        bigint created_by_user_id FK
        string status
    }
    status_history {
        bigint id PK
        bigint plan_id FK
        bigint plan_item_id FK
        bigint actor_user_id FK
    }
    department |o--o{ user_account : "department_id"
    department ||--o{ equipment : "department_id"
    department ||--o{ maintenance_plan_item : "department_id_at_plan"
    user_account |o--o{ maintenance_coverage : "verified_by_user_id"
    user_account ||--o{ maintenance_plan : "created_by_user_id"
    user_account ||--o{ approval_request : "created_by_user_id"
    user_account ||--o{ approval_action : "actor_user_id"
    user_account ||--o{ maintenance_execution : "started_by_user_id"
    user_account ||--o{ maintenance_progress_log : "recorded_by_user_id"
    user_account ||--o{ acceptance_record : "recorded_by_user_id"
    user_account |o--o{ acceptance_record : "department_confirmed_by_user_id"
    user_account |o--o{ acceptance_record : "vtyt_confirmed_by_user_id"
    user_account ||--o{ maintenance_report : "created_by_user_id"
    user_account ||--o{ status_history : "actor_user_id"
    equipment ||--o{ maintenance_coverage : "equipment_id"
    equipment ||--o{ maintenance_plan_item : "equipment_id"
    service_provider |o--o{ maintenance_coverage : "provider_id"
    service_provider |o--o{ maintenance_plan_item : "assigned_provider_id"
    service_provider |o--o{ approval_request : "proposed_provider_id"
    service_provider ||--o{ maintenance_execution : "provider_id"
    maintenance_plan ||--o{ maintenance_plan_item : "plan_id"
    maintenance_plan |o--o{ approval_request : "plan_id"
    maintenance_plan ||--o| maintenance_report : "plan_id"
    maintenance_plan |o--o{ status_history : "plan_id"
    maintenance_plan_item |o--o{ approval_request : "plan_item_id"
    maintenance_plan_item ||--o{ maintenance_execution : "plan_item_id"
    maintenance_plan_item |o--o{ status_history : "plan_item_id"
    maintenance_coverage |o--o{ maintenance_plan_item : "coverage_id"
    approval_request ||--o| approval_action : "request_id"
    maintenance_execution ||--o{ maintenance_progress_log : "execution_id"
    maintenance_execution ||--o{ acceptance_record : "execution_id"
```

All 31 FK relationships are shown. Approval and history have typed nullable targets with exactly-one rules. Known handover signers use explicit user FKs.

## 4. Core Workflow ERD

```mermaid
erDiagram
    equipment ||--o{ maintenance_plan_item : planned
    maintenance_plan ||--o{ maintenance_plan_item : contains
    maintenance_coverage |o--o{ maintenance_plan_item : classifies
    service_provider |o--o{ maintenance_plan_item : chosen
    maintenance_plan_item ||--o{ maintenance_execution : attempted
    maintenance_execution ||--o{ maintenance_progress_log : noted
    maintenance_execution ||--o{ acceptance_record : assessed
    maintenance_plan ||--o| maintenance_report : reported
```

An item can have multiple work attempts after rework. The report summarizes its plan.

## 5. Approval / Audit ERD

```mermaid
erDiagram
    maintenance_plan |o--o{ approval_request : plan_rounds
    maintenance_plan_item |o--o{ approval_request : vendor_rounds
    service_provider |o--o{ approval_request : proposed
    approval_request ||--o| approval_action : decided
    maintenance_plan |o--o{ status_history : plan_changes
    maintenance_plan_item |o--o{ status_history : item_changes
    user_account ||--o{ approval_action : director
    user_account ||--o{ status_history : actor
```

ApprovalRequest holds vendor proposal data directly. ApprovalAction records decisions; StatusHistory records transitions.

## 6. FK Relationship Inventory

| Parent → child | FK | Cardinality | Reason |
| --- | --- | --- | --- |
| department → user_account | department_id | parent 0..N; child 0..1 | scopes account |
| department → equipment | department_id | parent 0..N; child 1 | holds device |
| department → maintenance_plan_item | department_id_at_plan | parent 0..N; child 1 | historical scope |
| user_account → maintenance_coverage | verified_by_user_id | parent 0..N; child 0..1 | verifies coverage |
| user_account → maintenance_plan | created_by_user_id | parent 0..N; child 1 | creates plan |
| user_account → approval_request | created_by_user_id | parent 0..N; child 1 | submits request |
| user_account → approval_action | actor_user_id | parent 0..N; child 1 | makes decision |
| user_account → maintenance_execution | started_by_user_id | parent 0..N; child 1 | records start |
| user_account → maintenance_progress_log | recorded_by_user_id | parent 0..N; child 1 | records work |
| user_account → acceptance_record | recorded_by_user_id | parent 0..N; child 1 | records assessment |
| user_account → acceptance_record | department_confirmed_by_user_id | parent 0..N; child 0..1 | confirms receipt |
| user_account → acceptance_record | vtyt_confirmed_by_user_id | parent 0..N; child 0..1 | confirms handover |
| user_account → maintenance_report | created_by_user_id | parent 0..N; child 1 | authors report |
| user_account → status_history | actor_user_id | parent 0..N; child 1 | causes transition |
| equipment → maintenance_coverage | equipment_id | parent 0..N; child 1 | has evidence |
| equipment → maintenance_plan_item | equipment_id | parent 0..N; child 1 | appears in plan |
| service_provider → maintenance_coverage | provider_id | parent 0..N; child 0..1 | contract partner |
| service_provider → maintenance_plan_item | assigned_provider_id | parent 0..N; child 0..1 | chosen partner |
| service_provider → approval_request | proposed_provider_id | parent 0..N; child 0..1 | proposed partner |
| service_provider → maintenance_execution | provider_id | parent 0..N; child 1 | actual performer |
| maintenance_plan → maintenance_plan_item | plan_id | parent 0..N; child 1 | contains item |
| maintenance_plan → approval_request | plan_id | parent 0..N; child 0..1 | has approval rounds |
| maintenance_plan → maintenance_report | plan_id | parent 0..1; child 1 | has report |
| maintenance_plan → status_history | plan_id | parent 0..N; child 0..1 | has transitions |
| maintenance_plan_item → approval_request | plan_item_id | parent 0..N; child 0..1 | has vendor rounds |
| maintenance_plan_item → maintenance_execution | plan_item_id | parent 0..N; child 1 | has work attempts |
| maintenance_plan_item → status_history | plan_item_id | parent 0..N; child 0..1 | has transitions |
| maintenance_coverage → maintenance_plan_item | coverage_id | parent 0..N; child 0..1 | supports route |
| approval_request → approval_action | request_id | parent 0..1; child 1 | receives decision |
| maintenance_execution → maintenance_progress_log | execution_id | parent 0..N; child 1 | has notes |
| maintenance_execution → acceptance_record | execution_id | parent 0..N; child 1 | has assessments |
