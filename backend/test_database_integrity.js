const {Pool}=require('pg');
require('dotenv').config();
const pool=new Pool({connectionString:process.env.DATABASE_URL});
async function main(){let passed=0,failed=0;try{
 const checks=[
  ['reservation manager/customer mismatch',"SELECT count(*)::int n FROM reservations r JOIN customers c ON c.id=r.customer_id WHERE r.manager_id<>c.manager_id",0],
  ['reservation manager/station mismatch',"SELECT count(*)::int n FROM reservations r JOIN stations s ON s.id=r.station_id WHERE r.manager_id<>s.manager_id",0],
  ['invoice manager/customer mismatch',"SELECT count(*)::int n FROM invoices i JOIN customers c ON c.id=i.customer_id WHERE i.manager_id<>c.manager_id",0],
  ['manual payment manager/customer mismatch',"SELECT count(*)::int n FROM manual_payment_requests p JOIN customers c ON c.id=p.customer_id WHERE p.manager_id<>c.manager_id",0],
  ['active manager entitlements expired',"SELECT count(*)::int n FROM manager_entitlements WHERE status='ACTIVE' AND expires_at<=NOW()",0],
  ['duplicate active manager bindings',"SELECT count(*)::int n FROM (SELECT manager_id,device_id,count(*) n FROM manager_device_bindings WHERE active GROUP BY 1,2 HAVING count(*)>1)x",0]
 ];
 for(const [name,sql,expected] of checks){const q=await pool.query(sql);if(Number(q.rows[0].n)===expected){console.log('PASS:',name);passed++}else{console.log('FAIL:',name,q.rows[0].n);failed++}}
 const fks=await pool.query("SELECT count(*)::int n FROM information_schema.table_constraints WHERE constraint_type='FOREIGN KEY' AND table_name IN ('reservations','invoices','manual_payment_requests','stations','gn_ledger','lp_ledger')");
 if(Number(fks.rows[0].n)>=15){console.log('PASS: manager/customer/station FK coverage');passed++}else{console.log('FAIL: FK coverage',fks.rows[0].n);failed++}
}catch(e){console.error('DB_INTEGRITY_ERROR',e.stack||e);failed++}finally{await pool.end();console.log('Tests Result: Passed: '+passed+', Failed: '+failed);if(failed)process.exitCode=1}}
main();