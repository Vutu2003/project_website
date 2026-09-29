# Persistence Model

## JPA and Hibernate

JPA is the Java specification for mapping objects to relational tables. An **entity** is a Java class representing one table row; `@Entity` marks it as persistent, `@Id` identifies its primary key, and an object reference represents a foreign-key **relationship**. Hibernate 6.6.53.Final is the JPA provider used by Spring Boot 3.5.16 here: it loads, writes and validates these mappings against PostgreSQL.

## Mapping Strategy

This is database-first mapping. The frozen PostgreSQL 16 schema and six Flyway migrations define the names, SQL types and constraints. Each Java class has explicit `@Table`, `@Column` and `@JoinColumn` names. There are exactly 14 business entities for 14 tables, covering 117 columns and 31 FKs. The Flyway metadata table is not an entity.

## Entity Inventory

| Entity | Database table | Purpose |
| --- | --- | --- |
| Department | `department` | Hospital unit |
| UserAccount | `user_account` | Hospital actor and V1 role |
| Equipment | `equipment` | Device identity and current unit |
| ServiceProvider | `service_provider` | External maintenance organization |
| MaintenanceCoverage | `maintenance_coverage` | Dated maintenance entitlement evidence |
| MaintenancePlan | `maintenance_plan` | Campaign and current lifecycle state |
| MaintenancePlanItem | `maintenance_plan_item` | One device in one plan |
| ApprovalRequest | `approval_request` | Plan or vendor decision round |
| ApprovalAction | `approval_action` | Director's terminal decision |
| MaintenanceExecution | `maintenance_execution` | Numbered work attempt |
| MaintenanceProgressLog | `maintenance_progress_log` | Work note within an attempt |
| AcceptanceRecord | `acceptance_record` | Technical or handover assessment |
| MaintenanceReport | `maintenance_report` | Draft/final plan report |
| StatusHistory | `status_history` | Plan or item transition evidence |

Source packages: `vn.edu.medmaintenance.persistence.entity` and `vn.edu.medmaintenance.persistence.enums`.

## Relationship Strategy

The table holding an FK owns a child → parent Java reference, such as `MaintenancePlanItem.plan` or `MaintenanceExecution.provider`. All 31 relationships are unidirectional. The two unique FKs (`ApprovalAction.request`, `MaintenanceReport.plan`) are owning `@OneToOne` references; the others are `@ManyToOne`. Both annotation types explicitly use `LAZY`: a related row is loaded when its data is needed, avoiding an automatic graph of unrelated records. A lazy proxy still needs an open persistence context for non-ID property access.

No association has cascade or `orphanRemoval`; official history and evidence remain protected by restrictive DB FKs. Future services must persist related records deliberately. `ApprovalRequest` and `StatusHistory` each have two nullable typed targets; their PostgreSQL CHECK rules require exactly one target, so there is no generic target abstraction.

## Enum Strategy

The database uses `TEXT` plus exact CHECK vocabularies. Eleven Java enums use `@Enumerated(EnumType.STRING)` and the same values; ordinal storage is prohibited. The plan enum has 8 constants and the plan item enum has 11. `StatusHistory.oldState/newState` remain strings because those columns can hold either plan or item state according to the chosen target FK. The audit test compares all Java enum sets with both the frozen dictionary and PostgreSQL CHECK definitions.

## Optimistic Locking

Only `MaintenancePlan.version` and `MaintenancePlanItem.version` have `@Version`. Hibernate includes the version in an update and increments it after a successful update; a stale update can then be detected. The tests verify increments inside transactions that roll back. Conflict handling at an API boundary remains later work. Entities keep default Java object identity for `equals`/`hashCode` and have no relationship-traversing `toString`.

## Date/Time Strategy

`DATE` maps to `LocalDate`; `TIMESTAMPTZ` maps to `OffsetDateTime` throughout the model. PostgreSQL stores an instant for `TIMESTAMPTZ`, and the timezone-aware Java type preserves that distinction from a calendar date. `BIGINT` identity maps to `Long` with `@Id` and `GenerationType.IDENTITY`; `INTEGER` to `Integer`; `BOOLEAN` to `Boolean`; `TEXT` to `String` or an enum.

## Schema Ownership

Flyway alone owns schema changes. `spring.jpa.hibernate.ddl-auto=validate` makes Hibernate check the existing schema at startup without creating or changing tables. Startup order is DataSource → Flyway → Hibernate validation → HTTP health. The mapping audit additionally checks all columns and FK targets because Hibernate validation does not prove that every existing DB column has a Java field.

## Important Restrictions

The Phase 2.2 mapping itself contains no service or business-state transition logic. Phase 2.3 later added repositories, Phase 2.4 DTO/controllers and Phase 2.5 authentication/RBAC; entities remain persistence objects, never API responses. PostgreSQL CHECK, FK and UNIQUE constraints remain authoritative; cross-row workflow rules belong in later services.
