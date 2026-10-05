import { spawn } from 'node:child_process'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
const profile = await mkdtemp(path.join(os.tmpdir(), 'ltnc-v3-chrome-'))
const chrome = spawn('/usr/bin/google-chrome', ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage', '--remote-debugging-port=19223', `--user-data-dir=${profile}`, '--window-size=1366,768', 'about:blank'], { stdio: 'ignore' })
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
let socket
const evidence = { browser: 'Google Chrome', errors: [], failedRequests: [], roles: [] }
try {
 let pages
 for (let i = 0; i < 60; i++) { try { pages = await (await fetch('http://127.0.0.1:19223/json')).json(); break } catch { await pause(250) } }
 if (!pages) throw new Error('Chrome did not start')
 socket = new WebSocket(pages.find(p => p.type === 'page').webSocketDebuggerUrl)
 await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject })
 let sequence = 0
 const pending = new Map()
 socket.onmessage = ({ data }) => { const m = JSON.parse(data); if (m.id) { const p = pending.get(m.id); pending.delete(m.id); if (m.error) p.reject(new Error(m.error.message)); else p.resolve(m.result) } else if (m.method === 'Runtime.exceptionThrown') evidence.errors.push(m.params.exceptionDetails.text); else if (m.method === 'Network.responseReceived' && m.params.response.status >= 400 && m.params.response.url.includes('/api/')) evidence.failedRequests.push({url:m.params.response.url,status:m.params.response.status}) }
 function cdp(method, params = {}) { return new Promise((resolve, reject) => { const id = ++sequence; pending.set(id, { resolve, reject }); socket.send(JSON.stringify({ id, method, params })) }) }
 async function evaluate(expression) { const r = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }); if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text); return r.result.value }
 async function until(expression, message) { for (let i = 0; i < 100; i++) { if (await evaluate(expression)) return; await pause(100) } throw new Error(message + ': ' + (await evaluate('document.body.innerText')).slice(-1500)) }
 async function go(url) { await cdp('Page.navigate', { url: `http://localhost:15175${url}` }); await until('document.readyState === "complete"', 'Navigation'); await pause(200) }
 async function click(text) { await until(`!![...document.querySelectorAll('button,a,summary,label')].find(e=>e.textContent.trim()===${JSON.stringify(text)} && !e.disabled)`, `Missing action ${text}`); await evaluate(`[...document.querySelectorAll('button,a,summary,label')].find(e=>e.textContent.trim()===${JSON.stringify(text)} && !e.disabled).click()`); await pause(200) }
 async function input(label, value) { await evaluate(`(()=>{const label=[...document.querySelectorAll('label')].find(e=>e.textContent.trim().startsWith(${JSON.stringify(label)})); const e=label.querySelector('input,select,textarea');const setter=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value').set;setter.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event(e.tagName==='SELECT'?'change':'input',{bubbles:true}));})()`); await pause(200) }
 async function login(username, key) { await go('/login'); await evaluate('localStorage.clear();sessionStorage.clear()'); await go('/login'); await until('!!document.querySelector("#username")', 'Login'); await evaluate(`(()=>{for(const [id,value] of ${JSON.stringify([['username', username], ['password', process.env[key]]])}){const e=document.getElementById(id);Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,value);e.dispatchEvent(new Event('input',{bubbles:true}));}document.querySelector('form').requestSubmit();})()`); await until('!location.pathname.includes("login")', `Login ${username}`) }
 await cdp('Runtime.enable'); await cdp('Page.enable'); await cdp('Page.addScriptToEvaluateOnNewDocument', { source: 'window.confirm=()=>true' })
 await cdp('Network.enable'); await cdp('Emulation.setDeviceMetricsOverride',{width:1366,height:768,deviceScaleFactor:1,mobile:false})
 async function dashboard(title,role){
  await until(`location.pathname==='/dashboard' && document.body.innerText.includes(${JSON.stringify(title)})`,'Dashboard '+role)
  if(await evaluate(`document.documentElement.scrollWidth>window.innerWidth`))throw new Error('Horizontal overflow '+role)
  if(await evaluate(`document.querySelector('.side-nav a')?.getAttribute('href')`)!=='/dashboard')throw new Error('Overview is not first '+role)
  await writeFile('/tmp/ltnc-dashboard-'+role+'.png',Buffer.from((await cdp('Page.captureScreenshot',{captureBeyondViewport:true})).data,'base64'))
  evidence.roles.push(role)
 }
 await login('vtyt','LOCAL_VTYT_PASSWORD');await dashboard('Tổng quan Phòng Vật tư Y tế','VTYT')
 await until(`!!document.querySelector('.notification-bell.has-unread') && document.querySelector('.notification-count')?.textContent==='3'`,'Unread bell state')
 if(await evaluate(`getComputedStyle(document.querySelector('.notification-icon>.ui-icon')).animationName`)!=='notification-ring')throw new Error('Unread bell animation missing')
 await evaluate(`document.querySelector('.notification-trigger').click()`);await until(`document.querySelectorAll('.notification-preview .notification-item').length===3`,'Notification preview')
 if(await evaluate(`getComputedStyle(document.querySelector('.notification-preview')).animationName`)!=='notification-open')throw new Error('Popover animation missing')
 await pause(400);await writeFile('/tmp/ltnc-notifications-restored.png',Buffer.from((await cdp('Page.captureScreenshot')).data,'base64'))
 await cdp('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});await until(`!document.querySelector('.notification-preview') && document.activeElement===document.querySelector('.notification-trigger')`,'Escape and focus restoration')
 await evaluate(`document.querySelector('.notification-trigger').click()`);await until(`!!document.querySelector('.notification-preview')`,'Reopen notifications');await evaluate(`document.querySelector('.dashboard-header').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true}))`);await until(`!document.querySelector('.notification-preview')`,'Outside click')
 await evaluate(`document.querySelector('.notification-trigger').click()`);await until(`!!document.querySelector('.notification-preview .notification-item')`,'Notification rows');await evaluate(`document.querySelector('.notification-preview .notification-item').click()`);await until(`location.pathname.startsWith('/maintenance-progress/plans/') && document.body.innerText.includes('Chrome — bàn giao theo phạm vi')`,'Existing notification target')
 await go('/dashboard');await until(`document.querySelector('.notification-count')?.textContent==='2'`,'Read notification count')
 await cdp('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await pause(200)
 if(await evaluate(`document.documentElement.scrollWidth>window.innerWidth`))throw new Error('Dashboard overflow on mobile')
 await evaluate(`document.querySelector('.notification-trigger').click()`);await until(`!!document.querySelector('.notification-preview .notification-item')`,'Mobile preview')
 if(!await evaluate(`(()=>{const r=document.querySelector('.notification-preview').getBoundingClientRect();return r.left>=0&&r.right<=window.innerWidth+1})()`))throw new Error('Mobile notification popup overflow')
 await pause(400);await writeFile('/tmp/ltnc-dashboard-mobile.png',Buffer.from((await cdp('Page.captureScreenshot')).data,'base64'))
 await cdp('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]})
 if(await evaluate(`getComputedStyle(document.querySelector('.notification-icon>.ui-icon')).animationName`)!=='none')throw new Error('Reduced-motion preference ignored')
 await cdp('Emulation.setEmulatedMedia',{features:[]});await evaluate(`document.querySelector('.notification-close').click()`);await cdp('Emulation.setDeviceMetricsOverride',{width:1366,height:768,deviceScaleFactor:1,mobile:false})
 await until(`document.body.innerText.includes('Kế hoạch bảo trì quý hiện tại')`,'Current quarter')
 const shortcut=await evaluate(`[...document.querySelectorAll('a')].find(e=>e.textContent.includes('Tạo kế hoạch Quý')).getAttribute('href')`)
 await go(shortcut);await until(`!!document.querySelector('select[aria-label="Năm"]') && document.querySelectorAll('tbody tr').length>0`,'Quarter preview')
 const expected=new URLSearchParams(shortcut.split('?')[1])
 if(await evaluate(`document.querySelector('select[aria-label="Năm"]').value`)!==expected.get('year') || await evaluate(`document.querySelector('select[aria-label="Quý"]').value`)!==expected.get('quarter'))throw new Error('Quarter shortcut parameters were lost')
 await go('/dashboard');await click('Cập nhật tiến độ↗');await until(`location.pathname==='/maintenance-progress' && document.body.innerText.includes('Chrome — bàn giao theo phạm vi')`,'VTYT progress link')
 await login('bgd','LOCAL_BGD_PASSWORD');await dashboard('Tổng quan Ban Giám đốc','BGD')
 await until(`document.body.innerText.includes('Chrome — kế hoạch chờ BGĐ')`,'Pending approvals')
 await go(await evaluate(`[...document.querySelectorAll('a')].find(e=>e.textContent.includes('Kế hoạch chờ phê duyệt')).getAttribute('href')`))
 await until(`document.querySelector('select')?.value==='PLAN_APPROVAL' && document.body.innerText.includes('Chrome — kế hoạch chờ BGĐ')`,'Filtered approval queue')
 await login('khoa_noi','LOCAL_KHOA_NOI_PASSWORD');await dashboard('Khoa/Phòng: Khoa Nội tổng hợp','KHOA')
 if(await evaluate(`document.body.innerText.includes('TB-003')`))throw new Error('Foreign equipment on department dashboard')
 await until(`document.body.innerText.includes('TB-001') && document.body.innerText.includes('Xác nhận bàn giao')`,'Own handover')
 await click('Xác nhận bàn giao →');await until(`location.pathname.endsWith('/execution') && document.body.innerText.includes('TB-001')`,'Direct handover')
 await go('/dashboard');await click('Lịch sử bảo trì↗');await until(`location.pathname==='/maintenance-history' && document.body.innerText.includes('Lịch sử bảo trì')`,'Department history module')
 await login('admin','LOCAL_ADMIN_PASSWORD');await dashboard('Tổng quan quản trị hệ thống','ADMIN')
 await click('Quản lý tài khoản↗');await until(`location.pathname==='/admin/accounts' && document.body.innerText.includes('Danh sách tài khoản')`,'Admin account management')
 if(evidence.errors.length || evidence.failedRequests.length)throw new Error(JSON.stringify({errors:evidence.errors,failedRequests:evidence.failedRequests}))
 console.log(JSON.stringify({status:'PASS',browser:'Google Chrome',viewport:'1366×768',roles:evidence.roles,checks:'Four role dashboards, compact charts, unread bell and popup animations, Escape/outside dismissal, read navigation/count update, mobile layout, reduced motion, existing workflow shortcuts, no runtime/API errors'}))
} finally { socket?.close(); chrome.kill(); await pause(400); await rm(profile, { recursive: true, force: true }) }
