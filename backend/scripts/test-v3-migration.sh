#!/usr/bin/env bash
# Verify upgrading a populated V009 database without discarding workflow/evidence.
set +x
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT}/scripts/use-toolchain.sh"
source "${ROOT}/.local-postgres/backend-security.env"
TASK_CLUSTER="$(mktemp -d /tmp/ltnc-v3-migration.XXXXXX)"
export DB_HOST=127.0.0.1 DB_PORT=55442 DB_NAME=medical_maintenance_v3_migration_test DB_USERNAME="$(id -un)"
export DB_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')" PGPASSWORD=
export PGPASSWORD="${DB_PASSWORD}"
cleanup() {
 [[ "${TASK_CLUSTER}" == /tmp/ltnc-v3-migration.* ]] || return 1
 pg_ctl -D "${TASK_CLUSTER}/data" -m fast -w stop >/dev/null 2>&1 || true
 rm -rf -- "${TASK_CLUSTER}"
}
trap cleanup EXIT
initdb -D "${TASK_CLUSTER}/data" --auth-local=trust --auth-host=trust --no-instructions >/dev/null
mkdir "${TASK_CLUSTER}/run" "${TASK_CLUSTER}/migrations"
pg_ctl -D "${TASK_CLUSTER}/data" -l "${TASK_CLUSTER}/postgres.log" -o "-h 127.0.0.1 -p ${DB_PORT} -k ${TASK_CLUSTER}/run" -w start >/dev/null
createdb -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" "$DB_NAME"
cp "${ROOT}"/database/migrations/V00*.sql "${TASK_CLUSTER}/migrations/"
java -cp "${ROOT}/backend/target/setup-libs/*" "${ROOT}/backend/scripts/MigrateDatabase.java" "${TASK_CLUSTER}/migrations"
python3 "${ROOT}/database/seeds/local_credentials.py" --baseline v2
psql -X -q -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" <<'SQL'
-- Two evidence versions for the same contract must migrate together, without loss.
INSERT INTO maintenance_coverage(equipment_id,provider_id,contract_reference,coverage_scope,effective_from,effective_to,classification,verified_by_user_id,verified_at,basis_note,warranty_expires_on)
SELECT equipment_id,provider_id,contract_reference,coverage_scope,effective_from,effective_to,classification,verified_by_user_id,verified_at,basis_note,warranty_expires_on FROM maintenance_coverage WHERE id=1;
SQL
PSQL=(psql -X -q -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME")
# Compare original columns; validate newly added contract/quarter columns below.
SNAPSHOT_SQL="SELECT md5(jsonb_build_object(
 'plans',(SELECT jsonb_agg(to_jsonb(p)-'plan_year'-'plan_quarter' ORDER BY id) FROM maintenance_plan p),
 'items',(SELECT jsonb_agg(to_jsonb(i)-'last_maintenance_date'-'maintenance_due_date'-'contract_id' ORDER BY id) FROM maintenance_plan_item i),
 'executions',(SELECT jsonb_agg(to_jsonb(x) ORDER BY id) FROM maintenance_execution x),
 'acceptances',(SELECT jsonb_agg(to_jsonb(a) ORDER BY id) FROM acceptance_record a),
 'requests',(SELECT jsonb_agg(to_jsonb(r) ORDER BY id) FROM approval_request r),
 'actions',(SELECT jsonb_agg(to_jsonb(a) ORDER BY id) FROM approval_action a),
 'history',(SELECT jsonb_agg(to_jsonb(h) ORDER BY id) FROM status_history h),
 'reports',(SELECT jsonb_agg(to_jsonb(r) ORDER BY id) FROM maintenance_report r))::text)"
workflow_before="$("${PSQL[@]}" -At -c "$SNAPSHOT_SQL")"
"${ROOT}/backend/scripts/migrate-dev-db.sh" --test-env
workflow_after="$("${PSQL[@]}" -At -c "$SNAPSHOT_SQL")"
[[ "$workflow_before" == "$workflow_after" ]] || { echo 'Historical workflow changed'; exit 1; }
psql -X -q -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" <<'SQL'
DO $$ BEGIN
 IF (SELECT count(*) FROM maintenance_coverage WHERE classification='FREE' AND contract_id IS NOT NULL)<>5
 OR (SELECT count(*) FROM maintenance_contract)<>4
 OR (SELECT count(DISTINCT contract_id) FROM maintenance_coverage WHERE equipment_id=1)<>1 THEN
  RAISE EXCEPTION 'Contract migration did not preserve/group equipment coverage';
 END IF;
 IF EXISTS(SELECT 1 FROM equipment WHERE maintenance_enabled OR commissioning_date IS NOT NULL) THEN
  RAISE EXCEPTION 'Migration fabricated commissioning/scheduling data';
 END IF;
 IF EXISTS(SELECT 1 FROM maintenance_coverage c JOIN maintenance_contract k ON k.id=c.contract_id
 WHERE c.provider_id<>k.provider_id OR c.effective_from<>k.start_date OR c.effective_to<>k.end_date) THEN
  RAISE EXCEPTION 'Contract mirror drift';
 END IF;
 IF EXISTS(SELECT 1 FROM maintenance_plan_item i JOIN maintenance_coverage c ON c.id=i.coverage_id
 WHERE i.contract_id IS DISTINCT FROM c.contract_id) THEN
  RAISE EXCEPTION 'Plan item contract backfill drift';
 END IF;
 IF EXISTS(SELECT 1 FROM maintenance_plan p WHERE p.plan_year IS NOT NULL AND (
 p.plan_year<>extract(year from p.period_start)::integer OR
 p.plan_quarter<>'Q'||extract(quarter from p.period_start)::integer OR
 p.period_start<>date_trunc('quarter',p.period_start)::date OR
 p.period_end<>(date_trunc('quarter',p.period_start)+interval '3 months - 1 day')::date)) THEN
  RAISE EXCEPTION 'Quarter backfill does not match the original plan period';
 END IF;
END $$;
SQL
printf 'PASS: populated V009 → V013, original workflow/history preserved, repeated coverage evidence grouped, no invented schedule dates.\n'
