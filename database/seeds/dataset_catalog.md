# Phase 1.3 Dataset Catalog

All counts below are the **actual PostgreSQL counts** after `reset_database.sh --yes` on 2026-09-25. The dataset is synthetic except for documented public product/model wording.

## Final row counts

| Entity | Count |
| --- | ---: |
| department | 8 |
| user_account | 15 |
| equipment | 40 |
| service_provider | 7 |
| maintenance_coverage | 35 |
| maintenance_plan | 8 |
| maintenance_plan_item | 52 |
| approval_request | 19 |
| approval_action | 15 |
| maintenance_execution | 30 |
| maintenance_progress_log | 90 |
| acceptance_record | 40 |
| maintenance_report | 4 |
| status_history | 240 |
| **Total** | **603** |

## Equipment by department

| Department code | Equipment |
| --- | ---: |
| CAP_CUU | 7 |
| CHAN_DOAN_HINH_ANH | 5 |
| HOI_SUC | 8 |
| KHOA_NGOAI | 3 |
| KHOA_NOI | 5 |
| PHAU_THUAT | 6 |
| PHONG_VTYT | 0 |
| XET_NGHIEM | 6 |

Phòng VTYT coordinates maintenance but is not assigned clinical devices in this synthetic catalog.

## Equipment categories

| Category | Count | Category | Count |
| --- | ---: | --- | ---: |
| Máy theo dõi bệnh nhân | 7 | Bơm tiêm điện | 5 |
| Bơm truyền dịch | 3 | Máy điện tim | 3 |
| Máy siêu âm | 3 | Máy sốc điện | 2 |
| Máy thở | 2 | Hệ thống X-quang | 2 |
| Máy xét nghiệm sinh hóa | 2 | Máy xét nghiệm huyết học | 2 |
| Máy ly tâm | 2 | Máy gây mê | 2 |
| Dao mổ điện | 2 | Nồi hấp tiệt khuẩn | 2 |
| Máy tạo oxy | 1 | | |

## Users by role

| Role | Count |
| --- | ---: |
| PHONG_VTYT | 4 |
| BAN_GIAM_DOC | 2 |
| KHOA_PHONG | 7 |
| ADMIN | 2 |

## Coverage classification

| Classification | Count | Share |
| --- | ---: | ---: |
| FREE | 16 | 45.7% |
| NOT_FREE | 14 | 40.0% |
| UNKNOWN | 5 | 14.3% |

Five more devices have no coverage row and therefore also cannot be routed until evidence is entered. UNKNOWN never defaults to NOT_FREE.

## Plan current states

Each of the eight frozen plan states appears once: DRAFT, SUBMITTED, REVISION_REQUIRED, APPROVED, IN_PROGRESS, AWAITING_REPORT, REPORTED and CLOSED.

## Plan item current states

| State | Count |
| --- | ---: |
| PLANNED | 15 |
| UNDER_CONTRACT | 8 |
| PENDING_PROPOSAL | 1 |
| WAITING_VENDOR_APPROVAL | 2 |
| ASSIGNED_EXTERNAL | 3 |
| IN_MAINTENANCE | 1 |
| AWAITING_TECHNICAL_ACCEPTANCE | 1 |
| AWAITING_HANDOVER | 1 |
| COMPLETED | 15 |
| REWORK_REQUIRED | 1 |
| REPAIR_REQUIRED | 4 |

Route selection: 26 UNDER_CONTRACT, 8 EXTERNAL_APPROVED, 18 unassigned. Seventeen devices appear in two or more plans; `DEMO-EQ-004` has a completed earlier plan and a later repair hand-off.

## Approval and acceptance

| Measure | Distribution |
| --- | --- |
| ApprovalRequest | PLAN_APPROVAL: 7 DECIDED, 1 PENDING; VENDOR_SELECTION: 8 DECIDED, 2 PENDING, 1 DRAFT |
| ApprovalAction | 13 APPROVE; 2 REVISION_REQUIRED |
| AcceptanceRecord | Technical: 17 PASS, 7 FAIL; handover: 15 PASS, 1 FAIL |
| MaintenanceReport | 2 FINAL; 2 DRAFT |
| StatusHistory | 34 plan transitions; 206 item transitions |

Seven items have two numbered execution attempts. The current REWORK_REQUIRED item has a failed technical assessment and awaits its next attempt. Four REPAIR_REQUIRED items retain damage and reason evidence but are not counted as COMPLETED.

## Why history exceeds the suggested range

The request suggested 80–120 history rows, but the frozen BR05 contract requires a row for every plan/item transition, including creation. Fifty-two item creation events, eight plan lifecycles, completed paths and seven rework loops naturally produce **240** rows. Removing transitions to hit a quota would make the demo internally inconsistent. This is an intentional, documented target-range exception, not synthetic filler.
