const {Pool}=require('pg');
const jwt=require('jsonwebtoken');
require('dotenv').config();
const pool=new Pool({connectionString:process.env.DATABASE_URL});
const base='http://127.0.0.1:3000';
const id='sm_test_'+Date.now();
const token=()=>jwt.sign({id,managerId:id,role:'SUPER_MANAGER',tv:1},process.env.JWT_SECRET,{expiresIn:'1h'});
async function main(){
 try{
  await pool.query('DELETE FROM managers WHERE id=$1',[id]);
  await pool.query("INSERT INTO managers(id,username,password_hash,role) VALUES($1,$2,$3,'SUPER_MANAGER')",[id,id,'test']);
  await pool.query("INSERT INTO manager_entitlements(manager_id,entitlement_type,plan_id,status,starts_at,expires_at,source) VALUES($1,'SUPER_MANAGER_LIFETIME','SUPER_MANAGER_LIFETIME','ACTIVE',NOW(),NOW()+INTERVAL '10 years','SUPER_MANAGER')",[id]);
  const no=await fetch(base+'/api/v1/super-manager/managers');
  if(no.status!==401) throw Error('missing token was not rejected: '+no.status);
  const good=await fetch(base+'/api/v1/super-manager/managers',{headers:{Authorization:'Bearer '+token()}});
  if(good.status!==200) throw Error('valid Super Manager rejected: '+good.status+' '+await good.text());
  const expired=jwt.sign({id,managerId:id,role:'SUPER_MANAGER',tv:1},process.env.JWT_SECRET,{expiresIn:'-1s'});
  const ex=await fetch(base+'/api/v1/super-manager/managers',{headers:{Authorization:'Bearer '+expired}});
  if(ex.status!==401) throw Error('expired Super Manager accepted: '+ex.status);
  console.log('SUPER_MANAGER_AUTH_PASS');
 }catch(e){console.error('SUPER_MANAGER_AUTH_FAIL',e.stack||e);process.exitCode=1}
 finally{try{await pool.query('DELETE FROM manager_entitlements WHERE manager_id=$1',[id]);await pool.query('DELETE FROM managers WHERE id=$1',[id])}catch{}await pool.end()}
}
main();
