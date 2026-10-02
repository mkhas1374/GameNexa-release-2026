const {Pool}=require('pg');
const jwt=require('jsonwebtoken');
require('dotenv').config();
const BASE=process.env.TEST_BASE_URL || 'http://127.0.0.1:3000';
const pool=new Pool({connectionString:process.env.DATABASE_URL});
const run='SEC_'+Date.now();
const A='sec_a_'+run,B='sec_b_'+run;
let ca,cb,ra,reqA;
const tok=(id,role,mid)=>jwt.sign({id,managerId:mid,role},process.env.JWT_SECRET,{expiresIn:'1h'});
async function db(q,p=[]){return pool.query(q,p)}
async function api(path,o={}){const h={'Content-Type':'application/json'};if(o.token){h.Authorization='Bearer '+o.token; try{h['X-Manager-ID']=jwt.decode(o.token).managerId||''}catch{}}const r=await fetch(BASE+path,{method:o.method||'GET',headers:h,body:o.body?JSON.stringify(o.body):undefined});let t=await r.text(),d;try{d=JSON.parse(t)}catch{d={raw:t}}return {s:r.status,d}}
function ok(x,m){if(!x)throw Error('ASSERT '+m)}
async function clean(){for(const m of [A,B]){for(const q of [
'DELETE FROM financial_audit_logs WHERE manager_id=$1','DELETE FROM payment_transactions WHERE manager_id=$1',
'DELETE FROM invoices WHERE manager_id=$1','DELETE FROM wallet_transactions WHERE manager_id=$1',
'DELETE FROM gn_ledger WHERE manager_id=$1','DELETE FROM lp_ledger WHERE manager_id=$1',
'DELETE FROM notifications WHERE manager_id=$1','DELETE FROM manual_payment_requests WHERE manager_id=$1',
'DELETE FROM reservation_audit_logs WHERE manager_id=$1','DELETE FROM reservations WHERE manager_id=$1',
'DELETE FROM stations WHERE manager_id=$1','DELETE FROM customers WHERE manager_id=$1',
'DELETE FROM manager_payment_methods WHERE manager_id=$1','DELETE FROM managers WHERE id=$1']){try{await db(q,[m])}catch{}}}}
async function main(){
 try{
 console.log('SECURITY_E2E_START',run); await clean();
 await db('INSERT INTO managers(id,username,password_hash) VALUES($1,$2,$3),($4,$5,$6)',[A,A+'@t','$2b$12$dummy',B,B+'@t','$2b$12$dummy']);
 await db("INSERT INTO manager_entitlements(manager_id,entitlement_type,plan_id,status,starts_at,expires_at,source) VALUES($1,'SUBSCRIPTION','1_MONTH','ACTIVE',NOW(),NOW()+INTERVAL '1 day','SYSTEM'),($2,'SUBSCRIPTION','1_MONTH','ACTIVE',NOW(),NOW()+INTERVAL '1 day','SYSTEM')",[A,B]);
 ca=(await db("INSERT INTO customers(manager_id,phone_number,full_name,wallet_balance,gn_balance,lp_balance) VALUES($1,$2,$3,0,0,0) RETURNING id",[A,'9'+run+'1','A'])).rows[0].id;
 cb=(await db("INSERT INTO customers(manager_id,phone_number,full_name,wallet_balance,gn_balance,lp_balance) VALUES($1,$2,$3,0,0,0) RETURNING id",[B,'9'+run+'2','B'])).rows[0].id;
 ra=(await db("INSERT INTO reservations(manager_id,customer_id,type,status,start_time,end_time,duration_minutes,snap_base_price,snap_final_price,snap_payable_amount,snap_currency) VALUES($1,$2,'NORMAL_RESERVATION','PAYMENT_PENDING',NOW()+INTERVAL '1 day',NOW()+INTERVAL '1 day 1 hour',60,100000,100000,100000,'IRT') RETURNING id",[A,ca])).rows[0].id;
 const ma=tok(A,'MANAGER',A),mb=tok(B,'MANAGER',B),cu=tok(ca,'CUSTOMER',A),cbt=tok(cb,'CUSTOMER',B);
 console.log('fixtures_ready');
 let r=await api('/api/v1/customer/profile',{token:ma});ok(r.s===403,'manager token blocked from customer');
 r=await api('/api/v1/manager/customers',{token:cu});ok(r.s===403,'customer token blocked from manager');
 r=await api('/api/v1/customer/reservations/'+ra,{token:cbt});ok(r.s===404,'customer cross-manager reservation blocked');
 r=await api('/api/v1/manager/reservations/'+ra,{token:mb});ok(r.s===404,'manager cross-manager reservation blocked');
 r=await api('/api/v1/customer/manual-payment-requests?managerId='+B,{token:cu});ok(r.s===200 && Array.isArray(r.d) && r.d.every(x=>x.manager_id===A && x.customer_id===ca),'query manager spoof ignored');
 r=await api('/api/v1/customer/manual-payment-requests/'+999999+'/approve',{method:'POST',token:mb,body:{amount:1}});ok(r.s===404||r.s===400,'foreign/nonexistent request not approved');
 r=await api('/api/v1/customer/manual-payment-requests',{method:'POST',token:cu,body:{purpose:'WALLET_TOPUP',managerId:B,idempotencyKey:'sec_'+run+'_1',amount:'1000'}});ok(r.s===201 && r.d.request?.manager_id===A,'body manager spoof ignored');reqA=r.d.request.id;
 r=await api('/api/v1/manager/payment-methods',{token:ma});ok(r.s===200 && r.d.methods.every(x=>x.manager_id===A),'manager method isolation');
 r=await api('/api/v1/manager/payment-methods',{method:'POST',token:ma,body:{managerId:B,methodCode:'SEC_TEST',displayName:'SEC TEST'}});ok(r.s===201 && r.d.method.manager_id===A,'payment method manager spoof ignored');
 r=await api('/api/v1/manager/club/point-logs',{method:'POST',token:ma,body:{customerId:cb,title:'cross',points:1}});ok(r.s===404,'cross-manager point log blocked');
 r=await api('/api/v1/manager/customer-transactions',{method:'POST',token:ma,body:{customerId:cb,amount:1}});ok(r.s===404,'cross-manager customer transaction blocked');
 r=await api('/api/v1/customer/manual-payment-requests/'+reqA+'/approve',{method:'POST',token:mb,body:{amount:'1000'}});ok(r.s===404||r.s===400,'cross-manager approval blocked');
 console.log('idor_and_spoofing_ok');
 const expired=jwt.sign({id:A,managerId:A,role:'MANAGER'},process.env.JWT_SECRET,{expiresIn:'-1s'});
 const malformed=ma.slice(0,-1)+(ma.slice(-1)==='a'?'b':'a');
 const noBearer=await fetch(BASE+'/api/v1/manager/customers',{headers:{Authorization:ma}});ok(noBearer.status===401,'non-Bearer rejected');
 r=await api('/api/v1/manager/customers',{token:expired});ok(r.s===401,'expired token rejected');
 r=await api('/api/v1/manager/customers',{token:malformed});ok(r.s===401,'tampered token rejected');
 const badManager=jwt.sign({id:A,managerId:B,role:'MANAGER'},process.env.JWT_SECRET,{expiresIn:'1h'});
 r=await api('/api/v1/manager/customers',{token:badManager});ok(r.s===403,'manager id mismatch rejected');
 const noMid=jwt.sign({id:A,role:'MANAGER'},process.env.JWT_SECRET,{expiresIn:'1h'});
 r=await api('/api/v1/manager/customers',{token:noMid});ok(r.s===403,'manager without managerId rejected');
 const noCid=jwt.sign({managerId:A,role:'CUSTOMER'},process.env.JWT_SECRET,{expiresIn:'1h'});
 r=await api('/api/v1/customer/profile',{token:noCid});ok(r.s===403,'customer without id rejected');
 console.log('jwt_hardening_ok');
 r=await api('/api/v1/customer/manual-payment-requests',{method:'POST',token:cbt,body:{purpose:'RESERVATION_PAYMENT',reservationId:ra,idempotencyKey:'sec_'+run+'_foreign',amount:'100000'}});ok(r.s>=400&&r.s<500,'foreign reservation rejected');
 r=await api('/api/v1/customer/manual-payment-requests',{method:'POST',token:cu,body:{purpose:'RESERVATION_PAYMENT',reservationId:ra,idempotencyKey:'sec_'+run+'_partial',amount:'99999'}});ok(r.s===201 && r.d.request?.manager_id===A && r.d.request?.reservation_id===ra,'valid partial reservation payment request accepted');
 r=await api('/api/v1/customer/manual-payment-requests/'+reqA+'/approve',{method:'POST',token:ma,body:{amount:'-5'}});ok(r.s>=400&&r.s<500,'negative amount rejected');
 r=await api('/api/v1/customer/manual-payment-requests/'+reqA+'/approve',{method:'POST',token:ma,body:{amount:'NaN'}});ok(r.s>=400&&r.s<500,'NaN string rejected');
 const before=await db('SELECT wallet_balance,gn_balance,lp_balance FROM customers WHERE id=$1',[ca]);
 ok(Number(before.rows[0].wallet_balance)===0,'tamper attempts caused no wallet mutation');
 const aud=await db("SELECT count(*)::int n FROM financial_audit_logs WHERE manager_id IN ($1,$2) AND (metadata->>'requestId')=$3",[A,B,String(reqA)]);
 ok(aud.rows[0].n===0,'unapproved request has no financial audit');
 console.log('financial_tamper_guard_ok');
 const counts=await db('SELECT (SELECT count(*) FROM managers WHERE id LIKE $1) managers,(SELECT count(*) FROM customers WHERE manager_id=$2) customers',[ 'sec_%','sec_a_'+run]);
 console.log('security_fixtures_live',JSON.stringify(counts.rows[0]));
 await clean();
 const residue=await db('SELECT count(*)::int n FROM managers WHERE id=$1 OR id=$2',[A,B]);
 ok(residue.rows[0].n===0,'cleanup left no manager residue');
 console.log('ISOLATED_SECURITY_PASS');
}catch(e){console.error('ISOLATED_SECURITY_FAIL',e.stack||e);try{await clean()}catch{}process.exitCode=1}
finally{await pool.end()}
}
main();