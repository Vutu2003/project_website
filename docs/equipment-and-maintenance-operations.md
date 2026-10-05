# Equipment and maintenance operations

VTYT can add a device from `/equipment/new`, choose its department and scheduled quarters,
and optionally record a warranty expiry. It may use an existing maintenance contract or
create a contract with an existing/new company. Device, company, contract, schedule and
coverage are saved in one transaction. No warranty date is inferred from contract expiry.

Deleting a device sets `equipment.active=false`. Active equipment catalogs, quarter previews,
company device lists and contract device counts exclude it. Existing plan snapshots, coverage,
executions, reports and equipment history remain available. Company and contract records
remain when their device count reaches zero. Equipment codes cannot be reused, including
codes belonging to archived equipment.

Only VTYT may delete a plan. The backend requires the current version, DRAFT status, no
previous submission/approval request and no execution/report. Deleting the draft removes
its items, draft vendor proposals and status records; submitted plans are retained.

`/maintenance-progress` lists approved, started and awaiting-report plans together by default.
VTYT starts the entire plan, updates results using three states, and completes maintenance to
create its draft report. Damage requires a description. Bulk completion updates only ongoing
devices and preserves recorded damage. BGD approves the plan and every prepared outside
provider together in one transaction. Each provider decision inherits the plan approver
and decision time, with a link to the plan approval request in its audit comment. No
additional provider review is queued. VTYT can start contract and outside-provider items
immediately after the plan is approved. Returning a plan for revision leaves proposals
editable and unassigned. Monthly dashboard approval totals count plan decisions once.
The progress page reloads when the window receives focus after approval elsewhere.

Migration V013 applies a recorded plan approval to old pending proposals only when their
activation history and submission time match that plan decision and the provider remains
active. It preserves previous history and decisions, adds a conversion history entry,
increments versions, and marks obsolete pending-provider notifications read. Proposals
submitted after approval and plans requiring revision are not silently approved.
The existing detailed execution, acceptance and handover APIs remain available.

`/maintenance-history` groups VTYT results by completed plan, with one row per plan and
server-side pagination. Clicking a plan opens `/maintenance-history/plans/{id}` with its
saved device results. Searching by a device still returns the full plan summary. Other
roles retain their scoped device history. Both views read saved terminal items and their
latest execution from completed plans. Draft and final report links persist after leaving the progress page. VTYT can see
all results. Departments see their snapshot scope and can open reports explicitly delivered
to them. The board sees finalized reports delivered to the board. Archived equipment remains
in history. Dashboard report/history links use these same saved workflow records.

Frontend role guards send stale links for another role to `/dashboard`; backend role and
scope checks remain enforced. ADMIN navigation excludes Equipment and Contracts. Sidebar
captions and the decorative footer were removed; its brand now links to Overview.

Added endpoints:
- POST `/api/equipment`, DELETE `/api/equipment/{id}` (VTYT).
- DELETE `/api/plans/{id}?version=N` (VTYT).
- GET `/api/maintenance-tracking-plans?page=0&size=10&status=IN_PROGRESS` (VTYT;
  status optional).
- POST `/api/plans/{id}/start-maintenance` and `/mark-work-done`, body `{"version":N}` (VTYT).
- GET `/api/maintenance-history/plans?page=0&size=20&search=…` (VTYT plan summaries).
- GET `/api/maintenance-history?planId=N` (saved device results within a plan; role scope enforced).
- GET `/api/maintenance-history?page=0&size=20&search=...` (VTYT/BGD/KHOA, scoped).

No schema migration or baseline reset is required. Regression uses disposable databases via
`backend/scripts/test-v2.sh`; browser workflow validation uses
`backend/scripts/test-v3-browser.sh` and includes creation, archival/contract synchronization,
draft deletion, bulk tracking, report delivery/history and stale-role route handling.
