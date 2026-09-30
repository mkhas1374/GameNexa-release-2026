const {Pool}=require('pg');
const {calculatePrice,cancelReservation,completeVipReservation}=require('./financialService');
require('dotenv').config();
const pool=new Pool({connectionString:process.env.DATABASE_URL});
const run='FIN_'+Date.now();
const mid='fin_'+run;
let cid,station;
async function main(){
 let passed=0,failed=0;
 try{
  await pool.query("INSERT INTO managers(id,username,password_hash,role) VALUES($1,$2,$3,'MANAGER')",[mid,mid,'test']);
  cid=(await pool.query("INSERT INTO customers(manager_id,phone_number,club_tier,wallet_balance,gn_balance,lp_balance) VALUES($1,$2,'DIAMOND',0,1000,1000) RETURNING id",[mid,'9'+run])).rows[0].id;
  station=(await pool.query("INSERT INTO stations(manager_id,name,controller_capacity,reservable,active) VALUES($1,$2,4,TRUE,TRUE) RETURNING id",[mid,'FIN_TEST'])).rows[0].id;
  const p=await calculatePrice(pool,mid,cid,'NORMAL_RESERVATION','PS4',2,90,false,null);
  if(Number(p.basePrice)===210000 && Number(p.finalPrice)===210000 && Number(p.depositAmount)===63000){console.log('PASS: exact duration pricing');passed++}else{console.log('FAIL: duration pricing',p);failed++}
  await pool.query("UPDATE customers SET pending_surcharge_percent=5 WHERE id=$1",[cid]);
  const ps=await calculatePrice(pool,mid,cid,'NORMAL_RESERVATION','PS4',1,60,false,null);
  if(Number(ps.finalPrice)===126000 && Number(ps.surchargeAmount)===6000){console.log('PASS: surcharge pricing');passed++}else{console.log('FAIL: surcharge pricing',ps);failed++}
  const rid=(await pool.query("INSERT INTO reservations(manager_id,customer_id,station_id,type,status,start_time,end_time,duration_minutes,snap_base_price,snap_final_price,snap_payable_amount,snap_deposit_amount,snap_cancellation_policy,snap_vip_policy) VALUES($1,$2,$3,'NORMAL_RESERVATION','CONFIRMED',NOW()+INTERVAL '10 hours',NOW()+INTERVAL '12 hours',120,84000,84000,84000,25200,$4::jsonb,'{}'::jsonb) RETURNING id",[mid,cid,station,JSON.stringify({atOrAbove24h:{thresholdMinutes:1440,gn:50,lp:50,walletPercent:100},above15h:{thresholdMinutes:900,gn:70,lp:70,walletPercent:90},above5h:{thresholdMinutes:300,gn:120,lp:120,walletPercent:85},above2h:{thresholdMinutes:120,gn:200,lp:200,walletPercent:80},within2h:{thresholdMinutes:0,gn:250,lp:250,walletPercent:70},lateCancellation:{gn:250,lp:250,walletPercent:0},noShow:{gn:250,lp:250,walletPercent:0}})] )).rows[0].id;
  await pool.query("INSERT INTO payment_transactions(manager_id,customer_id,reservation_id,provider,gateway_transaction_id,amount,currency,status,idempotency_key,verified_at) VALUES($1,$2,$3,'TEST',$4,84000,'IRT','SUCCESS',$5,NOW())",[mid,cid,rid,'tx_'+rid,'pay_'+rid]);
  const c=await cancelReservation(pool,mid,rid,'cancel_'+rid);
  if(Number(c.actualPaid)===84000 && Number(c.refundAmount)===71400 && Number(c.gnLoss)===120){console.log('PASS: cancellation exact refund');passed++}else{console.log('FAIL: cancellation',c);failed++}
  const c2=await cancelReservation(pool,mid,rid,'cancel_'+rid);
  if(c2.idempotent===true){console.log('PASS: cancellation idempotency');passed++}else{console.log('FAIL: cancellation idempotency',c2);failed++}
  const vip=(await pool.query("INSERT INTO reservations(manager_id,customer_id,station_id,type,status,start_time,end_time,duration_minutes,snap_base_price,snap_final_price,snap_payable_amount,snap_vip_policy) VALUES($1,$2,$3,'NORMAL_RESERVATION','CONFIRMED',NOW(),NOW()+INTERVAL '1 hour',60,100,100,100,$4::jsonb) RETURNING id",[mid,cid,station,JSON.stringify({isVip:true,vipGnReward:300,vipLpReward:300})])).rows[0].id;
  await completeVipReservation(pool,mid,vip,'vip_'+vip);
  const bal=await pool.query('SELECT gn_balance,lp_balance FROM customers WHERE id=$1',[cid]);
  if(Number(bal.rows[0].gn_balance)===1180 && Number(bal.rows[0].lp_balance)===1180){console.log('PASS: VIP reward');passed++}else{console.log('FAIL: VIP reward',bal.rows[0]);failed++}
 }catch(e){console.error('FINANCIAL_TEST_ERROR',e.stack||e);failed++}
 finally{try{await pool.query('DELETE FROM financial_audit_logs WHERE manager_id=$1',[mid]);await pool.query('DELETE FROM payment_transactions WHERE manager_id=$1',[mid]);await pool.query('DELETE FROM wallet_transactions WHERE manager_id=$1',[mid]);await pool.query('DELETE FROM gn_ledger WHERE manager_id=$1',[mid]);await pool.query('DELETE FROM lp_ledger WHERE manager_id=$1',[mid]);await pool.query('DELETE FROM reservations WHERE manager_id=$1',[mid]);await pool.query('DELETE FROM stations WHERE manager_id=$1',[mid]);await pool.query('DELETE FROM customers WHERE manager_id=$1',[mid]);await pool.query('DELETE FROM managers WHERE id=$1',[mid])}catch{}await pool.end();console.log('Tests Result: Passed: '+passed+', Failed: '+failed);if(failed)process.exitCode=1}
}
main();
