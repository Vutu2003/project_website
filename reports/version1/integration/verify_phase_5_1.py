#!/usr/bin/env python3
"""Local real-Chrome/API/PostgreSQL audit. Source ignored env files first.

Uses installed websocket-client and Chrome CDP; never persists credentials.
Run stages in order: baseline, free, external, rework, boundaries, restart,
contracts, cleanup, final. Restart the existing backend/frontend between boundaries and restart.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.request
import websocket

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
STATE = OUT / 'phase_5_1_evidence.json'
API = 'http://localhost:8080'
UI = 'http://localhost:5173'
TABLES = ['department', 'service_provider', 'user_account', 'equipment',
          'maintenance_coverage', 'maintenance_plan', 'maintenance_plan_item',
          'approval_request', 'approval_action', 'maintenance_execution',
          'maintenance_progress_log', 'acceptance_record', 'maintenance_report',
          'status_history']
ROLES = {'vtyt': ('demo_vtyt', 'DEMO_VTYT_PASSWORD', 'PHONG_VTYT'),
         'bgd': ('demo_bgd', 'DEMO_BGD_PASSWORD', 'BAN_GIAM_DOC'),
         'khoa': ('demo_khoa_noi', 'DEMO_KHOA_PASSWORD', 'KHOA_PHONG'),
         'admin': ('demo_admin', 'DEMO_ADMIN_PASSWORD', 'ADMIN')}
ITEM_LABEL = {'PLANNED': 'Dự kiến', 'UNDER_CONTRACT': 'Theo hợp đồng',
              'PENDING_PROPOSAL': 'Chờ đề xuất đơn vị',
              'WAITING_VENDOR_APPROVAL': 'Chờ phê duyệt đơn vị',
              'ASSIGNED_EXTERNAL': 'Đã phân công đơn vị ngoài',
              'IN_MAINTENANCE': 'Đang bảo trì',
              'AWAITING_TECHNICAL_ACCEPTANCE': 'Chờ nghiệm thu kỹ thuật',
              'AWAITING_HANDOVER': 'Chờ bàn giao', 'COMPLETED': 'Hoàn tất',
              'REWORK_REQUIRED': 'Yêu cầu thực hiện lại'}
PLAN_LABEL = {'DRAFT': 'Nháp', 'SUBMITTED': 'Đã gửi duyệt',
              'APPROVED': 'Đã phê duyệt', 'IN_PROGRESS': 'Đang thực hiện',
              'AWAITING_REPORT': 'Chờ báo cáo', 'REPORTED': 'Đã báo cáo'}
data = json.loads(STATE.read_text()) if STATE.exists() else {'checks': [], 'network': []}
tokens = {}


def save():
    STATE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def check(name, condition, evidence=None):
    if not condition:
        raise AssertionError(name)
    data['checks'].append({'name': name, 'result': 'PASS', 'evidence': evidence})
    save()
    print('PASS:', name, flush=True)


def sql(query):
    env = dict(os.environ, PGPASSWORD=os.environ['DB_PASSWORD'])
    result = subprocess.run(['psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
        '-h', os.environ['DB_HOST'], '-p', os.environ['DB_PORT'],
        '-U', os.environ['DB_USERNAME'], '-d', os.environ['DB_NAME']],
        input=query, text=True, capture_output=True, env=env, check=True)
    return result.stdout.strip()


def rows(query):
    return json.loads(sql('SELECT coalesce(json_agg(q),\'[]\'::json) FROM (' + query + ') q;'))


def request(path, role=None, method='GET', body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if role:
        token = tokens.get(role) or login_api(role)
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(API + path, headers=headers, method=method,
        data=None if body is None else json.dumps(body).encode())
    try:
        response = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    return response.status, json.load(response)


def api(path, role='vtyt', method='GET', body=None):
    status, result = request(path, role, method, body)
    if status >= 400:
        raise AssertionError(f'{method} {path}: {status} {result.get("code")}')
    return result


def login_api(role):
    username, key, expected = ROLES[role]
    status, result = request('/api/auth/login', method='POST',
        body={'username': username, 'password': os.environ[key]})
    assert status == 200 and result['user']['role'] == expected
    tokens[role] = result['accessToken']
    return tokens[role]


class Browser:
    def __init__(self):
        tabs = json.load(urllib.request.urlopen('http://localhost:9222/json/list'))
        tab = next(t for t in tabs if t['type'] == 'page')
        self.ws = websocket.create_connection(tab['webSocketDebuggerUrl'],
            origin='http://localhost:9222', timeout=25)
        self.seq = 0
        self.requests = {}
        self.events = []
        self.primary = None
        self.cmd('Page.enable')
        self.cmd('Runtime.enable')
        self.cmd('Network.enable')
        self.cmd('Log.enable')
        self.cmd('Page.addScriptToEvaluateOnNewDocument', {'source': 'window.confirm = () => true'})

    def event(self, event):
        method, p = event.get('method'), event.get('params', {})
        if method == 'Network.requestWillBeSent':
            r = p['request']
            if urllib.parse.urlsplit(r['url']).path.startswith('/api/'):
                headers = {k.lower(): v for k, v in r.get('headers', {}).items()}
                record = {'path': urllib.parse.urlsplit(r['url']).path,
                          'method': r['method'], 'authorization': 'authorization' in headers,
                          'secondary_authorization': 'x-vtyt-authorization' in headers}
                if record['path'].endswith('/handover'):
                    record['primary_matches_khoa_session'] = headers.get('authorization') == 'Bearer ' + (self.primary or '')
                    record['secondary_distinct'] = headers.get('x-vtyt-authorization') != headers.get('authorization')
                if r.get('postData') and '/auth/' not in record['path']:
                    record['body'] = json.loads(r['postData'])
                self.requests[p['requestId']] = record
                data['network'].append(record)
        elif method == 'Network.responseReceived' and p['requestId'] in self.requests:
            self.requests[p['requestId']]['status'] = p['response']['status']
        elif method == 'Network.loadingFailed':
            if p.get('corsErrorStatus'):
                data.setdefault('cors_errors', []).append(p['corsErrorStatus'])
            if p['requestId'] in self.requests and not p.get('canceled'):
                self.requests[p['requestId']]['failure'] = p.get('errorText')
        elif method == 'Runtime.exceptionThrown':
            data.setdefault('uncaught_errors', []).append('Uncaught JavaScript exception')
        elif method == 'Runtime.consoleAPICalled':
            message = ' '.join(str(a.get('value', a.get('description', ''))) for a in p.get('args', []))
            sensitive = any(value and value in message for value in [*tokens.values(), self.primary,
                *[os.environ.get(v[1]) for v in ROLES.values()]])
            if sensitive:
                data.setdefault('secret_logs', []).append('REDACTED credential detected')
            elif p['type'] in ('warning', 'error'):
                data.setdefault('console_warnings', []).append(message[:500])
        elif method == 'Log.entryAdded':
            entry = p['entry']
            if entry.get('level') in ('warning', 'error'):
                data.setdefault('browser_log', []).append({k: entry.get(k) for k in ['source', 'level', 'text', 'url']})

    def cmd(self, method, params=None):
        self.seq += 1
        current = self.seq
        self.ws.send(json.dumps({'id': current, 'method': method, 'params': params or {}}))
        while True:
            result = json.loads(self.ws.recv())
            if result.get('id') == current:
                if 'error' in result:
                    raise AssertionError(f'CDP {method} failed')
                return result.get('result', {})
            self.event(result)

    def js(self, expression):
        result = self.cmd('Runtime.evaluate', {'expression': expression,
            'returnByValue': True, 'awaitPromise': True})
        if 'exceptionDetails' in result:
            raise AssertionError('Browser evaluation failed (details withheld to protect credentials)')
        return result.get('result', {}).get('value')

    def wait(self, expression, label, timeout=20):
        until = time.monotonic() + timeout
        while time.monotonic() < until:
            if self.js(expression):
                return
            time.sleep(.15)
        raise AssertionError('Browser timeout: ' + label + '\n' + self.text()[:2500])

    def text(self):
        return self.js('document.body.innerText') or ''

    def see(self, text):
        self.wait('document.body.innerText.includes(' + json.dumps(text) + ')', text)

    def goto(self, path):
        self.cmd('Page.navigate', {'url': UI + path})
        self.wait('document.readyState === "complete" && document.body.innerText.length > 40', path)

    def fill(self, selector, value):
        self.wait('!!document.querySelector(' + json.dumps(selector) + ')', selector)
        self.js('(() => {const e=document.querySelector(' + json.dumps(selector) + ');'
            'const proto=e.tagName==="TEXTAREA"?HTMLTextAreaElement.prototype:e.tagName==="SELECT"?HTMLSelectElement.prototype:HTMLInputElement.prototype;'
            'Object.getOwnPropertyDescriptor(proto,"value").set.call(e,' + json.dumps(value) + ');'
            'e.dispatchEvent(new Event(e.tagName==="SELECT"?"change":"input",{bubbles:true}));return true})()')

    def label(self, text, value):
        selector = self.js('(() => {const l=[...document.querySelectorAll("label")].find(x=>x.textContent.trim().startsWith(' + json.dumps(text) + '));'
            'if(!l)return null;const e=l.querySelector("input,textarea,select");e.dataset.auditField="selected";return "[data-audit-field=selected]"})()')
        if not selector:
            raise AssertionError('Missing field: ' + text)
        self.fill(selector, value)
        self.js('delete document.querySelector("[data-audit-field=selected]").dataset.auditField')

    def click(self, text):
        expression = '(() => {const e=[...document.querySelectorAll("button,a")].find(x=>x.textContent.trim()===' + json.dumps(text) + ' && !x.disabled);if(!e)return false;e.click();return true})()'
        self.wait(expression, 'click ' + text)

    def login(self, role):
        self.goto('/login')
        self.js('sessionStorage.clear();localStorage.clear()')
        self.goto('/login')
        self.see('Tên đăng nhập')
        username, key, expected = ROLES[role]
        self.fill('#username', username)
        self.fill('#password', os.environ[key])
        self.click('Đăng nhập')
        self.wait('location.pathname === "/" && !!sessionStorage.length', 'login ' + role)
        self.primary = self.js('sessionStorage.getItem("medical-maintenance.access-token")')
        if not self.primary:
            self.primary = self.js('sessionStorage.getItem(sessionStorage.key(0))')
        status, me = request('/api/auth/me', token=self.primary)
        check('Browser login / JWT / me: ' + role, status == 200 and me['role'] == expected,
              {'username': username, 'role': me['role'], 'departmentId': me.get('departmentId')})
        self.wait('!!document.querySelector("nav")', 'role navigation')
        data.setdefault('navigation', {})[role] = self.js('[...document.querySelectorAll("nav a")].map(e=>({text:e.textContent,path:e.getAttribute("href")}))')
        save()

    def close(self):
        self.js('document.readyState')
        for request_id, record in list(self.requests.items()):
            if record.get('status', 0) >= 400 and not record['path'].endswith('/login'):
                try:
                    body = json.loads(self.cmd('Network.getResponseBody', {'requestId': request_id})['body'])
                    record['error_code'] = body.get('code')
                except (AssertionError, KeyError, ValueError):
                    pass
        save()
        self.ws.close()


def baseline():
    counts = {t: int(sql(f'SELECT count(*) FROM {t};')) for t in TABLES}
    fingerprints = {t: sql(f"SELECT md5(string_agg(to_jsonb(t)::text, '|' ORDER BY id)) FROM {t} t;") for t in TABLES}
    schema = rows("SELECT count(*)::int AS columns FROM information_schema.columns WHERE table_schema='public' AND table_name <> 'flyway_schema_history'")[0]
    schema['tables'] = int(sql("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history';"))
    schema['foreign_keys'] = int(sql("SELECT count(*) FROM information_schema.table_constraints WHERE constraint_schema='public' AND constraint_type='FOREIGN KEY';"))
    schema['migrations'] = rows('SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank')
    return {'counts': counts, 'rows': sum(counts.values()), 'fingerprints': fingerprints, 'schema': schema}


def plan(pid):
    return api(f'/api/plans/{pid}')


def item(pid):
    return api(f'/api/plans/{pid}/items')['content'][0]


def consistency(b, pid, stage, expected=None):
    p, i = plan(pid), item(pid)
    dbp = rows(f'SELECT id,title,status,version,created_at FROM maintenance_plan WHERE id={pid}')[0]
    dbi = rows(f'SELECT id,plan_id,equipment_id,status,version,assigned_provider_id,coverage_id,assignment_route FROM maintenance_plan_item WHERE plan_id={pid}')[0]
    b.see(p['title'])
    b.see(PLAN_LABEL[p['status']])
    b.see(ITEM_LABEL[i['status']])
    b.see('v' + str(i['version']))
    check(stage + ': UI/API/DB state and version',
          p['id'] == dbp['id'] and p['title'] == dbp['title'] and p['status'] == dbp['status'] and p['version'] == dbp['version']
          and i['id'] == dbi['id'] and i['status'] == dbi['status'] and i['version'] == dbi['version']
          and i.get('assignedProviderId') == dbi['assigned_provider_id']
          and (expected is None or i['status'] == expected),
          {'plan': dbp, 'item': dbi, 'uiItemLabel': ITEM_LABEL[i['status']]})
    data.setdefault('states', []).append({'stage': stage, 'plan': p, 'item': i})
    save()
    return p, i


def create(b, key, equipment_code):
    title = data['run_prefix'] + '-' + key.upper()
    b.login('vtyt')
    b.goto('/plans/new')
    b.see('Chọn thêm thiết bị')
    b.label('Tiêu đề', title)
    b.label('Ngày bắt đầu', '2026-09-28')
    b.label('Ngày kết thúc', '2026-10-05')
    b.wait('(() => {const r=[...document.querySelectorAll("tr")].find(x=>x.innerText.includes(' + json.dumps(equipment_code) + '));if(!r)return false;r.querySelector("button").click();return true})()', 'choose equipment')
    b.click('Tạo kế hoạch')
    b.wait('/^\\/plans\\/\\d+$/.test(location.pathname)', 'created plan')
    pid = int(b.js('location.pathname.split("/").at(-1)'))
    data[key] = {'plan_id': pid, 'title': title}
    save()
    consistency(b, pid, key + ' UI create', 'PLANNED')
    b.click('Gửi phê duyệt')
    b.see('Đã gửi kế hoạch để phê duyệt.')
    b.see('Đã gửi duyệt')
    request_id = int(sql(f"SELECT id FROM approval_request WHERE plan_id={pid} AND status='PENDING';"))
    b.login('bgd')
    b.goto(f'/approvals/{request_id}')
    b.see(title)
    b.click('Xác nhận quyết định')
    b.wait('location.pathname === "/approvals"', 'approved')
    check(key + ': approval + decision + audit committed',
          plan(pid)['status'] == 'APPROVED' and int(sql(f"SELECT count(*) FROM approval_request r JOIN approval_action a ON a.request_id=r.id WHERE r.id={request_id} AND r.status='DECIDED' AND a.outcome='APPROVE';")) == 1
          and int(sql(f"SELECT count(*) FROM status_history WHERE plan_id={pid} AND new_state='APPROVED';")) == 1)
    b.login('vtyt')
    b.goto(f'/plans/{pid}')
    b.see('Đã phê duyệt')
    b.click('Chọn coverage')
    classification = 'NOT_FREE' if key == 'external' else 'FREE'
    coverage = int(sql(f"SELECT c.id FROM maintenance_coverage c JOIN equipment e ON e.id=c.equipment_id WHERE e.equipment_code='{equipment_code}' AND c.classification='{classification}';"))
    b.wait(f'(() => {{const e=document.querySelector("input[type=radio][value=\\\"{coverage}\\\"]");if(!e)return false;e.click();return true}})()', 'select coverage')
    b.click('Xác định tuyến')
    b.see('Đã xác định tuyến bảo trì từ hồ sơ coverage được chọn.')
    i = item(pid)
    data[key].update(item_id=i['id'], equipment_id=i['equipmentId'], coverage_id=coverage)
    save()
    consistency(b, pid, key + ' route', 'PENDING_PROPOSAL' if key == 'external' else 'UNDER_CONTRACT')
    return pid


def execution_page(b, pid):
    i = item(pid)
    b.goto(f'/plans/{pid}/items/{i["id"]}/execution')
    b.see('Lịch sử các lần thực hiện')


def start(b, pid, stage):
    b.click('Bắt đầu thực hiện')
    b.see('Đã bắt đầu lần thực hiện mới.')
    consistency(b, pid, stage, 'IN_MAINTENANCE')
    i = item(pid)
    attempts = rows(f'SELECT id,attempt_no,provider_id,started_at,ended_at FROM maintenance_execution WHERE plan_item_id={i["id"]} ORDER BY attempt_no')
    check(stage + ': execution/provider/audit committed', attempts[-1]['provider_id'] == i['assignedProviderId']
          and int(sql(f"SELECT count(*) FROM status_history WHERE plan_item_id={i['id']} AND new_state='IN_MAINTENANCE';")) == len(attempts), attempts)
    return attempts[-1]['id']


def progress_finish_technical(b, pid, result):
    b.label('Nội dung công việc', data['run_prefix'] + '-WORK-' + result)
    b.click('Lưu tiến độ')
    b.see('Đã lưu cập nhật tiến độ.')
    b.click('Kết thúc công việc')
    b.see('Đã kết thúc công việc; chờ nghiệm thu kỹ thuật.')
    if result == 'FAIL':
        b.js('document.querySelectorAll("input[name=technical-result]")[1].click()')
    b.label('Kết luận bắt buộc', data['run_prefix'] + '-TECHNICAL-' + result)
    b.click('Ghi nghiệm thu kỹ thuật')
    b.see('Đã ghi kết quả nghiệm thu kỹ thuật.')
    consistency(b, pid, 'technical ' + result, 'REWORK_REQUIRED' if result == 'FAIL' else 'AWAITING_HANDOVER')
    i = item(pid)
    check('Technical ' + result + ': acceptance and audit committed',
          int(sql(f"SELECT count(*) FROM acceptance_record a JOIN maintenance_execution e ON e.id=a.execution_id WHERE e.plan_item_id={i['id']} AND a.acceptance_type='TECHNICAL_ACCEPTANCE' AND a.result='{result}';")) == 1)


def free(b):
    pid = create(b, 'free', 'DEMO-EQ-001')
    execution_page(b, pid)
    eid = start(b, pid, 'FREE start')
    progress_finish_technical(b, pid, 'PASS')
    b.login('khoa')
    execution_page(b, pid)
    before = b.primary
    b.label('Kết luận bắt buộc', data['run_prefix'] + '-HANDOVER-PASS')
    b.label('Tên đăng nhập VTYT', ROLES['vtyt'][0])
    b.label('Mật khẩu VTYT', os.environ[ROLES['vtyt'][1]])
    b.click('Ghi kết quả bàn giao')
    b.see('Đã ghi kết quả bàn giao.')
    consistency(b, pid, 'FREE handover', 'COMPLETED')
    preserved = b.js('sessionStorage.getItem(sessionStorage.key(0))') == before
    check('Two signer: KHOA primary unchanged; temporary VTYT not stored', preserved
          and b.js('sessionStorage.length===1 && localStorage.length===0 && !document.querySelector("input[type=password]")'))
    signer = rows(f"SELECT a.result,u.role_code AS department_role,v.role_code AS vtyt_role,a.department_confirmed_at,a.vtyt_confirmed_at FROM acceptance_record a JOIN user_account u ON u.id=a.department_confirmed_by_user_id JOIN user_account v ON v.id=a.vtyt_confirmed_by_user_id WHERE a.execution_id={eid} AND a.acceptance_type='HANDOVER_ACCEPTANCE'")
    check('Two signer: both identities and timestamps in DB', len(signer) == 1 and signer[0]['department_role'] == 'KHOA_PHONG' and signer[0]['vtyt_role'] == 'PHONG_VTYT', signer)
    b.login('vtyt')
    b.goto(f'/plans/{pid}/report')
    b.see('Công việc đã thực hiện')
    b.label('Số báo cáo', data['run_prefix'] + '-REPORT')
    b.label('Công việc đã thực hiện', data['run_prefix'] + '-COMPLETED-WORK')
    b.click('Tạo bản nháp')
    b.see('Đã tạo bản nháp báo cáo.')
    draft = api(f'/api/plans/{pid}/report')
    b.goto(f'/plans/{pid}/report')
    b.see('Lưu bản nháp')
    stale = plan(pid)['version']
    changed = api(f'/api/plans/{pid}/report', method='PUT', body={'version': stale, 'workDone': data['run_prefix'] + '-CONCURRENT'})
    before_db = sql(f"SELECT md5(to_jsonb(p)::text || to_jsonb(r)::text) FROM maintenance_plan p JOIN maintenance_report r ON r.plan_id=p.id WHERE p.id={pid};")
    b.label('Công việc đã thực hiện', data['run_prefix'] + '-STALE-REJECTED')
    b.click('Lưu bản nháp')
    b.see('Xung đột phiên bản')
    check('Stale browser write: 409 OPTIMISTIC_LOCK_CONFLICT and no partial/duplicate state',
          before_db == sql(f"SELECT md5(to_jsonb(p)::text || to_jsonb(r)::text) FROM maintenance_plan p JOIN maintenance_report r ON r.plan_id=p.id WHERE p.id={pid};")
          and int(sql(f'SELECT count(*) FROM maintenance_report WHERE plan_id={pid};')) == 1,
          {'staleVersion': stale, 'currentVersion': changed['planVersion']})
    b.click('Tải lại dữ liệu')
    b.wait('document.querySelector("textarea")?.value===' + json.dumps(data['run_prefix'] + '-CONCURRENT'), 'reload current narrative')
    b.click('Hoàn tất báo cáo')
    b.see('Báo cáo đã hoàn tất; kế hoạch chuyển sang Đã báo cáo.')
    report = api(f'/api/plans/{pid}/report')
    db = rows(f'SELECT id,plan_id,status,report_number,work_done,finalized_at FROM maintenance_report WHERE plan_id={pid}')[0]
    check('FREE final: item COMPLETED / plan REPORTED / report FINAL in UI/API/DB',
          plan(pid)['status'] == 'REPORTED' and item(pid)['status'] == 'COMPLETED'
          and report['status'] == db['status'] == 'FINAL' and report['id'] == db['id']
          and report['workDone'] == db['work_done'] and report['completedCount'] == 1
          and report['repairRequiredCount'] == 0 and 'Đã báo cáo' in b.text()
          and 'Báo cáo chính thức' in b.text(), {'report': db, 'planVersion': plan(pid)['version']})
    b.goto('/equipment/1/history')
    b.see(data['free']['title'])
    history = api('/api/equipment/1/maintenance-history')
    campaign = next(c for c in history['campaigns'] if c['planId'] == pid)
    check('FREE UC12 campaign / progress / acceptance / FINAL report',
          campaign['itemStatus'] == 'COMPLETED' and campaign['report']['status'] == 'FINAL'
          and len(campaign['attempts']) == 1 and len(campaign['attempts'][0]['progress']) == 1, campaign)
    data['free']['report'] = report
    save()


def external(b):
    pid = create(b, 'external', 'DEMO-EQ-002')
    b.click('Đề xuất đơn vị')
    b.see('Đơn vị bảo trì đề xuất')
    b.label('Đơn vị bảo trì đề xuất', '2')
    b.label('Lý do đề xuất', data['run_prefix'] + '-EXTERNAL-PROPOSAL')
    b.click('Lưu bản nháp')
    b.see('Đang tiếp tục bản nháp đề xuất')
    b.click('Gửi đề xuất duyệt')
    b.see('Đã gửi đề xuất đơn vị bảo trì để phê duyệt.')
    iid = item(pid)['id']
    rid = int(sql(f"SELECT id FROM approval_request WHERE plan_item_id={iid} AND status='PENDING';"))
    b.login('bgd')
    b.goto(f'/approvals/{rid}')
    provider = rows('SELECT id,name FROM service_provider WHERE id=2')[0]
    b.see(provider['name'])
    review = api(f'/api/approvals/{rid}', 'bgd')
    b.click('Xác nhận quyết định')
    b.wait('location.pathname==="/approvals"', 'external approval')
    b.login('vtyt')
    execution_page(b, pid)
    consistency(b, pid, 'external assigned', 'ASSIGNED_EXTERNAL')
    eid = start(b, pid, 'external start')
    b.see(provider['name'])
    history = api('/api/equipment/2/maintenance-history')
    campaign = next(c for c in history['campaigns'] if c['planId'] == pid)
    db = rows(f'SELECT provider_id,attempt_no FROM maintenance_execution WHERE id={eid}')[0]
    check('External provider identity: proposal / BGD / assignment / execution / UI / DB',
          review['proposedProviderId'] == item(pid)['assignedProviderId'] == db['provider_id'] == campaign['attempts'][0]['actualProviderId'] == 2,
          {'provider': provider, 'executionId': eid, 'attemptNo': db['attempt_no'], 'reviewProviderId': review['proposedProviderId']})
    b.label('Nội dung công việc', data['run_prefix'] + '-RESTART-PERSISTED')
    b.click('Lưu tiến độ')
    b.see('Đã lưu cập nhật tiến độ.')
    data['external']['execution_id'] = eid
    data['restart_before'] = {'plan': plan(pid), 'item': item(pid), 'campaign': next(c for c in api('/api/equipment/2/maintenance-history')['campaigns'] if c['planId'] == pid)}
    save()


def rework(b):
    pid = create(b, 'rework', 'DEMO-EQ-001')
    execution_page(b, pid)
    first = start(b, pid, 'rework first start')
    progress_finish_technical(b, pid, 'FAIL')
    second = start(b, pid, 'rework second start')
    b.see('Lần thực hiện 1')
    b.see('Lần thực hiện 2')
    b.see(data['run_prefix'] + '-TECHNICAL-FAIL')
    history = api('/api/equipment/1/maintenance-history')
    campaign = next(c for c in history['campaigns'] if c['planId'] == pid)
    attempts = rows(f'SELECT id,attempt_no,ended_at FROM maintenance_execution WHERE plan_item_id={item(pid)["id"]} ORDER BY attempt_no')
    check('Rework: old FAIL retained and separate new attempt in UI/API/DB',
          first != second and [a['attempt_no'] for a in attempts] == [1, 2]
          and [a['attemptNo'] for a in campaign['attempts']] == [1, 2]
          and campaign['attempts'][0]['technicalAcceptance']['result'] == 'FAIL'
          and campaign['attempts'][1]['technicalAcceptance'] is None,
          {'attempts': attempts, 'apiCampaign': campaign})


def boundaries(b):
    for role in ROLES:
        b.login(role)
        forbidden = '/approvals' if role != 'bgd' else '/plans/new'
        b.goto(forbidden)
        b.see('Không có quyền xem trang')
        path = '/api/approvals/pending' if role != 'bgd' else '/api/plans'
        method = 'GET' if role != 'bgd' else 'POST'
        status, error = request(path, role, method, {} if method == 'POST' else None)
        check('Role frontend/backend protected action: ' + role, status == 403 and error['code'] == 'ACCESS_DENIED', {'path': path, 'status': status})
    status, error = request('/api/equipment')
    check('Unauthenticated API safe 401', status == 401 and 'message' in error and 'trace' not in error, error)
    b.login('vtyt')
    # Trigger real frontend client normalization using an invalid tab credential.
    b.js('sessionStorage.setItem(sessionStorage.key(0),"invalid-p51-credential")')
    b.goto('/equipment')
    b.see('Phiên đăng nhập đã hết hạn')
    check('401 frontend normalized expiry and credential cleared', b.js('location.pathname==="/login" && sessionStorage.length===0'))
    b.login('khoa')
    b.goto('/equipment/1/history')
    b.see('Máy theo dõi bệnh nhân')
    allowed = api('/api/equipment/1/maintenance-history', 'khoa')
    check('Department allowed UI/API history', allowed['equipmentId'] == 1)
    b.goto('/equipment/4/history')
    b.wait('!!document.querySelector("[role=alert]")', 'scope error')
    status, error = request('/api/equipment/4/maintenance-history', 'khoa')
    check('Department foreign UI/API denied; no foreign identity leakage', status == 403 and 'Máy sốc điện' not in b.text() and 'Máy sốc điện' not in json.dumps(error, ensure_ascii=False), error)
    b.login('vtyt')
    b.goto('/equipment/999999999/history')
    b.wait('!!document.querySelector("[role=alert]")', '404 UI')
    status, error = request('/api/equipment/999999999/maintenance-history', 'vtyt')
    check('404 backend ErrorResponse normalized to readable UI', status == 404 and len(b.js('document.querySelector("[role=alert]").innerText')) > 10 and 'Exception' not in b.text(), error)
    # Representative seed equipment + plan + history SQL/API/UI comparisons.
    b.goto('/equipment/1/history')
    b.see('Máy theo dõi bệnh nhân')
    eq = api('/api/equipment?size=100')['content']
    db = rows('SELECT id,equipment_code,name,department_id FROM equipment WHERE id=1')[0]
    row = next(e for e in eq if e['id'] == 1)
    check('Seed DB/API/UI equipment ID/code/name/department', row['equipmentCode'] == db['equipment_code'] and row['name'] == db['name'] and row['departmentId'] == db['department_id'], db)
    campaign = next(c for c in api('/api/equipment/1/maintenance-history')['campaigns'] if c['planId'] not in [data[k]['plan_id'] for k in ['free', 'external', 'rework']])
    dbp = rows(f'SELECT id,title,status,version FROM maintenance_plan WHERE id={campaign["planId"]}')[0]
    b.goto(f'/plans/{dbp["id"]}')
    consistency(b, dbp['id'], 'seed plan read')
    b.goto('/equipment/1/history')
    b.see(dbp['title'])
    check('Seed DB/API/UI campaign status and plan title', campaign['planTitle'] == dbp['title'] and campaign['planStatus'] == dbp['status'], {'planId': dbp['id'], 'title': dbp['title'], 'status': dbp['status']})
    # Check request stability after the final settled page.
    b.js('new Promise(r=>setTimeout(r,700))')
    before = len(data['network'])
    b.js('new Promise(r=>setTimeout(r,2500))')
    check('Settled frontend makes no infinite fetch loop', len(data['network']) == before, {'requestsDuring2500ms': len(data['network']) - before})


def restart(b):
    pid = data['external']['plan_id']
    b.login('vtyt')
    execution_page(b, pid)
    b.see(data['run_prefix'] + '-RESTART-PERSISTED')
    consistency(b, pid, 'after frontend/backend restart', 'IN_MAINTENANCE')
    after = {'plan': plan(pid), 'item': item(pid), 'campaign': next(c for c in api('/api/equipment/2/maintenance-history')['campaigns'] if c['planId'] == pid)}
    check('Restart: persisted incomplete plan/item/attempt/progress reconstructed exactly', after == data['restart_before'], {'planId': pid, 'itemId': item(pid)['id'], 'executionId': data['external']['execution_id']})



def contracts(b):
    from datetime import datetime
    def instant(value):
        return datetime.fromisoformat(value.replace('Z', '+00:00')) if value else None
    for key in ['free', 'external', 'rework']:
        pid = data[key]['plan_id']
        p = plan(pid)
        dbp = rows(f'SELECT created_at FROM maintenance_plan WHERE id={pid}')[0]
        check(key + ': API/DB plan creation timestamp', instant(p['createdAt']) == instant(dbp['created_at']))
        campaign = next(c for c in api(f'/api/equipment/{data[key]["equipment_id"]}/maintenance-history')['campaigns'] if c['planId'] == pid)
        for a in campaign['attempts']:
            dbe = rows(f'SELECT provider_id,attempt_no,started_at,ended_at FROM maintenance_execution WHERE id={a["executionId"]}')[0]
            check(key + f': execution {a["executionId"]} ID/provider/attempt/timestamps',
                a['actualProviderId'] == dbe['provider_id'] and a['attemptNo'] == dbe['attempt_no']
                and instant(a['startedAt']) == instant(dbe['started_at']) and instant(a['endedAt']) == instant(dbe['ended_at']))
            for log in a['progress']:
                db = rows(f'SELECT event_at,work_note FROM maintenance_progress_log WHERE id={log["id"]}')[0]
                check(key + f': progress {log["id"]} content/timestamp', log['workNote'] == db['work_note'] and instant(log['eventAt']) == instant(db['event_at']))
            for field in ['technicalAcceptance', 'handoverAcceptance']:
                record = a[field]
                if record:
                    db = rows(f'SELECT result,observed_at,conclusion FROM acceptance_record WHERE id={record["id"]}')[0]
                    check(key + f': {field} ID/result/conclusion/timestamp', record['result'] == db['result'] and record['conclusion'] == db['conclusion'] and instant(record['observedAt']) == instant(db['observed_at']))
    pid = data['free']['plan_id']
    report = api(f'/api/plans/{pid}/report')
    db = rows(f"SELECT r.finalized_at,h.action_timestamp FROM maintenance_report r JOIN status_history h ON h.plan_id=r.plan_id AND h.action='FINALIZE_REPORT' WHERE r.plan_id={pid}")[0]
    check('Report finalization and plan audit share persisted timestamp', instant(report['finalizedAt']) == instant(db['finalized_at']) == instant(db['action_timestamp']))
    check('Provider API ID/name agrees with DB', next(p for p in api('/api/providers') if p['id']==2)['name'] == rows('SELECT name FROM service_provider WHERE id=2')[0]['name'])
    before = baseline()
    status, error = request(f'/api/plans/{pid}/report', 'vtyt', 'PUT', {'version': 5, 'workDone': data['run_prefix'] + '-REJECTED'})
    check('Explicit stale API error code and full DB rollback', status == 409 and error['code'] == 'OPTIMISTIC_LOCK_CONFLICT' and before == baseline(), error)
    b.login('vtyt')
    log_start = len(data.get('browser_log', []))
    b.goto(f'/plans/{pid}/report')
    b.see('Báo cáo chính thức')
    b.goto('/equipment/1/history')
    b.see(data['free']['title'])
    b.js('new Promise(r=>setTimeout(r,2500))')
    logs = data.get('browser_log', [])[log_start:]
    data['post_fix_browser_log'] = logs
    check('After favicon fix: clean real report/history console and network', not logs and not data.get('uncaught_errors') and not data.get('console_warnings') and not data.get('cors_errors') and not data.get('secret_logs'))
    check('Two signer network uses KHOA Authorization and distinct VTYT secondary header', any(r.get('primary_matches_khoa_session') and r.get('secondary_distinct') and r.get('status')==201 for r in data['network'] if r['method']=='POST' and r['path'].endswith('/handover')))


def cleanup():
    ids = [data[k]['plan_id'] for k in ['free', 'external', 'rework']]
    ps = ','.join(map(str, ids))
    guard = rows(f"SELECT id,title FROM maintenance_plan WHERE title LIKE 'SMOKE-P51-%' ORDER BY id")
    assert sorted(r['id'] for r in guard) == sorted(ids)
    assert all(r['title'].startswith(data['run_prefix'] + '-') for r in guard)
    query = f"""BEGIN;
    DO $$ BEGIN IF (SELECT count(*) FROM maintenance_plan WHERE id IN ({ps}) AND title LIKE '{data['run_prefix']}-%' AND created_by_user_id=(SELECT id FROM user_account WHERE username='demo_vtyt')) <> 3 THEN RAISE EXCEPTION 'Smoke ownership mismatch'; END IF; END $$;
    DELETE FROM maintenance_report WHERE plan_id IN ({ps});
    DELETE FROM acceptance_record WHERE execution_id IN (SELECT e.id FROM maintenance_execution e JOIN maintenance_plan_item i ON i.id=e.plan_item_id WHERE i.plan_id IN ({ps}));
    DELETE FROM maintenance_progress_log WHERE execution_id IN (SELECT e.id FROM maintenance_execution e JOIN maintenance_plan_item i ON i.id=e.plan_item_id WHERE i.plan_id IN ({ps}));
    DELETE FROM maintenance_execution WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id IN ({ps}));
    DELETE FROM approval_action WHERE request_id IN (SELECT id FROM approval_request WHERE plan_id IN ({ps}) OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id IN ({ps})));
    DELETE FROM approval_request WHERE plan_id IN ({ps}) OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id IN ({ps}));
    DELETE FROM status_history WHERE plan_id IN ({ps}) OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id IN ({ps}));
    DELETE FROM maintenance_plan_item WHERE plan_id IN ({ps});
    DELETE FROM maintenance_plan WHERE id IN ({ps});
    COMMIT;"""
    sql(query)
    after = baseline()
    data['cleanup'] = after
    check('Cleanup: 603 rows, exact canonical fingerprints, schema and migrations restored', after == data['baseline'], after)
    check('Cleanup: zero SMOKE-P51 plans', int(sql("SELECT count(*) FROM maintenance_plan WHERE title LIKE 'SMOKE-P51-%';")) == 0)



def final(b):
    import xml.etree.ElementTree as ET
    suites=[]
    for path in sorted((ROOT/'backend/target/surefire-reports').glob('TEST-*.xml')):
        suite=ET.parse(path).getroot()
        suites.append({k:suite.attrib[k] for k in ['name','tests','failures','errors','skipped','time']})
    totals={k:sum(int(s[k]) for s in suites) for k in ['tests','failures','errors','skipped']}
    data['backend_regression']={'suites':suites,'totals':totals,'command':'env -u DEBUG mvn -f backend/pom.xml test','result':'PASS'}
    check('Full backend regression: 87 tests / zero failure / zero error / zero skip', totals=={'tests':87,'failures':0,'errors':0,'skipped':0})
    after=baseline()
    data['post_regression_baseline']=after
    check('After full regression: canonical row fingerprints/schema/migrations unchanged', after==data['baseline'])
    columns=rows("SELECT table_name,column_name FROM information_schema.columns WHERE table_schema='public' AND data_type IN ('text','character varying') AND table_name <> 'flyway_schema_history' AND column_name <> 'password_hash'")
    smoke_counts={}
    for t in TABLES:
        names=[c['column_name'] for c in columns if c['table_name']==t]
        where=' OR '.join(c + " LIKE 'SMOKE-P51-%'" for c in names)
        smoke_counts[t]=int(sql(f'SELECT count(*) FROM "{t}" WHERE {where};')) if where else 0
    data['smoke_counts']=smoke_counts
    check('All 14 business tables contain zero SMOKE-P51 text records', sum(smoke_counts.values())==0, smoke_counts)
    changed=[name for name,digest in data['source_hashes'].items() if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=digest]
    check('Frozen backend/frontend source, schema migrations and seed files unchanged', not changed, {'changed_files':changed})
    check('Only product edit is one empty favicon declaration',
        hashlib.sha256((ROOT/'frontend/index.html').read_text().replace('    <link rel="icon" href="data:," />\n','').encode()).hexdigest()==data['frontend_index_before_sha256'])
    b.login('vtyt')
    start=len(data.get('browser_log',[]))
    b.goto('/equipment')
    b.see('DEMO-EQ-001')
    b.goto('/equipment/1/history')
    b.see('Máy theo dõi bệnh nhân')
    b.js('new Promise(r=>setTimeout(r,2000))')
    check('Restored canonical seed loads in browser after cleanup/regression', data['run_prefix'] not in b.text())
    check('Final canonical browse: zero unexpected console/network errors', len(data.get('browser_log',[]))==start and not data.get('uncaught_errors') and not data.get('console_warnings') and not data.get('cors_errors') and not data.get('secret_logs'))
    data['final_health']=request('/actuator/health')[1]
    data['database_connection']=rows('SELECT current_database() AS database,current_user AS username,version() AS server')[0]
    check('Final live backend UP and PostgreSQL connection', data['final_health']['status']=='UP' and data['database_connection']['database']=='medical_maintenance_backend_dev')
    secrets=[os.environ[v[1]] for v in ROLES.values()]+list(tokens.values())+[b.primary,os.environ['DB_PASSWORD'],os.environ['JWT_SECRET']]
    contents=json.dumps(data,ensure_ascii=False)
    check('Sanitized evidence contains no password/JWT/signing-key values', all(secret not in contents for secret in secrets if secret))
    data['final_status']='PASS'
    save()


def main():
    stage = sys.argv[1]
    if stage == 'baseline':
        data['run_prefix'] = 'SMOKE-P51-' + time.strftime('%Y%m%d%H%M%S')
        data['baseline'] = baseline()
        data['source_hashes'] = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
            for base in ['backend/src', 'frontend/src', 'database/migrations', 'database/seeds']
            for p in sorted((ROOT / base).rglob('*')) if p.is_file()}
        data['runtime_versions'] = {'java': subprocess.check_output(['java', '-version'], stderr=subprocess.STDOUT, text=True).splitlines()[0],
            'node': subprocess.check_output(['node', '--version'], text=True).strip(),
            'postgres': sql('SHOW server_version;'),
            'chrome': subprocess.check_output(['google-chrome', '--version'], text=True).strip()}
        check('Canonical baseline 603 / 14 / 117 / 31 / V001-V006', data['baseline']['rows'] == 603
            and data['baseline']['schema']['tables'] == 14 and data['baseline']['schema']['columns'] == 117
            and data['baseline']['schema']['foreign_keys'] == 31
            and [r['version'] for r in data['baseline']['schema']['migrations']] == ['001','002','003','004','005','006']
            and all(r['success'] for r in data['baseline']['schema']['migrations']))
        check('Backend health UP', request('/actuator/health')[1]['status'] == 'UP')
        return
    if stage == 'cleanup':
        cleanup()
        return
    b = Browser()
    try:
        globals()[stage](b)
    finally:
        b.close()


if __name__ == '__main__':
    main()
