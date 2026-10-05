import { spawn } from 'node:child_process'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
const profile = await mkdtemp(path.join(os.tmpdir(), 'ltnc-v3-chrome-'))
const chrome = spawn('/usr/bin/google-chrome', ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage', '--remote-debugging-port=19223', `--user-data-dir=${profile}`, '--window-size=1600,1100', 'about:blank'], { stdio: 'ignore' })
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
let socket
const evidence = { browser: 'Google Chrome', errors: [] }
try {
 let pages
 for (let i = 0; i < 60; i++) { try { pages = await (await fetch('http://127.0.0.1:19223/json')).json(); break } catch { await pause(250) } }
 if (!pages) throw new Error('Chrome did not start')
 socket = new WebSocket(pages.find(p => p.type === 'page').webSocketDebuggerUrl)
 await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject })
 let sequence = 0
 const pending = new Map()
 socket.onmessage = ({ data }) => { const m = JSON.parse(data); if (m.id) { const p = pending.get(m.id); pending.delete(m.id); if (m.error) p.reject(new Error(m.error.message)); else p.resolve(m.result) } else if (m.method === 'Runtime.exceptionThrown') evidence.errors.push(m.params.exceptionDetails.text) }
 function cdp(method, params = {}) { return new Promise((resolve, reject) => { const id = ++sequence; pending.set(id, { resolve, reject }); socket.send(JSON.stringify({ id, method, params })) }) }
 async function evaluate(expression) { const r = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }); if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text); return r.result.value }
 async function until(expression, message) { for (let i = 0; i < 100; i++) { if (await evaluate(expression)) return; await pause(100) } throw new Error(message + ': ' + (await evaluate('document.body.innerText')).slice(-1500)) }
 async function go(url) { await cdp('Page.navigate', { url: `http://localhost:15175${url}` }); await until('document.readyState === "complete"', 'Navigation'); await pause(200) }
 async function click(text) { await until(`!![...document.querySelectorAll('button,a,summary,label')].find(e=>e.textContent.trim()===${JSON.stringify(text)} && !e.disabled)`, `Missing action ${text}`); await evaluate(`[...document.querySelectorAll('button,a,summary,label')].find(e=>e.textContent.trim()===${JSON.stringify(text)} && !e.disabled).click()`); await pause(200) }
 async function input(label, value) { await evaluate(`(()=>{const label=[...document.querySelectorAll('label')].find(e=>e.textContent.trim().startsWith(${JSON.stringify(label)})); const e=label.querySelector('input,select,textarea');const setter=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value').set;setter.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event(e.tagName==='SELECT'?'change':'input',{bubbles:true}));})()`); await pause(200) }
 async function login(username, key) { await go('/login'); await evaluate('localStorage.clear();sessionStorage.clear()'); await go('/login'); await until('!!document.querySelector("#username")', 'Login'); await evaluate(`(()=>{for(const [id,value] of ${JSON.stringify([['username', username], ['password', process.env[key]]])}){const e=document.getElementById(id);Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,value);e.dispatchEvent(new Event('input',{bubbles:true}));}document.querySelector('form').requestSubmit();})()`); await until('!location.pathname.includes("login")', `Login ${username}`) }
 await cdp('Runtime.enable'); await cdp('Page.enable'); await cdp('Page.addScriptToEvaluateOnNewDocument', { source: 'window.confirm=()=>true' })
 await login('vtyt', 'LOCAL_VTYT_PASSWORD'); await go('/contracts')
 await until(`!!document.querySelector('a[href^="/providers/"]')`,'Company list')
 const expectedVtyt=['Tổng quan','Danh sách hợp đồng','Danh sách thiết bị','Kế hoạch bảo trì','Theo dõi tiến độ bảo trì','Báo cáo bảo trì']
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
 if(await evaluate(`document.querySelectorAll("thead th").length`)!==6)throw new Error('Equipment list is not compact')
 await go(await evaluate(`document.querySelector('tbody a[href^="/equipment/"]').getAttribute("href")`))
 await until(`document.body.innerText.includes("Lịch bảo trì định kỳ")`,'Equipment quarters')
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
 const expectedBgd=['Tổng quan','Phê duyệt','Báo cáo']
 if(JSON.stringify(await evaluate(`[...document.querySelectorAll(".side-nav a")].map(e=>e.textContent.trim())`))!==JSON.stringify(expectedBgd))throw new Error('BGD navigation mismatch')
 await until(`!!document.querySelector('tbody a[href^="/approvals/"]')`,'Approval queue')
 await go(await evaluate(`document.querySelector('tbody a[href^="/approvals/"]').getAttribute("href")`))
 await until(`document.querySelectorAll('tbody tr').length===${previewCount}`,'All devices in approval table')
 if(await evaluate(`!!document.querySelector('details')`))throw new Error('Approval unexpectedly uses accordions')
 await click('Xác nhận quyết định'); await until(`location.pathname==='/approvals'`,'Plan approval saved')
 for(let proposal=0;proposal<2;proposal++){
  await until(`!!document.querySelector('tbody a[href^="/approvals/"]')`,'Vendor approval queue')
  await go(await evaluate(`document.querySelector('tbody a[href^="/approvals/"]').getAttribute('href')`))
  await until(`document.body.innerText.includes('Đề xuất đơn vị bảo trì')`,'Vendor review')
  await click('Xác nhận quyết định'); await until(`location.pathname==='/approvals'`,'Vendor approval saved')
 }
 await login('vtyt','LOCAL_VTYT_PASSWORD'); await go(`/maintenance-progress/plans/${planId}`)
 await until(`document.querySelectorAll('tbody tr').length===8`,'Tracking equipment')
 const codes=await evaluate(`[...document.querySelectorAll('tbody tr td:first-child strong')].map(e=>e.textContent.split(' · ')[0])`)
 for(const [index,code] of codes.entries()){
  await evaluate(`(()=>{const row=[...document.querySelectorAll('tbody tr')].find(e=>e.querySelector('td').textContent.includes(${JSON.stringify(code)}));[...row.querySelectorAll('button')].find(e=>e.textContent==='Bắt đầu bảo trì').click()})()`)
  await until(`!!document.querySelector('select[aria-label="Tiến độ ${code}"]')`,'Start '+code)
  const stage=index===0?'DAMAGE_DETECTED':'WORK_DONE';const note=index===0?'Cảm biến lỗi, cần thay thế.':'Đã vệ sinh, kiểm tra và bảo dưỡng.'
  await evaluate(`(()=>{const e=document.querySelector('select[aria-label="Tiến độ ${code}"]');Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(e,${JSON.stringify(stage)});e.dispatchEvent(new Event('change',{bubbles:true}))})()`)
  await pause(100)
  await evaluate(`(()=>{const e=document.querySelector('textarea[aria-label="Ghi chú ${code}"]');Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(e,${JSON.stringify(note)});e.dispatchEvent(new Event('input',{bubbles:true}))})()`)
  await pause(100)
  await evaluate(`(()=>{const row=[...document.querySelectorAll('tbody tr')].find(e=>e.querySelector('td').textContent.includes(${JSON.stringify(code)}));[...row.querySelectorAll('button')].find(e=>e.textContent==='Lưu tiến độ').click()})()`)
  await until(`[...document.querySelectorAll('tbody tr')].find(e=>e.querySelector('td').textContent.includes(${JSON.stringify(code)}))?.querySelector('.tracking-note')?.textContent.includes(${JSON.stringify(note)})`,'Progress saved '+code)
 }
 await writeFile('/tmp/ltnc-progress-ui.png',Buffer.from((await cdp('Page.captureScreenshot',{captureBeyondViewport:true})).data,'base64'))
 await click('Hoàn thành bảo trì & tạo báo cáo'); await until(`location.pathname==='/plans/${planId}/report' && document.body.innerText.includes('Bản nháp báo cáo')`,'Automatic draft report')
 await until(`[...document.querySelectorAll('textarea')].some(e=>e.value.includes('Cảm biến lỗi, cần thay thế.'))`,'Actual damage evidence')
 await click('Hoàn tất báo cáo'); await until(`document.body.innerText.includes('Nội dung báo cáo chính thức')`,'Final report')
 await click('Gửi báo cáo cho BGĐ và khoa/phòng'); await until(`document.body.innerText.includes('Báo cáo đã được gửi')`,'Report delivery receipts')
 await login('bgd','LOCAL_BGD_PASSWORD'); await go('/reports')
 await until(`!!document.querySelector('a[href="/plans/${planId}/report"]')`,'BGD received report')
 await go(`/plans/${planId}/report`);await until(`document.querySelectorAll('details.campaign-card').length===8`,'BGD all devices')
 await login('khoa_noi','LOCAL_KHOA_NOI_PASSWORD');await go('/reports')
 await until(`!!document.querySelector('a[href="/plans/${planId}/report"]')`,'Department received report')
 await go(`/plans/${planId}/report`);await until(`document.body.innerText.includes('TB-001') && document.body.innerText.includes('Nội dung báo cáo chính thức')`,'Department scoped report')
 if(await evaluate(`document.body.innerText.includes('TB-005')`))throw new Error('Other department equipment leaked')
 if(evidence.errors.length)throw new Error('Chrome runtime exceptions: '+JSON.stringify(evidence.errors))
 console.log(JSON.stringify({status:'PASS',browser:'Google Chrome',flow:'Company and equipment warranty UI; 8-device progress; VTYT completion/autodraft/finalize/send; BGD full report; department scoped report',devices:previewCount}))
} finally { socket?.close(); chrome.kill(); await pause(400); await rm(profile, { recursive: true, force: true }) }
