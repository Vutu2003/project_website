#!/usr/bin/env python3
"""Phase 5.2 driver reusing reviewed Phase 5.1 helpers without editing them.

Source scripts/use-toolchain.sh and both ignored local PostgreSQL env files.
Run: init, quick, happy, restart, cleanup, final.
Restart backend/frontend after happy, before restart; run the full frontend and
backend suites after cleanup, before final. Never rerun init over saved evidence.
"""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
OUT = Path(__file__).resolve().parent
STATE = OUT / 'phase_5_2_evidence.json'
spec = importlib.util.spec_from_file_location('reviewed_phase_5_1', OUT / 'verify_phase_5_1.py')
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)
h.PLAN_LABEL['CLOSED'] = 'Đã đóng'  # Existing canonical seed; no closing command added.
h.STATE = STATE
h.data = json.loads(STATE.read_text()) if STATE.exists() else {'checks': [], 'network': []}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def reviewed_hashes():
    return {p.name: digest(p) for p in OUT.glob('phase_5_1*') if p.is_file()} | {'verify_phase_5_1.py': digest(OUT / 'verify_phase_5_1.py')}


def source_hashes():
    files = set()
    for directory in ['backend/src', 'backend/docs', 'backend/scripts', 'frontend/src', 'frontend/docs', 'database', 'scripts']:
        files.update(p for p in (h.ROOT / directory).rglob('*') if p.is_file())
    files.update(p for p in (h.ROOT / 'frontend').iterdir() if p.is_file() and (p.suffix in ['.json', '.ts', '.html', '.md', '.js'] or p.name == '.env.example'))
    files.update(h.ROOT / name for name in ['backend/pom.xml', 'backend/README.md', 'backend/.env.example', 'README.md', '.gitignore', '.editorconfig'])
    return {str(p.relative_to(h.ROOT)): digest(p) for p in sorted(files)}


def pids():
    import re
    output = subprocess.check_output(['ss', '-ltnp'], text=True)
    return {str(port): int(re.search(r'pid=(\d+)', line).group(1)) for port in [5173, 8080, 55432]
            for line in output.splitlines() if f':{port} ' in line and re.search(r'pid=(\d+)', line)}


def init():
    assert not STATE.exists(), 'Preserve existing Phase 5.2 evidence; use a new filename for a new run.'
    h.data['run_prefix'] = 'SMOKE-P52-' + time.strftime('%Y%m%d%H%M%S')
    h.data['started_at'] = time.strftime('%Y-%m-%dT%H:%M:%S%z')
    h.data['phase_5_1_hashes'] = reviewed_hashes()
    h.data['source_hashes'] = source_hashes()
    h.data['baseline'] = h.baseline()
    accepted = json.loads((OUT / 'phase_5_1_evidence.json').read_text())
    h.check('Canonical baseline exactly matches accepted Phase 5.1', h.data['baseline'] == accepted['post_regression_baseline'])
    h.check('Accepted business source remains unchanged', all(digest(h.ROOT / name) == value for name, value in accepted['source_hashes'].items()))
    h.check('Backend health UP', h.request('/actuator/health')[1]['status'] == 'UP')
    h.check('Frontend login HTTP 200', urllib.request.urlopen(h.UI + '/login').status == 200)
    h.data['startup_pids'] = pids()
    pom = ET.parse(h.ROOT / 'backend/pom.xml').getroot()
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    package = json.loads((h.ROOT / 'frontend/package.json').read_text())
    lock = json.loads((h.ROOT / 'frontend/package-lock.json').read_text())
    h.data['metadata'] = {'backend_artifact': pom.find('m:artifactId', ns).text,
        'backend_version': pom.find('m:version', ns).text, 'spring_boot': pom.find('m:parent/m:version', ns).text,
        'java_target': pom.find('m:properties/m:java.version', ns).text,
        'frontend_name': package['name'], 'frontend_version': package['version'],
        'resolved_frontend_dependencies': {name: lock['packages']['node_modules/' + name]['version'] for name in ['react', 'react-dom', 'react-router', 'typescript', 'vite', 'vitest']},
        'migration_files': sorted(p.name for p in (h.ROOT / 'database/migrations').glob('V*.sql')),
        'git_baseline': 'No commit exists; freeze identified by SHA-256 file manifest.'}
    h.check('Frozen metadata: Java17, SpringBoot3.5.16, six migrations, frontend lock version',
        h.data['metadata']['java_target'] == '17' and h.data['metadata']['spring_boot'] == '3.5.16'
        and len(h.data['metadata']['migration_files']) == 6 and package['version'] == lock['version'], h.data['metadata'])
    h.save()


def quick(b):
    if h.data.get('auth_regression_complete'):
        canonical(b)
        return
    accepted = json.loads((OUT / 'phase_5_1_evidence.json').read_text())
    for role in h.ROLES:
        b.login(role)
        h.check('Role navigation matches accepted Phase 5.1: ' + role, h.data['navigation'][role] == accepted['navigation'][role], h.data['navigation'][role])
        forbidden = '/plans/new' if role == 'bgd' else '/approvals'
        b.goto(forbidden)
        b.see('Không có quyền xem trang')
        status, error = h.request('/api/plans' if role == 'bgd' else '/api/approvals/pending', role,
            'POST' if role == 'bgd' else 'GET', {} if role == 'bgd' else None)
        h.check('Representative frontend/backend boundary: ' + role, status == 403 and error['code'] == 'ACCESS_DENIED')
        b.click('Đăng xuất')
        b.see('Tên đăng nhập')
        h.check('Real logout clears tab credential: ' + role, b.js('sessionStorage.length===0 && location.pathname==="/login"'))
    b.login('vtyt')
    b.js('sessionStorage.setItem("medical-maintenance.access-token","invalid-p52-session")')
    b.goto('/equipment')
    b.see('Phiên đăng nhập đã hết hạn')
    h.check('401 restores login and clears invalid session', b.js('sessionStorage.length===0 && location.pathname==="/login"'))
    b.login('khoa')
    b.goto('/equipment/1/history')
    b.see('Máy theo dõi bệnh nhân')
    h.check('KHOA allowed history equipment1', h.api('/api/equipment/1/maintenance-history', 'khoa')['equipmentId'] == 1)
    b.goto('/equipment/4/history')
    b.wait('!!document.querySelector("[role=alert]")', 'foreign scope error')
    status, error = h.request('/api/equipment/4/maintenance-history', 'khoa')
    h.check('KHOA foreign history denied without identity leakage', status == 403 and 'Máy sốc điện' not in b.text() and 'Máy sốc điện' not in json.dumps(error, ensure_ascii=False), error)
    h.data['auth_regression_complete'] = True
    h.save()
    canonical(b)


def canonical(b):
    b.login('vtyt')
    b.goto('/equipment/1/history')
    b.see('Máy theo dõi bệnh nhân')
    db = h.rows('SELECT id,equipment_code,name,department_id FROM equipment WHERE id=1')[0]
    api_eq = h.api('/api/equipment/1')
    h.check('Seed equipment SQL/API/UI IDs/code/name/department agree', api_eq['id'] == db['id'] and api_eq['equipmentCode'] == db['equipment_code'] and api_eq['name'] == db['name'] and api_eq['departmentId'] == db['department_id'], db)
    b.goto('/plans/2')
    h.consistency(b, 2, 'canonical plan read')
    b.goto('/equipment/4/history')
    title = h.plan(2)['title']
    b.see(title)
    b.js('(() => {const c=[...document.querySelectorAll(".campaign-card")].find(x=>x.querySelector("h2").textContent===' + json.dumps(title) + ');c.querySelectorAll("details").forEach(x=>x.open=true);return true})()')
    b.see('Lần thực hiện 1')
    b.see('Lần thực hiện 2')
    b.see('Yêu cầu thực hiện lại')
    campaign = next(c for c in h.api('/api/equipment/4/maintenance-history')['campaigns'] if c['itemId'] == 3)
    db_attempts = h.rows("SELECT e.id,e.attempt_no,e.provider_id,a.result,a.conclusion FROM maintenance_execution e JOIN acceptance_record a ON a.execution_id=e.id AND a.acceptance_type='TECHNICAL_ACCEPTANCE' WHERE e.plan_item_id=3 ORDER BY e.attempt_no")
    provider = h.rows('SELECT id,name FROM service_provider WHERE id=7')[0]
    provider_api = next(p for p in h.api('/api/providers') if p['id'] == 7)
    b.see(provider['name'])
    h.check('Seed provider SQL/API/UI id/name agrees', provider_api['name'] == provider['name'] and all(a['actualProviderId'] == 7 and a['actualProviderName'] == provider['name'] for a in campaign['attempts']), provider)
    h.check('Canonical history SQL/API/UI plan/item states agree', campaign['planTitle'] == title and campaign['planStatus'] == h.plan(2)['status'] and campaign['itemStatus'] == h.rows('SELECT status FROM maintenance_plan_item WHERE id=3')[0]['status'], campaign)
    h.check('Canonical failure: old FAIL retained and new distinct attempt in SQL/API/UI',
        [a['id'] for a in db_attempts] == [1, 2] and [a['attempt_no'] for a in db_attempts] == [1, 2]
        and [a['result'] for a in db_attempts] == ['FAIL', 'PASS']
        and [a['executionId'] for a in campaign['attempts']] == [1, 2]
        and [a['technicalAcceptance']['result'] for a in campaign['attempts']] == ['FAIL', 'PASS']
        and any(e['newState'] == 'REWORK_REQUIRED' for e in campaign['itemHistory'])
        and all(a['conclusion'] in b.text() for a in db_attempts), {'planId': 2, 'itemId': 3, 'equipmentId': 4, 'attempts': db_attempts})
    h.data['failure_regression'] = {'source': 'Canonical campaign re-read in live SQL/API/browser; accepted Phase5.1 live FAIL/rework evidence retained.', 'planId': 2, 'itemId': 3, 'executions': [1, 2]}
    h.save()


def happy(b):
    assert 'free' not in h.data, 'Do not duplicate the recorded smoke plan.'
    h.free(b)
    pid = h.data['free']['plan_id']
    h.data['restart_before'] = {'plan': h.plan(pid), 'item': h.item(pid), 'report': h.api(f'/api/plans/{pid}/report'),
        'campaign': next(c for c in h.api('/api/equipment/1/maintenance-history')['campaigns'] if c['planId'] == pid)}
    h.data['restart_before_pids'] = pids()
    h.check('Only one Phase5.2 smoke plan created', int(h.sql("SELECT count(*) FROM maintenance_plan WHERE title LIKE 'SMOKE-P52-%';")) == 1)
    h.save()


def restart(b):
    pid = h.data['free']['plan_id']
    h.data['restart_after_pids'] = pids()
    h.check('Actual backend and frontend processes restarted',
        h.data['restart_before_pids']['8080'] != h.data['restart_after_pids']['8080']
        and h.data['restart_before_pids']['5173'] != h.data['restart_after_pids']['5173'])
    b.login('vtyt')
    b.goto(f'/plans/{pid}/report')
    b.see('Báo cáo chính thức')
    after = {'plan': h.plan(pid), 'item': h.item(pid), 'report': h.api(f'/api/plans/{pid}/report'),
        'campaign': next(c for c in h.api('/api/equipment/1/maintenance-history')['campaigns'] if c['planId'] == pid)}
    h.check('Restart reconstructs identical plan/item/FINAL report/attempt/history', after == h.data['restart_before'], {'planId': pid, 'planVersion': after['plan']['version'], 'itemVersion': after['item']['version']})
    status, error = h.request(f'/api/plans/{pid}/report', 'vtyt', 'PUT', {'version': after['plan']['version'] - 1, 'workDone': 'SMOKE-P52-REJECTED'})
    h.check('Exact stale error code is OPTIMISTIC_LOCK_CONFLICT', status == 409 and error['code'] == 'OPTIMISTIC_LOCK_CONFLICT', error)
    h.check('Secondary signer token never stored and primary header retained', any(r['method'] == 'POST' and r['path'].endswith('/handover') and r.get('primary_matches_khoa_session') and r.get('secondary_distinct') and r.get('status') == 201 for r in h.data['network']))
    h.check('No uncaught JS/React/CORS/credential logging', not any(h.data.get(k) for k in ['uncaught_errors', 'console_warnings', 'cors_errors', 'secret_logs']))


def cleanup():
    pid = h.data['free']['plan_id']
    prefix = h.data['run_prefix']
    fixtures = h.rows("SELECT id,title FROM maintenance_plan WHERE title LIKE 'SMOKE-P52-%'")
    assert len(fixtures) == 1 and fixtures[0]['id'] == pid and fixtures[0]['title'] == prefix + '-FREE'
    h.sql(f"""BEGIN;
    DO $$ BEGIN IF (SELECT count(*) FROM maintenance_plan WHERE id={pid} AND title='{prefix}-FREE' AND created_by_user_id=(SELECT id FROM user_account WHERE username='demo_vtyt')) <> 1 THEN RAISE EXCEPTION 'Phase5.2 smoke ownership mismatch'; END IF; END $$;
    DELETE FROM maintenance_report WHERE plan_id={pid};
    DELETE FROM acceptance_record WHERE execution_id IN (SELECT e.id FROM maintenance_execution e JOIN maintenance_plan_item i ON i.id=e.plan_item_id WHERE i.plan_id={pid});
    DELETE FROM maintenance_progress_log WHERE execution_id IN (SELECT e.id FROM maintenance_execution e JOIN maintenance_plan_item i ON i.id=e.plan_item_id WHERE i.plan_id={pid});
    DELETE FROM maintenance_execution WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id={pid});
    DELETE FROM approval_action WHERE request_id IN (SELECT id FROM approval_request WHERE plan_id={pid} OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id={pid}));
    DELETE FROM approval_request WHERE plan_id={pid} OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id={pid});
    DELETE FROM status_history WHERE plan_id={pid} OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id={pid});
    DELETE FROM maintenance_plan_item WHERE plan_id={pid};
    DELETE FROM maintenance_plan WHERE id={pid};
    COMMIT;""")
    h.data['cleanup_baseline'] = h.baseline()
    h.check('Guarded cleanup restores exact canonical rows/schema/migrations', h.data['cleanup_baseline'] == h.data['baseline'])


def final(b):
    suites = []
    for p in sorted((h.ROOT / 'backend/target/surefire-reports').glob('TEST-*.xml')):
        s = ET.parse(p).getroot()
        suites.append({k: s.attrib[k] for k in ['name', 'tests', 'failures', 'errors', 'skipped', 'time']})
    totals = {k: sum(int(s[k]) for s in suites) for k in ['tests', 'failures', 'errors', 'skipped']}
    h.data['backend_regression'] = {'totals': totals, 'suites': suites}
    h.check('Full existing backend suite passes', totals == {'tests': 87, 'failures': 0, 'errors': 0, 'skipped': 0}, totals)
    h.check('Frontend build/lint/20 tests pass', h.data['frontend_regression'] == {'build': 'PASS', 'lint': 'PASS', 'tests': 20, 'test_files': 8, 'failures': 0})
    h.data['final_baseline'] = h.baseline()
    h.check('Final canonical row counts/fingerprints/schema match accepted baseline', h.data['final_baseline'] == h.data['baseline'])
    h.check('No business/config/metadata/migration/seed source drift', source_hashes() == h.data['source_hashes'])
    h.check('All reviewed Phase5.1 files preserved byte for byte', reviewed_hashes() == h.data['phase_5_1_hashes'])
    columns = h.rows("SELECT table_name,column_name FROM information_schema.columns WHERE table_schema='public' AND data_type IN ('text','character varying') AND table_name <> 'flyway_schema_history' AND column_name <> 'password_hash'")
    h.data['smoke_counts'] = {}
    for t in h.TABLES:
        names = [c['column_name'] for c in columns if c['table_name'] == t]
        where = ' OR '.join(c + " LIKE 'SMOKE-P52-%'" for c in names)
        h.data['smoke_counts'][t] = int(h.sql(f'SELECT count(*) FROM {t} WHERE {where};')) if where else 0
    h.check('All 14 tables contain zero SMOKE-P52 records', sum(h.data['smoke_counts'].values()) == 0)
    b.login('vtyt')
    b.goto('/equipment/1/history')
    b.see('Máy theo dõi bệnh nhân')
    b.js('new Promise(r=>setTimeout(r,1000))')
    before = len(h.data['network'])
    logs_before = len(h.data.get('browser_log', []))
    b.js('new Promise(r=>setTimeout(r,2500))')
    h.check('Final canonical browse clean and no repeated fetch loop', before == len(h.data['network']) and logs_before == len(h.data.get('browser_log', [])) and h.data['run_prefix'] not in b.text())
    h.data['final_health'] = h.request('/actuator/health')[1]
    h.data['final_database'] = h.rows('SELECT current_database() AS database,current_user AS username,version() AS version')[0]
    h.check('Final backend UP and authenticated PostgreSQL connected', h.data['final_health']['status'] == 'UP')
    h.data['freeze_manifest_sha256'] = hashlib.sha256(json.dumps(h.data['source_hashes'], sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    h.data['completed_at'] = time.strftime('%Y-%m-%dT%H:%M:%S%z')
    h.data['final_status'] = 'PASS'
    h.save()


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=['init', 'quick', 'happy', 'restart', 'cleanup', 'final'])
    stage = parser.parse_args().stage
    if stage in ['init', 'cleanup']:
        globals()[stage]()
        return
    assert STATE.exists(), 'Run init first.'
    b = h.Browser()
    try:
        globals()[stage](b)
    finally:
        b.close()
    secrets = [os.environ[k] for k in ['DB_PASSWORD', 'JWT_SECRET', *[v[1] for v in h.ROLES.values()]]] + list(h.tokens.values()) + [b.primary]
    h.check('Phase5.2 evidence has no credential values: ' + stage, all(v not in STATE.read_text() for v in secrets if v))


if __name__ == '__main__':
    main()
