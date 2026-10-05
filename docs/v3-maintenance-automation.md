# V3 audit and ownership

V001–V009 were audited. V2 already has department snapshots, coverage evidence, proposal approval, approval-action comments, status history and user notifications. Suggestions infer an average interval and choose the latest coverage, with no configured schedule or contract catalog. Forms require manual classification. Revision comments are saved but not visible in plan detail/edit.

V010: maintenance_contract owns provider, contract code/name, active flag and validity dates. maintenance_coverage remains the equipment-contract relation and owns scope, verification evidence and separate warranty expiry. Its legacy contract/provider/date fields are synchronized for existing execution/history APIs; new decisions read the contract. No second equipment link table is created. Distinct legacy provider/code/date tuples become distinct contracts to preserve ambiguity. No production commissioning dates are fabricated.

Equipment owns enabled flag, positive interval and DAY/MONTH/YEAR unit plus an explicitly configured commissioning date for devices without history. Next due is derived from latest successful, signed handover of an ended execution, otherwise commissioning date. Open or failed attempts never count. Due status uses the hospital business date and app.maintenance.due-soon-days (default 30). Plan items snapshot last maintenance and due dates when generated/edited.

Equipment row locks serialize plan generation and duplicate checks. Open planning excludes completed/repair items and plans awaiting report, reported or closed, matching the existing workflow. Conflicting valid contracts block creation instead of selecting one arbitrarily.

## Audit coverage

- V001 master data: no acquisition/commissioning date or periodic policy; existing ADMIN equipment/warranty architecture provides the configuration entry point.
- V002 coverage/planning: equipment evidence embeds provider/reference/validity, so a shared contract entity is necessary. Department snapshots, multiple plan items, and equipment references already exist.
- V003 approval: one immutable action per request; each resubmission gets a fresh request. Actor, timestamp, outcome and mandatory revision reason already persist correctly.
- V004 execution/acceptance: ended executions and signed PASS handovers provide valid maintenance history; failed or unfinished work is excluded.
- V005 reports/status history: retained. Revision reason exists in history but dedicated plan-page visibility was absent.
- V006 indexes and V007 provider code: retained; provider catalog reused.
- V008 decisions/notifications: prepared external proposal, vendor review, and notification ownership remain; manual classification is replaced by server derivation.
- V009 warranty/service choice: warranty remains independent from maintenance-contract validity. Optional manufacturer proposal remains available outside contract.

## API and authorization

`GET /api/maintenance-suggestions`: VTYT only; search, departmentId, dueFrom/dueTo (inclusive), dueStatus,
classification, providerId, notInOpenPlan, page, size and sort. Sort supports equipmentCode, id and
nextMaintenanceDueDate. Empty department/date results retain requested pagination metadata.

`GET /api/contracts`, `/api/contracts/{id}`, `/api/contracts/{id}/equipment`, and
`GET /api/providers/{id}/detail`: ADMIN, VTYT, BGĐ read; KHOA denied.

`GET /api/equipment/{id}/schedule`: ADMIN/VTYT/BGĐ read.
`PUT /api/equipment/{id}/schedule`: ADMIN write; validates interval pairing, unit and positive range.
`GET /api/equipment/{id}/planning-context?referenceDate=YYYY-MM-DD`: VTYT preview;
reuses exactly the contract rules used by plan creation and submission.

Existing plan create/edit endpoints remain VTYT-only. The server ignores incoming classification/coverage
selection and derives them again, snapshots the equipment department and dates, and retains the external
provider proposal approval flow. `GET /api/plans/{id}/review-comments` returns every persisted revision action,
newest first; KHOA cannot read these comments.

## Validation and boundaries

156 backend tests and 136 frontend tests passed. Frontend build and lint passed. Real Google Chrome
passed scenarios A–E against an isolated canonical database. Populated V009 upgrade was verified by
fingerprinting every saved workflow row before/after V010, including repeated coverage evidence.

Suggestion evidence is fetched in batches, filtered and sorted before response pagination. This avoids
per-row HTTP/database lookups but currently materializes the search/department candidate set in memory;
large production datasets have not been load-tested. Summary cards explicitly describe the current page.
Historical plan items retain null schedule snapshots until edited; migration does not fabricate old due dates.
Legacy undated coverage retains its previous unbounded validity via migration sentinel limits. Contract
catalogs are read-only; this upgrade introduces no contract administration workflow.

Primary local runtime was not migrated/restarted during validation. All browser plans and approvals ran
in disposable clusters; applying V010 and canonical configuration is available through setup-v3.sh.
