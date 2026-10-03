const bcrypt = require('bcrypt');
const crypto = require('crypto');
const { calculatePrice, cancelReservation, getReservationConfiguration, deepMerge } = require('./financialService');
const { bookReservation, transitionState } = require('./reservationService');

module.exports = function registerCanonicalRoutes({ app, pool, requireManagerAuth, requireActiveEntitlement, requireCustomerAuth, requireSuperManagerAuth, rateLimit }) {
  const manager = (req) => String(req.user?.managerId || req.user?.id || '');
  const json = (v, fallback) => v == null ? fallback : v;
  const DEFAULT_SUBSCRIPTION_STORE = {
    pageTitleFa: 'پلن‌های اشتراک گیم‌نکسا',
    pageTitleEn: 'GameNexa Subscription Plans',
    pageSubtitleFa: 'اشتراک مناسب کسب‌وکار خود را انتخاب کنید',
    pageSubtitleEn: 'Choose the subscription that fits your business',
    paymentInstructionFa: 'پس از پرداخت در فوربیکس، رسید پرداخت را برای Super Manager ارسال کنید. فعال‌سازی اشتراک پس از بررسی و تأیید دستی انجام می‌شود.',
    paymentInstructionEn: 'After payment on Forbix, send the payment receipt to the Super Manager. Activation is performed only after manual review and confirmation.',
    purchaseButtonFa: 'ادامه و پرداخت از طریق فوربیکس',
    purchaseButtonEn: 'Continue to Forbix Payment',
    currencyFa: 'تومان',
    currencyEn: 'Toman',
    supportMessageFa: 'برای تأیید پرداخت، رسید را از طریق بله یا تلگرام برای Super Manager ارسال کنید.',
    supportMessageEn: 'For payment confirmation, send the receipt to the Super Manager via Bale or Telegram.',
    plans: [
      {id:'MONTHLY',nameFa:'یک ماهه',nameEn:'Monthly',price:500000,durationDays:30,active:true,sortOrder:1,paymentUrl:''},
      {id:'THREE_MONTHS',nameFa:'سه ماهه',nameEn:'3 Months',price:1200000,durationDays:90,active:true,sortOrder:2,paymentUrl:''},
      {id:'YEARLY',nameFa:'یک ساله',nameEn:'Yearly',price:3259000,durationDays:365,active:true,sortOrder:3,paymentUrl:''}
    ]
  };
  async function getSubscriptionStore() {
    const q = await pool.query('SELECT settings, version_number FROM subscription_store_config WHERE id=1');
    const raw = q.rows[0]?.settings;
    const store = raw && typeof raw === 'object' ? raw : DEFAULT_SUBSCRIPTION_STORE;
    const plans = Array.isArray(store.plans) ? store.plans : [];
    return { ...DEFAULT_SUBSCRIPTION_STORE, ...store, plans };
  }
  function publicSubscriptionPlans(store) {
    return store.plans.filter(p => p && p.active !== false).map(p => ({
      id: String(p.id || ''),
      name: String(p.nameFa || p.nameEn || p.id || ''),
      nameFa: String(p.nameFa || ''),
      nameEn: String(p.nameEn || ''),
      price: Number(p.price || 0),
      durationDays: Number(p.durationDays || 0),
      paymentUrl: String(p.paymentUrl || ''),
      description: String(p.descriptionFa || p.descriptionEn || ''),
      savingText: String(p.descriptionFa || p.descriptionEn || ''),
      descriptionFa: String(p.descriptionFa || ''),
      descriptionEn: String(p.descriptionEn || ''),
      maxDevices: 1,
      sortOrder: Number(p.sortOrder || 0),
      pageTitleFa: String(store.pageTitleFa || ''),
      pageTitleEn: String(store.pageTitleEn || ''),
      pageSubtitleFa: String(store.pageSubtitleFa || ''),
      pageSubtitleEn: String(store.pageSubtitleEn || ''),
      paymentInstructionFa: String(store.paymentInstructionFa || ''),
      paymentInstructionEn: String(store.paymentInstructionEn || ''),
      purchaseButtonFa: String(store.purchaseButtonFa || ''),
      purchaseButtonEn: String(store.purchaseButtonEn || ''),
      currencyFa: String(store.currencyFa || 'تومان'),
      currencyEn: String(store.currencyEn || 'Toman'),
      supportMessageFa: String(store.supportMessageFa || ''),
      supportMessageEn: String(store.supportMessageEn || '')
    })).sort((a,b)=>a.sortOrder-b.sortOrder);
  }

  const normalizeCustomer = (r) => ({
    id:r.id, manager_id:r.manager_id, fullName:r.full_name || '', phoneNumber:r.phone_number || '',
    debt:Number(r.debt || 0), credit:Number(r.credit || 0), points:Number(r.gn_balance || 0),
    availableGn:Number(r.gn_balance || 0), pendingGn:Number(r.pending_gn || 0), lp:Number(r.lp_balance || 0),
    tier:r.club_tier || 'BRONZE', inviteCode:r.invite_code || '', invitedByCode:r.invited_by_code || '',
    description:r.description || '', totalQualifiedSpend:Number(r.total_qualified_spend || 0), totalVisitsCount:Number(r.total_visits_count || 0)
  });
  async function cfgGet(mid) {
    const q=await pool.query('SELECT settings FROM configuration_revisions WHERE manager_id=$1 ORDER BY version_number DESC LIMIT 1',[mid]);
    return q.rows[0]?.settings || {};
  }
  async function cfgPut(mid, patch) {
    const c=await pool.connect();
    try {
      await c.query('BEGIN');
      await c.query('SELECT pg_advisory_xact_lock(hashtext($1))',[String(mid)]);
      const v=await c.query('SELECT COALESCE(MAX(version_number),0)+1 AS v FROM configuration_revisions WHERE manager_id=$1',[mid]);
      const current=(await c.query('SELECT settings FROM configuration_revisions WHERE manager_id=$1 ORDER BY version_number DESC LIMIT 1',[mid])).rows[0]?.settings || {};
      const merged=deepMerge(current,patch);
      await c.query('INSERT INTO configuration_revisions(manager_id,version_number,settings) VALUES($1,$2,$3)',[mid,v.rows[0].v,JSON.stringify(merged)]);
      await c.query('COMMIT');
      return merged;
    } catch(e) { try{await c.query('ROLLBACK')}catch(_){} throw e; } finally { c.release(); }
  }
  function validateReservationPatch(patch) {
    const r=patch.reservationRules || {};
    const positiveList=(v,name)=>{if(v!==undefined && (!Array.isArray(v)||v.some(x=>!Number.isInteger(Number(x))||Number(x)<=0))) throw new Error(`INVALID_${name}`);};
    const nonNegative=(v,name)=>{if(v!==undefined && (!Number.isFinite(Number(v))||Number(v)<0)) throw new Error(`INVALID_${name}`);};
    const percent=(v,name)=>{if(v!==undefined && (!Number.isFinite(Number(v))||Number(v)<0||Number(v)>100)) throw new Error(`INVALID_${name}`);};
    positiveList(r.normalDurationsMinutes,'NORMAL_DURATIONS'); positiveList(r.vipDurationsMinutes,'VIP_DURATIONS');
    for(const k of ['vipMinDurationMinutes','fullHallMinDurationMinutes','exclusiveFullDayDurationMinutes','paymentDeadlineMinutes','vipPaymentDeadlineMinutes','arrivalWarningMinutes']) nonNegative(r[k],k);
    if(r.arrivalReminderMinutes!==undefined) positiveList(r.arrivalReminderMinutes,'ARRIVAL_REMINDERS');
    if(r.vipStartTime!==undefined && !/^([01]\\d|2[0-3]):[0-5]\\d$/.test(String(r.vipStartTime))) throw new Error('INVALID_VIP_START_TIME');
    if(r.vipEndTime!==undefined && !/^([01]\\d|2[0-3]):[0-5]\\d$/.test(String(r.vipEndTime))) throw new Error('INVALID_VIP_END_TIME');
    nonNegative(r.vipReward?.gn,'VIP_GN_REWARD'); nonNegative(r.vipReward?.lp,'VIP_LP_REWARD');
    percent(patch.policies?.reservation?.depositPercent,'DEPOSIT_PERCENT');
    nonNegative(r.vipPrice,'VIP_PRICE'); nonNegative(patch.pricing?.reservations?.exclusiveFullDay,'EXCLUSIVE_PRICE');
    for(const [name,stage] of Object.entries(r.cancellation||{})) { nonNegative(stage?.thresholdMinutes,`${name}_THRESHOLD`); nonNegative(stage?.gn,`${name}_GN`); nonNegative(stage?.lp,`${name}_LP`); percent(stage?.walletPercent,`${name}_WALLET`); nonNegative(stage?.restrictionDays,`${name}_RESTRICTION`); percent(stage?.surchargePercent,`${name}_SURCHARGE`); }
    for(const [name,stage] of [['lateCancellation',r.lateCancellation],['noShow',r.noShow]]) { nonNegative(stage?.gn,`${name}_GN`); nonNegative(stage?.lp,`${name}_LP`); percent(stage?.walletPercent,`${name}_WALLET`); nonNegative(stage?.restrictionDays,`${name}_RESTRICTION`); percent(stage?.surchargePercent,`${name}_SURCHARGE`); }
  }

  // Canonical manager customer resource. One route family; no manager id is accepted from the client.
  app.get('/api/v1/manager/customers', requireManagerAuth, requireActiveEntitlement, async (req,res)=>{ try {
    const q=await pool.query("SELECT * FROM customers WHERE manager_id=$1 AND COALESCE(description,'') NOT LIKE '[GAMENEX_ARCHIVED:%' ORDER BY id DESC",[manager(req)]);
    res.json(q.rows.map(normalizeCustomer));
  } catch(e){res.status(500).json({error:'Internal server error'});} });

  app.post('/api/v1/manager/customers', requireManagerAuth, requireActiveEntitlement, async (req,res)=>{ try {
    const mid=manager(req), b=req.body||{}; const phone=String(b.phoneNumber||b.phone_number||b.customer_phone||'').trim();
    const name=String(b.fullName||b.full_name||b.customer_name||'').trim(); if(!phone||!name) return res.status(400).json({error:'fullName and phoneNumber are required'});
    const existing=await pool.query('SELECT id FROM customers WHERE manager_id=$1 AND phone_number=$2 LIMIT 1',[mid,phone]);
    let q;
    if(existing.rows[0]) q=await pool.query("UPDATE customers SET full_name=$1,debt=COALESCE($2,debt),credit=COALESCE($3,credit),description=COALESCE($4,description),club_tier=COALESCE($5,club_tier),invite_code=COALESCE(NULLIF($6,''),invite_code),invited_by_code=COALESCE(NULLIF($7,''),invited_by_code),updated_at=NOW() WHERE id=$8 AND manager_id=$9 RETURNING *",[name,b.debt,b.credit,b.description,b.tier,b.inviteCode,b.invitedByCode,existing.rows[0].id,mid]);
    else q=await pool.query("INSERT INTO customers(manager_id,phone_number,full_name,debt,credit,description,club_tier,invite_code,invited_by_code,pending_gn,lp_balance,gn_balance,last_activity_at,last_tier_review_at) VALUES($1,$2,$3,COALESCE($4,0),COALESCE($5,0),COALESCE($6,''),COALESCE($7,'BRONZE'),NULLIF($8,''),NULLIF($9,''),0,0,0,NOW(),NOW()) RETURNING *",[mid,phone,name,b.debt,b.credit,b.description,b.tier,b.inviteCode,b.invitedByCode]);
    if(typeof b.password==='string' && b.password.length>=8) await pool.query('UPDATE customers SET password_hash=$1,token_version=token_version+1,updated_at=NOW() WHERE id=$2 AND manager_id=$3',[await bcrypt.hash(b.password,12),q.rows[0].id,mid]);
    res.status(existing.rows[0]?200:201).json(normalizeCustomer(q.rows[0]));
  } catch(e){res.status(500).json({error:'Internal server error'});} });

  // Customer deletion is logical/archival: financial, invoice, reservation and session history
  // remains intact, while the customer is removed from the active customer directory and login.
  app.delete('/api/v1/manager/customers/:id', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    const mid=manager(req), id=Number(req.params.id);
    if(!Number.isInteger(id)||id<=0) return res.status(400).json({error:'Invalid customer id'});
    const c=await pool.connect();
    try {
      await c.query('BEGIN');
      const customer=(await c.query("SELECT id,phone_number,description FROM customers WHERE id=$1 AND manager_id=$2 FOR UPDATE",[id,mid])).rows[0];
      if(!customer){await c.query('ROLLBACK');return res.status(404).json({error:'Customer not found'});}
      const active=(await c.query("SELECT session_id FROM active_session_customer_claims WHERE customer_id=$1 AND manager_id=$2 LIMIT 1",[id,mid])).rows[0];
      if(active){await c.query('ROLLBACK');return res.status(409).json({success:false,code:'CUSTOMER_IN_ACTIVE_SESSION',sessionId:active.session_id});}
      const stamp=Date.now();
      await c.query("UPDATE customers SET phone_number=$1,description=$2,updated_at=NOW() WHERE id=$3 AND manager_id=$4",['archived:'+id+':'+stamp,'[GAMENEX_ARCHIVED:'+stamp+'] '+String(customer.description||''),id,mid]);
      await c.query('COMMIT');
      return res.json({success:true,archived:true,customerId:id});
    } catch(e){try{await c.query('ROLLBACK')}catch(_){} console.error('[customer-archive]',e?.message||e);return res.status(500).json({error:'Customer archive failed'});}
    finally{c.release();}
  });
  app.post('/api/v1/manager/customers/delete-batch', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    const mid=manager(req), ids=Array.isArray(req.body?.customerIds)?req.body.customerIds.map(Number).filter(Number.isInteger):[];
    if(!ids.length) return res.status(400).json({error:'customerIds are required'});
    const c=await pool.connect();
    try{
      await c.query('BEGIN');
      const active=await c.query("SELECT customer_id,session_id FROM active_session_customer_claims WHERE manager_id=$1 AND customer_id=ANY($2::int[]) LIMIT 1",[mid,ids]);
      if(active.rows[0]){await c.query('ROLLBACK');return res.status(409).json({success:false,code:'CUSTOMER_IN_ACTIVE_SESSION',customerId:active.rows[0].customer_id,sessionId:active.rows[0].session_id});}
      const rows=await c.query("SELECT id,description FROM customers WHERE manager_id=$1 AND id=ANY($2::int[]) FOR UPDATE",[mid,ids]);
      for(const row of rows.rows){
        const stamp=Date.now();
        await c.query("UPDATE customers SET phone_number=$1,description=$2,updated_at=NOW() WHERE id=$3 AND manager_id=$4",['archived:'+row.id+':'+stamp,'[GAMENEX_ARCHIVED:'+stamp+'] '+String(row.description||''),row.id,mid]);
      }
      await c.query('COMMIT');
      return res.json({success:true,archivedCustomerIds:rows.rows.map(r=>r.id),count:rows.rows.length});
    }catch(e){try{await c.query('ROLLBACK')}catch(_){} return res.status(500).json({error:'Customer batch archive failed'});}
    finally{c.release();}
  });

  // Canonical configuration/settings resource. JSONB is used only for manager-editable, variable settings.
  app.get('/api/v1/manager/settings/:key', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgGet(manager(req));res.json({key:req.params.key,value:c[req.params.key] ?? ''});}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/manager/reservation-configuration', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const cfg=await getReservationConfiguration(pool,manager(req));res.json({success:true,configurationRevisionId:cfg.revisionId,version:cfg.version,configuration:{reservationRules:cfg.reservationRules,pricing:{consoles:cfg.consoles,reservations:cfg.reservations},policies:{reservation:cfg.reservationPolicy}}});}catch(e){res.status(500).json({error:'Reservation configuration lookup failed'});} });
  app.put('/api/v1/manager/reservation-configuration', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const b=req.body||{};const patch={};if(b.reservationRules&&typeof b.reservationRules==='object')patch.reservationRules=b.reservationRules;if(b.pricing&&typeof b.pricing==='object')patch.pricing=b.pricing;if(b.policies&&typeof b.policies==='object')patch.policies=b.policies;if(!Object.keys(patch).length)return res.status(400).json({error:'Reservation configuration patch is empty'});validateReservationPatch(patch);const merged=await cfgPut(manager(req),patch);res.json({success:true,configuration:merged});}catch(e){res.status(400).json({error:'Reservation configuration update failed'});} });
  app.put('/api/v1/manager/settings/:key', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgPut(manager(req),{[req.params.key]:req.body?.value ?? ''});res.json({key:req.params.key,value:c[req.params.key]});}catch(e){res.status(500).json({error:'Internal server error'});} });

  // Console types and buffet products live inside the manager configuration document.
  app.get('/api/v1/manager/console-types', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{res.json((await cfgGet(manager(req))).consoleTypes||[]);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/console-types', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgGet(manager(req));const a=Array.isArray(c.consoleTypes)?c.consoleTypes:[];const x=req.body||{};const i=a.findIndex(v=>v.name===x.name);if(i>=0)a[i]=x;else a.push(x);await cfgPut(manager(req),{consoleTypes:a});res.json(x);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.delete('/api/v1/manager/console-types/:name', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgGet(manager(req));await cfgPut(manager(req),{consoleTypes:(c.consoleTypes||[]).filter(v=>v.name!==req.params.name)});res.status(204).end();}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/manager/products', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{res.json((await cfgGet(manager(req))).products||[]);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/products', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgGet(manager(req));const a=Array.isArray(c.products)?c.products:[];const x=req.body||{};const i=a.findIndex(v=>v.name===x.name);if(i>=0)a[i]=x;else a.push(x);await cfgPut(manager(req),{products:a});res.json(x);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.delete('/api/v1/manager/products/:name', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgGet(manager(req));await cfgPut(manager(req),{products:(c.products||[]).filter(v=>v.name!==req.params.name)});res.status(204).end();}catch(e){res.status(500).json({error:'Internal server error'});} });

  // Station configuration/state resource.
  app.get('/api/v1/manager/stations', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query('SELECT * FROM stations WHERE manager_id=$1 ORDER BY id',[manager(req)]);res.json(q.rows.map((x,i)=>({id:x.id,status:x.active?'FREE':'DISABLED',controllerCount:x.controller_capacity,consoleType:x.console_type,name:(/^ایستگاه\s+\d+$/.test(String(x.name||'')) || !String(x.name||'').trim()) ? ('ایستگاه '+(i+1)) : x.name,reservable:x.reservable})));}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/stations', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const mid=manager(req),b=req.body||{},id=Number(b.id);const q=await pool.query(`INSERT INTO stations(id,manager_id,name,controller_capacity,console_type,active,reservable) VALUES($1,$2,$3,$4,$5,$6,$7) ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,controller_capacity=EXCLUDED.controller_capacity,console_type=EXCLUDED.console_type,active=EXCLUDED.active,reservable=EXCLUDED.reservable,updated_at=NOW() WHERE stations.manager_id=$2 RETURNING *`,[id,mid,b.name||('ایستگاه '+id),Number(b.controllerCount||b.controller_capacity||1),b.consoleType||b.console_type||'PS5',b.status!=='DISABLED',b.reservable!==false]);res.json(q.rows[0]);}catch(e){res.status(500).json({error:'Internal server error'});} });

  // Legacy-looking station actions are represented by the canonical station session engine already used by Android.
  app.get('/api/v1/manager/live-stations', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query(`SELECT s.*,g.id session_id,g.status session_status,g.started_at,g.ended_at,g.game_cost,g.buffet_cost,g.total_cost FROM stations s LEFT JOIN LATERAL (SELECT * FROM game_sessions g WHERE g.station_id=s.id AND g.manager_id=s.manager_id AND g.status IN ('ACTIVE','PAUSED') ORDER BY g.created_at DESC LIMIT 1) g ON TRUE WHERE s.manager_id=$1 ORDER BY s.id`,[manager(req)]);res.json(q.rows);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/live-stations', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const mid=manager(req),b=req.body||{},id=Number(b.id||0);if(!Number.isInteger(id)||id<=0)return res.status(400).json({error:'Valid station id is required'});const q=await pool.query(`INSERT INTO stations(id,manager_id,name,controller_capacity,console_type,active,reservable) VALUES($1,$2,$3,$4,$5,$6,TRUE) ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,controller_capacity=EXCLUDED.controller_capacity,console_type=EXCLUDED.console_type,active=EXCLUDED.active,updated_at=NOW() WHERE stations.manager_id=$2 RETURNING *`,[id,mid,String(b.name||('ایستگاه '+id)),Math.max(1,Number(b.controllerCount||1)),String(b.consoleType||'PS5'),String(b.status||'FREE')!=='DISABLED']);if(!q.rows[0])return res.status(404).json({error:'Station not found for this Manager'});res.json({success:true,canonical:true,station:q.rows[0]});}catch(e){console.error('live-stations sync error:',e);res.status(500).json({error:'Live station synchronization failed'});}});

  // Orders and session history are stored in the canonical configuration until a dedicated legacy schema exists.
  app.get('/api/v1/manager/orders/:stationId', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgGet(manager(req));const o=c.stationOrders||{};res.json(o[String(req.params.stationId)]||[]);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/orders', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgGet(manager(req));const o={...(c.stationOrders||{})};const x=req.body||{};const k=String(x.stationId);const a=Array.isArray(o[k])?o[k]:[];const i=a.findIndex(v=>String(v.id)===String(x.id));if(i>=0)a[i]=x;else a.push(x);o[k]=a;await cfgPut(manager(req),{stationOrders:o});res.json(x);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.delete('/api/v1/manager/orders/:id', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgGet(manager(req));const o={...(c.stationOrders||{})};for(const k of Object.keys(o))o[k]=(o[k]||[]).filter(v=>String(v.id)!==String(req.params.id));await cfgPut(manager(req),{stationOrders:o});res.status(204).end();}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.delete('/api/v1/manager/orders/station/:stationId', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgGet(manager(req));const o={...(c.stationOrders||{})};delete o[String(req.params.stationId)];await cfgPut(manager(req),{stationOrders:o});res.status(204).end();}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/manager/session-history', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query(`SELECT id,station_id,console_type,controller_count,EXTRACT(EPOCH FROM started_at)*1000 start_time_millis,EXTRACT(EPOCH FROM ended_at)*1000 end_time_millis,game_cost,buffet_cost,total_cost FROM game_sessions WHERE manager_id=$1 AND ended_at IS NOT NULL ORDER BY ended_at DESC`,[manager(req)]);res.json(q.rows);}catch(e){res.status(500).json({error:'Internal server error'});} });

  // Canonical reservation collection; all writes are manager-scoped by JWT.
  app.get('/api/v1/manager/reservations', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query(`SELECT r.*,EXTRACT(EPOCH FROM r.start_time)*1000 AS start_time_millis,c.full_name,c.phone_number FROM reservations r JOIN customers c ON c.id=r.customer_id AND c.manager_id=r.manager_id WHERE r.manager_id=$1 ORDER BY r.start_time DESC`,[manager(req)]);res.json(q.rows);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/reservations', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    const b=req.body||{};
    const mid=manager(req);
    const stationId=Number(b.stationId||0);
    const isVip=Boolean(b.isVip);
    const phone=String(b.phoneNumber||b.phone_number||'').trim();
    const customerId=Number(b.customerId||0);
    const key=String(req.headers['idempotency-key']||b.idempotencyKey||'').trim();
    if((!isVip && !stationId) || (!customerId && !phone) || !key) return res.status(400).json({error:'customerId/phoneNumber, stationId (for normal) and idempotencyKey are required'});
    const c=await pool.connect();
    try{
      await c.query('BEGIN');
      const customer = customerId
        ? (await c.query('SELECT id FROM customers WHERE id=$1 AND manager_id=$2 FOR UPDATE',[customerId,mid])).rows[0]
        : (await c.query('SELECT id FROM customers WHERE phone_number=$1 AND manager_id=$2 FOR UPDATE',[phone,mid])).rows[0];
      if(!customer){await c.query('ROLLBACK');return res.status(404).json({error:'Customer not found for this Manager'});}
      const prior=await c.query('SELECT reservation_ids FROM reservation_request_idempotency WHERE manager_id=$1 AND idempotency_key=$2 AND customer_id=$3 FOR UPDATE',[mid,key,customer.id]);
      if(prior.rows[0]){await c.query('COMMIT');return res.json({success:true,reservation:prior.rows[0].reservation_ids,idempotent:true});}
      const ids=await bookReservation({
        managerId:mid,
        customerId:customer.id,
        type:isVip ? 'FULL_HALL' : 'NORMAL_RESERVATION',
        stationIds:isVip ? [] : [stationId],
        startTime:b.startTime||new Date(Number(b.reservationTimeMillis||0)).toISOString(),
        durationMinutes:Number(b.durationMinutes),
        controllersCount:Number(b.controllersCount||1),
        isVip,
        actor:'MANAGER'
      },c);
      await c.query('INSERT INTO reservation_request_idempotency(manager_id,customer_id,idempotency_key,reservation_ids) VALUES($1,$2,$3,$4::jsonb)',[mid,customer.id,key,JSON.stringify(ids)]);
      await c.query('COMMIT');
      res.status(201).json({success:true,reservation:ids,idempotent:false});
    }catch(e){try{await c.query('ROLLBACK')}catch(_){}res.status(400).json({error:e.message});}finally{c.release();}
  });
  app.delete('/api/v1/manager/reservations/:id', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{ return res.status(405).json({error:'Physical reservation deletion is disabled; use cancellation/rejection workflow'}); });
  app.post('/api/v1/manager/reservations/:id/cancel', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    const c=await pool.connect();
    try{
      const key=String(req.headers['idempotency-key']||req.body?.idempotencyKey||'').trim();
      if(!key)return res.status(400).json({error:'A valid idempotency key is required'});
      await c.query('BEGIN');
      const result=await cancelReservation(c,manager(req),req.params.id,key,null);
      await c.query('COMMIT');
      res.json({success:true,refundDetails:result});
    }catch(e){try{await c.query('ROLLBACK')}catch(_){}res.status(400).json({error:e.message});}finally{c.release();}
  });
  app.put('/api/v1/manager/reservations/:id/status', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    const mid=manager(req), id=Number(req.params.id), s=String(req.body?.status||'').toUpperCase();
    if(!id || !s) return res.status(400).json({error:'Invalid reservation status payload'});
    const c=await pool.connect();
    try {
      await c.query('BEGIN');
      const q=await c.query('SELECT * FROM reservations WHERE id=$1 AND manager_id=$2 FOR UPDATE',[id,mid]);
      const r=q.rows[0]; if(!r){await c.query('ROLLBACK');return res.status(404).json({error:'Reservation not found'});}
      const vipPolicy=r.snap_vip_policy ? (typeof r.snap_vip_policy==='string'?JSON.parse(r.snap_vip_policy):r.snap_vip_policy) : {};
      const isVip=Boolean(vipPolicy.isVip);
      const allowedByStatus = {
        PENDING: {REJECT:'REJECTED'},
        PAYMENT_PENDING: {REJECT:'REJECTED'},
        PENDING_APPROVAL: {CONFIRMED:'CONFIRMED', REJECTED:'REJECTED'},
        VIP_PENDING_PAYMENT: {REJECTED:'REJECTED', EXPIRED:'EXPIRED'},
        VIP_PAYMENT_PAID: {CONFIRMED:'CONFIRMED', REJECTED:'REJECTED', EXPIRED:'EXPIRED'},
        CONFIRMED: {ACTIVE:'ACTIVE', NO_SHOW:'NO_SHOW'},
        ACTIVE: {COMPLETED:'COMPLETED'}
      };
      const transition=allowedByStatus[r.status]?.[s];
      if(!transition){await c.query('ROLLBACK');return res.status(409).json({error:'Invalid reservation state transition'});}
      if(s==='REJECTED'){
        const paid=Number((await c.query("SELECT COALESCE(SUM(amount),0) paid FROM payment_transactions WHERE reservation_id=$1 AND manager_id=$2 AND status='SUCCESS'",[id,mid])).rows[0].paid||0);
        if(paid>0){
          const refundKey='reservation-reject-refund:'+id;
          const refund=await c.query(`
            INSERT INTO wallet_transactions(manager_id,customer_id,type,reference_type,amount,reference_id,idempotency_key)
            VALUES($1,$2,'CREDIT','RESERVATION_REJECTION',$3,$4,$5)
            ON CONFLICT(idempotency_key) DO NOTHING
            RETURNING id
          `,[mid,r.customer_id,paid,String(id),refundKey]);
          if(refund.rowCount>0) await c.query('UPDATE customers SET wallet_balance=wallet_balance+$1 WHERE id=$2 AND manager_id=$3',[paid,r.customer_id,mid]);
        }
      }
      if(s==='CONFIRMED'){
        if(isVip && r.status!=='VIP_PAYMENT_PAID'){await c.query('ROLLBACK');return res.status(422).json({error:'VIP must be fully paid before Manager confirmation'});}
        if(!isVip && r.status!=='PENDING_APPROVAL'){await c.query('ROLLBACK');return res.status(422).json({error:'Reservation must be pending Manager approval'});}
        const paid=await c.query("SELECT COALESCE(SUM(amount),0) paid FROM payment_transactions WHERE reservation_id=$1 AND manager_id=$2 AND status='SUCCESS'",[id,mid]);
        if(isVip && Number(paid.rows[0].paid||0) < Number(r.snap_final_price||0)){await c.query('ROLLBACK');return res.status(422).json({error:'VIP requires full verified payment before confirmation'});}
        if(isVip){
          const overlaps=await c.query(`
            SELECT id,status,snap_vip_policy FROM reservations
            WHERE manager_id=$1
              AND station_id IS NOT NULL
              AND COALESCE(snap_vip_policy->>'isVip','false') <> 'true'
              AND status NOT IN ('CANCELLED','EXPIRED','NO_SHOW','REJECTED','SUPERSEDED_BY_VIP','SUPERSEDED_BY_VIP_PRIORITY','VIP_PENDING_PAYMENT','VIP_PAYMENT_PAID')
              AND start_time < $2 AND end_time > $3
            FOR UPDATE
          `,[mid,r.end_time,r.start_time]);
          const competingFullHallVip=await c.query(`
            SELECT id,status FROM reservations
            WHERE manager_id=$1 AND station_id IS NULL AND id<>$2
              AND snap_vip_policy->>'isVip'='true'
              AND status IN ('CONFIRMED','ACTIVE')
              AND start_time < $3 AND end_time > $4
            FOR UPDATE
          `,[mid,id,r.end_time,r.start_time]);
          if(competingFullHallVip.rows.length>0){await c.query('ROLLBACK');return res.status(409).json({error:'Another confirmed VIP full-hall reservation overlaps this time'});}
          for(const row of overlaps.rows){
            const paidRow=await c.query("SELECT COALESCE(SUM(amount),0) paid FROM payment_transactions WHERE reservation_id=$1 AND manager_id=$2 AND status='SUCCESS'",[row.id,mid]);
            const paid=Number(paidRow.rows[0]?.paid||0);
            if(paid>0 && row.customer_id){
              const refundKey='reservation-vip-supersede-refund:'+row.id;
              const refund=await c.query("INSERT INTO wallet_transactions(manager_id,customer_id,type,reference_type,amount,reference_id,idempotency_key) VALUES($1,$2,'CREDIT','VIP_PRIORITY_SUPERSEDE',$3,$4,$5) ON CONFLICT(idempotency_key) DO NOTHING RETURNING id",[mid,row.customer_id,paid,String(row.id),refundKey]);
              if(refund.rowCount>0) await c.query('UPDATE customers SET wallet_balance=wallet_balance+$1,updated_at=NOW() WHERE id=$2 AND manager_id=$3',[paid,row.customer_id,mid]);
            }
            await c.query("UPDATE reservations SET status='SUPERSEDED_BY_VIP_PRIORITY',updated_at=NOW() WHERE id=$1 AND manager_id=$2",[row.id,mid]);
            await c.query('INSERT INTO reservation_audit_logs(manager_id,reservation_id,actor,old_status,new_status,reason) VALUES($1,$2,$3,$4,$5,$6)',[mid,row.id,'MANAGER',row.status,'SUPERSEDED_BY_VIP_PRIORITY',paid>0?'Confirmed VIP priority; paid amount credited to wallet':'Confirmed VIP priority']);
          }
        }
      }
      if(r.status===s){await c.query('COMMIT');return res.json(r);}
      await c.query('UPDATE reservations SET status=$1,updated_at=NOW() WHERE id=$2 AND manager_id=$3',[s,id,mid]);
      await c.query('INSERT INTO reservation_audit_logs(manager_id,reservation_id,actor,old_status,new_status,reason) VALUES($1,$2,$3,$4,$5,$6)',[mid,id,'MANAGER',r.status,s,'Manager status change']);
      const out=await c.query('SELECT * FROM reservations WHERE id=$1 AND manager_id=$2',[id,mid]);
      await c.query('COMMIT'); return res.json(out.rows[0]);
    } catch(e){try{await c.query('ROLLBACK')}catch(_){}return res.status(400).json({error:e.message});} finally{c.release();}
  });

  app.post('/api/v1/manager/settings', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const key=String(req.body?.key||'');if(!key)return res.status(400).json({error:'key required'});const c=await cfgPut(manager(req),{[key]:req.body?.value??''});res.json({success:true,key,value:c[key]});}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/session-history', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const b=req.body||{},q=await pool.query('SELECT id FROM game_sessions WHERE manager_id=$1 AND station_id=$2 AND ended_at IS NOT NULL ORDER BY ended_at DESC LIMIT 1',[manager(req),b.stationId]);res.json({...b,id:q.rows[0]?.id||b.id||0});}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.delete('/api/v1/manager/session-history', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{res.status(204).end();});
  app.post('/api/v1/customer/reservations', requireCustomerAuth, async(req,res)=>{
    const b=req.body||{}; const key=String(req.headers['idempotency-key']||b.idempotencyKey||'').trim();
    if(!key) return res.status(400).json({error:'A valid idempotency key is required'});
    const c=await pool.connect();
    try {
      await c.query('BEGIN');
      const prior=await c.query('SELECT reservation_ids FROM reservation_request_idempotency WHERE manager_id=$1 AND idempotency_key=$2 AND customer_id=$3 FOR UPDATE',[req.user.managerId,key,req.user.id]);
      if(prior.rows[0]) { await c.query('COMMIT'); return res.json({success:true,reservation:prior.rows[0].reservation_ids,idempotent:true}); }
      const ids=await bookReservation({managerId:req.user.managerId,customerId:req.user.id,type:b.type||b.reservationType||'NORMAL_RESERVATION',stationIds:b.stationIds||((b.stationId!=null)?[Number(b.stationId)]:[]),startTime:b.startTime||new Date(Number(b.reservationTimeMillis||0)).toISOString(),durationMinutes:Number(b.durationMinutes),controllersCount:Number(b.controllersCount||1),isVip:!!b.isVip,actor:'CUSTOMER',timeSlot:b.timeSlot||null},c);
      await c.query('INSERT INTO reservation_request_idempotency(manager_id,customer_id,idempotency_key,reservation_ids) VALUES($1,$2,$3,$4::jsonb)',[req.user.managerId,req.user.id,key,JSON.stringify(ids)]);
      await c.query('COMMIT'); return res.status(201).json({success:true,reservation:ids,idempotent:false});
    } catch(e) { try{await c.query('ROLLBACK')}catch(_){} return res.status(400).json({error:e.message}); }
    finally { c.release(); }
  });
  app.get('/api/v1/customer/stations', requireCustomerAuth, async(req,res)=>{
    try {
      const q=await pool.query(
        `SELECT s.id,s.name,s.console_type,s.controller_capacity,s.reservable,s.active,
          EXISTS(SELECT 1 FROM reservations r WHERE r.manager_id=s.manager_id AND r.station_id=s.id) AS has_prior_reservation
         FROM stations s
         WHERE s.manager_id=$1 AND s.active=TRUE AND s.reservable=TRUE
         ORDER BY s.id`,
        [req.user.managerId]
      );
      res.json({success:true,stations:q.rows});
    } catch(e) { res.status(500).json({error:'Customer stations lookup failed'}); }
  });
  app.get('/api/v1/customer/reservations/rules', requireCustomerAuth, async(req,res)=>{
    try {
      const cfg=await getReservationConfiguration(pool,req.user.managerId);
      const r=cfg.reservationRules || {};
      const duration=Number(req.query.durationMinutes||0);
      const render=(template, values)=>{
        let s=String(template||'');
        for(const [k,v] of Object.entries(values||{})) s=s.split('{' + k + '}').join(String(v));
        return s;
      };
      const c=r.cancellation||{};
      const safe={
        normalDurationsMinutes:r.normalDurationsMinutes||[],
        vipDurationsMinutes:r.vipDurationsMinutes||[],
        vipMinDurationMinutes:r.vipMinDurationMinutes||0,
        vipStartTime:r.vipStartTime||'',
        vipEndTime:r.vipEndTime||'',
        paymentDeadlineMinutes:r.paymentDeadlineMinutes||0,
        vipPaymentDeadlineMinutes:r.vipPaymentDeadlineMinutes||0,
        cancellation:c,
        lateCancellation:r.lateCancellation||{},
        noShow:r.noShow||{},
        vipReward:r.vipReward||{},
        vipPrice:Number(r.vipPrice||0),
        arrivalWarningMinutes:Number(r.arrivalWarningMinutes||0),
        arrivalReminderMinutes:Array.isArray(r.arrivalReminderMinutes)?r.arrivalReminderMinutes.map(Number).filter(Number.isFinite):[],
        messages:r.messages||{}
      };
      safe.messages=Object.fromEntries(Object.entries(safe.messages).map(([k,v])=>{
        const stage=k==='cancel24'?c.atOrAbove24h:k==='cancel15'?c.above15h:k==='cancel5'?c.above5h:k==='cancel2'?c.within2h:c.within2h;
        return [k,render(v,{
          duration:duration/60,
          payment_deadline:k==='vipPaymentDeadline'?safe.vipPaymentDeadlineMinutes:safe.paymentDeadlineMinutes,
          minutes_before_arrival:(r.arrivalWarningMinutes||0),
          gn_penalty:stage?.gn,
          lp_penalty:stage?.lp,
          refund_percent:stage?.walletPercent,
          threshold_minutes:stage?.thresholdMinutes,
          restriction_days:stage?.restrictionDays||0,
          surcharge_percent:stage?.surchargePercent||0,
          vip_price:safe.vipPrice,
          vip_gn_reward:safe.vipReward?.gn,
          vip_lp_reward:safe.vipReward?.lp
        })];
      }));
      res.json({success:true,configurationRevisionId:cfg.revisionId,version:cfg.version,rules:safe});
    } catch(e) { res.status(500).json({error:'Reservation rules lookup failed'}); }
  });
  app.post('/api/v1/customer/reservations/pricing-preview', requireCustomerAuth, async(req,res)=>{try{const b=req.body||{},stationId=b.stationId,st=stationId?await pool.query('SELECT console_type FROM stations WHERE id=$1 AND manager_id=$2 AND active=TRUE',[stationId,req.user.managerId]):{rows:[]};const data=await calculatePrice(pool,req.user.managerId,req.user.id,b.type||b.reservationType||'NORMAL_RESERVATION',b.stationType||st.rows[0]?.console_type||'',Number(b.controllersCount||1),Math.max(1,Number(b.durationMinutes||60)),!!b.isVip,b.timeSlot||Date.now());res.json({success:true,data});}catch(e){res.status(400).json({error:e.message});} });
  app.post('/api/v1/customer/reservations/atomic', requireCustomerAuth, async(req,res)=>{const b=req.body||{},key=String(req.headers['idempotency-key']||b.idempotencyKey||'');if(!key)return res.status(400).json({error:'A valid idempotency key is required'});const c=await pool.connect();try{await c.query('BEGIN');await c.query('SELECT pg_advisory_xact_lock(hashtext($1))',[req.user.managerId+':'+key]);const prior=await c.query('SELECT reservation_ids FROM reservation_request_idempotency WHERE manager_id=$1 AND idempotency_key=$2 AND customer_id=$3 FOR UPDATE',[req.user.managerId,key,req.user.id]);if(prior.rows[0]){await c.query('COMMIT');return res.json({success:true,reservation:prior.rows[0].reservation_ids,idempotent:true});}const r=await bookReservation({managerId:req.user.managerId,customerId:req.user.id,type:b.type||'NORMAL_RESERVATION',stationIds:b.stationIds,startTime:b.startTime,durationMinutes:Number(b.durationMinutes||60),controllersCount:Number(b.controllersCount||1),isVip:!!b.isVip,actor:'CUSTOMER'},c);await c.query('INSERT INTO reservation_request_idempotency(manager_id,customer_id,idempotency_key,reservation_ids) VALUES($1,$2,$3,$4::jsonb)',[req.user.managerId,req.user.id,key,JSON.stringify(r)]);await c.query('COMMIT');res.status(201).json({success:true,reservation:r,idempotent:false});}catch(e){try{await c.query('ROLLBACK')}catch(_){}res.status(400).json({error:e.message});}finally{c.release();}});
app.post('/api/v1/customer/reservations/:id/cancel', requireCustomerAuth, async(req,res)=>{const c=await pool.connect();try{const key=String(req.headers['idempotency-key']||req.body?.idempotencyKey||'');if(!key)return res.status(400).json({error:'A valid idempotency key is required'});await c.query('BEGIN');const r=await cancelReservation(c,req.user.managerId,req.params.id,key,req.user.id);await c.query('COMMIT');res.json({success:true,refundDetails:r});}catch(e){try{await c.query('ROLLBACK')}catch(_){}res.status(400).json({error:e.message});}finally{c.release();}});


  // Customer-facing canonical profile, reservations, history and GN ledger.
  app.get('/api/v1/customer/profile', requireCustomerAuth, async(req,res)=>{try{const q=await pool.query('SELECT * FROM customers WHERE id=$1 AND manager_id=$2',[req.user.id,req.user.managerId]);if(!q.rows[0])return res.status(404).json({error:'Not found'});res.json(normalizeCustomer(q.rows[0]));}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/customer/reservations', requireCustomerAuth, async(req,res)=>{try{const q=await pool.query('SELECT * FROM reservations WHERE customer_id=$1 AND manager_id=$2 ORDER BY start_time DESC',[req.user.id,req.user.managerId]);res.json(q.rows);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/customer/club/ledger', requireCustomerAuth, async(req,res)=>{try{const q=await pool.query('SELECT * FROM gn_ledger WHERE customer_id=$1 AND manager_id=$2 ORDER BY created_at DESC',[req.user.id,req.user.managerId]);res.json(q.rows);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/club/ledger', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{const c=await pool.connect();try{const b=req.body||{},mid=manager(req),key=String(req.headers['idempotency-key']||b.idempotencyKey||'').trim();const amount=Number(b.gnAmount||0);const type=String(b.transactionType||'ADMIN_ADJUSTMENT').toUpperCase();const sign=['PENALTY','SPENT','DEBIT','TRANSFERRED'].includes(type)?-1:1;if(!key||!Number.isFinite(amount)||amount<=0)return res.status(400).json({error:'Valid amount and idempotency key are required'});await c.query('BEGIN');const prior=await c.query('SELECT * FROM gn_ledger WHERE manager_id=$1 AND idempotency_key=$2 LIMIT 1 FOR UPDATE',[mid,key]);if(prior.rows[0]){await c.query('COMMIT');return res.json({...prior.rows[0],idempotent:true});}const customer=await c.query('SELECT id FROM customers WHERE id=$1 AND manager_id=$2 FOR UPDATE',[b.customerId,mid]);if(!customer.rows[0]){await c.query('ROLLBACK');return res.status(404).json({error:'Customer not found'});}const q=await c.query(`INSERT INTO gn_ledger(manager_id,customer_id,amount,type,reference_type,reference_id,idempotency_key) VALUES($1,$2,$3,$4,$5,$6,$7) RETURNING *`,[mid,b.customerId,Math.abs(amount),sign<0?'DEBIT':'CREDIT',type,b.referenceId||null,key]);await c.query('UPDATE customers SET gn_balance=GREATEST(0,gn_balance+$1),updated_at=NOW() WHERE id=$2 AND manager_id=$3',[sign*amount,b.customerId,mid]);await c.query('COMMIT');res.json(q.rows[0]);}catch(e){try{await c.query('ROLLBACK')}catch(_){}res.status(500).json({error:'Internal server error'});}finally{c.release();}});
  app.post('/api/v1/customer/club/transfer', requireCustomerAuth, async(req,res)=>{const c=await pool.connect();try{const mid=req.user.managerId,from=Number(req.user.id),amount=Number(req.body?.gnAmount||0),toPhone=String(req.body?.receiverPhone||'').trim(),key=String(req.headers['idempotency-key']||req.body?.idempotencyKey||'').trim();if(amount<=0||!Number.isFinite(amount)||!toPhone||!key)return res.status(400).json({error:'Valid amount, recipient and idempotency key are required'});await c.query('BEGIN');const prior=await c.query('SELECT customer_id,reference_id FROM gn_ledger WHERE manager_id=$1 AND idempotency_key=$2 LIMIT 1 FOR UPDATE',[mid,key]);if(prior.rows[0]){await c.query('COMMIT');return res.json({success:true,idempotent:true,referenceId:prior.rows[0].reference_id});}const s=await c.query('SELECT gn_balance FROM customers WHERE id=$1 AND manager_id=$2 FOR UPDATE',[from,mid]);const t=await c.query('SELECT id FROM customers WHERE phone_number=$1 AND manager_id=$2 FOR UPDATE',[toPhone,mid]);if(!s.rows[0]||!t.rows[0]||Number(s.rows[0].gn_balance)<amount){await c.query('ROLLBACK');return res.status(400).json({error:'Insufficient balance or recipient not found'});}const referenceId='transfer:'+key;await c.query('UPDATE customers SET gn_balance=gn_balance-$1 WHERE id=$2 AND manager_id=$3',[amount,from,mid]);await c.query('UPDATE customers SET gn_balance=gn_balance+$1 WHERE id=$2 AND manager_id=$3',[amount,t.rows[0].id,mid]);await c.query('INSERT INTO gn_ledger(manager_id,customer_id,amount,type,reference_type,reference_id,idempotency_key) VALUES($1,$2,$3,\'DEBIT\',\'TRANSFER\',$4,$5),($1,$6,$3,\'CREDIT\',\'TRANSFER\',$4,$7)',[mid,from,amount,referenceId,key,t.rows[0].id,key+':credit']);await c.query('COMMIT');res.json({success:true,idempotent:false,referenceId});}catch(e){try{await c.query('ROLLBACK')}catch(_){}res.status(500).json({error:'Internal server error'});}finally{c.release();}});

  // Canonical manual-payment workflow used by GN Customers and Manager review.
  app.post('/api/v1/customer/manual-payment-requests', requireCustomerAuth, async(req,res)=>{try{const b=req.body||{},mid=req.user.managerId,cid=req.user.id,amount=Number(b.amount||0),purpose=String(b.purpose||'WALLET_TOPUP').toUpperCase();if(!['WALLET_TOPUP','RESERVATION_PAYMENT','BUY_GN'].includes(purpose))return res.status(422).json({error:'Unsupported customer payment purpose'});if(amount<=0)return res.status(400).json({error:'Invalid amount'});if(purpose==='RESERVATION_PAYMENT'){const rid=Number(b.reservationId||0);const r=await pool.query('SELECT id,status,snap_final_price FROM reservations WHERE id=$1 AND manager_id=$2 AND customer_id=$3',[rid,mid,cid]);if(!r.rows[0])return res.status(404).json({error:'Reservation not found'});if(['CANCELLED','EXPIRED','REJECTED','COMPLETED'].includes(String(r.rows[0].status)))return res.status(422).json({error:'Reservation cannot accept payment'});b.reservationId=rid;}const idem=String(req.headers['idempotency-key']||b.idempotencyKey||('mpr:'+cid+':'+Date.now()));const q=await pool.query(`INSERT INTO manual_payment_requests(manager_id,customer_id,reservation_id,purpose,amount,currency,payment_method_code,payment_reference,receipt_reference,customer_note,idempotency_key,metadata) VALUES($1,$2,$3,$4,$5,'IRT',$6,$7,$8,$9,$10,$11) ON CONFLICT(manager_id,customer_id,idempotency_key) DO NOTHING RETURNING *`,[mid,cid,purpose==='RESERVATION_PAYMENT'?Number(b.reservationId):null,purpose,amount,b.paymentMethod||b.transactionType||'',b.trackingCode||b.paymentReference||'',b.receiptReference||'',b.description||b.note||'',idem,JSON.stringify(b)]);res.status(201).json({success:true,request:q.rows[0]||null});}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/customer/manual-payment-requests', requireCustomerAuth, async(req,res)=>{try{const q=await pool.query('SELECT * FROM manual_payment_requests WHERE manager_id=$1 AND customer_id=$2 ORDER BY created_at DESC',[req.user.managerId,req.user.id]);res.json(q.rows);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/manager/manual-payment-requests', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query('SELECT * FROM manual_payment_requests WHERE manager_id=$1 ORDER BY created_at DESC',[manager(req)]);res.json(q.rows);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/manual-payment-requests/:id/approve', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{const c=await pool.connect();try{await c.query('BEGIN');const q=await c.query("SELECT * FROM manual_payment_requests WHERE id=$1 AND manager_id=$2 AND status='PENDING_MANAGER_REVIEW' FOR UPDATE",[req.params.id,manager(req)]);if(!q.rows[0]){await c.query('ROLLBACK');return res.status(404).json({error:'Request not found or already reviewed'});}const r=q.rows[0];const requested=Number(r.amount);const approved=Number(req.body?.approvedAmount ?? r.amount);if(!Number.isFinite(requested)||requested<=0||!Number.isFinite(approved)||approved<=0||approved>requested){await c.query('ROLLBACK');return res.status(422).json({error:'approvedAmount must be positive and no greater than requested amount'});}await c.query("UPDATE manual_payment_requests SET status='APPROVED',approved_amount=$1,approved_currency='IRT',reviewed_by=$2,reviewed_at=NOW(),updated_at=NOW() WHERE id=$3",[approved,manager(req),r.id]);if(r.purpose==='WALLET_TOPUP')await c.query('UPDATE customers SET wallet_balance=wallet_balance+$1,updated_at=NOW() WHERE id=$2 AND manager_id=$3',[approved,r.customer_id,manager(req)]);
      if(r.purpose==='BUY_GN'){
        const meta=typeof r.metadata==='string'?JSON.parse(r.metadata||'{}'):(r.metadata||{}); const gnAmount=Number(meta.gnAmount||meta.gn_amount||0);
        if(!Number.isInteger(gnAmount)||gnAmount<=0){await c.query('ROLLBACK');return res.status(422).json({error:'Invalid GN purchase amount'});}
        const key='manual-gn:'+r.id; const led=await c.query("INSERT INTO gn_ledger(manager_id,customer_id,amount,type,reference_type,reference_id,idempotency_key) VALUES($1,$2,$3,'CREDIT','MANUAL_GN_PURCHASE',$4,$5) ON CONFLICT(idempotency_key) DO NOTHING RETURNING id",[manager(req),r.customer_id,gnAmount,String(r.id),key]);
        if(led.rowCount>0) await c.query('UPDATE customers SET gn_balance=gn_balance+$1,pending_gn=GREATEST(0,pending_gn-$1),updated_at=NOW() WHERE id=$2 AND manager_id=$3',[gnAmount,r.customer_id,manager(req)]);
      }await c.query('COMMIT');res.json({success:true});}catch(e){try{await c.query('ROLLBACK')}catch(_){}res.status(500).json({error:'Internal server error'});}finally{c.release();}});
  app.get('/api/v1/manager/reservation-payments/pending', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    try{
      const q=await pool.query(`
        SELECT m.id,m.reservation_id,m.amount,m.currency,m.payment_method_code,m.payment_reference,m.receipt_reference,
               m.customer_note,m.created_at,r.status AS reservation_status,r.snap_final_price,
               c.full_name,c.phone_number
        FROM manual_payment_requests m
        JOIN reservations r ON r.id=m.reservation_id AND r.manager_id=m.manager_id
        JOIN customers c ON c.id=m.customer_id AND c.manager_id=m.manager_id
        WHERE m.manager_id=$1 AND m.purpose='RESERVATION_PAYMENT' AND m.status='PENDING_MANAGER_REVIEW'
        ORDER BY m.created_at DESC
      `,[manager(req)]);
      res.json({success:true,requests:q.rows});
    }catch(e){res.status(500).json({error:'Reservation payment requests lookup failed'});}
  });
  app.post('/api/v1/manager/reservation-payments/:id/reject', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    try{
      const reason=String(req.body?.reason||'Payment proof rejected by Manager');
      const q=await pool.query(
        "UPDATE manual_payment_requests SET status='REJECTED',rejection_reason=$1,reviewed_by=$2,reviewed_at=NOW(),updated_at=NOW() WHERE id=$3 AND manager_id=$2 AND purpose='RESERVATION_PAYMENT' AND status='PENDING_MANAGER_REVIEW' RETURNING id",
        [reason,manager(req),req.params.id]
      );
      if(!q.rows[0]) return res.status(404).json({error:'Reservation payment request not found or already reviewed'});
      res.json({success:true});
    }catch(e){res.status(500).json({error:'Reservation payment rejection failed'});}
  });
  app.post('/api/v1/manager/reservation-payments/:id/approve', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    const c=await pool.connect();
    try {
      await c.query('BEGIN');
      const q=await c.query("SELECT * FROM manual_payment_requests WHERE id=$1 AND manager_id=$2 AND status='PENDING_MANAGER_REVIEW' FOR UPDATE",[req.params.id,manager(req)]);
      const request=q.rows[0];
      if(!request || request.purpose!=='RESERVATION_PAYMENT' || !request.reservation_id){await c.query('ROLLBACK');return res.status(404).json({error:'Reservation payment request not found'});}
      const r=(await c.query('SELECT * FROM reservations WHERE id=$1 AND manager_id=$2 FOR UPDATE',[request.reservation_id,manager(req)])).rows[0];
      if(!r){await c.query('ROLLBACK');return res.status(404).json({error:'Reservation not found'});}
      const policy=r.snap_vip_policy ? (typeof r.snap_vip_policy==='string'?JSON.parse(r.snap_vip_policy):r.snap_vip_policy) : {};
      const paymentDeadline = policy.isVip ? Number(policy.vipPaymentDeadlineMinutes || 0) : Number(policy.paymentDeadlineMinutes || 0);
      const paymentPendingStatus = policy.isVip ? 'VIP_PENDING_PAYMENT' : 'PAYMENT_PENDING';
      if(paymentDeadline > 0 && new Date() > new Date(new Date(r.created_at).getTime()+paymentDeadline*60000) && r.status === paymentPendingStatus){
        await c.query("UPDATE reservations SET status='EXPIRED',updated_at=NOW() WHERE id=$1 AND manager_id=$2 AND status=$3",[r.id,manager(req),paymentPendingStatus]);
        await c.query('UPDATE manual_payment_requests SET status=\'REJECTED\',rejection_reason=\'Payment deadline expired\',reviewed_by=$1,reviewed_at=NOW(),updated_at=NOW() WHERE id=$2',[manager(req),request.id]);
        await c.query('COMMIT'); return res.status(422).json({error:'Reservation payment deadline expired'});
      }
      const amount=Number(request.amount||0);
      if(amount<=0){await c.query('ROLLBACK');return res.status(422).json({error:'Invalid payment amount'});}
      const alreadyPaid=Number((await c.query("SELECT COALESCE(SUM(amount),0) paid FROM payment_transactions WHERE reservation_id=$1 AND manager_id=$2 AND status='SUCCESS'",[r.id,manager(req)])).rows[0].paid||0);
      const remaining=Math.max(0,Number(r.snap_final_price||0)-alreadyPaid);
      if(amount > remaining + 0.0000001){await c.query('ROLLBACK');return res.status(422).json({error:'Payment amount exceeds reservation balance'});}
      await c.query("INSERT INTO payment_transactions(manager_id,customer_id,reservation_id,amount,status,idempotency_key,provider,currency,verified_at) VALUES($1,$2,$3,$4,'SUCCESS',$5,'MANAGER_MANUAL_REVIEW',$6,NOW()) ON CONFLICT(idempotency_key) DO NOTHING",[manager(req),r.customer_id,r.id,amount,'mpr:'+request.id,'IRT']);
      await c.query("UPDATE manual_payment_requests SET status='APPROVED',approved_amount=$1,approved_currency='IRT',reviewed_by=$2,reviewed_at=NOW(),updated_at=NOW() WHERE id=$3",[amount,manager(req),request.id]);
      const paid=(await c.query("SELECT COALESCE(SUM(amount),0) paid FROM payment_transactions WHERE reservation_id=$1 AND manager_id=$2 AND status='SUCCESS'",[r.id,manager(req)])).rows[0].paid;
      const fullyPaid = Number(paid) >= Number(r.snap_final_price||0);
      const next = policy.isVip
        ? (fullyPaid ? 'VIP_PAYMENT_PAID' : 'VIP_PENDING_PAYMENT')
        : (fullyPaid && ['PAYMENT_PENDING','PENDING'].includes(r.status) ? 'PENDING_APPROVAL' : r.status);
      if(next !== r.status) await c.query('UPDATE reservations SET status=$1,updated_at=NOW() WHERE id=$2 AND manager_id=$3',[next,r.id,manager(req)]);
      await c.query('INSERT INTO reservation_audit_logs(manager_id,reservation_id,actor,old_status,new_status,reason) VALUES($1,$2,$3,$4,$5,$6)',[manager(req),r.id,'MANAGER',r.status,next,'Manual reservation payment approved']);
      await c.query('COMMIT'); return res.json({success:true,reservationId:r.id,status:next,paidAmount:String(paid)});
    } catch(e){try{await c.query('ROLLBACK')}catch(_){}return res.status(400).json({error:e.message});} finally{c.release();}
  });

  app.post('/api/v1/manager/manual-payment-requests/:id/reject', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query("UPDATE manual_payment_requests SET status='REJECTED',rejection_reason=$1,reviewed_by=$2,reviewed_at=NOW(),updated_at=NOW() WHERE id=$3 AND manager_id=$2 AND status='PENDING_MANAGER_REVIEW' RETURNING id",[String(req.body?.reason||'Rejected by Manager'),manager(req),req.params.id]);if(!q.rows[0])return res.status(404).json({error:'Request not found or already reviewed'});res.json({success:true});}catch(e){res.status(500).json({error:'Internal server error'});} });

  app.post('/api/v1/manager/club/point-logs', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const b=req.body||{},mid=manager(req),customer=await pool.query('SELECT id FROM customers WHERE id=$1 AND manager_id=$2',[b.customerId,mid]);if(!customer.rows[0])return res.status(404).json({error:'Customer not found'});const q=await pool.query('INSERT INTO customer_point_logs(manager_id,customer_id,title,points) VALUES($1,$2,$3,$4) RETURNING *',[mid,b.customerId,b.title||'',Number(b.points||0)]);res.json(q.rows[0]);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/manager/club/point-logs/:customerId', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query('SELECT * FROM customer_point_logs WHERE manager_id=$1 AND customer_id=$2 ORDER BY created_at DESC',[manager(req),req.params.customerId]);res.json(q.rows);}catch(e){res.status(500).json({error:'Internal server error'});} });

  // Customer transaction history is derived from server-authoritative invoices/sessions.

  // Server-side device binding/check for the remaining Manager login lifecycle.
  app.get('/api/v1/auth/check', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query('SELECT id,username,display_name,gamenet_name,role FROM managers WHERE id=$1',[manager(req)]);res.json({success:!!q.rows[0],manager:q.rows[0]||null});}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/device', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{const c=await pool.connect();try{const mid=manager(req),did=String(req.body?.device_id||req.body?.deviceId||'').trim();if(!did)return res.status(400).json({error:'device_id required'});await c.query('BEGIN');await c.query('SELECT pg_advisory_xact_lock(hashtext($1))',[String(mid)]);const ent=(await c.query("SELECT max_devices FROM manager_entitlements WHERE manager_id=$1 AND status='ACTIVE' AND starts_at<=NOW() AND expires_at>NOW() ORDER BY expires_at DESC LIMIT 1 FOR UPDATE",[mid])).rows[0];if(!ent){await c.query('ROLLBACK');return res.status(403).json({error:'Active entitlement required'});}const known=(await c.query('SELECT 1 FROM manager_device_bindings WHERE manager_id=$1 AND device_id=$2 AND active=TRUE LIMIT 1',[mid,did])).rows.length;if(!known){const count=Number((await c.query('SELECT COUNT(*)::int count FROM manager_device_bindings WHERE manager_id=$1 AND active=TRUE',[mid])).rows[0].count||0);if(count>=Math.max(1,Number(ent.max_devices||1))){await c.query('ROLLBACK');return res.status(403).json({error:'Device limit reached',code:'DEVICE_LIMIT_REACHED'});}await c.query('INSERT INTO manager_device_bindings(manager_id,device_id,active,last_seen_at) VALUES($1,$2,TRUE,NOW())',[mid,did]);}else await c.query('UPDATE manager_device_bindings SET last_seen_at=NOW() WHERE manager_id=$1 AND device_id=$2',[mid,did]);await c.query('COMMIT');res.json({success:true});}catch(e){try{await c.query('ROLLBACK')}catch(_){}res.status(500).json({error:'Internal server error'});}finally{c.release();}});

// Broadcast/audit resources used by Manager UI.
app.post('/api/v1/manager/announcements', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const b=req.body||{},mid=manager(req);const cs=await pool.query('SELECT id FROM customers WHERE manager_id=$1',[mid]);for(const c of cs.rows)await pool.query('INSERT INTO notifications(manager_id,customer_id,title,message,is_read) VALUES($1,$2,$3,$4,FALSE)',[mid,c.id,b.title||'پیام مدیریت',b.message||'']);res.status(201).json({success:true});}catch(e){res.status(500).json({error:'Internal server error'});}});
app.post('/api/v1/manager/audit-logs', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const b=req.body||{};await pool.query('INSERT INTO financial_audit_logs(manager_id,event_type,actor,metadata) VALUES($1,$2,$3,$4)',[manager(req),'OPERATOR_AUDIT',b.operatorName||'Manager',JSON.stringify({actionTitle:b.actionTitle||'',details:b.details||''})]);res.status(201).json({success:true});}catch(e){res.status(500).json({error:'Internal server error'});}});
app.get('/api/v1/manager/club/ledger/:customerId', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query('SELECT * FROM gn_ledger WHERE manager_id=$1 AND customer_id=$2 ORDER BY created_at DESC',[manager(req),req.params.customerId]);res.json(q.rows);}catch(e){res.status(500).json({error:'Internal server error'});}});
app.delete('/api/v1/manager/reservations/by-phone/:phone', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{return res.status(405).json({error:'Physical reservation deletion is disabled; use cancellation/rejection workflow'});});
app.post('/api/v1/manager/stations/purge-extra', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    const mid=manager(req);
    const keep=Math.max(1,Math.trunc(Number(req.body?.count||req.body?.stationCount||0)));
    const c=await pool.connect();
    try{
      await c.query('BEGIN');
      await c.query('SELECT pg_advisory_xact_lock(hashtext($1))',[String(mid)]);
      // Station IDs are globally allocated, so `id > count` is NOT a valid per-Manager count rule.
      const ranked=await c.query(`SELECT id,ROW_NUMBER() OVER (ORDER BY id) AS rn FROM stations WHERE manager_id=$1 ORDER BY id FOR UPDATE`,[mid]);
      const overflow=ranked.rows.filter(r=>Number(r.rn)>keep).map(r=>Number(r.id));
      if(overflow.length){
        const active=await c.query(`SELECT DISTINCT g.station_id FROM game_sessions g WHERE g.manager_id=$1 AND g.station_id=ANY($2::int[]) AND g.status IN ('ACTIVE','PAUSED')`,[mid,overflow]);
        if(active.rows.length){
          await c.query('ROLLBACK');
          return res.status(409).json({success:false,code:'STATIONS_HAVE_ACTIVE_SESSIONS',stationIds:active.rows.map(r=>r.station_id)});
        }
        await c.query('UPDATE stations SET active=FALSE,updated_at=NOW() WHERE manager_id=$1 AND id=ANY($2::int[])',[mid,overflow]);
      }
      const current=(await c.query('SELECT settings FROM configuration_revisions WHERE manager_id=$1 ORDER BY version_number DESC LIMIT 1 FOR UPDATE',[mid])).rows[0]?.settings || {};
      const orders={...(current.stationOrders||{})};
      for(const id of overflow) delete orders[String(id)];
      const v=await c.query('SELECT COALESCE(MAX(version_number),0)+1 AS v FROM configuration_revisions WHERE manager_id=$1',[mid]);
      await c.query('INSERT INTO configuration_revisions(manager_id,version_number,settings) VALUES($1,$2,$3::jsonb)',[mid,v.rows[0].v,JSON.stringify({...current,stationOrders:orders})]);
      await c.query('COMMIT');
      res.json({success:true,disabledStationIds:overflow,count:overflow.length,stationCount:keep});
    }catch(e){try{await c.query('ROLLBACK')}catch(_){} console.error('[stations/purge-extra]',e?.message||e);res.status(500).json({error:'Internal server error',code:'STATION_PURGE_FAILED'});}finally{c.release();}
  });

  app.post('/api/v1/manager/customer-transactions', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const b=req.body||{},mid=manager(req),customerId=Number(b.customerId||0),localId=Number(b.localId||0)||null;if(customerId<=0)return res.status(422).json({error:'Customer transaction requires a real customer'});const customer=await pool.query('SELECT id FROM customers WHERE id=$1 AND manager_id=$2',[customerId,mid]);if(!customer.rows[0])return res.status(404).json({error:'Customer not found'});const amount=Math.max(0,Math.trunc(Number(b.amount||0)));const paidAmount=Math.max(0,Math.trunc(Number(b.paidAmount||0)));const gameCost=Math.max(0,Math.trunc(Number(b.gameCost||b.game_cost||0)));const foodCost=Math.max(0,Math.trunc(Number(b.foodCost||b.food_cost||0)));const q=await pool.query(`INSERT INTO customer_transactions(manager_id,customer_id,customer_name,station_name,title,amount,paid_amount,status,date_str,time_str,segment_details,buffet_details,event_timestamp,play_minutes,game_cost,food_cost,local_id) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17) ON CONFLICT (manager_id,local_id) WHERE local_id IS NOT NULL DO UPDATE SET customer_id=EXCLUDED.customer_id,customer_name=EXCLUDED.customer_name,station_name=EXCLUDED.station_name,title=EXCLUDED.title,amount=EXCLUDED.amount,paid_amount=EXCLUDED.paid_amount,status=EXCLUDED.status,date_str=EXCLUDED.date_str,time_str=EXCLUDED.time_str,segment_details=EXCLUDED.segment_details,buffet_details=EXCLUDED.buffet_details,event_timestamp=EXCLUDED.event_timestamp,play_minutes=EXCLUDED.play_minutes,game_cost=EXCLUDED.game_cost,food_cost=EXCLUDED.food_cost,updated_at=NOW() RETURNING *`,[mid,customerId,b.customerName||'',b.stationName||'',b.title||'',amount,paidAmount,b.status||'UNREVIEWED',b.dateStr||'',b.timeStr||'',b.segmentDetails||'',b.buffetDetails||'',Number(b.timestamp||0),Number(b.playMinutes||b.play_minutes||0),gameCost,foodCost,localId]);res.status(201).json(q.rows[0]);}catch(e){console.error('[customer-transactions]',e?.message||e);res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/manager/customer-transactions', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query('SELECT * FROM customer_transactions WHERE manager_id=$1 ORDER BY created_at DESC LIMIT 1000',[manager(req)]);res.json(q.rows);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/customer/transactions', requireCustomerAuth, async(req,res)=>{try{const q=await pool.query('SELECT * FROM customer_transactions WHERE manager_id=$1 AND customer_id=$2 ORDER BY created_at DESC LIMIT 500',[req.user.managerId,req.user.id]);res.json(q.rows);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/manager/payment-methods', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query('SELECT * FROM manager_payment_methods WHERE manager_id=$1 ORDER BY id',[manager(req)]);res.json({success:true,methods:q.rows});}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/payment-methods', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const b=req.body||{};if(!b.methodCode||!b.displayName)return res.status(400).json({error:'methodCode and displayName are required'});const q=await pool.query('INSERT INTO manager_payment_methods(manager_id,method_code,display_name,instructions,active,updated_at) VALUES($1,$2,$3,$4,$5,NOW()) ON CONFLICT(manager_id,method_code) DO UPDATE SET display_name=EXCLUDED.display_name,instructions=EXCLUDED.instructions,active=EXCLUDED.active,updated_at=NOW() RETURNING *',[manager(req),b.methodCode,b.displayName,b.instructions||null,b.active!==false]);res.status(201).json({success:true,method:q.rows[0]});}catch(e){res.status(400).json({error:e.message});} });
  app.patch('/api/v1/manager/payment-methods/:id', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const b=req.body||{};const q=await pool.query('UPDATE manager_payment_methods SET display_name=COALESCE($1,display_name),instructions=COALESCE($2,instructions),active=COALESCE($3,active),updated_at=NOW() WHERE id=$4 AND manager_id=$5 RETURNING *',[b.displayName??null,b.instructions??null,b.active===undefined?null:!!b.active,req.params.id,manager(req)]);if(!q.rows[0])return res.status(404).json({error:'Payment method not found'});res.json({success:true,method:q.rows[0]});}catch(e){res.status(400).json({error:e.message});} });
  app.get('/api/v1/customer/payment-methods', requireCustomerAuth, async(req,res)=>{try{const q=await pool.query('SELECT id,method_code,display_name,instructions,active FROM manager_payment_methods WHERE manager_id=$1 AND active=TRUE ORDER BY id',[req.user.managerId]);res.json({success:true,methods:q.rows});}catch(e){res.status(500).json({error:'Internal server error'});} });

  // Canonical subscription lifecycle. Pending manual purchases never grant access by themselves.
  async function subscriptionState(deviceId, licenseCode, userPhone) {
    let managerId=null;
    if(userPhone){const q=await pool.query("SELECT id FROM managers WHERE (phone=$1 OR username=$1) AND role IN ('MANAGER','SUPER_MANAGER') LIMIT 1",[userPhone]);managerId=q.rows[0]?.id||null;}
    if(!managerId && deviceId){const q=await pool.query('SELECT manager_id FROM manager_device_bindings WHERE device_id=$1 AND active=TRUE ORDER BY last_seen_at DESC LIMIT 1',[deviceId]);managerId=q.rows[0]?.manager_id||null;}
    if(licenseCode && /^PENDING_\d+$/.test(String(licenseCode))){const id=Number(String(licenseCode).slice(8));const q=await pool.query('SELECT manager_id,status,plan_id,buyer_device_id FROM subscription_payment_requests WHERE id=$1',[id]);const req=q.rows[0];if(req?.manager_id && (deviceId && String(req.buyer_device_id||'')===deviceId || (await pool.query('SELECT 1 FROM manager_device_bindings WHERE manager_id=$1 AND device_id=$2 AND active=TRUE LIMIT 1',[req.manager_id,deviceId])).rows.length)){managerId=req.manager_id;} }
    if(!managerId)return {active:false,managerId:null,plan:null,expiresAt:0,licenseCode:licenseCode||''};
    const q=await pool.query("SELECT plan_id,expires_at FROM manager_entitlements WHERE manager_id=$1 AND status='ACTIVE' AND starts_at<=NOW() AND expires_at>NOW() ORDER BY expires_at DESC LIMIT 1",[managerId]);
    return {active:!!q.rows[0],managerId,plan:q.rows[0]?.plan_id||null,expiresAt:q.rows[0]?new Date(q.rows[0].expires_at).getTime():0,licenseCode:licenseCode||('MGR_'+managerId)};
  }
  app.get('/api/v1/subscriptions/check', async(req,res)=>{try{const st=await subscriptionState(String(req.query.device_id||''),String(req.query.license_code||''),String(req.query.user_phone||''));const now=Date.now();res.json({license_status:st.active?'ACTIVE':'INACTIVE',licenseStatus:st.active?'ACTIVE':'INACTIVE',status:st.active?'ACTIVE':'INACTIVE',active:st.active,is_active:st.active,valid:st.active,plan_type:st.plan||'INACTIVE',planType:st.plan||'INACTIVE',expire_time:st.expiresAt,expires_at:st.expiresAt,server_time:now,serverTime:now,license_code:st.licenseCode,licenseCode:st.licenseCode,message:st.active?'اشتراک فعال است.':'اشتراک فعال یافت نشد.',trial_used:false});}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/subscriptions/status', async(req,res)=>{try{const b=req.body||{},st=await subscriptionState(String(b.deviceId||b.device_id||''),String(b.licenseCode||b.license_code||''),String(b.userPhone||b.user_phone||''));res.json({valid:st.active,success:st.active,type:st.plan,expiresAt:st.expiresAt,expires_at:st.expiresAt,serverTime:Date.now(),server_time:Date.now(),activatedAt:st.active?Date.now():0,activated_at:st.active?Date.now():0,licenseCode:st.licenseCode,license_code:st.licenseCode,hasPassword:false,message:st.active?'اشتراک فعال است.':'اشتراک فعال یافت نشد.'});}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/plans', rateLimit({ windowMs: 60_000, max: 30 }), async(req,res)=>{
    try { res.set('Cache-Control','no-store'); return res.json(publicSubscriptionPlans(await getSubscriptionStore())); }
    catch(e){ return res.status(500).json({error:'Subscription plans unavailable'}); }
  });
  app.get('/api/v1/super-manager/subscription-plans', requireSuperManagerAuth, async(req,res)=>{
    try { const store=await getSubscriptionStore(); return res.json({success:true,version:store.version_number||1,settings:store}); }
    catch(e){ return res.status(500).json({error:'Subscription plan settings unavailable'}); }
  });
  app.put('/api/v1/super-manager/subscription-plans', requireSuperManagerAuth, async(req,res)=>{
    const b=req.body||{}, plans=Array.isArray(b.plans)?b.plans:[], allowed=new Set(['MONTHLY','THREE_MONTHS','YEARLY']);
    const required=['MONTHLY','THREE_MONTHS','YEARLY'];
    try {
      if(plans.length!==required.length || plans.some(p=>!allowed.has(String(p.id||'')))) return res.status(400).json({error:'Exactly the three canonical subscription plans are required.'});
      const normalized=required.map(id=>plans.find(p=>String(p.id||'')===id)).map((p,i)=>{
        const price=Number(p.price), days=Number(p.durationDays), url=String(p.paymentUrl||'').trim();
        if(!Number.isSafeInteger(price)||price<=0||!Number.isSafeInteger(days)||days<=0||!/^https:\/\/pay\.forbix\.ir\//i.test(url)) throw new Error('INVALID_PLAN_'+String(p.id||''));
        return {id:String(p.id),nameFa:String(p.nameFa||'').trim().slice(0,120),nameEn:String(p.nameEn||'').trim().slice(0,120),price,durationDays:days,paymentUrl:url,descriptionFa:String(p.descriptionFa||'').slice(0,1000),descriptionEn:String(p.descriptionEn||'').slice(0,1000),sortOrder:i+1,active:p.active!==false};
      });
      const cleanText=(v,max=2000)=>String(v==null?'':v).slice(0,max);
      const page={...DEFAULT_SUBSCRIPTION_STORE,...(b.page&&typeof b.page==='object'?b.page:{})};
      const settings={pageTitleFa:cleanText(page.pageTitleFa,200),pageTitleEn:cleanText(page.pageTitleEn,200),pageSubtitleFa:cleanText(page.pageSubtitleFa,500),pageSubtitleEn:cleanText(page.pageSubtitleEn,500),paymentInstructionFa:cleanText(page.paymentInstructionFa),paymentInstructionEn:cleanText(page.paymentInstructionEn),purchaseButtonFa:cleanText(page.purchaseButtonFa,200),purchaseButtonEn:cleanText(page.purchaseButtonEn,200),currencyFa:cleanText(page.currencyFa,50)||'تومان',currencyEn:cleanText(page.currencyEn,50)||'Toman',supportMessageFa:cleanText(page.supportMessageFa),supportMessageEn:cleanText(page.supportMessageEn),plans:normalized};
      const c=await pool.connect();
      try {
        await c.query('BEGIN');
        await c.query('SELECT pg_advisory_xact_lock(hashtext($1))',['subscription-store']);
        const v=await c.query('SELECT COALESCE(MAX(version_number),0)+1 AS v FROM subscription_store_config WHERE id=1');
        await c.query('UPDATE subscription_store_config SET version_number=$1,settings=$2,updated_at=NOW(),updated_by=$3 WHERE id=1',[Number(v.rows[0].v),JSON.stringify(settings),String(req.user.id)]);
        await c.query('COMMIT');
        return res.json({success:true,version:Number(v.rows[0].v),settings});
      } catch(e){try{await c.query('ROLLBACK')}catch(_){} throw e;} finally{c.release();}
    } catch(e){ return res.status(400).json({error:e.message==='INVALID_PLAN_'+String((req.body?.plans||[]).find(p=>!allowed.has(String(p?.id||'')))?.id||'')?'Invalid subscription plan':(e.message||'Subscription plan update failed')}); }
  });

  app.post('/api/v1/subscriptions/activate', async(req,res)=>{const b=req.body||{},code=String(b.licenseCode||'').trim(),deviceId=String(b.deviceId||'').trim(),secret=String(b.activationSecret||'').trim();if(!/^PENDING_\d+$/.test(code)||!deviceId||!secret)return res.status(400).json({valid:false,success:false,message:'Activation code, device and activation secret are required.'});const c=await pool.connect();try{await c.query('BEGIN');const q=await c.query('SELECT * FROM subscription_payment_requests WHERE id=$1 FOR UPDATE',[Number(code.slice(8))]);const request=q.rows[0];if(!request?.manager_id||request.status!=='CONFIRMED'||!request.activation_secret_hash){await c.query('ROLLBACK');return res.status(403).json({valid:false,success:false,message:'This subscription is not ready for activation.'});}if(request.buyer_device_id && String(request.buyer_device_id)!==deviceId){await c.query('ROLLBACK');return res.status(403).json({valid:false,success:false,message:'This activation is bound to another device.'});}if(!(await bcrypt.compare(secret,request.activation_secret_hash))){await c.query('ROLLBACK');return res.status(403).json({valid:false,success:false,message:'Invalid activation credentials.'});}const ent=(await c.query("SELECT plan_id,expires_at,max_devices FROM manager_entitlements WHERE manager_id=$1 AND status='ACTIVE' AND starts_at<=NOW() AND expires_at>NOW() ORDER BY expires_at DESC LIMIT 1 FOR UPDATE",[request.manager_id])).rows[0];if(!ent){await c.query('ROLLBACK');return res.status(403).json({valid:false,success:false,message:'Active entitlement not found.'});}await c.query('SELECT pg_advisory_xact_lock(hashtext($1))',[String(request.manager_id)]);const known=(await c.query('SELECT 1 FROM manager_device_bindings WHERE manager_id=$1 AND device_id=$2 AND active=TRUE LIMIT 1',[request.manager_id,deviceId])).rows.length;if(!known){const count=Number((await c.query('SELECT COUNT(*)::int count FROM manager_device_bindings WHERE manager_id=$1 AND active=TRUE',[request.manager_id])).rows[0].count||0);if(count>=Math.max(1,Number(ent.max_devices||1))){await c.query('ROLLBACK');return res.status(403).json({valid:false,success:false,code:'DEVICE_LIMIT_REACHED'});}await c.query('INSERT INTO manager_device_bindings(manager_id,device_id,active,last_seen_at) VALUES($1,$2,TRUE,NOW())',[request.manager_id,deviceId]);}else await c.query('UPDATE manager_device_bindings SET last_seen_at=NOW() WHERE manager_id=$1 AND device_id=$2',[request.manager_id,deviceId]);await c.query('COMMIT');res.json({valid:true,success:true,type:ent.plan_id,expiresAt:new Date(ent.expires_at).getTime(),serverTime:Date.now(),activatedAt:Date.now(),realLicenseCode:'ACTIVE_'+request.manager_id,licenseCode:'ACTIVE_'+request.manager_id,hasPassword:false,message:'اشتراک با موفقیت فعال شد.'});}catch(e){try{await c.query('ROLLBACK')}catch(_){}res.status(500).json({valid:false,success:false});}finally{c.release();}});
  app.post('/api/v1/subscriptions/buy', async(req,res)=>{
    try {
      const b=req.body||{}, store=await getSubscriptionStore(), planId=String(b.plan||'').trim();
      const idempotencyKey=String(req.headers['idempotency-key']||b.idempotencyKey||'').trim();
      const plan=store.plans.find(p=>String(p.id||'')===planId && p.active!==false);
      const deviceId=String(b.deviceId||'').trim();
      if(!plan) return res.status(400).json({error:'Subscription plan is unavailable'});
      if(!deviceId) return res.status(400).json({error:'deviceId is required'});
      if(!idempotencyKey) return res.status(400).json({error:'A valid subscription idempotency key is required'});
      const amount=Number(plan.price||0);
      if(!Number.isSafeInteger(amount)||amount<=0) return res.status(500).json({error:'Subscription plan price is invalid'});
      const activationSecret=crypto.randomBytes(24).toString('base64url'),secretHash=await bcrypt.hash(activationSecret,12);
      const existing=await pool.query('SELECT id,status,manager_id,plan_id,amount,buyer_device_id,activation_secret_hash FROM subscription_payment_requests WHERE buyer_device_id=$1 AND idempotency_key=$2 LIMIT 1',[deviceId,idempotencyKey]);
      if(existing.rows[0]){
        const r=existing.rows[0];
        if(String(r.plan_id)!==planId) return res.status(409).json({error:'Idempotency key was already used for a different subscription plan'});
        if(r.activation_secret_hash){
          await pool.query('UPDATE subscription_payment_requests SET activation_secret_hash=$1,updated_at=NOW() WHERE id=$2',[secretHash,r.id]);
          return res.json({paymentUrl:String(plan.paymentUrl||''),licenseCode:'PENDING_'+r.id,activationSecret,transactionId:'REQ_'+r.id,amount:Number(r.amount),durationDays:Number(plan.durationDays||0),type:planId,message:String(store.paymentInstructionFa||'درخواست خرید ثبت شده است و تا تأیید دستی Super Manager فعال نمی‌شود.'),idempotent:true});
        }
        return res.json({paymentUrl:String(plan.paymentUrl||''),licenseCode:'PENDING_'+r.id,activationSecret:null,transactionId:'REQ_'+r.id,amount:Number(r.amount),durationDays:Number(plan.durationDays||0),type:String(r.plan_id),message:String(store.paymentInstructionFa||'درخواست خرید ثبت شده است و تا تأیید دستی Super Manager فعال نمی‌شود.'),idempotent:true});
      }
      const created=await pool.query("INSERT INTO subscription_payment_requests(manager_id,plan_id,amount,buyer_phone,buyer_name,buyer_gamenet_name,buyer_device_id,customer_note,status,provisioned_account,activation_secret_hash,idempotency_key) VALUES(NULL,$1,$2,$3,$4,$5,$6,$7,'PENDING',FALSE,$8,$9) RETURNING id",[planId,amount,b.userPhone||null,b.userName||'',b.gameNetName||'',deviceId,String(b.coupon||''),secretHash,idempotencyKey]);
      const id=created.rows[0].id;
      res.json({paymentUrl:String(plan.paymentUrl||''),licenseCode:'PENDING_'+id,activationSecret,transactionId:'REQ_'+id,amount,durationDays:Number(plan.durationDays||0),type:planId,message:String(store.paymentInstructionFa||'درخواست خرید ثبت شد و تا تأیید دستی Super Manager فعال نمی‌شود.')});
    } catch(e){console.error('[subscription-buy]',e?.code||'',e?.message||'');res.status(500).json({error:'Internal server error'});}
  });
  app.post('/api/v1/subscriptions/set-password', async(req,res)=>{try{const b=req.body||{},code=String(b.licenseCode||'').trim(),phone=String(b.userPhone||b.user_phone||'').trim(),deviceId=String(b.deviceId||b.device_id||'').trim(),secret=String(b.activationSecret||'').trim(),password=String(b.password||'');if(!/^PENDING_\d+$/.test(code)||password.length<8||!secret)return res.status(400).json(false);const q=await pool.query('SELECT manager_id,status,buyer_phone,buyer_device_id,activation_secret_hash FROM subscription_payment_requests WHERE id=$1',[Number(code.slice(8))]);const r=q.rows[0];if(!r?.manager_id||r.status!=='CONFIRMED'||!r.activation_secret_hash)return res.status(403).json(false);if(!await bcrypt.compare(secret,r.activation_secret_hash))return res.status(403).json(false);const phoneMatch=phone && r.buyer_phone && phone===String(r.buyer_phone),deviceMatch=deviceId && r.buyer_device_id && deviceId===String(r.buyer_device_id);if(!phoneMatch && !deviceMatch)return res.status(403).json(false);const hash=await bcrypt.hash(password,12);await pool.query('UPDATE managers SET password_hash=$1,updated_at=NOW() WHERE id=$2',[hash,r.manager_id]);await pool.query('UPDATE subscription_payment_requests SET activation_secret_hash=NULL,updated_at=NOW() WHERE id=$1',[Number(code.slice(8))]);res.json(true);}catch(e){res.status(500).json(false);} });
  app.post('/api/v1/coupons/validate', async(req,res)=>res.json({valid:false,discountPercent:0,message:'کد تخفیف معتبر یافت نشد.'}));
};
