# FINAL INDEX STRATEGY

Use access patterns and measured plans; avoid indexing every FK by habit. A PK/UNIQUE constraint already supplies its own index in PostgreSQL.

| Table / columns | Query / UC | Benefit and tradeoff | Freeze recommendation |
| --- | --- | --- | --- |
| `maintenance_plan_item(equipment_id, plan_id)` | Equipment history UC12 | Fast all-plan lookup for a device; one extra insert index | CREATE NOW |
| `maintenance_plan_item(plan_id, status)` | Plan status counts UC08/11 | Speeds 500-item aggregation; status updates cost | CREATE NOW |
| `approval_request(status, request_type, submitted_at)` | Pending director queue UC04/07 | Fast pending list; decision updates index | CREATE NOW |
| `status_history(plan_id, action_timestamp)` | Plan audit BR05 | Ordered transition lookup; append cost | CREATE NOW |
| `status_history(plan_item_id, action_timestamp)` | Item/equipment audit UC12 | Ordered transition lookup; append cost | CREATE NOW |
| `maintenance_execution(plan_item_id, attempt_no)` | Attempt chronology UC08/12 | Supplied by required UNIQUE | CREATE NOW via constraint |
| `maintenance_progress_log(execution_id, event_at)` | Work log UC08/12 | Ordered attempt notes; append cost | CREATE NOW |
| `acceptance_record(execution_id, acceptance_type)` | Current attempt validation UC09/10 | Supplied by required UNIQUE | CREATE NOW via constraint |
| `maintenance_coverage(equipment_id, effective_from, effective_to)` | Entitlement lookup UC05 | Speeds dated evidence query; add after plan inspection | DEFER UNTIL PERFORMANCE TEST |
| `equipment(department_id, serial_number)` | Department/serial search UC12 | Serial nullable and unproven unique; workload unknown | DEFER UNTIL PERFORMANCE TEST |
| `maintenance_report(plan_id)` | UC11 report | Supplied by required UNIQUE | CREATE NOW via constraint |

The previous generic `status_history(target_entity_type,target_entity_id,action_timestamp)` suggestion does not fit typed FK history and is not part of the frozen design. Test representative UC12 and 500-item UC11 queries before further indexes.

