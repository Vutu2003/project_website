# Version 3 — Maintenance planning automation review

Validation date: 05/10/2026 (Asia/Ho_Chi_Minh). Status: **PASS** for implementation and isolated validation.

1. **Status:** PASS. Requested planning flow implemented and exercised; primary local runtime remains available for human-controlled application of V010.
2. **Audit conclusion:** V001–V009 and database entities, suggestions, planning, provider proposal, approval, revision, notifications, history/report services and role UIs inspected. Existing approval-action persistence, proposal workflow and notifications reused. Missing configured scheduling, shared contracts and visible revision comments added. Detailed audit: `docs/v3-maintenance-automation.md`.
3. **Migration:** `V010__periodic_maintenance_and_contracts.sql`. New `maintenance_contract`: id, contract_code, contract_name, provider_id, start_date, end_date, active, notes. Equipment adds maintenance_enabled, maintenance_interval_value, maintenance_interval_unit, commissioning_date. Coverage adds contract_id; plan items add last_maintenance_date and maintenance_due_date. FKs: contract.provider_id → service_provider.id; coverage.contract_id → maintenance_contract.id. Positive/paired cycle constraints, contract-code uniqueness/date checks, lookup/history indexes and compatibility mirror triggers added. Populated V009 upgrade passed without changing saved workflow rows; repeated coverage evidence retained.
4. **Periodic model:** ADMIN configures enabled flag, positive value and DAY/MONTH/YEAR unit through equipment management. Explicit commissioning date supports never-maintained devices. Migration does not invent dates; canonical synthetic seed supplies meaningful examples.
5. **Next-due algorithm:** Most recent ended execution with signed PASS handover + configured interval. Otherwise use configured commissioning date. Missing base/cycle or disabled maintenance → NO_SCHEDULE. OVERDUE < today; DUE = today; DUE_SOON ≤ today+30; otherwise NOT_DUE. Threshold configurable with Spring `app.maintenance.due-soon-days`; hospital date is Asia/Ho_Chi_Minh. Calendar month/year arithmetic handles month ends and leap years.
6. **Suggestion API:** Existing GET /api/maintenance-suggestions upgraded. Filters: search, departmentId, dueFrom, dueTo, dueStatus, classification, providerId, notInOpenPlan, page, size, sort. Response includes due state/date, interval, history, department, contract/provider, validity, conflict flag and open-plan links. Evidence fetched in batches.
7. **Suggestion UI:** Vietnamese “Đề xuất bảo trì” inside the existing equipment workspace; table exposes all planning context, due badges, contract/provider links, warranty/history access and summary cards.
8. **Department/date filtering:** Khoa/Phòng and inclusive “Từ ngày đến hạn / Đến ngày đến hạn” filter nextMaintenanceDueDate. Backend rejects reversed ranges and invalid due-state values. Changing filters clears the selection.
9. **Bulk selection:** Due/overdue/due-soon actions fetch every page under current filters. Inactive, already-planned or ambiguous-contract devices cannot be selected. Individual deselection remains available; no silent 100-item truncation.
10. **Suggestion → plan:** Selected equipment is prefilled, with title and suggested period. Overdue planned dates start at today; due dates remain visible. Known data is derived; optional catalog browsing is collapsed. Draft creation and submission require explicit user action.
11. **Contract model:** Contract owns provider/identity/validity/active flag. Existing coverage is the equipment relation and evidence owner, preserving warranty as separate information. Legacy mirrored columns keep existing history/execution interfaces compatible.
12. **Contract → equipment:** Contract list/detail includes provider, validity, state and distinct covered-equipment count. Detail shows equipment code/name, department, cycle, due date/state and coverage validity. Provider detail reuses existing provider identity/contact data and shows its contracts/count.
13. **FREE/provider automation:** Active date-valid contract and active provider → FREE; provider/coverage derived server-side using the item's planned date, falling back to plan start. Client overrides ignored. Distinct overlapping valid contracts are visible and block creation; repeated evidence for the same contract is not a conflict.
14. **NOT_FREE:** No eligible contract → external proposal. VTYT chooses an active provider and enters basis; incomplete drafts can be saved but cannot submit. Existing vendor approval flow and optional manufacturer proposal retained. Warranty expiry alone does not cancel a separate maintenance contract.
15. **BGĐ form automation:** Existing PLAN_APPROVAL request references the generated plan/items. Approval view shows equipment, department, due/planned/last-maintenance dates, method, contract/provider, validity and external proposal evidence without copying.
16. **Revision persistence:** Existing immutable approval_action preserves actor, action, timestamp and mandatory revision comment. Fresh requests retain multiple cycles.
17. **VTYT comment visibility:** Prominent “Ý kiến Ban Giám đốc” on detail and edit; latest comment first, older cycles in expandable history with reviewer, timestamp and request identifier.
18. **Notification integration:** Existing recipient-scoped notification system retained. Revision notifications open plan edit with the exact comment visible. Two review cycles validated.
19. **Backend tests:** **156 passed**, zero failures/errors/skips. Includes cycle boundaries, successful history, failed/unfinished events, missing base, filters, valid/expired/future/inactive/conflicting contracts, repeated evidence, forged client fields, automatic plan snapshots, duplicates, revisions and roles. Existing execution, handover, history/report, catalog/account and security regressions passed.
20. **Frontend:** Build PASS, lint PASS, **136 tests across 15 files passed**. Covers filtering, due dates, filtered bulk selection/prefill, automatic FREE/provider links, external proposals, contracts/equipment/provider detail, ADMIN configuration, BGĐ review, revision comments and notifications.
21. **Real Chrome:** All requested scenarios A–E passed in real headless Google Chrome: FREE → approval; NOT_FREE → proposal/submission; department/date planning; contract equipment; two revision cycles with notifications. No captured JavaScript exceptions. JSON and six screenshots included in this directory. Runner: backend/scripts/test-v3-browser.sh.
22. **Regression:** Required npm build/lint/test and env -u DEBUG Maven test passed. Backend runner uses a temporary PostgreSQL cluster. V2 assertions directly conflicting with V3 classification/date rules updated; original regression fixture plans are temporarily isolated and restored for command tests. Populated migration runner: backend/scripts/test-v3-migration.sh.
23. **Files changed:** Full inventory below and in validation-summary.json. No commit/deployment performed; browser test plans stayed in disposable databases.
24. **Remaining limitations:** Suggestion candidates are batched but filtered/sorted in memory before pagination; large hospital datasets have not been load-tested. Summary cards show the current page explicitly. Historic items retain null schedule snapshots until edited. Contract catalog is read-only. Legacy undated terms retain previous unbounded semantics through migration limits. Primary runtime was not migrated/restarted; README provides the non-destructive setup/build/restart commands for review.

## Browser evidence

- [Automatic FREE draft](automatic-free-draft.png)
- [BGĐ generated approval summary](bgd-approval-summary.png)
- [Department and date filters](department-date-filter.png)
- [Contract equipment](contract-equipment.png)
- [First revision](revision-first.png)
- [Second revision](revision-second.png)
- [Chrome result](chrome-validation.json)
- [Machine-readable validation and file inventory](validation-summary.json)

## Changed files

- `README.md`
- `backend/scripts/setup-v3.sh`
- `backend/scripts/test-v3-browser.sh`
- `backend/scripts/test-v3-migration.sh`
- `backend/src/main/java/vn/edu/medmaintenance/api/controller/MaintenanceAutomationController.java`
- `backend/src/main/java/vn/edu/medmaintenance/api/controller/MaintenanceSuggestionController.java`
- `backend/src/main/java/vn/edu/medmaintenance/api/controller/PlanController.java`
- `backend/src/main/java/vn/edu/medmaintenance/api/dto/response/MaintenancePlanItemResponse.java`
- `backend/src/main/java/vn/edu/medmaintenance/api/dto/response/MaintenanceSuggestionResponse.java`
- `backend/src/main/java/vn/edu/medmaintenance/api/mapper/PlanMapper.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/entity/Equipment.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/entity/MaintenanceContract.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/entity/MaintenanceCoverage.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/entity/MaintenancePlanItem.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/repository/EquipmentRepository.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/repository/MaintenanceCoverageRepository.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/repository/MaintenancePlanItemRepository.java`
- `backend/src/main/java/vn/edu/medmaintenance/security/config/SecurityConfig.java`
- `backend/src/main/java/vn/edu/medmaintenance/service/MaintenanceAutomationService.java`
- `backend/src/main/java/vn/edu/medmaintenance/service/MaintenanceSuggestionService.java`
- `backend/src/main/java/vn/edu/medmaintenance/service/PeriodicSchedule.java`
- `backend/src/main/java/vn/edu/medmaintenance/service/PlanningDecisionService.java`
- `backend/src/main/java/vn/edu/medmaintenance/service/PlanningService.java`
- `backend/src/test/java/vn/edu/medmaintenance/BackendFoundationIntegrationTest.java`
- `backend/src/test/java/vn/edu/medmaintenance/PersistenceMappingAuditTest.java`
- `backend/src/test/java/vn/edu/medmaintenance/service/BackendBusinessFinalIntegrationTest.java`
- `backend/src/test/java/vn/edu/medmaintenance/service/ExecutionAcceptanceIntegrationTest.java`
- `backend/src/test/java/vn/edu/medmaintenance/service/MaintenanceAutomationIntegrationTest.java`
- `backend/src/test/java/vn/edu/medmaintenance/service/PeriodicScheduleTest.java`
- `backend/src/test/java/vn/edu/medmaintenance/service/PlanningApprovalIntegrationTest.java`
- `backend/src/test/java/vn/edu/medmaintenance/service/PlanningTestData.java`
- `backend/src/test/java/vn/edu/medmaintenance/service/ProviderRoutingIntegrationTest.java`
- `database/migrations/V010__periodic_maintenance_and_contracts.sql`
- `database/seeds/v3_automation.sql`
- `database/seeds/v3_canonical.sql`
- `docs/v3-maintenance-automation.md`
- `frontend/src/App.tsx`
- `frontend/src/api/maintenanceSuggestionsApi.ts`
- `frontend/src/components/ReviewComments.tsx`
- `frontend/src/components/ScheduleEditor.tsx`
- `frontend/src/pages/ApprovalDetailPage.tsx`
- `frontend/src/pages/ContractPages.test.tsx`
- `frontend/src/pages/ContractPages.tsx`
- `frontend/src/pages/EquipmentListPage.tsx`
- `frontend/src/pages/MaintenanceSuggestionsPage.tsx`
- `frontend/src/pages/PlanDetailPage.tsx`
- `frontend/src/pages/PlanFormPage.tsx`
- `frontend/src/pages/PlanningRedesign.test.tsx`
- `frontend/src/routes/navigation.ts`
- `frontend/src/types/workflow.ts`
- `frontend/src/utils/maintenanceSchedule.ts`
- `reports/version3/automatic-free-draft.png`
- `reports/version3/bgd-approval-summary.png`
- `reports/version3/chrome-validation.json`
- `reports/version3/contract-equipment.png`
- `reports/version3/department-date-filter.png`
- `reports/version3/maintenance-automation-review.md`
- `reports/version3/revision-first.png`
- `reports/version3/revision-second.png`
- `reports/version3/validation-summary.json`
- `scripts/validate-v3-chrome.mjs`
