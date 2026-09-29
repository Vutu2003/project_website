# Phase 1.3 Demo Scenario Catalog

All scenarios use **synthetic** device codes, users, approvals and workflow records. The state paths are stored in `status_history` and checked by `database/tests/validate_demo_data.sql`.

## DS-01 — FREE contract happy path

- **Purpose:** Show a verified FREE coverage record choosing its contract provider, then normal maintenance and handover.
- **Main records:** `DEMO-EQ-011` in `DEMO — Bảo trì quý I/2026`; FREE coverage, one execution, technical PASS and handover PASS.
- **Starting state:** PLANNED; plan approved before work.
- **Workflow path:** PLANNED → UNDER_CONTRACT → IN_MAINTENANCE → AWAITING_TECHNICAL_ACCEPTANCE → AWAITING_HANDOVER → COMPLETED.
- **Final result:** COMPLETED; provider equals the verified coverage provider.
- **Roles:** PHONG_VTYT, BAN_GIAM_DOC, department KHOA_PHONG signer.
- **Future screen/API:** Plan item detail, coverage, execution, acceptance and equipment history.

## DS-02 — External provider happy path

- **Purpose:** Show a NOT_FREE route requiring BGĐ decision before external assignment.
- **Main records:** `DEMO-EQ-010` in `DEMO — Bảo trì tháng 07/2026`; NOT_FREE coverage, VENDOR_SELECTION request/action, execution and two PASS assessments.
- **Starting state:** PLANNED with verified NOT_FREE evidence.
- **Workflow path:** PLANNED → PENDING_PROPOSAL → WAITING_VENDOR_APPROVAL → ASSIGNED_EXTERNAL → IN_MAINTENANCE → AWAITING_TECHNICAL_ACCEPTANCE → AWAITING_HANDOVER → COMPLETED.
- **Final result:** COMPLETED; execution provider matches the approved proposal.
- **Roles:** PHONG_VTYT, BAN_GIAM_DOC, department signer.
- **Future screen/API:** Vendor proposal, director queue, provider route, acceptance.

## DS-03 — Plan revision and resubmission

- **Purpose:** Demonstrate retained approval rounds rather than overwriting a prior decision.
- **Main records:** `DEMO — Kế hoạch tháng 10/2026 đã duyệt`; two PLAN_APPROVAL requests and two director actions (REVISION_REQUIRED, then APPROVE).
- **Starting state:** DRAFT.
- **Workflow path:** DRAFT → SUBMITTED → REVISION_REQUIRED → DRAFT → SUBMITTED → APPROVED.
- **Final result:** APPROVED; both rounds and plan history remain visible.
- **Roles:** PHONG_VTYT and BAN_GIAM_DOC.
- **Future screen/API:** Plan revision form, approval timeline and audit view.

## DS-04 — Technical rework

- **Purpose:** Preserve the failed assessment and first attempt while a second attempt succeeds.
- **Main records:** `DEMO-EQ-004` in `DEMO — Bảo trì quý I/2026`; attempts 1 and 2, technical FAIL on attempt 1, PASS plus handover PASS on attempt 2.
- **Starting state:** IN_MAINTENANCE after valid FREE routing.
- **Workflow path:** IN_MAINTENANCE → AWAITING_TECHNICAL_ACCEPTANCE → REWORK_REQUIRED → IN_MAINTENANCE → AWAITING_TECHNICAL_ACCEPTANCE → AWAITING_HANDOVER → COMPLETED.
- **Final result:** COMPLETED with both work attempts retained.
- **Roles:** PHONG_VTYT, technical recorder, department signer.
- **Future screen/API:** Rework queue, attempt comparison, technical acceptance history.

## DS-05 — Handover failure

- **Purpose:** Show that technical PASS alone does not complete the item.
- **Main records:** `DEMO-EQ-006` in `DEMO — Bảo trì quý I/2026`; first attempt has technical PASS then handover FAIL; second attempt has technical and handover PASS.
- **Starting state:** AWAITING_HANDOVER after technical PASS.
- **Workflow path:** AWAITING_HANDOVER → REWORK_REQUIRED → IN_MAINTENANCE → AWAITING_TECHNICAL_ACCEPTANCE → AWAITING_HANDOVER → COMPLETED.
- **Final result:** COMPLETED only after successful, scoped handover confirmation.
- **Roles:** KHOA_PHONG, PHONG_VTYT.
- **Future screen/API:** Handover form, failed acceptance, signer confirmation and rework.

## DS-06 — Repair hand-off

- **Purpose:** Keep damage evidence and mark the V1 maintenance outcome without inventing Repair V2 data.
- **Main records:** `DEMO-EQ-004` in `DEMO — Bảo trì tháng 09/2026 đang thực hiện`; execution, progress damage note, REPAIR_REQUIRED history with reason.
- **Starting state:** IN_MAINTENANCE.
- **Workflow path:** IN_MAINTENANCE → REPAIR_REQUIRED.
- **Final result:** Terminal V1 hand-off; not counted as COMPLETED.
- **Roles:** PHONG_VTYT.
- **Future screen/API:** Damage note, equipment history, reportable plan summary.

## DS-07 — UNKNOWN coverage blocks routing

- **Purpose:** Demonstrate the difference between unknown evidence and confirmed NOT_FREE.
- **Main records:** `DEMO-EQ-003` in `DEMO — Kế hoạch tháng 10/2026 đã duyệt`; UNKNOWN coverage and no assigned provider/route.
- **Starting state:** PLANNED.
- **Workflow path:** Remains PLANNED until coverage is verified; no invented assignment transition.
- **Final result:** Unrouted item; it cannot be started merely because a plan is approved.
- **Roles:** PHONG_VTYT.
- **Future screen/API:** Coverage assessment exception, plan item action guard.

## DS-08 — Pending vendor decision

- **Purpose:** Show the director queue and a WAITING_VENDOR_APPROVAL item without prematurely assigning a provider.
- **Main records:** `DEMO-EQ-034` in the September in-progress plan; one PENDING VENDOR_SELECTION request with proposed provider and rationale.
- **Starting state:** PENDING_PROPOSAL after verified NOT_FREE coverage.
- **Workflow path:** PENDING_PROPOSAL → WAITING_VENDOR_APPROVAL; no decision yet.
- **Final result:** No route/provider assignment and no execution.
- **Roles:** PHONG_VTYT and future BAN_GIAM_DOC decision-maker.
- **Future screen/API:** Pending approval queue, vendor proposal detail.

## DS-09 — UC12 equipment history across campaigns

- **Purpose:** Show one device in more than one maintenance plan without duplicating its master record.
- **Main records:** `DEMO-EQ-004` appears in the closed Q1 plan and the September in-progress plan; prior completion, later repair hand-off, three total attempts and linked notes/assessments/history.
- **Starting state:** Prior plan item COMPLETED.
- **Workflow path:** Separate plan item histories for each campaign; the later item ends REPAIR_REQUIRED.
- **Final result:** An equipment-history query exposes both campaigns and distinct outcomes.
- **Roles:** PHONG_VTYT, authorized KHOA_PHONG, BAN_GIAM_DOC read view.
- **Future screen/API:** UC12 equipment timeline, filtered history and report drill-down.
