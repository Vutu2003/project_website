import { spawn } from 'node:child_process'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
const profile = await mkdtemp(path.join(os.tmpdir(), 'ltnc-v3-chrome-'))
const chrome = spawn('/usr/bin/google-chrome', ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage', '--remote-debugging-port=19223', `--user-data-dir=${profile}`, '--window-size=1366,768', 'about:blank'], { stdio: 'ignore' })
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
let socket
const evidence = { browser: 'Google Chrome', errors: [], failedRequests: [] }
try {
 let pages
 for (let i = 0; i < 60; i++) { try { pages = await (await fetch('http://127.0.0.1:19223/json')).json(); break } catch { await pause(250) } }
 if (!pages) throw new Error('Chrome did not start')
 socket = new WebSocket(pages.find(p => p.type === 'page').webSocketDebuggerUrl)
 await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject })
 let sequence = 0
 const pending = new Map()
 socket.onmessage = ({ data }) => { const m = JSON.parse(data); if (m.id) { const p = pending.get(m.id); pending.delete(m.id); if (m.error) p.reject(new Error(m.error.message)); else p.resolve(m.result) } else if (m.method === 'Runtime.exceptionThrown') evidence.errors.push(m.params.exceptionDetails.text); else if(m.method==='Network.responseReceived'&&m.params.response.status>=400&&m.params.response.url.includes('/api/'))evidence.failedRequests.push({url:m.params.response.url,status:m.params.response.status}) }
 function cdp(method, params = {}) { return new Promise((resolve, reject) => { const id = ++sequence; pending.set(id, { resolve, reject }); socket.send(JSON.stringify({ id, method, params })) }) }
 async function evaluate(expression) { const r = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }); if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text); return r.result.value }
 async function until(expression, message) { for (let i = 0; i < 100; i++) { if (await evaluate(expression)) return; await pause(100) } throw new Error(message + ': ' + (await evaluate('document.body.innerText')).slice(-1500)) }
 async function go(url) { await cdp('Page.navigate', { url: `http://localhost:15175${url}` }); await until('document.readyState === "complete"', 'Navigation'); await pause(200) }
 async function click(text) { await until(`!![...document.querySelectorAll('button,a,summary,label')].find(e=>e.textContent.trim()===${JSON.stringify(text)} && !e.disabled)`, `Missing action ${text}`); await evaluate(`[...document.querySelectorAll('button,a,summary,label')].find(e=>e.textContent.trim()===${JSON.stringify(text)} && !e.disabled).click()`); await pause(200) }
 async function input(label, value) { await evaluate(`(()=>{const label=[...document.querySelectorAll('label')].find(e=>e.textContent.trim().startsWith(${JSON.stringify(label)})); const e=label.querySelector('input,select,textarea');const setter=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value').set;setter.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event(e.tagName==='SELECT'?'change':'input',{bubbles:true}));})()`); await pause(200) }
 async function login(username, key) { await go('/login'); await evaluate('localStorage.clear();sessionStorage.clear()'); await go('/login'); await until('!!document.querySelector("#username")', 'Login'); await evaluate(`(()=>{for(const [id,value] of ${JSON.stringify([['username', username], ['password', process.env[key]]])}){const e=document.getElementById(id);Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,value);e.dispatchEvent(new Event('input',{bubbles:true}));}document.querySelector('form').requestSubmit();})()`); await until('!location.pathname.includes("login")', `Login ${username}`) }
 await cdp('Runtime.enable'); await cdp('Page.enable'); await cdp('Network.enable'); await cdp('Page.addScriptToEvaluateOnNewDocument', { source: 'window.confirm=()=>true' })
 await login('vtyt', 'LOCAL_VTYT_PASSWORD'); await go('/contracts')
 await until(`!!document.querySelector('a[href^="/providers/"]')`,'Company list')
 const expectedVtyt=['Tổng quan','Danh sách hợp đồng','Danh sách thiết bị','Kế hoạch bảo trì','Theo dõi tiến độ bảo trì','Báo cáo bảo trì','Lịch sử bảo trì']
 if(JSON.stringify(await evaluate(`[...document.querySelectorAll(".side-nav a")].map(e=>e.textContent.trim())`))!==JSON.stringify(expectedVtyt))throw new Error('VTYT navigation mismatch')
 await go(await evaluate(`document.querySelector('a[href^="/providers/"]').getAttribute("href")`))
 await until(`document.body.innerText.includes("Danh sách hợp đồng") && !!document.querySelector('a[href^="/contracts/"]')`,'Provider contracts')
 await until(`document.body.innerText.includes('Danh sách thiết bị') && document.body.innerText.includes('Thời hạn bảo hành') && document.body.innerText.includes('Đến')`,'Company equipment warranties')
 await writeFile('/tmp/ltnc-company-ui.png',Buffer.from((await cdp('Page.captureScreenshot',{captureBeyondViewport:true})).data,'base64'))
 await go(await evaluate(`document.querySelector('a[href^="/contracts/"]').getAttribute("href")`))
 await until(`document.body.innerText.includes("Thiết bị thuộc hợp đồng") && !!document.querySelector('a[href^="/equipment/"]')`,'Contract equipment')
 await go(await evaluate(`document.querySelector('a[href^="/equipment/"]').getAttribute("href")`))
 await until(`document.body.innerText.includes("Lịch bảo trì định kỳ") && document.body.innerText.includes("Lịch sử bảo trì") && document.body.innerText.includes("Hợp đồng / công ty")`,'Equipment detail')
 await go('/equipment'); await until(`!!document.querySelector('tbody a[href^="/equipment/"]')`,'Equipment list')
 if(await evaluate(`document.querySelectorAll("thead th").length`)!==7)throw new Error('Equipment list is not compact')
 await go(await evaluate(`document.querySelector('tbody a[href^="/equipment/"]').getAttribute("href")`))
 await until(`document.body.innerText.includes("Lịch bảo trì định kỳ")`,'Equipment quarters')
 await go('/equipment');await click('Thêm thiết bị');await until(`location.pathname==='/equipment/new' && !!document.querySelector('input[name="code"]')`,'New equipment form')
 await input('Mã thiết bị','CHROME-NEW-DEVICE');await input('Tên thiết bị','Máy bổ sung');await input('Khoa/Phòng','1');await input('Ngày hết bảo hành','2028-06-30');await click('Quý 1');await input('Hợp đồng áp dụng','NEW');await input('Mã hợp đồng','CHROME-NEW-CONTRACT');await input('Tên hợp đồng','Hợp đồng mới Chrome');await input('Ngày bắt đầu','2026-01-01');await input('Ngày hết hạn','2030-12-31');await input('Công ty bảo trì','NEW');await input('Mã công ty','CHROME-NEW-COMPANY');await input('Tên công ty','Công ty mới Chrome');await writeFile('/tmp/ltnc-create-equipment-ui.png',Buffer.from((await cdp('Page.captureScreenshot',{captureBeyondViewport:true})).data,'base64'));await click('Lưu thiết bị')
 await until(`location.pathname==='/equipment' && document.body.innerText.includes('CHROME-NEW-DEVICE')`,'Created device')
 await go('/contracts');await until(`document.body.innerText.includes('Công ty mới Chrome')`,'New company on contracts');await go(await evaluate(`[...document.querySelectorAll('a[href^="/providers/"]')].find(e=>e.textContent.includes('Công ty mới Chrome')).getAttribute('href')`));await until(`document.body.innerText.includes('CHROME-NEW-DEVICE')`,'Company device mapping');const companyUrl=await evaluate('location.pathname')
 await go('/equipment');await until(`!!document.querySelector('button[aria-label="Xóa thiết bị CHROME-NEW-DEVICE"]')`,'Device delete');await evaluate(`document.querySelector('button[aria-label="Xóa thiết bị CHROME-NEW-DEVICE"]').click()`);await until(`!document.body.innerText.includes('CHROME-NEW-DEVICE')`,'Device removed from catalog');await go(companyUrl);await until(`document.body.innerText.includes('Công ty mới Chrome') && !document.body.innerText.includes('CHROME-NEW-DEVICE')`,'Contract devices synchronized')
 await go('/plans');await click('Tạo kế hoạch bảo trì');await until(`!!document.querySelector('select[aria-label="Năm"]')`,'Draft quarter');await input('Năm','2028');await input('Quý','Q1');await until(`document.querySelectorAll('tbody tr').length===8`,'Draft preview');await evaluate(`(()=>{for(const e of document.querySelectorAll('select[aria-label^="Đơn vị đề xuất"]')){Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(e,e.options[1].value);e.dispatchEvent(new Event('change',{bubbles:true}));}for(const e of document.querySelectorAll('textarea[aria-label^="Căn cứ"]')){Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(e,'Đơn vị phù hợp');e.dispatchEvent(new Event('input',{bubbles:true}));}})()`);await pause(100);await click('Tạo kế hoạch');await until(`location.pathname.startsWith('/plans/') && document.body.innerText.includes('Gửi phê duyệt')`,'Draft created');const draftId=await evaluate(`location.pathname.split('/')[2]`);await go('/plans');await until(`!!document.querySelector('a[href="/plans/${draftId}"]')`,'Draft on list');await click('Xóa kế hoạch');await until(`!document.querySelector('a[href="/plans/${draftId}"]')`,'Draft deleted')
 await go('/plans'); await click('Tạo kế hoạch bảo trì')
 await until(`!!document.querySelector('select[aria-label="Năm"]')`,'Quarter template')
 await input('Năm','2027'); await input('Quý','Q1')
 await until(`document.body.innerText.includes("Kế hoạch bảo trì Quý I năm 2027") && document.querySelectorAll("tbody tr").length===8`,'Automatic quarter preview')
 if(!await evaluate(`!!document.querySelector('tbody a[href^="/contracts/"]') && !!document.querySelector('tbody a[href^="/providers/"]')`))throw new Error('Automatic contract/provider missing')
 const previewCount=await evaluate(`document.querySelectorAll("tbody tr").length`)
 await evaluate(`(()=>{for(const e of document.querySelectorAll('select[aria-label^="Đơn vị đề xuất"]')){Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(e,e.options[1].value);e.dispatchEvent(new Event('change',{bubbles:true}));}for(const e of document.querySelectorAll('textarea[aria-label^="Căn cứ"]')){Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(e,'Đơn vị có năng lực bảo dưỡng và đáp ứng lịch khoa.');e.dispatchEvent(new Event('input',{bubbles:true}));}})()`)
 await pause(200); await click('Tạo kế hoạch'); await until(`location.pathname.startsWith("/plans/") && Number.isInteger(Number(location.pathname.split("/")[2]))`,'Created quarterly plan')
 const planId=await evaluate(`Number(location.pathname.split("/")[2])`)
 await click('Gửi phê duyệt'); await until(`document.body.innerText.includes("Đã gửi kế hoạch để phê duyệt.")`,'Submitted quarter plan')
 await login('bgd','LOCAL_BGD_PASSWORD'); await go('/approvals')
 const expectedBgd=['Tổng quan','Lịch sử bảo trì','Phê duyệt','Báo cáo']
 if(JSON.stringify(await evaluate(`[...document.querySelectorAll(".side-nav a")].map(e=>e.textContent.trim())`))!==JSON.stringify(expectedBgd))throw new Error('BGD navigation mismatch')
 await until(`!!document.querySelector('tbody a[href^="/approvals/"]')`,'Approval queue')
 await go(await evaluate(`document.querySelector('tbody a[href^="/approvals/"]').getAttribute("href")`))
 await until(`document.querySelectorAll('tbody tr').length===${previewCount}`,'All devices in approval table')
 if(await evaluate(`!!document.querySelector('details')`))throw new Error('Approval unexpectedly uses accordions')
 await click('Xác nhận quyết định'); await until(`location.pathname==='/approvals'`,'Plan approval saved')
 await until(`document.body.innerText.includes('Không có yêu cầu nào đang chờ')`,'No separate provider approval required')
 await login('vtyt','LOCAL_VTYT_PASSWORD'); await go(`/maintenance-progress/plans/${planId}`)
 await until(`document.querySelectorAll('tbody tr').length===8`,'Tracking equipment')
 const codes=await evaluate(`[...document.querySelectorAll('tbody tr td:first-child strong')].map(e=>e.textContent.split(' · ')[0])`)
 await click('Bắt đầu kế hoạch'); await until(`document.querySelectorAll('select[aria-label^="Tiến độ "]').length===8`,'Bulk start')
 const code=codes[0]
 await evaluate(`(()=>{const e=document.querySelector('select[aria-label="Tiến độ ${code}"]');Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(e,'DAMAGE_DETECTED');e.dispatchEvent(new Event('change',{bubbles:true}))})()`)
 await until(`!!document.querySelector('dialog[open]')`,'Damage dialog');await input('Mô tả hỏng hóc','Cảm biến lỗi, cần thay thế.');await click('Lưu hỏng hóc')
 await until(`document.body.innerText.includes('Cảm biến lỗi, cần thay thế.') && !document.querySelector('dialog[open]')`,'Damage saved')
 await click('Đánh dấu tất cả đã xong');await until(`[...document.querySelectorAll('select[aria-label^="Tiến độ "]')].filter(e=>e.value==='WORK_DONE').length===7`,'Bulk results')
 if(!await evaluate(`document.querySelector('select[aria-label="Tiến độ ${code}"]').value==='DAMAGE_DETECTED'`))throw new Error('Bulk action overwrote damage')
 await writeFile('/tmp/ltnc-progress-ui.png',Buffer.from((await cdp('Page.captureScreenshot',{captureBeyondViewport:true})).data,'base64'))
 await click('Hoàn thành bảo trì & tạo báo cáo'); await until(`location.pathname==='/plans/${planId}/report' && document.body.innerText.includes('Bản nháp báo cáo')`,'Automatic draft report')
 await until(`[...document.querySelectorAll('textarea')].some(e=>e.value.includes('Cảm biến lỗi, cần thay thế.'))`,'Actual damage evidence')
 await click('Hoàn tất báo cáo'); await until(`document.body.innerText.includes('Nội dung báo cáo chính thức')`,'Final report')
 await click('Gửi báo cáo cho BGĐ và khoa/phòng'); await until(`document.body.innerText.includes('Báo cáo đã được gửi')`,'Report delivery receipts')
 await go('/maintenance-history');await until(`document.querySelectorAll('tbody tr').length===1 && !!document.querySelector('a[href="/maintenance-history/plans/${planId}"]')`,'History grouped into one plan');await evaluate(`document.querySelector('a[href="/maintenance-history/plans/${planId}"]').click()`);await until(`document.querySelectorAll('tbody tr').length===8 && !!document.querySelector('a[href="/plans/${planId}/report"]')`,'Devices inside saved plan history');await writeFile('/tmp/ltnc-history-ui.png',Buffer.from((await cdp('Page.captureScreenshot',{captureBeyondViewport:true})).data,'base64'))
 await go('/dashboard');await until(`!!document.querySelector('a[href="/plans/${planId}/report"]') && !!document.querySelector('a[href="/maintenance-history"]')`,'Dashboard synchronized report history')
 await login('bgd','LOCAL_BGD_PASSWORD'); await go('/reports')
 await until(`!!document.querySelector('a[href="/plans/${planId}/report"]')`,'BGD received report')
 await go(`/plans/${planId}/report`);await until(`document.querySelectorAll('details.campaign-card').length===8`,'BGD all devices')
 await login('khoa_noi','LOCAL_KHOA_NOI_PASSWORD');await go('/reports')
 await until(`!!document.querySelector('a[href="/plans/${planId}/report"]')`,'Department received report')
 await go(`/plans/${planId}/report`);await until(`document.body.innerText.includes('TB-001') && document.body.innerText.includes('Nội dung báo cáo chính thức')`,'Department scoped report')
 if(await evaluate(`document.body.innerText.includes('TB-005')`))throw new Error('Other department equipment leaked')
 await go('/maintenance-history');await until(`document.body.innerText.includes('TB-001')`,'Department maintenance history');if(await evaluate(`document.body.innerText.includes('TB-005')`))throw new Error('Foreign equipment history leaked')
 await login('admin','LOCAL_ADMIN_PASSWORD');await go('/dashboard');await until(`!!document.querySelector('.side-nav')`,'Admin sidebar');if(await evaluate(`!!document.querySelector('.side-nav a[href="/contracts"],.side-nav a[href="/admin/equipment"]') || !!document.querySelector('.sidebar .nav-heading,.sidebar-foot,.brand-block small')`))throw new Error('Admin or caption sidebar cleanup failed')
 await go('/maintenance-progress');await until(`location.pathname==='/dashboard' && document.body.innerText.includes('Tổng quan quản trị')`,'Stale role route fallback')
 if(evidence.errors.length||evidence.failedRequests.length)throw new Error('Chrome failures: '+JSON.stringify(evidence))
 console.log(JSON.stringify({status:'PASS',browser:'Google Chrome',flow:'Equipment mutations and contract sync; draft deletion; simplified bulk progress; finalized report/history/dashboard sync; department scope; sidebar cleanup and role fallback',devices:previewCount}))
} finally { socket?.close(); chrome.kill(); await pause(400); await rm(profile, { recursive: true, force: true }) }
