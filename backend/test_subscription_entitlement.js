const {Pool}=require('pg'); const jwt=require('jsonwebtoken'); require('dotenv').config();
const pool=new Pool({connectionString:process.env.DATABASE_URL}); const base='http://127.0.0.1:3000';
const sm='sm_ent_'+Date.now(), mgr='mgr_ent_'+Date.now(), dev1='dev_ent_1_'+Date.now(), dev2='dev_ent_2_'+Date.now(), trial='trial_ent_'+Date.now();
const tok=(id,role)=>jwt.sign({id,managerId:id,role,tv:1},process.env.JWT_SECRET,{expiresIn:'1h'});
async function req(path,opts={}){const r=await fetch(base+path,opts); const t=await r.text(); let d={}; try{d=JSON.parse(t)}catch{} return {s:r.status,d};}
async function main(){let pass=0,fail=0; const ok=(x,n)=>{if(x){console.log('PASS:',n);pass++}else{console.log('FAIL:',n);fail++}};
try{
 await pool.query("INSERT INTO managers(id,username,password_hash,role) VALUES($1,$2,'x','SUPER_MANAGER'),($3,$4,'x','MANAGER')",[sm,sm,mgr,mgr]);
 await pool.query("INSERT INTO manager_entitlements(manager_id,entitlement_type,plan_id,status,starts_at,expires_at,source,max_devices) VALUES($1,'SUPER_MANAGER_LIFETIME','SUPER_MANAGER_LIFETIME','ACTIVE',NOW(),NOW()+INTERVAL '10 years','SUPER_MANAGER',100),($2,'SUBSCRIPTION','MONTHLY','ACTIVE',NOW(),NOW()+INTERVAL '1 day','SUPER_MANAGER',1)",[sm,mgr]);
 const st=tok(sm,'SUPER_MANAGER'); const auth={Authorization:'Bearer '+st};
 let a=await req(`/api/v1/super-manager/managers/${mgr}/subscription/extend`,{method:'POST',headers:{...auth,'Content-Type':'application/json'},body:JSON.stringify({planId:'THREE_MONTHS'})});
 ok(a.s===200 && a.d.success && a.d.entitlement?.plan_id==='THREE_MONTHS','entitlement extension creates new canonical plan');
 const ents=await pool.query("SELECT status,plan_id FROM manager_entitlements WHERE manager_id=$1 ORDER BY id",[mgr]); ok(ents.rows.filter(x=>x.status==='ACTIVE').length===1 && ents.rows.some(x=>x.status==='EXPIRED'&&x.plan_id==='MONTHLY'),'only one active entitlement and prior entitlement retired');
 await pool.query("INSERT INTO manager_device_bindings(manager_id,device_id,active) VALUES($1,$2,TRUE),($1,$3,TRUE)",[mgr,dev1,dev2]);
 let u=await req(`/api/v1/super-manager/managers/${mgr}/devices/${encodeURIComponent(dev1)}`,{method:'DELETE',headers:auth}); ok(u.s===200 && u.d.success,'Super Manager device unbinding');
 const binds=await pool.query('SELECT device_id,active FROM manager_device_bindings WHERE manager_id=$1 ORDER BY device_id',[mgr]); ok(binds.rows.find(x=>x.device_id===dev1)?.active===false && binds.rows.find(x=>x.device_id===dev2)?.active===true,'device binding state isolation');
 const trialStart=await req('/api/v1/trial/start',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({deviceId:trial,deviceFingerprint:trial,deviceName:'test'})});
 ok(trialStart.s===201 && trialStart.d.trialActive===true,'24h trial first activation');
 const race=await Promise.all([1,2,3].map(()=>req('/api/v1/trial/start',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({deviceId:trial,deviceFingerprint:trial,deviceName:'test'})})));
 ok(race.every(x=>x.s===200||x.s===403) && race.filter(x=>x.s===201).length===0,'24h trial duplicate/concurrent activation prevented');
 const ext=await req(`/api/v1/super-manager/trial-devices/${encodeURIComponent(trial)}/extend`,{method:'POST',headers:auth}); ok(ext.s===200 && ext.d.success,'Super Manager trial extension');
 const del=await req(`/api/v1/super-manager/trial-devices/${encodeURIComponent(trial)}`,{method:'DELETE',headers:auth}); ok(del.s===200 && del.d.success,'Super Manager trial deletion');
 const blocked=await req('/api/v1/trial/start',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({deviceId:trial,deviceFingerprint:trial,deviceName:'test'})}); ok(blocked.s===403,'deleted trial cannot be reactivated');
} catch(e){console.error('TEST_ERROR',e.stack||e);fail++} finally {try{await pool.query('DELETE FROM trial_device_blocks WHERE device_id=$1',[trial]);await pool.query('DELETE FROM trial_device_audit_log WHERE device_id=$1',[trial]);await pool.query('DELETE FROM trial_devices WHERE device_id=$1',[trial]);await pool.query('DELETE FROM manager_device_bindings WHERE manager_id=$1',[mgr]);await pool.query('DELETE FROM manager_entitlements WHERE manager_id IN ($1,$2)',[mgr,sm]);await pool.query('DELETE FROM managers WHERE id IN ($1,$2)',[mgr,sm]);}catch(e){console.error('cleanup',e.message)} await pool.end(); console.log(`Tests Result: Passed: ${pass}, Failed: ${fail}`); if(fail)process.exitCode=1}}
main();
