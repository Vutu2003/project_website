# Phase 2.2 — JPA Persistence Mapping

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-25  
**Status:** **PASS**

## 1. Objective

Map the frozen 14-table PostgreSQL schema to Java persistence objects without changing database structure. Completion requires all 117 columns and 31 foreign keys to be represented and checked against the real database.

## 2. Starting Point

Phase 2.1 provided Java 17, Spring Boot 3.5.16, PostgreSQL 16.15, Flyway V001–V006, a live DataSource and `/actuator/health`. The separate backend dev database already held the Phase 1.3 synthetic seed. No JPA mapping existed before this phase.

## 3. What is JPA?

JPA is the Java specification for connecting Java objects to relational tables. An **entity** is a Java class representing a table row; a field represents a column, and an object reference can represent a foreign key. Here JPA gives later backend work a typed view of the frozen schema.

## 4. What is Hibernate?

Hibernate 6.6.53.Final is the JPA provider brought in by Spring Boot 3.5.16. It reads the mapping annotations, executes SQL when entities are loaded or updated, and validates their expected schema at application startup. It is configured to validate, never generate, database objects.

## 5. Database-First Strategy

The Phase 1.1 design freeze, Phase 1.2 migrations, and PostgreSQL metadata are authoritative. Java classes follow actual table names, columns, SQL types, nullability and FKs. A mapping mismatch is corrected in Java; the frozen SQL was not altered.

## 6. Persistence Architecture

```text
Controller / API             later
        ↓
Service                      later
        ↓
Repository                   Phase 2.3
        ↓
JPA Entity                   Phase 2.2 (this phase)
        ↓
Hibernate                    SQL/object mapping and validation
        ↓
PostgreSQL                   frozen business schema
```

## 7. Dependencies Added

Only `spring-boot-starter-data-jpa` was added to `backend/pom.xml`. Existing Spring Web, Actuator, JDBC, PostgreSQL, Flyway and test dependencies remain. The starter supplies Spring Data JPA integration and Hibernate; no repository interface was added.

## 8. Package Structure

`vn.edu.medmaintenance.persistence.entity` contains the 14 entity classes. `vn.edu.medmaintenance.persistence.enums` contains 11 constrained vocabularies. Java cannot use `enum` as a package segment because it is a language keyword; `enums` follows the intended separation.

## 9. Entity Inventory

| Entity | Table | Main responsibility |
| --- | --- | --- |
| Department | `department` | Hospital unit |
| UserAccount | `user_account` | Hospital actor and role |
| Equipment | `equipment` | Device master record |
| ServiceProvider | `service_provider` | External organization |
| MaintenanceCoverage | `maintenance_coverage` | Maintenance entitlement evidence |
| MaintenancePlan | `maintenance_plan` | Campaign state |
| MaintenancePlanItem | `maintenance_plan_item` | Device within a campaign |
| ApprovalRequest | `approval_request` | Plan/vendor approval round |
| ApprovalAction | `approval_action` | Director decision |
| MaintenanceExecution | `maintenance_execution` | Numbered work attempt |
| MaintenanceProgressLog | `maintenance_progress_log` | Work note |
| AcceptanceRecord | `acceptance_record` | Technical/handover assessment |
| MaintenanceReport | `maintenance_report` | Plan report |
| StatusHistory | `status_history` | Plan/item state change evidence |

### Beginner Note — Entity

`Equipment` is one Java object for one `equipment` row. `@Entity` and `@Table(name = "equipment")` tell Hibernate where its fields belong.

## 10. Primary Key Mapping

All 14 SQL primary keys are `BIGINT GENERATED ALWAYS AS IDENTITY`. Their Java fields are `Long id` with `@Id`, `@GeneratedValue(strategy = IDENTITY)`, and explicit `@Column(name = "id", nullable = false)`. PostgreSQL generates the value; Java does not invent a UUID or composite key.

## 11. Column Type Mapping

| PostgreSQL | Java | Use |
| --- | --- | --- |
| `BIGINT` | `Long` or entity reference | Identity/FK |
| `INTEGER` | `Integer` | Version, attempt number |
| `BOOLEAN` | `Boolean` | Active flag |
| `DATE` | `LocalDate` | Calendar date |
| `TIMESTAMPTZ` | `OffsetDateTime` | Timestamp with offset |
| `TEXT` | `String` or string enum | Labels, notes, constrained codes |

Every `TEXT` field has explicit `columnDefinition = "text"` so Hibernate validation expects PostgreSQL's actual type. SQL nullability is reflected in `@Column`/`@JoinColumn` and was compared column by column.

## 12. Enum Mapping

PostgreSQL stores each constrained code as `TEXT` with a CHECK. Java uses `@Enumerated(EnumType.STRING)`, so names are saved rather than positions. The 11 enums are `UserRole`, `CoverageClassification`, `PlanStatus`, `PlanItemStatus`, `AssignmentRoute`, `ApprovalRequestType`, `ApprovalRequestStatus`, `ApprovalOutcome`, `AcceptanceType`, `AcceptanceResult`, and `ReportStatus`. The audit compares all 11 Java sets with both the frozen expected values and live PostgreSQL CHECK definitions.

## 13. Plan / PlanItem State Mapping

`PlanStatus` has exactly 8 constants: DRAFT, SUBMITTED, REVISION_REQUIRED, APPROVED, IN_PROGRESS, AWAITING_REPORT, REPORTED, CLOSED. `PlanItemStatus` has exactly 11: PLANNED, UNDER_CONTRACT, PENDING_PROPOSAL, WAITING_VENDOR_APPROVAL, ASSIGNED_EXTERNAL, IN_MAINTENANCE, AWAITING_TECHNICAL_ACCEPTANCE, AWAITING_HANDOVER, COMPLETED, REWORK_REQUIRED, REPAIR_REQUIRED. No transition methods were implemented.

## 14. Relationships

A SQL FK becomes an owning object reference: `Equipment.department`, `MaintenancePlanItem.plan`, `MaintenancePlanItem.equipment`, `MaintenanceCoverage.provider`, `MaintenanceExecution.planItem`, and `AcceptanceRecord.execution` are representative examples. `ApprovalAction.request` and `MaintenanceReport.plan` are owning one-to-one references because their FKs are unique; the other 29 are many-to-one. The automated audit compares every child column and parent table with PostgreSQL metadata.

### Beginner Note — Relationship

`maintenance_plan_item.plan_id` is a database FK. Java exposes the same link as `MaintenancePlanItem.getPlan()`.

## 15. Why Mostly Unidirectional?

All mapped references go from FK-owning child to parent. Reverse collections were omitted because this phase needs accurate storage mapping, not a large in-memory object graph. This also avoids recursive traversal, accidental loading, and equality problems.

## 16. Fetch Strategy

All 31 associations explicitly use `FetchType.LAZY`; related records are loaded when accessed inside a persistence context. A smoke test loaded an `Equipment`, verified its department proxy was initially uninitialized, then read its name. Later repository queries can choose explicit fetch plans for specific screens.

### Beginner Note — Lazy Loading

Reading an equipment row does not automatically read every related record. Hibernate retrieves related data when the code needs it.

## 17. Cascade Strategy

No association declares cascade or orphan removal. The frozen database uses restrictive FKs to retain approvals, work attempts, acceptances and audit evidence. Future services must write records intentionally; the mapping cannot recursively delete official history.

## 18. Optimistic Locking

Only `MaintenancePlan.version` and `MaintenancePlanItem.version` have `@Version`. For example, if A reads version 3 and B saves version 4, A's later stale save can be detected. Both test updates incremented their versions by one and then rolled back; API-level conflict handling is later work.

### Beginner Note — @Version

The version column lets an update check that the row has not changed since it was read, without holding a database lock throughout a user interaction.

## 19. Date and Time Mapping

`LocalDate` represents a date without a time, such as a plan period. `OffsetDateTime` represents each PostgreSQL `TIMESTAMPTZ` field; this preserves awareness that event chronology is based on instants, not an ambiguous local wall-clock value. No `java.util.Date` or timezone-naive `LocalDateTime` was used.

## 20. Typed Approval / History Targets

`ApprovalRequest` has nullable `plan` and `planItem` references; `StatusHistory` has the same typed target shape. PostgreSQL CHECK constraints enforce exactly one target and the request type rule. Java does not introduce a generic `targetType/targetId` pair or JPA inheritance. History's old/new states stay `String` because the allowed vocabulary depends on which target FK is present.

## 21. Hibernate Schema Validation

`spring.jpa.hibernate.ddl-auto=validate` is active. On both empty-then-migrated and seeded starts, Flyway validated/applied six migrations before Hibernate initialized the entity manager factory. Hibernate found no table/type/column mismatch. `open-in-view=false` keeps persistence-context lifetime explicit. Hibernate did not create or alter tables.

## 22. Mapping Completeness Audit

The PostgreSQL-backed audit in [`phase_2_2_mapping_audit.md`](phase_2_2_mapping_audit.md) passed: **14/14 entities**, **117/117 columns**, **31/31 FK associations**, **11/11 constrained enums**, and **2/2 version fields**. It compares the JPA metamodel, mapping annotations, PostgreSQL column types/nullability, FK targets and CHECK definitions; it also checks identity strategy, `LAZY`, and absent cascades. No removed Phase 1.1 table or Flyway table has an entity.

## 23. Persistence Smoke Tests

| Test | Purpose | Result |
| --- | --- | --- |
| Application context and Hibernate startup | Validate real schema | PASS |
| Metamodel/column/FK/enum audit | Exact frozen coverage | PASS |
| Seeded enum and association reads | Load equipment, item, approval, execution, history | PASS |
| Lazy department access | Verify deferred relationship loading | PASS |
| Plan and item version updates | Check `@Version` increments; rollback | PASS |

The three new JPA test methods passed with zero failures. Tests use `EntityManager` directly; no production repository exists.

## 24. Phase 2.1 Regression

All four original foundation tests passed unchanged: context/DataSource, PostgreSQL/`SELECT 1`, 14/117/31 schema plus six Flyway rows, and Actuator health. `mvn clean test` ran seven test methods total, **7 passed / 0 failed**.

## 25. Database Regression

After a guarded reset of only `medical_maintenance_backend_dev`, Flyway rebuilt V001–V006, the original Phase 1.3 seed was reloaded, and tests completed. Final metadata: **14 business tables, 117 business columns, 31 FKs, 6 successful SQL migrations, 603 demo rows**. The seed counts remained unchanged after rollback tests. The two source PDFs and six SQL migration files were not edited.

## 26. Problems Found

The first independent audit run found `@Column(nullable = true)` on generated `id` fields. SQL declares identity plus PRIMARY KEY, which PostgreSQL correctly reports as NOT NULL. Hibernate startup had succeeded, demonstrating why the stronger metadata audit matters. A Java test compile also exposed an overloaded `JdbcTemplate.query` callback that needed an explicit callback type.

## 27. Fixes Applied

The 14 ID mappings now say `nullable = false`; no SQL was changed. The audit uses an explicit `RowCallbackHandler`. The complete suite then passed on the seeded database and again after a clean rebuild.

## 28. What Has NOT Been Implemented

There are no Spring Data repositories, services, API controllers/DTOs, authentication, RBAC, workflow transitions, report generation, or frontend changes. Entity classes are not JSON response models. Complex cross-row business validation remains future service work.

## 29. Phase 2.3 Handoff

Repository work can use the 14 validated entity types, all 31 child-to-parent references, exact string enums and two optimistic-lock fields. Query design should choose fetch plans deliberately and preserve the frozen retention and department-scope rules. See [`persistence-model.md`](../../backend/docs/persistence-model.md) and the generated audit for exact mapping inventory.

## 30. Final Status

**PASS.** The mapping is complete against the frozen schema, Hibernate validates at startup, all tests pass, the packaged application reports `{"status":"UP"}`, and the schema and 603-row demo dataset are unchanged after testing.

## 31. Slide-ready Summary

- Phase 2.2 maps all 14 PostgreSQL business tables to JPA entities.
- Hibernate 6.6 validates the existing schema; Flyway remains the only schema owner.
- Automated audit proves 117/117 columns, 31/31 FKs and 11 exact enum vocabularies.
- Plan and plan item use `@Version`; all relationships load lazily without cascade deletes.
- Clean rebuild and 7/7 tests pass; health is UP and 603 demo records remain.
