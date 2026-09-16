// Android Chrome CDP response fixture. Every page request is fulfilled locally; none is continued to the network.
import fs from 'node:fs';
import http from 'node:http';
import { randomUUID } from 'node:crypto';
const html = fs.readFileSync('fixtures/attendance/Present.html','utf8');
const dir='build/site-replica-2026-09-16';fs.mkdirSync(dir,{recursive:true});
const runId=randomUUID();fs.writeFileSync(`${dir}/run-id.txt`,runId);
let version;for(let i=0;i<20;i++){try{version=await (await fetch('http://127.0.0.1:9222/json/version')).json();break;}catch(e){if(i===19)throw e;await new Promise(r=>setTimeout(r,1000));}}
const ws=new WebSocket(version.webSocketDebuggerUrl);await new Promise((yes,no)=>{ws.onopen=yes;ws.onerror=no;});
let serial=0;const pending=new Map(),sessions=new Set(),ready=new Set(),targetBySession=new Map();let errors=[];
function cmd(method,params={},sessionId){return new Promise((resolve,reject)=>{let id=++serial;pending.set(id,{resolve,reject,method});ws.send(JSON.stringify({id,method,params,...(sessionId?{sessionId}:{})}));});}
let state;
function reset(name){state={runId,case:name,in:(name.startsWith('checkout')||name.startsWith('already-in'))?'08:45:00':'',out:'',requests:[],entries:0,exits:0,dialogs:0,documents:0};}
reset('entry');
function record(item){state.requests.push({...item,at:new Date().toISOString()});fs.writeFileSync(`${dir}/router-state.json`,JSON.stringify(state,null,2));}
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function respond(e,sid){
 const url=new URL(e.request.url);const path=url.pathname;let body='',status=200,type='text/plain; charset=utf-8';let delay=0;
 const snapshot=state;
 if(path==='/Worknet/Present/Present.asp'){
   snapshot.documents++;snapshot.targetId=targetBySession.get(sid);type='text/html; charset=utf-8';
   const day=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
   body=html.replaceAll('__TODAY__',snapshot.case==='wrong-date'?'2000-01-01':day);
   if(snapshot.case==='already-in-ready')body='<meta name="viewport" content="width=device-width"><h2>수강생 출결관리</h2><p>출결일 : '+day+'</p><input type="button" value="입실시간 : 08:45:00"><input type="button" value="퇴실">';
   if(snapshot.case==='checkout-unknown-alert')body=body.replaceAll('location.reload();','alert("재현 시험: 원인을 알 수 없는 안내");location.reload();');
   if(snapshot.case==='checkout-reappeared')body=body.replace('var strCodeO  =', 'confirm("정말로 퇴실 하시겠습니까?"); var strCodeO  =');
   if(snapshot.case==='auth')body=body.replace("CstrCode:'replica-student'","CstrCode:null");
   if(snapshot.case==='auth-message')body='<h1>인증절차를 진행한후 사용해주세요. 관리자에게 문의바랍니다.</h1>';
   if(snapshot.case==='denied'){status=403;body='<h1>403 Forbidden</h1>'; }
   if(snapshot.case==='rate-limit'){status=429;body='<h1>429 Too Many Requests</h1>'; }
   if(snapshot.case==='human-check')body='<h1>Verify you are human</h1>';
   if(snapshot.case==='network-error')body='<h1>ERR_CONNECTION_TIMED_OUT</h1>';
   if(snapshot.case==='server-error'){status=503;body='<h1>503 Service Unavailable</h1>';}
 }else if(path.endsWith('/PresentIP.asp')){body='1';delay=150;}
 else if(path.endsWith('/PresentStrCName.asp')){body='[SK플래닛] 재현 시험 과정';delay=150;}
 else if(path.endsWith('/PresentPeriodChk.asp')){body=snapshot.case==='no-course'?'0':'1';delay=150;}
 else if(path.endsWith('/PresentRollBookChk.asp')){body=snapshot.in+'|'+snapshot.out;delay=['slow-page','early-return'].includes(snapshot.case)?7000:snapshot.case.startsWith('focus')?5000:150;}
 else if(path.endsWith('/PresentRollBooklist.asp')){body='<table><tr><th>이수시간</th><th>총출석시간</th><th>남은시간</th></tr><tr><td>940시간</td><td>62시간 0분</td><td>878시간 0분</td></tr></table>';}
 else if(path.endsWith('/PresentRollBooklist1.asp'))body='';
 else if(path.endsWith('/PresentIn_ok.asp')){snapshot.entries++;body='1';if(!['no-result','return-after-click'].includes(snapshot.case))snapshot.in='08:45:00';delay=snapshot.case==='slow-result'?10000:150;}
 else if(path.endsWith('/PresentOut_ok.asp')){
   snapshot.exits++;body='1';delay=150;
   if(snapshot.case==='checkout-wifi')body='0';
   else if(snapshot.case==='checkout-no-result'){} // Successful HTTP response without a saved server time.
   else if(snapshot.case==='checkout-unknown-alert')body="<script>";
   else snapshot.out='17:50:05';
 }
 else {status=404;body='Blocked by local replica';}
 record({url:e.request.url,method:e.request.method,action:'fulfilled',status});
 if(delay)await pause(delay);
 // No Fetch.continueRequest exists in this fixture. Unknown URLs receive local 404.
 await cmd('Fetch.fulfillRequest',{requestId:e.requestId,responseCode:status,responseHeaders:[{name:'Content-Type',value:type},{name:'Cache-Control',value:'no-store'},{name:'Content-Security-Policy',value:"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src data:; form-action 'none'; base-uri 'none'"}],body:Buffer.from(body).toString('base64')},sid);
}
async function attach(e){
 const sid=e.sessionId;sessions.add(sid);targetBySession.set(sid,e.targetInfo.targetId);console.log('Attached',e.targetInfo.type,e.targetInfo.targetId,e.targetInfo.url);
 // Enable interception and resume concurrently: Android may defer Fetch.enable until renderer startup.

 if(e.targetInfo.type!=='page'){await cmd('Runtime.runIfWaitingForDebugger',{},sid);return;}
 const interception=cmd('Fetch.enable',{patterns:[{urlPattern:'*',requestStage:'Request'}]},sid);
 await cmd('Runtime.runIfWaitingForDebugger',{},sid);
 await interception;
 await cmd('Network.setCacheDisabled',{cacheDisabled:true},sid);await cmd('Page.enable',{},sid);
 ready.add(sid);await cmd('Runtime.runIfWaitingForDebugger',{},sid);
}
ws.onmessage=({data})=>{const m=JSON.parse(data);if(m.id){let p=pending.get(m.id);pending.delete(m.id);if(p){if(m.error)p.reject(Error(JSON.stringify(m.error)));else p.resolve(m.result);}return;}
 if(m.method==='Fetch.requestPaused')console.log('Intercepted',m.params.request.url);
 if(m.method==='Target.attachedToTarget')attach(m.params).catch(fatal);
 if(m.method==='Target.detachedFromTarget'){sessions.delete(m.params.sessionId);ready.delete(m.params.sessionId);}
 if(m.method==='Fetch.requestPaused')respond(m.params,m.sessionId).catch(fatal);
 if(m.method==='Page.javascriptDialogOpening'){state.dialogs++;record({dialog:m.params.type,message:m.params.message});}
 if(m.method==='Page.javascriptDialogClosed')record({dialogClosed:true,accepted:m.params.result});
};
function fatal(e){errors.push(String(e));console.error(e);}
await cmd('Target.setAutoAttach',{autoAttach:true,waitForDebuggerOnStart:true,flatten:true});
await pause(500);
const server=http.createServer(async(req,res)=>{try{
 const u=new URL(req.url,'http://localhost');
 if(u.searchParams.get('id')!==runId){res.writeHead(403);res.end('{}');return;}
 if(u.pathname==='/reset')reset(u.searchParams.get('case')||'entry');
 if(u.pathname==='/return'){
   const targets=await cmd('Target.getTargets');const target=targets.targetInfos.find(t=>t.targetId===state.targetId);
   if(target)await cmd('Target.activateTarget',{targetId:target.targetId});
 }
 res.setHeader('Content-Type','application/json');res.end(JSON.stringify({...state,armed:ready.size>0&&errors.length===0,sessions:ready.size,errors}));
 }catch(e){res.writeHead(500);res.end(JSON.stringify({error:String(e)}));}});
server.listen(9333,'127.0.0.1',()=>console.log(`Replica armed (${ready.size} page sessions), run ${runId}`));
process.on('SIGINT',()=>{ws.close();server.close();process.exit(0);});