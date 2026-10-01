const {Pool}=require('pg');
const bcrypt=require('bcrypt');
const jwt=require('jsonwebtoken');
require('dotenv').config();
const pool=new Pool({connectionString:process.env.DATABASE_URL});
const base='http://127.0.0.1:3000';
const mid='act_rt_'+Date.now(), did='act_dev_'+Date.now();
async function call(path,body){const r=await fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});return {s:r.status,d:await r.json()};}
(async()=>{let p=0,f=0;try{
await pool.query("INSERT INTO managers(id,username,password_hash,role) VALUES($1,$1,'x','MANAGER')",[mid]);
await pool.query("INSERT INTO manager_entitlements(manager_id,entitlement_type,plan_id,status,starts_at,expires_at,source,max_devices) VALUES($1,'SUBSCRIPTION','MONTHLY','ACTIVE',NOW(),NOW()+INTERVAL '30 days','SUPER_MANAGER',1)",[mid]);
const secret='rt-secret-'+Date.now(), hash=await bcrypt.hash(secret,10);
const rid=(await pool.query("INSERT INTO subscription_payment_requests(manager_id,plan_id,amount,buyer_device_id,status,provisioned_account,activation_secret_hash,idempotency_key) VALUES($1,'MONTHLY',500000,$2,'CONFIRMED',TRUE,$3,$4) RETURNING id",[mid,did,hash,'rt-'+Date.now()])).rows[0].id;
let a=await call('/api/v1/subscriptions/activate',{licenseCode:'PENDING_'+rid,deviceId:did,activationSecret:secret});
if(a.s===200&&a.d.success){console.log('PASS activation');p++}else{console.log('FAIL activation',a);f++}
let b=await call('/api/v1/subscriptions/activate',{licenseCode:'PENDING_'+rid,deviceId:did,activationSecret:secret});
const count=Number((await pool.query("SELECT COUNT(*) FROM manager_device_bindings WHERE manager_id=$1 AND active=TRUE",[mid])).rows[0].count);
if(b.s===200&&count===1){console.log('PASS activation replay no duplicate binding');p++}else{console.log('FAIL activation replay',b,count);f++}
let c=await call('/api/v1/subscriptions/activate',{licenseCode:'PENDING_'+rid,deviceId:did,activationSecret:'wrong'});
if(c.s===403){console.log('PASS invalid secret rejected');p++}else{console.log('FAIL invalid secret',c);f++}
let d=await call('/api/v1/subscriptions/set-password',{licenseCode:'PENDING_'+rid,deviceId:did,activationSecret:secret,password:'StrongPass123!'});
if(d.s===200&&d.d===true){console.log('PASS secret consumed by password setup');p++}else{console.log('FAIL password setup',d);f++}
let e=await call('/api/v1/subscriptions/activate',{licenseCode:'PENDING_'+rid,deviceId:did,activationSecret:secret});
if(e.s===403){console.log('PASS consumed secret cannot reactivate');p++}else{console.log('FAIL consumed secret replay',e);f++}
}catch(e){console.error('ACTIVATION_TEST_ERROR',e);f++}finally{try{await pool.query('DELETE FROM subscription_payment_requests WHERE manager_id=$1',[mid]);await pool.query('DELETE FROM manager_device_bindings WHERE manager_id=$1',[mid]);await pool.query('DELETE FROM manager_entitlements WHERE manager_id=$1',[mid]);await pool.query('DELETE FROM managers WHERE id=$1',[mid])}catch{}await pool.end();console.log('Tests Result: Passed: '+p+', Failed: '+f);if(f)process.exitCode=1}})();