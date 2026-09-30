const {Pool}=require('pg');
const jwt=require('jsonwebtoken');
require('dotenv').config();
const pool=new Pool({connectionString:process.env.DATABASE_URL});
const base='http://127.0.0.1:3000';
const id='sm_route_'+Date.now();
async function main(){
 try{
  await pool.query("INSERT INTO managers(id,username,password_hash,role) VALUES($1,$2,$3,'SUPER_MANAGER')",[id,id,'test']);
  await pool.query("INSERT INTO manager_entitlements(manager_id,entitlement_type,plan_id,status,starts_at,expires_at,source) VALUES($1,'SUPER_MANAGER_LIFETIME','SUPER_MANAGER_LIFETIME','ACTIVE',NOW(),NOW()+INTERVAL '10 years','SUPER_MANAGER')",[id]);
  const token=jwt.sign({id,managerId:id,role:'SUPER_MANAGER'},process.env.JWT_SECRET,{expiresIn:'1h'});
  const list=await fetch(base+'/api/v1/super-manager/managers',{headers:{Authorization:'Bearer '+token}});
  if(list.status!==200) throw Error('manager list failed: '+list.status);
  const legacy=await fetch(base+'/api/v1/super-manager/subscription-requests/1/confirm',{method:'POST',headers:{Authorization:'Bearer '+token}});
  if(legacy.status!==410) throw Error('automatic subscription confirmation not disabled: '+legacy.status);
  console.log('SUPER_MANAGER_ROUTES_PASS');
 }catch(e){console.error('SUPER_MANAGER_ROUTES_FAIL',e.stack||e);process.exitCode=1}
 finally{try{await pool.query('DELETE FROM manager_entitlements WHERE manager_id=$1',[id]);await pool.query('DELETE FROM managers WHERE id=$1',[id])}catch{}await pool.end()}
}
main();
