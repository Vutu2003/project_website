# Phase 2.2 Mapping Audit

Automated comparison of the JPA metamodel and annotations with PostgreSQL `public` metadata. Run `mvn -f backend/pom.xml test` with the Phase 2.1 development database to regenerate.

**Result: PASS — 14 entities / 14 tables; 117 / 117 columns; 31 / 31 FK associations; 11 / 11 constrained enums; 2 / 2 version fields.**

## Table and column coverage

| Table | DB columns | Mapped fields | DB FKs | Association fields | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `acceptance_record` | 11 | 11 | 4 | 4 | PASS |
| `approval_action` | 6 | 6 | 2 | 2 | PASS |
| `approval_request` | 11 | 11 | 4 | 4 | PASS |
| `department` | 4 | 4 | 0 | 0 | PASS |
| `equipment` | 8 | 8 | 1 | 1 | PASS |
| `maintenance_coverage` | 11 | 11 | 3 | 3 | PASS |
| `maintenance_execution` | 8 | 8 | 3 | 3 | PASS |
| `maintenance_plan` | 8 | 8 | 1 | 1 | PASS |
| `maintenance_plan_item` | 10 | 10 | 5 | 5 | PASS |
| `maintenance_progress_log` | 6 | 6 | 2 | 2 | PASS |
| `maintenance_report` | 14 | 14 | 2 | 2 | PASS |
| `service_provider` | 4 | 4 | 0 | 0 | PASS |
| `status_history` | 9 | 9 | 3 | 3 | PASS |
| `user_account` | 7 | 7 | 1 | 1 | PASS |

## Foreign key ownership

| Database FK | Child entity field | Parent table | Result |
| --- | --- | --- | --- |
| `acceptance_record.department_confirmed_by_user_id` | `AcceptanceRecord.departmentConfirmedByUser` | `user_account` | PASS |
| `acceptance_record.execution_id` | `AcceptanceRecord.execution` | `maintenance_execution` | PASS |
| `acceptance_record.recorded_by_user_id` | `AcceptanceRecord.recordedByUser` | `user_account` | PASS |
| `acceptance_record.vtyt_confirmed_by_user_id` | `AcceptanceRecord.vtytConfirmedByUser` | `user_account` | PASS |
| `approval_action.actor_user_id` | `ApprovalAction.actorUser` | `user_account` | PASS |
| `approval_action.request_id` | `ApprovalAction.request` | `approval_request` | PASS |
| `approval_request.created_by_user_id` | `ApprovalRequest.createdByUser` | `user_account` | PASS |
| `approval_request.plan_id` | `ApprovalRequest.plan` | `maintenance_plan` | PASS |
| `approval_request.plan_item_id` | `ApprovalRequest.planItem` | `maintenance_plan_item` | PASS |
| `approval_request.proposed_provider_id` | `ApprovalRequest.proposedProvider` | `service_provider` | PASS |
| `equipment.department_id` | `Equipment.department` | `department` | PASS |
| `maintenance_coverage.equipment_id` | `MaintenanceCoverage.equipment` | `equipment` | PASS |
| `maintenance_coverage.provider_id` | `MaintenanceCoverage.provider` | `service_provider` | PASS |
| `maintenance_coverage.verified_by_user_id` | `MaintenanceCoverage.verifiedByUser` | `user_account` | PASS |
| `maintenance_execution.plan_item_id` | `MaintenanceExecution.planItem` | `maintenance_plan_item` | PASS |
| `maintenance_execution.provider_id` | `MaintenanceExecution.provider` | `service_provider` | PASS |
| `maintenance_execution.started_by_user_id` | `MaintenanceExecution.startedByUser` | `user_account` | PASS |
| `maintenance_plan.created_by_user_id` | `MaintenancePlan.createdByUser` | `user_account` | PASS |
| `maintenance_plan_item.assigned_provider_id` | `MaintenancePlanItem.assignedProvider` | `service_provider` | PASS |
| `maintenance_plan_item.coverage_id` | `MaintenancePlanItem.coverage` | `maintenance_coverage` | PASS |
| `maintenance_plan_item.department_id_at_plan` | `MaintenancePlanItem.departmentAtPlan` | `department` | PASS |
| `maintenance_plan_item.equipment_id` | `MaintenancePlanItem.equipment` | `equipment` | PASS |
| `maintenance_plan_item.plan_id` | `MaintenancePlanItem.plan` | `maintenance_plan` | PASS |
| `maintenance_progress_log.execution_id` | `MaintenanceProgressLog.execution` | `maintenance_execution` | PASS |
| `maintenance_progress_log.recorded_by_user_id` | `MaintenanceProgressLog.recordedByUser` | `user_account` | PASS |
| `maintenance_report.created_by_user_id` | `MaintenanceReport.createdByUser` | `user_account` | PASS |
| `maintenance_report.plan_id` | `MaintenanceReport.plan` | `maintenance_plan` | PASS |
| `status_history.actor_user_id` | `StatusHistory.actorUser` | `user_account` | PASS |
| `status_history.plan_id` | `StatusHistory.plan` | `maintenance_plan` | PASS |
| `status_history.plan_item_id` | `StatusHistory.planItem` | `maintenance_plan_item` | PASS |
| `user_account.department_id` | `UserAccount.department` | `department` | PASS |

## Constrained enums

| Column | Values | Result |
| --- | ---: | --- |
| `acceptance_record.acceptance_type` | 2 | PASS |
| `acceptance_record.result` | 2 | PASS |
| `approval_action.outcome` | 2 | PASS |
| `approval_request.request_type` | 2 | PASS |
| `approval_request.status` | 3 | PASS |
| `maintenance_coverage.classification` | 3 | PASS |
| `maintenance_plan.status` | 8 | PASS |
| `maintenance_plan_item.assignment_route` | 2 | PASS |
| `maintenance_plan_item.status` | 11 | PASS |
| `maintenance_report.status` | 2 | PASS |
| `user_account.role_code` | 4 | PASS |

`@Version`: `maintenance_plan.version`, `maintenance_plan_item.version` only. No removed table or Flyway metadata table is mapped.
