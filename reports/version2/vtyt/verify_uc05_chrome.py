#!/usr/bin/env python3
"""UC05 smoke on an isolated PostgreSQL database, real UI/API and Chrome CDP.
Source local credential env first; never persist passwords/tokens. No UC06 command.
Required endpoints: backend 8081, Vite 5174, Chrome CDP 9335.
"""
import base64
import datetime
import importlib.util
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import urllib.parse
import urllib.request
import websocket

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('v1_cdp_helpers', ROOT / 'reports/version1/integration/verify_phase_5_1.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
m.API, m.UI = 'http://localhost:8081', 'http://localhost:5174'
m.STATE = OUT / 'uc05_chrome_evidence.json'
m.data = {'checks': [], 'network': [], 'started_at': datetime.datetime.now().astimezone().isoformat(),
          'chrome': subprocess.check_output(['google-chrome', '--version'], text=True).strip(),
          'database': os.environ['DB_NAME']}
m.tokens = {}
assert os.environ['DB_NAME'] == 'medical_maintenance_uc05_test', 'Isolated test database required'
prefix = 'SMOKE-V2-UC05-' + secrets.token_hex(6)
m.data['run_prefix'] = prefix
before = m.baseline()
m.data['baseline'] = before
created_plans = []


def check(name, condition, evidence=None):
    m.check(name, condition, evidence)


class Chrome(m.Browser):
    def __init__(self):
        tabs = json.load(urllib.request.urlopen('http://localhost:9335/json/list'))
        info = next(row for row in tabs if row['type'] == 'page')
        self.ws = websocket.create_connection(info['webSocketDebuggerUrl'], origin='http://localhost:9335', timeout=25)
        self.seq, self.requests, self.events, self.primary = 0, {}, [], None
        for domain in ['Page', 'Runtime', 'Network', 'Log']:
            self.cmd(domain + '.enable')
        self.cmd('Page.addScriptToEvaluateOnNewDocument', {'source': 'window.confirm=()=>true'})
        self.cmd('Emulation.setDeviceMetricsOverride', {'width': 1366, 'height': 900, 'deviceScaleFactor': 1, 'mobile': False})

    def event(self, event):
        method, params = event.get('method'), event.get('params', {})
        if method == 'Network.requestWillBeSent':
            request = params['request']
            path = urllib.parse.urlsplit(request['url']).path
            if path.startswith('/api/'):
                record = {'method': request['method'], 'path': path}
                # Only capture UC05 command fields, never credential bodies or headers.
                if path.endswith('/route') and request.get('postData'):
                    body = json.loads(request['postData'])
                    record['body'] = {key: body.get(key) for key in ['version', 'coverageId']}
                self.requests[params['requestId']] = record
                m.data['network'].append(record)
        elif method == 'Network.responseReceived' and params['requestId'] in self.requests:
            self.requests[params['requestId']]['status'] = int(params['response']['status'])
        elif method == 'Runtime.exceptionThrown':
            m.data.setdefault('uncaught_errors', []).append('Uncaught JavaScript exception')
        elif method == 'Network.loadingFailed' and params.get('corsErrorStatus'):
            m.data.setdefault('cors_errors', []).append('CORS failure')
        elif method == 'Runtime.consoleAPICalled' and params.get('type') in ['warning', 'error']:
            m.data.setdefault('console_errors', []).append(params['type'])

    def screenshot(self, name):
        (OUT / 'screens').mkdir(exist_ok=True)
        self.js('document.querySelector(".workflow-panel")?.scrollIntoView({block:"start"})')
        (OUT / 'screens' / name).write_bytes(base64.b64decode(self.cmd('Page.captureScreenshot', {'format': 'png'})['data']))


def read_items(pid):
    return m.api(f'/api/plans/{pid}/items?size=100')['content']


def open_uc05(b, pid, code):
    b.goto(f'/plans/{pid}')
    b.see(prefix)
    b.wait('(() => {const r=[...document.querySelectorAll("tbody tr")].find(r=>r.innerText.includes(' + json.dumps(code) + '));'
           'const button=r && [...r.querySelectorAll("button")].find(x=>x.textContent==="Xác định hình thức & đối tác");'
           'if(!button)return false;button.click();return true})()', 'discover UC05 for ' + code)
    b.see('Xác định hình thức & đối tác bảo trì')
    b.wait('!document.body.innerText.includes("Đang tải hồ sơ hợp đồng")', 'coverage loaded')


def choose(b, coverage_id):
    b.wait('(() => {const e=document.querySelector("input[type=radio][value=\\"' + str(coverage_id) + '\\"]");'
           'if(!e || e.disabled)return false;e.click();return true})()', 'select valid coverage')


def assert_persisted(pid, equipment_id, status, provider_id, coverage_id, route):
    row = next(row for row in read_items(pid) if row['equipmentId'] == equipment_id)
    db = m.rows(f'SELECT status,version,coverage_id,assigned_provider_id,assignment_route FROM maintenance_plan_item WHERE id={row["id"]}')[0]
    check('API/SQL persisted ' + status, row['status'] == db['status'] == status
          and row['version'] == db['version'] and db['coverage_id'] == coverage_id
          and row['assignedProviderId'] == db['assigned_provider_id'] == provider_id
          and row['assignmentRoute'] == db['assignment_route'] == route, {'item_id': row['id'], 'status': status, 'version': row['version'], 'provider_id': provider_id})
    return row


def create_plan_ui(b, equipment):
    b.goto('/plans/new'); b.see('Chọn thêm thiết bị')
    b.label('Tiêu đề', prefix + '-ALL'); b.label('Ngày bắt đầu', '2026-11-01'); b.label('Ngày kết thúc', '2026-11-30')
    for code in equipment:
        for _ in range(10):
            expression = '(() => {const r=[...document.querySelectorAll("tbody tr")].find(r=>r.innerText.includes(' + json.dumps(code) + ') && [...r.querySelectorAll("button")].some(x=>x.textContent==="Thêm"));'
            expression += 'if(!r)return false;r.querySelector("button").click();return true})()'
            if b.js(expression):
                break
            b.click('Sau')
            b.wait('!document.body.innerText.includes("Đang tải thiết bị")', 'equipment page loaded')
        else:
            raise AssertionError('Equipment not found in real UI')
        b.wait('document.querySelectorAll("input[aria-label^=\\"Ngày dự kiến\\"]").length>=' + str(equipment.index(code)+1), 'item added')
        # Reset pagination so finding another fixture does not depend on the previous page.
        while b.js('[...document.querySelectorAll("button")].some(x=>x.textContent==="Trước" && !x.disabled)'):
            b.click('Trước'); b.wait('!document.body.innerText.includes("Đang tải thiết bị")', 'previous equipment page loaded')
    b.click('Tạo kế hoạch')
    b.wait('/^\\/plans\\/\\d+$/.test(location.pathname)', 'real UI plan created')
    pid = int(b.js('location.pathname.split("/").at(-1)'))
    created_plans.append(pid)
    check('UC01 real UI creates four isolated items', len(read_items(pid)) == 4)
    return pid


def cleanup():
    found = m.rows("SELECT id,title FROM maintenance_plan WHERE title LIKE '" + prefix + "%'")
    for row in found:
        assert row['title'].startswith(prefix)
        if row['id'] not in created_plans:
            created_plans.append(row['id'])
        pid = row['id']
        assert int(m.sql(f'SELECT count(*) FROM maintenance_execution WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id={pid})')) == 0
        m.sql(f'''BEGIN;
DELETE FROM approval_action WHERE request_id IN (SELECT id FROM approval_request WHERE plan_id={pid});
DELETE FROM approval_request WHERE plan_id={pid};
DELETE FROM status_history WHERE plan_id={pid} OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id={pid});
DELETE FROM maintenance_plan_item WHERE plan_id={pid};
DELETE FROM maintenance_plan WHERE id={pid} AND title LIKE '{prefix}%';
COMMIT;''')
    after = m.baseline()
    check('Guarded cleanup restores all 14 table fingerprints and V001-V007', after == before)
    m.data['cleanup'] = {'plan_ids': created_plans, 'canonical_restored': after == before,
                         'remaining_smoke': int(m.sql("SELECT count(*) FROM maintenance_plan WHERE title LIKE '"+prefix+"%'"))}


def main():
    check('V2 database baseline', before['rows'] == 603 and before['schema']['tables'] == 14
          and before['schema']['columns'] == 118 and before['schema']['foreign_keys'] == 31
          and len(before['schema']['migrations']) == 7)
    b = Chrome()
    try:
        b.login('vtyt')
        eqs = m.rows("SELECT e.id,e.equipment_code,c.id AS coverage_id,c.classification,c.provider_id FROM equipment e JOIN maintenance_coverage c ON c.equipment_id=e.id WHERE e.equipment_code IN ('DEMO-EQ-001','DEMO-EQ-002') OR c.classification='UNKNOWN' ORDER BY e.id")
        free = next(row for row in eqs if row['equipment_code'] == 'DEMO-EQ-001')
        paid = next(row for row in eqs if row['equipment_code'] == 'DEMO-EQ-002')
        unknown = next(row for row in eqs if row['classification'] == 'UNKNOWN')
        absent = m.rows('SELECT e.id,e.equipment_code FROM equipment e WHERE NOT EXISTS (SELECT 1 FROM maintenance_coverage c WHERE c.equipment_id=e.id) ORDER BY e.id LIMIT 1')[0]
        pid = create_plan_ui(b, [row['equipment_code'] for row in [free, paid, unknown, absent]])
        m.data['fixtures'] = {'plan_id': pid, 'items': [{'equipment_id': row['id'], 'equipment_code': row['equipment_code']} for row in [free, paid, unknown, absent]]}
        parent = m.api(f'/api/plans/{pid}')
        open_uc05(b, pid, free['equipment_code'])
        b.see('Đơn vị hợp đồng:'); b.see('Theo hợp đồng (FREE)'); b.see('Chỉ có thể bắt đầu thực hiện sau khi kế hoạch được phê duyệt.')
        provider_name = m.sql(f'SELECT name FROM service_provider WHERE id={free["provider_id"]}')
        check('FREE contract, validity, basis and provider visible before approval', provider_name in b.text() and 'Căn cứ:' in b.text() and 'Hiệu lực:' in b.text())
        choose(b, free['coverage_id']); b.screenshot('uc05_free_before_approval.png')
        b.click('Xác nhận bảo trì theo hợp đồng'); b.see('Đã xác nhận bảo trì theo hợp đồng.')
        routed = assert_persisted(pid, free['id'], 'UNDER_CONTRACT', free['provider_id'], free['coverage_id'], 'UNDER_CONTRACT')
        check('UC05 leaves DRAFT parent unchanged', m.api(f'/api/plans/{pid}') == parent)
        b.cmd('Page.reload'); b.see(prefix); b.see(provider_name)
        check('FREE route/provider retained after refresh and no execution link', b.js('[...document.querySelectorAll("a")].every(x=>x.textContent!=="Thực hiện / bàn giao")'))
        b.goto(f'/plans/{pid}/items/{routed["id"]}/execution'); b.see('Hình thức bảo trì đã được xác định.')
        check('Direct execution page has no start before approval', b.js('[...document.querySelectorAll("button")].every(x=>x.textContent!=="Bắt đầu thực hiện")'))
        status, error = m.request(f'/api/plan-items/{routed["id"]}/executions', 'vtyt', 'POST', {'version': routed['version'], 'planVersion': parent['version']})
        check('API execution before approval blocked', status == 409 and error['code'] == 'PLAN_NOT_EXECUTABLE')
        open_uc05(b, pid, paid['equipment_code']); b.see('Ngoài hợp đồng (NOT_FREE)'); choose(b, paid['coverage_id'])
        b.see('Thiết bị không thuộc diện bảo trì miễn phí theo hợp đồng.'); b.screenshot('uc05_not_free.png')
        b.click('Xác nhận ngoài hợp đồng'); b.see('Đã xác nhận ngoài hợp đồng.')
        assert_persisted(pid, paid['id'], 'PENDING_PROPOSAL', None, paid['coverage_id'], None)
        b.cmd('Page.reload'); b.see('Bước tiếp theo: UC06 — Lập tờ trình chọn đơn vị bảo trì.')
        check('NOT_FREE next step clear; no UC06 form added/opened', 'Lưu bản nháp' not in b.text())
        open_uc05(b, pid, unknown['equipment_code']); b.see('Chưa đủ căn cứ để xác định hình thức bảo trì.')
        check('UNKNOWN visible, disabled and no confirmation', b.js('[...document.querySelectorAll("input[type=radio]")].every(x=>x.disabled) && [...document.querySelectorAll("button")].every(x=>!x.textContent.startsWith("Xác nhận"))'))
        b.screenshot('uc05_unknown.png')
        assert_persisted(pid, unknown['id'], 'PLANNED', None, None, None)
        open_uc05(b, pid, absent['equipment_code']); b.see('Thiết bị chưa có hồ sơ coverage phù hợp.')
        check('Missing coverage warns with no confirmation', b.js('document.querySelectorAll("input[type=radio]").length===0 && [...document.querySelectorAll("button")].every(x=>!x.textContent.startsWith("Xác nhận"))'))
        assert_persisted(pid, absent['id'], 'PLANNED', None, None, None)
        b.cmd('Emulation.setDeviceMetricsOverride', {'width': 760, 'height': 1000, 'deviceScaleFactor': 1, 'mobile': False})
        check('UC05 no page overflow at 760px', b.js('document.documentElement.scrollWidth<=innerWidth'))
        b.cmd('Emulation.setDeviceMetricsOverride', {'width': 1366, 'height': 900, 'deviceScaleFactor': 1, 'mobile': False})
        b.goto(f'/plans/{pid}'); b.see(prefix); b.click('Gửi phê duyệt'); b.see('Đã gửi kế hoạch để phê duyệt.')
        check('UC03 accepts UC05 decisions without changing them', m.api(f'/api/plans/{pid}')['status'] == 'SUBMITTED')
        req = int(m.sql(f"SELECT id FROM approval_request WHERE plan_id={pid} AND status='PENDING'"))
        b.login('bgd'); b.goto(f'/approvals/{req}'); b.see(prefix); b.click('Xác nhận quyết định')
        b.wait('location.pathname==="/approvals"', 'BGD plan approval')
        check('Plan approval retains FREE and NOT_FREE states', m.api(f'/api/plans/{pid}')['status'] == 'APPROVED'
              and next(row for row in read_items(pid) if row['equipmentId']==free['id'])['status']=='UNDER_CONTRACT'
              and next(row for row in read_items(pid) if row['equipmentId']==paid['id'])['status']=='PENDING_PROPOSAL')
        b.login('vtyt'); b.goto(f'/plans/{pid}/items/{routed["id"]}/execution'); b.see('Bắt đầu thực hiện')
        check('Approved FREE item eligible in existing UC08 UI', 'Bắt đầu thực hiện' in b.text())
        # Two clients: a browser-held PLANNED snapshot, then an independent valid UC05 API command.
        stale = m.api('/api/plans', 'vtyt', 'POST', {'title': prefix+'-STALE', 'periodStart': '2026-11-01', 'periodEnd': '2026-11-30', 'items': [{'equipmentId': free['id']}]})
        stale_pid = stale['id']; created_plans.append(stale_pid)
        stale_item = read_items(stale_pid)[0]
        open_uc05(b, stale_pid, free['equipment_code']); choose(b, free['coverage_id'])
        m.api(f'/api/plan-items/{stale_item["id"]}/route', 'vtyt', 'POST', {'version': stale_item['version'], 'coverageId': free['coverage_id']})
        b.click('Xác nhận bảo trì theo hợp đồng'); b.see('Xung đột phiên bản')
        check('Stale UC05 returns 409 without retry', any(row.get('status')==409 and row['path'].endswith('/route') for row in m.data['network']))
        b.click('Tải lại dữ liệu'); b.see(provider_name)
        check('Conflict reload shows current decision and removes old action', b.js('[...document.querySelectorAll("button")].every(x=>x.textContent!=="Xác định hình thức & đối tác")'))
        for role in ['bgd','khoa','admin']:
            b.login(role); b.goto(f'/plans/{pid}'); b.see(prefix)
            check(role+' cannot see UC05 action', b.js('[...document.querySelectorAll("button")].every(x=>x.textContent!=="Xác định hình thức & đối tác")'))
            status, error = m.request(f'/api/plan-items/{routed["id"]}/route', role, 'POST', {'version': routed['version'], 'coverageId': free['coverage_id']})
            check(role+' route API forbidden', status == 403 and error['code'] == 'ACCESS_DENIED')
        histories = m.rows(f"SELECT old_state,new_state,action,reason,actor_user_id,action_timestamp FROM status_history WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id={pid}) AND old_state='PLANNED'")
        check('UC05 StatusHistory actor/time/evidence persisted', len(histories)==2 and all(row['actor_user_id'] and row['action_timestamp'] and 'coverage=' in row['reason'] for row in histories))
        check('No UC06 command sent by browser', not any(row['method']=='POST' and ('vendor-proposals' in row['path']) for row in m.data['network']))
        check('No JS/CORS/console errors in real Chrome', not m.data.get('uncaught_errors') and not m.data.get('cors_errors') and not m.data.get('console_errors'))
        m.data['result'] = 'PASS'
    finally:
        b.close()


try:
    main()
finally:
    cleanup()
    m.data['finished_at'] = datetime.datetime.now().astimezone().isoformat()
    m.save()
