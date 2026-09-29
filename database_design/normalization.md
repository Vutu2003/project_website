# FINAL NORMALIZATION REVIEW

The 14-table model is a minimum sufficient normalized design. It separates actual repeating groups and independent lifecycles without treating every paper form as a table.

## 1NF — actual repeated groups

BM01's month/week departments are represented as a plan with many equipment items, not an array or month columns. An equipment can appear in multiple plans. UC08's repeated work notes become `maintenance_progress_log` rows; REWORK_REQUIRED starts a new `maintenance_execution` attempt. BM06/BM08 show multiple paper signers, but V1 requires only the known department and VTYT digital confirmations, so two explicit signer pairs on `acceptance_record` suffice. Other paper participants are outside required V1 structured data.

## 2NF — key dependency

Surrogate IDs identify the main records. The unique (`plan_id`, `equipment_id`) candidate key means per-item planned date and state depend on the **whole participation**, not only the plan or equipment. The former composite `user_role` table is removed because one role/account is enough for V1; no partial dependency is introduced by `user_account.role_code`.

## 3NF — project-specific examples

- `equipment.department_id` identifies current custody; `department.name` is not copied into equipment or plan item. `maintenance_plan_item.department_id_at_plan` is a separate historical FK for scoped history.
- `maintenance_coverage` stores dated entitlement evidence, not a nullable boolean on equipment. An absent row or UNKNOWN is distinct from verified NOT_FREE.
- `service_provider` owns current provider details. The plan item stores only current provider ID/route; each execution stores provider ID actually used. No repeated provider address/name snapshots.
- `approval_request` holds submitted choice/rationale and `approval_action` holds a later decision. Resubmission is a new request row, avoiding overwritten decisions. Vendor proposal data can live on request because it has no independent required lifecycle after the round.
- `maintenance_plan` and `maintenance_plan_item` have separate states. `status_history` records transitions but does not duplicate current-state ownership. `maintenance_report` has narrative source fields; statistics derive from items.

## Deliberate simplifications

No cryptographic approval snapshot, generalized attachment table, arbitrary acceptance-participant table or assignment-history table remains. The item keeps historical department ID only because otherwise a later equipment move would change the scope of old maintenance history. Actual provider history is preserved by execution.provider_id, while proposal/decision history is preserved by approval rows. These are small, justified references rather than duplicated labels.

