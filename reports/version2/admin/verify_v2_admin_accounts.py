#!/usr/bin/env python3
"""Local Chrome/API/PostgreSQL account audit; source ignored env files first.
All passwords/tokens remain in memory. Evidence stores safe metadata/booleans only.
Uses the existing V1 baseline and CDP DOM helpers, without writing V1 evidence.
"""
import base64
import datetime
import importlib.util
import json
import os
from pathlib import Path
import secrets
import sys

sys.dont_write_bytecode = True
import time
import urllib.request
import urllib.parse
import websocket
import bcrypt

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('v1_helpers', ROOT / 'reports/version1/integration/verify_phase_5_1.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
m.STATE = OUT / 'v2_admin_account_management_evidence.json'
m.data = {'checks': [], 'network': [], 'baseline': m.baseline(), 'chrome': 'Chrome 154 (real headless Chrome, CDP)', 'started_at': datetime.datetime.now().astimezone().isoformat()}
m.tokens = {}
assert m.data['baseline']['rows'] == 603
prefix = 'SMOKE-V2-ADMIN-' + datetime.datetime.now().strftime('%Y%m%d%H%M%S') + '-' + secrets.token_hex(3)
m.data['run_prefix'] = prefix
credentials = {}
private_values = [os.environ.get(key, '') for key in ['DB_PASSWORD','JWT_SECRET','DEMO_ADMIN_PASSWORD','DEMO_VTYT_PASSWORD','DEMO_BGD_PASSWORD','DEMO_KHOA_PASSWORD']]
created = {}

def check(name, condition, evidence=None):
    m.check(name, condition, evidence)

def password():
    # Deliberately simple direct ADMIN choice, exercising the absence of complexity rules.
    value = ''.join(secrets.choice('0123456789') for _ in range(6))
    private_values.append(value)
    return value

def literal(value):
    return "'" + str(value).replace("'", "''") + "'"

class Chrome(m.Browser):
    def __init__(self, target):
        info = next(row for row in json.load(urllib.request.urlopen('http://localhost:9333/json/list')) if row['id'] == target)
        self.ws = websocket.create_connection(info['webSocketDebuggerUrl'], origin='http://localhost:9333', timeout=25)
        self.seq = 0
        self.requests = {}
        self.events = []
        self.primary = None
        for domain in ['Page','Runtime','Network','Log']:
            self.cmd(domain + '.enable')
        self.cmd('Page.addScriptToEvaluateOnNewDocument', {'source':'window.confirm=()=>true'})

    def event(self, event):
        # Never delegate network events to the V1 helper: account POST bodies contain passwords.
        method, params = event.get('method'), event.get('params', {})
        if method == 'Network.requestWillBeSent':
            request = params['request']
            path = urllib.parse.urlsplit(request['url']).path
            if path.startswith('/api/'):
                record = {'method': request['method'], 'path': path}
                self.requests[params['requestId']] = record
                m.data['network'].append(record)
        elif method == 'Network.responseReceived' and params['requestId'] in self.requests:
            self.requests[params['requestId']]['status'] = int(params['response']['status'])
        elif method == 'Runtime.exceptionThrown':
            m.data.setdefault('uncaught_errors', []).append('Uncaught JavaScript exception')
        elif method == 'Network.loadingFailed' and params.get('corsErrorStatus'):
            m.data.setdefault('cors_errors', []).append('CORS error')
        elif method == 'Runtime.consoleAPICalled':
            message = ' '.join(str(arg.get('value',arg.get('description',''))) for arg in params.get('args',[]))
            if any(value and value in message for value in private_values):
                m.data.setdefault('secret_logs', []).append('Sensitive value detected; contents withheld')
            if params.get('type') in ['warning','error']:
                # Keep only classification, never arbitrary console text.
                m.data.setdefault('console_warnings', []).append(params.get('type'))

    def login_user(self, username, raw, role, allowed=True):
        self.goto('/login'); self.js('sessionStorage.clear();localStorage.clear()'); self.goto('/login')
        self.see('Tên đăng nhập'); self.fill('#username',username); self.fill('#password',raw); self.click('Đăng nhập')
        if not allowed:
            self.see('Tên đăng nhập hoặc mật khẩu không đúng.')
            check('Real browser login rejected', self.js('location.pathname==="/login" && !sessionStorage.length'))
            return None
        self.wait('location.pathname==="/" && !!document.querySelector("nav")','real login')
        token = self.js('sessionStorage.getItem("medical-maintenance.access-token")')
        private_values.append(token)
        status, me = m.request('/api/auth/me', token=token)
        check('Real login and /me: '+role, status==200 and me['username']==username and me['role']==role, me)
        return token


def browser_targets():
    browser_info = json.load(urllib.request.urlopen('http://localhost:9333/json/version'))
    ws = websocket.create_connection(browser_info['webSocketDebuggerUrl'], origin='http://localhost:9333', timeout=25)
    seq = 0
    def cmd(method, params=None):
        nonlocal seq
        seq += 1; ws.send(json.dumps({'id':seq,'method':method,'params':params or {}}))
        while True:
            result = json.loads(ws.recv())
            if result.get('id')==seq:
                assert 'error' not in result, 'Browser context command failed'
                return result.get('result',{})
    targets=[]
    for _ in range(2):
        context = cmd('Target.createBrowserContext')['browserContextId']
        targets.append(cmd('Target.createTarget',{'url':m.UI+'/login','browserContextId':context})['targetId'])
    ws.close()
    return [Chrome(target) for target in targets]


def account_api(account_id, admin_token):
    status, result = m.request('/api/admin/accounts/'+str(account_id),token=admin_token)
    assert status==200
    allowed={'id','username','role','departmentId','departmentCode','departmentName','active'}
    check('Safe account detail DTO', set(result)==allowed, result)
    return result


def create_ui(browser, admin_token, role, department=None):
    username=prefix+'-'+role
    raw=password()
    browser.goto('/admin/accounts/new'); browser.see('Thông tin tài khoản')
    browser.label('Tên đăng nhập',username); browser.label('Mật khẩu',raw); browser.label('Vai trò',role)
    check('Create password masked', browser.js('document.querySelector("input[type=password]")!==null'))
    if role=='KHOA_PHONG':
        browser.click('Tạo tài khoản'); browser.see('Vui lòng chọn Khoa/Phòng cho tài khoản Khoa/Phòng.')
        check('KHOA missing department blocked by real form', m.rows('SELECT id FROM user_account WHERE username='+literal(username))==[])
    if department is not None: browser.label('Khoa/Phòng',str(department))
    browser.click('Tạo tài khoản')
    browser.wait('!!location.pathname.match(/^\\/admin\\/accounts\\/\\d+$/)','created account detail')
    account_id=int(browser.js('location.pathname.split("/").at(-1)'))
    created[account_id]=username; credentials[role]=(username,raw)
    browser.see('Đã tạo tài khoản.')
    account=account_api(account_id,admin_token)
    check('UI create '+role, account['role']==role and account['departmentId']==department and account['active'])
    check('Password cleared after create', browser.js('document.querySelectorAll("input[type=password]").length===0'))
    hash_value=m.sql('SELECT password_hash FROM user_account WHERE id='+str(account_id))
    check('SQL BCrypt and plaintext absent: '+role, hash_value.startswith(('$2a$12$','$2b$12$','$2y$12$')) and hash_value!=raw and bcrypt.checkpw(raw.encode(),hash_value.encode()), {'password_is_bcrypt':True,'plaintext_not_stored':True})
    browser.cmd('Page.reload'); browser.see(username)
    return account_id


def cleanup():
    rows=m.rows('SELECT id,username FROM user_account WHERE username LIKE '+literal(prefix+'%')+' ORDER BY id')
    # Unique run prefix was absent before this run; verify exact IDs and names before deleting.
    for row in rows:
        assert row['username'].startswith(prefix+'-')
        if row['id'] not in created:
            assert row['username'] in [prefix+'-'+role for role in ['KHOA_PHONG','PHONG_VTYT','BAN_GIAM_DOC','ADMIN']]
            created[row['id']]=row['username']
        assert created[row['id']]==row['username']
    foreign_keys=m.rows("SELECT c.conrelid::regclass::text AS table_name,a.attname AS column_name FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1] WHERE c.contype='f' AND c.confrelid='user_account'::regclass")
    statements=[]
    for row in rows:
        for fk in foreign_keys:
            assert int(m.sql('SELECT count(*) FROM '+fk['table_name']+' WHERE '+fk['column_name']+'='+str(row['id'])))==0,'Smoke account acquired business references; do not delete'
        statements.append('DELETE FROM user_account WHERE id='+str(row['id'])+' AND username='+literal(row['username'])+';')
    if statements: m.sql('BEGIN;\n'+'\n'.join(statements)+'\nCOMMIT;')
    after=m.baseline()
    m.data['cleanup']={'deleted_accounts':len(rows),'exact_ids':list(created),'canonical_restored':after==m.data['baseline'],'baseline':after}
    check('Guarded cleanup restores all canonical fingerprints/schema/migrations', after==m.data['baseline'], {'deleted_accounts':len(rows),'canonical_rows':after['rows']})


def main():
    admin_browser,user_browser=browser_targets()
    a,u=admin_browser,user_browser
    admin_token=a.login_user('demo_admin',os.environ['DEMO_ADMIN_PASSWORD'],'ADMIN')
    check('ADMIN navigation is account-only', a.js('[...document.querySelectorAll("nav a")].map(x=>x.textContent)').__eq__(['Tổng quan','Quản lý tài khoản']))
    a.click('Quản lý tài khoản'); a.see('Danh sách tài khoản')
    a.wait('document.querySelectorAll("tbody tr").length===10','first account page')
    first=a.js('[...document.querySelectorAll("tbody tr td:first-child")].map(x=>x.innerText)')
    a.click('Sau'); a.wait('document.body.innerText.includes("Trang 2/")','second page')
    second=a.js('[...document.querySelectorAll("tbody tr td:first-child")].map(x=>x.innerText)')
    check('Real UI pagination', bool(second) and not(set(first)&set(second)))
    ids={}
    for role,dep in [('KHOA_PHONG',1),('PHONG_VTYT',2),('BAN_GIAM_DOC',None),('ADMIN',2)]:
        ids[role]=create_ui(a,admin_token,role,dep)
    username,old=credentials['KHOA_PHONG']; khoa_id=ids['KHOA_PHONG']
    a.goto('/admin/accounts'); a.see('Danh sách tài khoản')
    a.label('Tìm theo tên đăng nhập',prefix.lower()); a.click('Tìm kiếm')
    a.wait('document.querySelectorAll("tbody tr").length===4','username search')
    check('Real username search case-insensitive', a.js('[...document.querySelectorAll("tbody tr")].every(x=>x.innerText.includes('+json.dumps(prefix)+'))'))
    a.label('Vai trò','KHOA_PHONG'); a.label('Khoa/Phòng','1'); a.label('Trạng thái','true')
    a.wait('document.querySelectorAll("tbody tr").length===1','combined filters')
    check('Role / department / status filters combined', username in a.text())
    a.label('Trạng thái','false'); a.see('Không có tài khoản phù hợp với bộ lọc.')
    a.click('Xóa bộ lọc'); a.wait('document.querySelectorAll("tbody tr").length===10','clear filters')
    check('Empty state / clear filters', a.js('document.querySelectorAll("tbody tr").length===10'))
    for width in [1366,1024,760]:
        a.cmd('Emulation.setDeviceMetricsOverride',{'width':width,'height':900,'deviceScaleFactor':1,'mobile':False})
        check('Account page no document overflow width '+str(width), a.js('document.documentElement.scrollWidth<=innerWidth'))
    a.cmd('Emulation.setDeviceMetricsOverride',{'width':1366,'height':900,'deviceScaleFactor':1,'mobile':False})
    (OUT/'screens').mkdir(exist_ok=True)
    (OUT/'screens/v2_admin_account_list.png').write_bytes(base64.b64decode(a.cmd('Page.captureScreenshot',{'format':'png'})['data']))
    khoa_token=u.login_user(username,old,'KHOA_PHONG')
    check('Created KHOA navigation', u.js('[...document.querySelectorAll("nav a")].map(x=>x.textContent)')==['Tổng quan','Thiết bị & lịch sử','Bàn giao'])
    status,me=m.request('/api/auth/me',token=khoa_token)
    check('Created KHOA current department',status==200 and me['departmentId']==1)
    u.goto('/equipment/1/history'); u.see('DEMO-EQ-001')
    check('Created KHOA allowed history real UI', 'DEMO-EQ-001' in u.text())
    u.goto('/equipment/4/history'); u.see('Bạn không có quyền thực hiện thao tác này.')
    status,_=m.request('/api/equipment/4/maintenance-history',token=khoa_token)
    check('Created KHOA foreign department 403 without details',status==403 and 'Máy sốc điện' not in u.text())
    u.goto('/admin/accounts'); u.see('Không có quyền xem trang')
    status,_=m.request('/api/admin/accounts',token=khoa_token)
    check('Created KHOA ADMIN access blocked',status==403)
    u.goto('/equipment/1/history'); u.see('DEMO-EQ-001')
    a.goto('/admin/accounts/'+str(khoa_id)); a.see(username); a.click('Vô hiệu hóa'); a.see('Đã vô hiệu hóa tài khoản.')
    check('UI deactivate persisted', not account_api(khoa_id,admin_token)['active'])
    u.click('Tải lại'); u.see('Tên đăng nhập')
    check('Existing browser session rejected on next protected request',u.js('location.pathname==="/login" && sessionStorage.length===0'))
    status,_=m.request('/api/auth/me',token=khoa_token)
    check('Existing JWT rejected by DB reload',status==401)
    u.login_user(username,old,'KHOA_PHONG',False)
    a.click('Kích hoạt'); a.see('Đã kích hoạt tài khoản.')
    khoa_token=u.login_user(username,old,'KHOA_PHONG')
    check('Reactivation restores real login',account_api(khoa_id,admin_token)['active'])
    new=password()
    a.click('Đặt lại mật khẩu'); a.label('Mật khẩu mới',new); a.label('Xác nhận mật khẩu mới',password()); a.click('Lưu mật khẩu mới')
    a.see('Xác nhận mật khẩu mới không khớp.')
    check('Real password confirmation mismatch blocked',bcrypt.checkpw(old.encode(),m.sql('SELECT password_hash FROM user_account WHERE id='+str(khoa_id)).encode()))
    a.label('Xác nhận mật khẩu mới',new); a.click('Lưu mật khẩu mới'); a.see('Đã đặt lại mật khẩu.')
    check('Password fields cleared after reset',a.js('document.querySelectorAll("input[type=password]").length===0'))
    status,_=m.request('/api/auth/me',token=khoa_token)
    check('Reset preserves existing V1 JWT lifetime behavior',status==200)
    u.login_user(username,old,'KHOA_PHONG',False); khoa_token=u.login_user(username,new,'KHOA_PHONG')
    check('Reset BCrypt matches only new password',bcrypt.checkpw(new.encode(),m.sql('SELECT password_hash FROM user_account WHERE id='+str(khoa_id)).encode()) and not bcrypt.checkpw(old.encode(),m.sql('SELECT password_hash FROM user_account WHERE id='+str(khoa_id)).encode()), {'old_password_rejected':True,'new_password_accepted':True})
    a.click('Chỉnh sửa'); a.see('Thông tin tài khoản'); a.label('Khoa/Phòng','8'); a.click('Lưu chỉnh sửa'); a.see('Đã lưu vai trò và khoa/phòng.')
    status,me=m.request('/api/auth/me',token=khoa_token)
    check('Department edit on existing token',status==200 and me['departmentId']==8)
    status,_=m.request('/api/equipment/1/maintenance-history',token=khoa_token)
    check('Old department history rejected after edit',status==403)
    u.goto('/equipment/38/history'); u.see('DEMO-EQ-038')
    check('New department history allowed in real UI','DEMO-EQ-038' in u.text())
    a.click('Chỉnh sửa'); a.see('Thông tin tài khoản'); a.label('Vai trò','BAN_GIAM_DOC'); a.label('Khoa/Phòng',''); a.click('Lưu chỉnh sửa'); a.see('Đã lưu vai trò và khoa/phòng.')
    status,me=m.request('/api/auth/me',token=khoa_token)
    check('Role edit affects current request principal',status==200 and me['role']=='BAN_GIAM_DOC' and me['departmentId'] is None)
    u.goto('/'); u.see('Ban Giám đốc')
    check('Role navigation refreshed from DB', 'Phê duyệt' in u.text() and 'Bàn giao' not in u.js('document.querySelector("nav").innerText'))
    for role in ['PHONG_VTYT','BAN_GIAM_DOC','ADMIN']:
        name,raw=credentials[role]; token=u.login_user(name,raw,role)
        expected={'PHONG_VTYT':['Kế hoạch bảo trì','Thực hiện bảo trì','Báo cáo','Thiết bị & lịch sử'], 'BAN_GIAM_DOC':['Báo cáo','Thiết bị & lịch sử','Phê duyệt'], 'ADMIN':['Quản lý tài khoản']}[role]
        check('Created '+role+' correct navigation',u.js('[...document.querySelectorAll("nav a")].map(x=>x.textContent)')==['Tổng quan']+expected)
        if role!='ADMIN':
            u.goto('/admin/accounts'); u.see('Không có quyền xem trang')
            check('Created '+role+' ADMIN API forbidden',m.request('/api/admin/accounts',token=token)[0]==403)
        else:
            u.click('Quản lý tài khoản'); u.see('Danh sách tài khoản')
            u.goto('/admin/accounts/'+str(ids['ADMIN'])); u.see(name); u.click('Vô hiệu hóa'); u.see('Bạn không thể vô hiệu hóa')
            check('Own ADMIN deactivation business error preserves account',account_api(ids['ADMIN'],admin_token)['active'])
            check('ADMIN maintenance command still 403',m.request('/api/plans',method='POST',body={},token=token)[0]==403)
    a.goto('/admin/accounts/new'); a.see('Thông tin tài khoản'); a.label('Tên đăng nhập',username); a.label('Mật khẩu',password()); a.click('Tạo tài khoản'); a.see('Tên đăng nhập đã tồn tại.')
    check('Duplicate rejected in real UI without extra row',len(m.rows('SELECT id FROM user_account WHERE username='+literal(username)))==1)
    a.goto('/admin/accounts/'+str(khoa_id)); a.see(username)
    (OUT/'screens/v2_admin_account_detail.png').write_bytes(base64.b64decode(a.cmd('Page.captureScreenshot',{'format':'png'})['data']))
    check('No uncaught JS/CORS/credential console errors', not m.data.get('uncaught_errors') and not m.data.get('cors_errors') and not m.data.get('secret_logs') and not m.data.get('console_warnings'))
    m.data['created_accounts']=[{'id':id,'username':name} for id,name in created.items()]
    m.save()
    a.ws.close();u.ws.close()

try:
    main()
    m.data['browser_result']='PASS'
finally:
    cleanup()
    m.data['finished_at']=datetime.datetime.now().astimezone().isoformat()
    m.save()
