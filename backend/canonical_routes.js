const bcrypt = require('bcrypt');
const crypto = require('crypto');
const { calculatePrice, cancelReservation, getReservationConfiguration, deepMerge } = require('./financialService');
const { bookReservation, transitionState } = require('./reservationService');

module.exports = function registerCanonicalRoutes({ app, pool, requireManagerAuth, requireActiveEntitlement, requireCustomerAuth, requireSuperManagerAuth, rateLimit, getManagerConfiguration }) {
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
    debt:Number(r.debt || 0), credit:Number(r.wallet_balance ?? r.credit ?? 0), points:Number(r.gn_balance || 0),
    availableGn:Number(r.gn_balance || 0), pendingGn:Number(r.pending_gn || 0), lp:Number(r.lp_balance || 0), lp_balance:Number(r.lp_balance || 0),
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

  app.get('/api/v1/manager/customers/archived', requireManagerAuth, requireActiveEntitlement, async (req,res)=>{ try {
    const q=await pool.query(`SELECT c.*,COALESCE(NULLIF(substring(c.description from 'original_phone=([^ ]+)'),''),c.phone_number) AS archived_original_phone,regexp_replace(COALESCE(c.description,''),'^\\[GAMENEX_ARCHIVED:[^]]+\\] ?','') AS archived_description FROM customers c WHERE c.manager_id=$1 AND COALESCE(c.description,'') LIKE '[GAMENEX_ARCHIVED:%' ORDER BY c.id DESC`,[manager(req)]);
    res.json(q.rows.map(r=>normalizeCustomer({...r,phone_number:r.archived_original_phone,description:r.archived_description})));
  } catch(e){res.status(500).json({error:'Internal server error'});} });

  app.post('/api/v1/manager/customers', requireManagerAuth, requireActiveEntitlement, async (req,res)=>{ try {
    const mid=manager(req), b=req.body||{}; const phone=String(b.phoneNumber||b.phone_number||b.customer_phone||'').trim();
    const name=String(b.fullName||b.full_name||b.customer_name||'').trim(); if(!phone||!name) return res.status(400).json({error:'fullName and phoneNumber are required'});
    const existing=await pool.query('SELECT id FROM customers WHERE manager_id=$1 AND phone_number=$2 LIMIT 1',[mid,phone]);
    let q;
    if(existing.rows[0]) q=await pool.query("UPDATE customers SET full_name=$1,debt=COALESCE($2,debt),wallet_balance=COALESCE($3,wallet_balance),credit=COALESCE($3,credit),description=COALESCE($4,description),club_tier=COALESCE($5,club_tier),invite_code=COALESCE(NULLIF($6,''),invite_code),invited_by_code=COALESCE(NULLIF($7,''),invited_by_code),lp_balance=GREATEST(0,COALESCE($8,lp_balance)),gn_balance=GREATEST(0,COALESCE($9,gn_balance)),pending_gn=GREATEST(0,COALESCE($10,pending_gn)),updated_at=NOW() WHERE id=$11 AND manager_id=$12 RETURNING *",[name,b.debt,b.credit,b.description,b.tier,b.inviteCode,b.invitedByCode,b.lp,b.availableGn,b.pendingGn,existing.rows[0].id,mid]);
    else q=await pool.query("INSERT INTO customers(manager_id,phone_number,full_name,debt,wallet_balance,credit,description,club_tier,invite_code,invited_by_code,pending_gn,lp_balance,gn_balance,last_activity_at,last_tier_review_at) VALUES($1,$2,$3,COALESCE($4,0),COALESCE($5,0),COALESCE($5,0),COALESCE($6,''),COALESCE($7,'BRONZE'),NULLIF($8,''),NULLIF($9,''),GREATEST(0,COALESCE($10,0)),GREATEST(0,COALESCE($11,0)),GREATEST(0,COALESCE($12,0)),NOW(),NOW()) RETURNING *",[mid,phone,name,b.debt,b.credit,b.description,b.tier,b.inviteCode,b.invitedByCode,b.pendingGn,b.lp,b.availableGn]);
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
      let customer=(await c.query("SELECT id,phone_number,description FROM customers WHERE id=$1 AND manager_id=$2 FOR UPDATE",[id,mid])).rows[0];
      // Android may have a stale Room primary key after reinstall/sync. Resolve it by the
      // manager-scoped phone number supplied by the authenticated client instead of deleting
      // or archiving an unrelated customer.
      const requestedPhone=String(req.query.phone||'').trim();
      if(!customer && requestedPhone){
        customer=(await c.query("SELECT id,phone_number,description FROM customers WHERE manager_id=$1 AND phone_number=$2 FOR UPDATE",[mid,requestedPhone])).rows[0];
      }
      if(!customer){await c.query('ROLLBACK');return res.status(404).json({error:'Customer not found'});}
      if(String(customer.description||'').startsWith('[GAMENEX_ARCHIVED:')){await c.query('COMMIT');return res.json({success:true,archived:true,customerId:id,idempotent:true});}
      const active=(await c.query("SELECT session_id FROM active_session_customer_claims WHERE customer_id=$1 AND manager_id=$2 LIMIT 1",[id,mid])).rows[0];
      if(active){await c.query('ROLLBACK');return res.status(409).json({success:false,code:'CUSTOMER_IN_ACTIVE_SESSION',sessionId:active.session_id});}
      const stamp=Date.now();
      const archivedPhone=('ARCH:'+id).slice(0,20);
      const archivedDescription='[GAMENEX_ARCHIVED:'+stamp+'] original_phone='+String(customer.phone_number||'')+' '+String(customer.description||'');
      await c.query("UPDATE customers SET phone_number=$1,description=$2,updated_at=NOW() WHERE id=$3 AND manager_id=$4",[archivedPhone,archivedDescription,customer.id,mid]);
      await c.query('COMMIT');
      return res.json({success:true,archived:true,customerId:Number(customer.id),requestedCustomerId:id});
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

  app.post('/api/v1/manager/customers/:id/restore', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    const mid=manager(req), id=Number(req.params.id);
    if(!Number.isInteger(id)||id<=0) return res.status(400).json({error:'Invalid customer id'});
    const c=await pool.connect();
    try{
      await c.query('BEGIN');
      let customer=(await c.query("SELECT id,phone_number,description FROM customers WHERE id=$1 AND manager_id=$2 FOR UPDATE",[id,mid])).rows[0];
      const requestedPhone=String(req.query.phone||'').trim();
      if(!customer && requestedPhone) customer=(await c.query("SELECT id,phone_number,description FROM customers WHERE manager_id=$1 AND (phone_number=$2 OR description LIKE $3) FOR UPDATE",[mid,requestedPhone,'%original_phone='+requestedPhone+'%'])).rows[0];
      if(!customer){await c.query('ROLLBACK');return res.status(404).json({error:'Customer not found'});}
      if(!String(customer.description||'').startsWith('[GAMENEX_ARCHIVED:')){await c.query('COMMIT');return res.json({success:true,restored:false,idempotent:true,customerId:Number(customer.id)});}
      const originalPhone=(String(customer.description||'').match(/original_phone=([^ ]+)/)||[])[1] || requestedPhone || '';
      if(!originalPhone){await c.query('ROLLBACK');return res.status(409).json({success:false,code:'ORIGINAL_PHONE_MISSING'});}
      const duplicate=(await c.query("SELECT id FROM customers WHERE manager_id=$1 AND phone_number=$2 AND id<>$3 LIMIT 1",[mid,originalPhone,customer.id])).rows[0];
      if(duplicate){await c.query('ROLLBACK');return res.status(409).json({success:false,code:'PHONE_ALREADY_IN_USE',customerId:duplicate.id});}
      const cleanDescription=String(customer.description||'').replace(/^\\[GAMENEX_ARCHIVED:[^\\]]+\\] ?/,'').replace(/ ?original_phone=[^ ]+/,'').trim();
      await c.query("UPDATE customers SET phone_number=$1,description=$2,updated_at=NOW() WHERE id=$3 AND manager_id=$4",[originalPhone,cleanDescription,customer.id,mid]);
      await c.query('COMMIT');
      return res.json({success:true,restored:true,customerId:Number(customer.id)});
    }catch(e){try{await c.query('ROLLBACK')}catch(_){}return res.status(500).json({error:'Customer restore failed'});}
    finally{c.release();}
  });

  // Permanently purge an already-archived customer and every server-side record tied to that customer.
  // This is intentionally separate from the normal DELETE/archive endpoint and cannot target an active customer.
  app.delete('/api/v1/manager/customers/:id/purge', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    const mid=manager(req), id=Number(req.params.id);
    if(!Number.isInteger(id)||id<=0) return res.status(400).json({error:'Invalid customer id'});
    const c=await pool.connect();
    try{
      await c.query('BEGIN');
      let customer=(await c.query("SELECT id,description,phone_number FROM customers WHERE id=$1 AND manager_id=$2 FOR UPDATE",[id,mid])).rows[0];
      const requestedPhone=String(req.query.phone||'').trim();
      if(!customer && requestedPhone) customer=(await c.query("SELECT id,description FROM customers WHERE manager_id=$1 AND (phone_number=$2 OR description LIKE $3) FOR UPDATE",[mid,requestedPhone,'%original_phone='+requestedPhone+'%'])).rows[0];
      if(!customer){await c.query('ROLLBACK');return res.status(404).json({success:false,error:'Customer not found'});}
      if(!String(customer.description||'').startsWith('[GAMENEX_ARCHIVED:')){
        await c.query('ROLLBACK');
        return res.status(409).json({success:false,code:'CUSTOMER_NOT_ARCHIVED',error:'Only archived customers can be permanently purged'});
      }
      await c.query("DELETE FROM session_orders WHERE manager_id=$1 AND target_customer_id=$2",[mid,customer.id]);
      await c.query("DELETE FROM session_participants WHERE manager_id=$1 AND customer_id=$2",[mid,customer.id]);
      await c.query("DELETE FROM invoices WHERE manager_id=$1 AND customer_id=$2",[mid,customer.id]);
      await c.query("DELETE FROM reservations WHERE manager_id=$1 AND customer_id=$2",[mid,customer.id]);

      const candidates=await c.query(`
        SELECT DISTINCT c.table_name,c.column_name,
               EXISTS(
                 SELECT 1 FROM information_schema.columns mc
                 WHERE mc.table_schema='public' AND mc.table_name=c.table_name AND mc.column_name='manager_id'
               ) AS has_manager
        FROM information_schema.columns c
        WHERE c.table_schema='public'
          AND c.table_name <> 'customers'
          AND c.column_name IN ('customer_id','target_customer_id')
      `);
      for(const row of candidates.rows){
        if(row.table_name==='session_orders'||row.table_name==='session_participants'||row.table_name==='invoices'||row.table_name==='reservations') continue;
        const table=String(row.table_name).replace(/"/g,'""');
        const column=String(row.column_name).replace(/"/g,'""');
        if(row.has_manager){
          await c.query(`DELETE FROM "${table}" WHERE "${column}"=$1 AND manager_id=$2`,[customer.id,mid]);
        }else{
          await c.query(`DELETE FROM "${table}" WHERE "${column}"=$1`,[id]);
        }
      }

      const removed=(await c.query("DELETE FROM customers WHERE id=$1 AND manager_id=$2 AND description LIKE '[GAMENEX_ARCHIVED:%' RETURNING id",[customer.id,mid])).rowCount;
      if(!removed){await c.query('ROLLBACK');return res.status(409).json({success:false,code:'CUSTOMER_PURGE_RACE'});}
      await c.query('COMMIT');
      res.json({success:true,purged:true,customerId:Number(customer.id)});
    }catch(e){
      try{await c.query('ROLLBACK')}catch(_){}
      console.error('[customer-purge]',e?.message||e);
      res.status(500).json({success:false,error:'Customer permanent purge failed'});
    }finally{c.release();}
  });

  // Canonical configuration/settings resource. JSONB is used only for manager-editable, variable settings.
  app.get('/api/v1/manager/settings/:key', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgGet(manager(req));res.json({key:req.params.key,value:c[req.params.key] ?? ''});}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/manager/reservation-configuration', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const cfg=await getReservationConfiguration(pool,manager(req));res.json({success:true,configurationRevisionId:cfg.revisionId,version:cfg.version,configuration:{reservationRules:cfg.reservationRules,pricing:{consoles:cfg.consoles,reservations:cfg.reservations},policies:{reservation:cfg.reservationPolicy}}});}catch(e){res.status(500).json({error:'Reservation configuration lookup failed'});} });
  app.put('/api/v1/manager/reservation-configuration', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const b=req.body||{};const patch={};if(b.reservationRules&&typeof b.reservationRules==='object')patch.reservationRules=b.reservationRules;if(b.pricing&&typeof b.pricing==='object')patch.pricing=b.pricing;if(b.policies&&typeof b.policies==='object')patch.policies=b.policies;if(!Object.keys(patch).length)return res.status(400).json({error:'Reservation configuration patch is empty'});validateReservationPatch(patch);const merged=await cfgPut(manager(req),patch);res.json({success:true,configuration:merged});}catch(e){res.status(400).json({error:'Reservation configuration update failed'});} });
  app.put('/api/v1/manager/settings/:key', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const c=await cfgPut(manager(req),{[req.params.key]:req.body?.value ?? ''});res.json({key:req.params.key,value:c[req.params.key]});}catch(e){res.status(500).json({error:'Internal server error'});} });

  // Console types and buffet products live inside the manager configuration document.
  app.get('/api/v1/manager/console-types', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{res.json((await cfgGet(manager(req))).consoleTypes||[]);}catch(e){res.status(500).json({error:'Internal server error'});} });
  app.post('/api/v1/manager/billing-preview', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    try {
      const x=req.body||{};
      const consoleType=String(x.consoleType||'').trim();
      const controllerCount=Number(x.controllerCount||0);
      const amount=Number(x.amountToman||0);
      const minutes=Number(x.minutes||0);
      if(!consoleType || !Number.isInteger(controllerCount) || controllerCount<1 || controllerCount>4 || !Number.isSafeInteger(amount) || amount<0 || !Number.isSafeInteger(minutes) || minutes<0) return res.status(422).json({success:false,code:'INVALID_BILLING_PREVIEW_INPUT'});
      const cfg=await cfgGet(manager(req));
      const rates=Array.isArray(cfg.consoleTypes)?cfg.consoleTypes:[];
      const c=rates.find(v=>String(v?.name||'').trim().toLowerCase()===consoleType.toLowerCase());
      const hourlyRate=Number(c?.['price'+controllerCount]||0);
      if(!Number.isSafeInteger(hourlyRate) || hourlyRate<=0) return res.status(422).json({success:false,code:'SERVER_PRICING_NOT_CONFIGURED'});
      const durationMillis=amount>0 ? Math.floor(amount*3600000/hourlyRate) : 0;
      const durationCost=minutes>0 ? Math.floor(hourlyRate*minutes/60) : 0;
      return res.json({success:true,consoleType,controllerCount,hourlyRate,amountToman:amount,minutes,durationMillis,durationSeconds:Math.floor(durationMillis/1000),costForMinutesToman:durationCost});
    }catch(e){res.status(500).json({success:false,error:'Internal server error'});}
  });
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
  app.post('/api/v1/manager/live-stations', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const mid=manager(req),b=req.body||{},id=Number(b.id||b.stationId||0);if(!Number.isInteger(id)||id<=0)return res.status(400).json({error:'Valid station id is required'});const q=await pool.query(`INSERT INTO stations(id,manager_id,name,controller_capacity,console_type,active,reservable) VALUES($1,$2,$3,$4,$5,$6,TRUE) ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,controller_capacity=EXCLUDED.controller_capacity,console_type=EXCLUDED.console_type,active=EXCLUDED.active,updated_at=NOW() WHERE stations.manager_id=$2 RETURNING *`,[id,mid,String(b.name||('ایستگاه '+id)),Math.max(1,Number(b.controllerCount||1)),String(b.consoleType||'PS5'),String(b.status||'FREE')!=='DISABLED']);if(!q.rows[0])return res.status(404).json({error:'Station not found for this Manager'});res.json({success:true,canonical:true,station:q.rows[0]});}catch(e){console.error('live-stations sync error:',e);res.status(500).json({error:'Live station synchronization failed'});}});

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
      const paidAmount=Number(b.paidAmount||0);
      if(!Number.isFinite(paidAmount)||paidAmount<0){await c.query('ROLLBACK');return res.status(422).json({error:'Invalid payment amount'});}
      for(const rid of ids){
        const rr=(await c.query('SELECT snap_final_price,status,snap_vip_policy FROM reservations WHERE id=$1 AND manager_id=$2 FOR UPDATE',[rid,mid])).rows[0];
        if(paidAmount>Number(rr.snap_final_price||0)+0.0000001){await c.query('ROLLBACK');return res.status(422).json({error:'Payment amount exceeds reservation price'});}
        if(paidAmount>0){
          await c.query("INSERT INTO payment_transactions(manager_id,customer_id,reservation_id,amount,status,idempotency_key,provider,currency,verified_at) VALUES($1,$2,$3,$4,'SUCCESS',$5,'MANAGER_DIRECT_ENTRY','IRT',NOW()) ON CONFLICT(idempotency_key) DO NOTHING",[mid,customer.id,rid,paidAmount,'manager-direct-payment:'+key+':'+rid]);
          const full=paidAmount>=Number(rr.snap_final_price||0);
          const next=isVip ? (full?'VIP_PAYMENT_PAID':'VIP_PENDING_PAYMENT') : (full?'PENDING_APPROVAL':'PAYMENT_PENDING');
          if(next!==rr.status) await c.query('UPDATE reservations SET status=$1,updated_at=NOW() WHERE id=$2 AND manager_id=$3',[next,rid,mid]);
        }
      }
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
        PENDING: {REJECT:'REJECTED', REJECTED:'REJECTED'},
        PAYMENT_PENDING: {REJECT:'REJECTED', REJECTED:'REJECTED'},
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
            SELECT id,status,customer_id,snap_vip_policy
            FROM reservations
            WHERE manager_id=$1 AND station_id IS NULL AND id<>$2
              AND snap_vip_policy->>'isVip'='true'
              AND status IN ('CONFIRMED','ACTIVE')
              AND start_time < $3 AND end_time > $4
            FOR UPDATE
          `,[mid,id,r.end_time,r.start_time]);
          for(const vipRow of competingFullHallVip.rows){
            const existingTier=String(vipRow.snap_vip_policy?.customerTier||'').toUpperCase();
            const incomingTier=String(vipPolicy.customerTier||'').toUpperCase();
            const incomingRank=incomingTier==='DIAMOND'?2:incomingTier==='GOLD'?1:0;
            const existingRank=existingTier==='DIAMOND'?2:existingTier==='GOLD'?1:0;
            if(incomingRank>existingRank && incomingRank>0 && existingRank>0){
              const paidRow=await c.query("SELECT COALESCE(SUM(amount),0) paid FROM payment_transactions WHERE reservation_id=$1 AND manager_id=$2 AND status='SUCCESS'",[vipRow.id,mid]);
              const paidAmount=Number(paidRow.rows[0]?.paid||0);
              if(paidAmount>0 && vipRow.customer_id){
                const refundKey='reservation-vip-priority-refund:'+vipRow.id;
                const refund=await c.query("INSERT INTO wallet_transactions(manager_id,customer_id,type,reference_type,amount,reference_id,idempotency_key) VALUES($1,$2,'CREDIT','VIP_PRIORITY_SUPERSEDE',$3,$4,$5) ON CONFLICT(idempotency_key) DO NOTHING RETURNING id",[mid,vipRow.customer_id,paidAmount,String(vipRow.id),refundKey]);
                if(refund.rowCount>0) await c.query('UPDATE customers SET wallet_balance=wallet_balance+$1,updated_at=NOW() WHERE id=$2 AND manager_id=$3',[paidAmount,vipRow.customer_id,mid]);
              }
              await c.query("UPDATE reservations SET status='SUPERSEDED_BY_VIP_PRIORITY',updated_at=NOW() WHERE id=$1 AND manager_id=$2",[vipRow.id,mid]);
              await c.query('INSERT INTO reservation_audit_logs(manager_id,reservation_id,actor,old_status,new_status,reason) VALUES($1,$2,$3,$4,$5,$6)',[mid,vipRow.id,'MANAGER',vipRow.status,'SUPERSEDED_BY_VIP_PRIORITY','Diamond VIP superseded Gold VIP by configured VIP priority']);
            } else {
              await c.query('ROLLBACK');
              return res.status(409).json({error:incomingRank<existingRank?'Higher-priority Diamond VIP already confirmed for this time':'Another confirmed VIP full-hall reservation overlaps this time'});
            }
          }
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
          EXISTS(SELECT 1 FROM reservations r WHERE r.manager_id=s.manager_id AND r.station_id=s.id AND r.status NOT IN ('CANCELLED','EXPIRED','REJECTED','NO_SHOW','SUPERSEDED_BY_VIP','SUPERSEDED_BY_VIP_PRIORITY')) AS has_prior_reservation
         FROM stations s
         WHERE s.manager_id=$1 AND s.active=TRUE AND s.reservable=TRUE
         ORDER BY s.id`,
        [req.user.managerId]
      );
      res.json({success:true,stations:q.rows});
    } catch(e) { res.status(500).json({error:'Customer stations lookup failed'}); }
  });
  app.get('/api/v1/customer/stations/:stationId/reservations', requireCustomerAuth, async(req,res)=>{
    try {
      const stationId=Number(req.params.stationId);
      if(!Number.isSafeInteger(stationId)||stationId<=0)return res.status(422).json({error:'Invalid station id'});
      const q=await pool.query(`
        SELECT id,start_time,end_time,duration_minutes,status
        FROM reservations
        WHERE manager_id=$1 AND station_id=$2
          AND status NOT IN ('CANCELLED','EXPIRED','REJECTED','NO_SHOW','SUPERSEDED_BY_VIP','SUPERSEDED_BY_VIP_PRIORITY')
        ORDER BY start_time ASC`,[req.user.managerId,stationId]);
      res.json({success:true,reservations:q.rows.map(r=>({id:r.id,start_time:r.start_time,end_time:r.end_time,duration_minutes:r.duration_minutes,status:r.status}))});
    } catch(e) { res.status(500).json({error:'Customer station reservations lookup failed'}); }
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
      const paymentDeadlineAt = policy.paymentDeadlineAt ? new Date(policy.paymentDeadlineAt) : new Date(new Date(r.created_at).getTime()+paymentDeadline*60000);
      if(paymentDeadline > 0 && Number.isFinite(paymentDeadlineAt.getTime()) && new Date() > paymentDeadlineAt && r.status === paymentPendingStatus){
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
      // Equivalent to ROW_NUMBER() OVER (ORDER BY id), but lock base station rows directly; PostgreSQL forbids FOR UPDATE with window functions.
      const ranked=await c.query(`SELECT id FROM stations WHERE manager_id=$1 ORDER BY id FOR UPDATE`,[mid]);
      const overflow=ranked.rows.slice(keep).map(r=>Number(r.id));
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

  app.get('/api/v1/manager/customers/:id/activity', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    try{
      const mid=manager(req), id=Number(req.params.id);
      if(!Number.isInteger(id)||id<=0) return res.status(400).json({error:'Invalid customer id'});
      const customer=(await pool.query('SELECT * FROM customers WHERE id=$1 AND manager_id=$2',[id,mid])).rows[0];
      if(!customer) return res.status(404).json({error:'Customer not found'});
      const [tx,gn,lp,behavior,payments]=await Promise.all([
        pool.query(`SELECT ct.*,COALESCE((SELECT SUM(g.amount) FROM gn_ledger g WHERE g.manager_id=ct.manager_id AND g.customer_id=ct.customer_id AND g.type='CREDIT' AND (g.reference_id LIKE ('SESSION_REVIEW_'||ct.session_id::text||'_CUST_'||ct.customer_id::text||'%') OR g.reference_id IN (SELECT i.id::text FROM invoices i WHERE i.manager_id=ct.manager_id AND i.session_id=ct.session_id AND i.customer_id=ct.customer_id))),0)::bigint AS earned_gn,COALESCE((SELECT SUM(l.amount) FROM lp_ledger l WHERE l.manager_id=ct.manager_id AND l.customer_id=ct.customer_id AND l.type='CREDIT' AND (l.reference_id=ct.session_id::text OR l.reference_id IN (SELECT i.id::text FROM invoices i WHERE i.manager_id=ct.manager_id AND i.session_id=ct.session_id AND i.customer_id=ct.customer_id))),0)::bigint AS earned_lp FROM customer_transactions ct WHERE ct.manager_id=$1 AND ct.customer_id=$2 AND ct.status<>'DELETED' ORDER BY ct.created_at DESC LIMIT 500`,[mid,customer.id]),
        pool.query("SELECT * FROM gn_ledger WHERE manager_id=$1 AND customer_id=$2 ORDER BY created_at DESC LIMIT 500",[mid,customer.id]),
        pool.query("SELECT * FROM lp_ledger WHERE manager_id=$1 AND customer_id=$2 ORDER BY created_at DESC LIMIT 500",[mid,customer.id]),
        pool.query("SELECT * FROM behavior_logs WHERE manager_id=$1 AND customer_id=$2 ORDER BY timestamp DESC LIMIT 500",[mid,customer.id]).catch(()=>({rows:[]})),
        pool.query("SELECT * FROM payment_transactions WHERE manager_id=$1 AND customer_id=$2 ORDER BY created_at DESC LIMIT 500",[mid,customer.id]).catch(()=>({rows:[]}))
      ]);
      res.json({success:true,customer:normalizeCustomer(customer),transactions:tx.rows,gnLedger:gn.rows,lpLedger:lp.rows,behaviorLogs:behavior.rows,payments:payments.rows});
    }catch(e){console.error('[customer-activity]',e?.message||e);res.status(500).json({error:'Customer activity lookup failed'});}
  });

  app.post('/api/v1/manager/customer-transactions', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const b=req.body||{},mid=manager(req),customerId=Number(b.customerId||0),localId=Number(b.localId||0)||null;const customerOrNull=customerId>0?customerId:null;if(customerOrNull!==null){const customer=await pool.query('SELECT id FROM customers WHERE id=$1 AND manager_id=$2',[customerOrNull,mid]);if(!customer.rows[0])return res.status(404).json({error:'Customer not found'});}const amount=Math.max(0,Math.trunc(Number(b.amount||0)));const paidAmount=Math.max(0,Math.trunc(Number(b.paidAmount||0)));const gameCost=Math.max(0,Math.trunc(Number(b.gameCost||b.game_cost||0)));const foodCost=Math.max(0,Math.trunc(Number(b.foodCost||b.food_cost||0)));const sessionId=String(b.sessionId||b.session_id||'').trim()||null;const q=await pool.query(`INSERT INTO customer_transactions(manager_id,customer_id,customer_name,station_name,title,amount,paid_amount,status,date_str,time_str,segment_details,buffet_details,event_timestamp,play_minutes,game_cost,food_cost,local_id,session_id) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18) ON CONFLICT (manager_id,local_id) WHERE local_id IS NOT NULL DO UPDATE SET customer_id=EXCLUDED.customer_id,customer_name=EXCLUDED.customer_name,station_name=EXCLUDED.station_name,title=EXCLUDED.title,amount=EXCLUDED.amount,paid_amount=EXCLUDED.paid_amount,status=EXCLUDED.status,date_str=EXCLUDED.date_str,time_str=EXCLUDED.time_str,segment_details=EXCLUDED.segment_details,buffet_details=EXCLUDED.buffet_details,event_timestamp=EXCLUDED.event_timestamp,play_minutes=EXCLUDED.play_minutes,game_cost=EXCLUDED.game_cost,food_cost=EXCLUDED.food_cost,session_id=COALESCE(EXCLUDED.session_id,customer_transactions.session_id),updated_at=NOW() RETURNING *`,[mid,customerOrNull,b.customerName||'',b.stationName||'',b.title||'',amount,paidAmount,b.status||'UNREVIEWED',b.dateStr||'',b.timeStr||'',b.segmentDetails||'',b.buffetDetails||'',Number(b.timestamp||0),Number(b.playMinutes||b.play_minutes||0),gameCost,foodCost,localId,sessionId]);res.status(201).json(q.rows[0]);}catch(e){console.error('[customer-transactions]',e?.message||e);res.status(500).json({error:'Internal server error'});} });
  app.get('/api/v1/manager/customer-transactions', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const q=await pool.query(`SELECT ct.*, COALESCE((SELECT gs.id FROM game_sessions gs WHERE ct.session_id IS NULL AND ct.customer_id IS NULL AND gs.manager_id=ct.manager_id AND gs.station_id=CAST(NULLIF(regexp_replace(ct.station_name,'[^0-9]','','g'),'') AS integer) AND gs.status='SETTLED' AND gs.ended_at BETWEEN ct.created_at-INTERVAL '5 seconds' AND ct.created_at+INTERVAL '5 seconds' ORDER BY ABS(EXTRACT(EPOCH FROM (gs.ended_at-ct.created_at))) LIMIT 1),ct.session_id) AS session_id, COALESCE((SELECT SUM(g.amount) FROM gn_ledger g WHERE g.manager_id=ct.manager_id AND g.customer_id=ct.customer_id AND g.type='CREDIT' AND (g.reference_id LIKE ('SESSION_REVIEW_'||ct.session_id::text||'_CUST_'||ct.customer_id::text||'%') OR g.reference_id IN (SELECT i.id::text FROM invoices i WHERE i.manager_id=ct.manager_id AND i.session_id=ct.session_id AND i.customer_id=ct.customer_id))),0)::bigint AS earned_gn, COALESCE((SELECT SUM(l.amount) FROM lp_ledger l WHERE l.manager_id=ct.manager_id AND l.customer_id=ct.customer_id AND l.type='CREDIT' AND (l.reference_id=ct.session_id::text OR l.reference_id IN (SELECT i.id::text FROM invoices i WHERE i.manager_id=ct.manager_id AND i.session_id=ct.session_id AND i.customer_id=ct.customer_id))),0)::bigint AS earned_lp FROM customer_transactions ct WHERE ct.manager_id=$1 AND ct.status <> 'DELETED' ORDER BY ct.created_at DESC LIMIT 1000`,[manager(req)]);res.json(q.rows);}catch(e){console.error('[customer-transactions/list]',e?.message||e);res.status(500).json({error:'Internal server error'});} });
  app.delete('/api/v1/manager/customer-transactions/:id', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{try{const id=Number(req.params.id);if(!Number.isSafeInteger(id)||id<=0)return res.status(422).json({error:'Invalid transaction id'});const mid=manager(req);let q=await pool.query("UPDATE customer_transactions SET status='DELETED',updated_at=NOW() WHERE id=$1 AND manager_id=$2 AND status <> 'DELETED' RETURNING id,local_id",[id,mid]);if(!q.rows[0]) q=await pool.query("UPDATE customer_transactions SET status='DELETED',updated_at=NOW() WHERE local_id=$1 AND manager_id=$2 AND status <> 'DELETED' RETURNING id,local_id",[id,mid]);if(!q.rows[0]){const existing=await pool.query("SELECT id,local_id,status FROM customer_transactions WHERE manager_id=$1 AND (id=$2 OR local_id=$2) LIMIT 1",[mid,id]);if(existing.rows[0] && existing.rows[0].status==='DELETED')return res.json({success:true,id:existing.rows[0].id,localId:existing.rows[0].local_id||null,idempotent:true});return res.status(404).json({error:'Transaction not found'});}res.json({success:true,id:q.rows[0].id,localId:q.rows[0].local_id||null});}catch(e){console.error('[customer-transactions/delete]',e?.message||e);res.status(500).json({error:'Internal server error'});} });
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
  app.get('/api/v1/manager/sessions/:sessionId/settlement-review', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    try{
      const mid=manager(req), sid=String(req.params.sessionId||'');
      const requestedCustomerId = req.query?.customerId !== undefined && req.query?.customerId !== ''
        ? Number(req.query.customerId)
        : null;
      const requestedParticipantName = String(req.query?.participantName || '').trim();
      if (requestedCustomerId !== null && (!Number.isSafeInteger(requestedCustomerId) || (requestedCustomerId === 0 && !requestedParticipantName))) {
        return res.status(400).json({error:'Invalid customerId'});
      }
      const session=(await pool.query("SELECT id,station_id,status,started_at,ended_at,game_cost,buffet_cost,total_cost,pricing_snapshot FROM game_sessions WHERE id=$1 AND manager_id=$2",[sid,mid])).rows[0];
      if(!session) return res.status(404).json({error:'Session not found'});
      const participants=(await pool.query("SELECT customer_id,participant_key,participant_name,is_guest,is_payer,prepayment_amount FROM session_participants WHERE session_id=$1 AND manager_id=$2 ORDER BY id",[sid,mid])).rows;
      const snapshot=session.pricing_snapshot && typeof session.pricing_snapshot==='object' ? session.pricing_snapshot : {};
      const snapshotPrepayments=snapshot.customerPrepayments && typeof snapshot.customerPrepayments==='object' ? snapshot.customerPrepayments : {};
      // A true walk-in session may have no session_participants row. Preserve the session-level
      // initial payment as a single guest payer so settlement review can still account for it.
      if(participants.length===0 && Number(snapshot.initialPrepaymentAmount||0)>0){
        participants.push({customer_id:null,participant_key:'guest:walk-in',participant_name:'مشتری گذری (بدون اشتراک)',is_guest:true,is_payer:true,prepayment_amount:Number(snapshot.initialPrepaymentAmount||0)});
      }
      const invoiceRows=(await pool.query("SELECT invoice_number,customer_id,game_cost,buffet_cost,total_amount,paid_amount,status,customer_snapshot FROM invoices WHERE session_id=$1 AND manager_id=$2 ORDER BY id",[sid,mid])).rows;
      const orderBuffet=Number((await pool.query("SELECT COALESCE(SUM(line_total),0) amount FROM session_orders WHERE session_id=$1 AND manager_id=$2",[sid,mid])).rows[0]?.amount||0);
      const invoiceByCustomer=new Map(invoiceRows.filter(r=>r.customer_id!==null).map(r=>[Number(r.customer_id),r]));
      const invoiceByParticipantKey=new Map();
      invoiceRows.filter(r=>r.customer_id===null).forEach(r=>{
        const snapshotKey=String(r.customer_snapshot?.participantKey||'');
        if(snapshotKey) invoiceByParticipantKey.set(snapshotKey,r);
        const match=String(r.invoice_number||'').match(/-((?:guest|walk-in|customer)[-_].*)$/i);
        if(match) {
          invoiceByParticipantKey.set(match[1],r);
          invoiceByParticipantKey.set(match[1].replace(/_/g, ':'),r);
        }
      });
      const enriched=participants.map((p,pIndex)=>{
        const cid=p.customer_id===null?0:Number(p.customer_id);
        const guestBillingId=String(-(pIndex+1));
        const snapshotPre=cid>0
          ? Number(snapshotPrepayments[String(cid)]||0)
          : Number(snapshotPrepayments[guestBillingId]||snapshotPrepayments[String(p.participant_key)]||0);
        const sessionInitial=Number(snapshot.initialPrepaymentAmount||0);
        const solePayerFallback=(participants.filter(x=>x.is_payer).length===1 && p.is_payer && sessionInitial>0)?sessionInitial:0;
        const prepayment=Math.max(0,Number(p.prepayment_amount||0)||snapshotPre||solePayerFallback);
        const inv=cid>0
          ? invoiceByCustomer.get(cid)
          : invoiceByParticipantKey.get(String(p.participant_key||'')) || invoiceRows.find(r=>r.customer_id===null && String(r.customer_snapshot?.name||'')===String(p.participant_name||''));
        const gameCost=Number(inv?.game_cost||0);
        const buffetCost=Number(inv?.buffet_cost||0);
        const invoiceTotal=Number(inv?.total_amount||0);
        // Initial payment covers the full final invoice (game + buffet), not game time alone.
        const unusedPrepayment=Math.max(0,prepayment-invoiceTotal);
        return {...p,prepayment_amount:String(prepayment),game_cost:String(gameCost),buffet_cost:String(buffetCost),invoice_total:String(invoiceTotal),unused_prepayment:String(unusedPrepayment)};
      });
      const guestParticipants=enriched.filter(p=>Boolean(p.is_guest) && p.customer_id===null);
      const selectedParticipants = requestedCustomerId === null
        ? enriched
        : requestedCustomerId < 0
          ? (guestParticipants[-requestedCustomerId-1] ? [guestParticipants[-requestedCustomerId-1]] : [])
          : requestedCustomerId === 0
            ? guestParticipants.filter(p => String(p.participant_name||'') === requestedParticipantName).slice(0,1)
            : enriched.filter(p => Number(p.customer_id || 0) === requestedCustomerId);
      if (requestedCustomerId !== null && selectedParticipants.length === 0) {
        return res.status(404).json({error:'Customer is not a payer in this session',customerId:requestedCustomerId});
      }
      const allocatedPrepaymentTotal=enriched.filter(p=>p.is_payer).reduce((n,p)=>n+Number(p.prepayment_amount||0),0);
      const sessionTotalPrepayment=Math.max(allocatedPrepaymentTotal, Number(snapshot.initialPrepaymentAmount||0));
      const poolAmount=selectedParticipants.filter(p=>p.is_payer).reduce((n,p)=>n+Number(p.prepayment_amount||0),0);
      const gameCost=selectedParticipants.filter(p=>p.is_payer).reduce((n,p)=>n+Number(p.game_cost||0),0);
      const buffetCost=selectedParticipants.filter(p=>p.is_payer).reduce((n,p)=>n+Number(p.buffet_cost||0),0);
      const requestedGuestIndex = requestedCustomerId === 0
        ? guestParticipants.findIndex(p => String(p.participant_name||'') === requestedParticipantName)
        : -1;
      const requestedBillingCustomerId = requestedCustomerId === 0 && requestedGuestIndex >= 0
        ? -(requestedGuestIndex + 1)
        : requestedCustomerId;
      const refundParams = requestedBillingCustomerId === null ? [mid,sid] : [mid,sid,requestedBillingCustomerId];
      const refunded=(await pool.query(
        requestedCustomerId === null
          ? "SELECT COALESCE(SUM(refund_amount),0) amount FROM session_review_refunds WHERE manager_id=$1 AND session_id=$2 AND method='WALLET'"
          : "SELECT COALESCE(SUM(refund_amount),0) amount FROM session_review_refunds WHERE manager_id=$1 AND session_id=$2 AND customer_id=$3 AND method='WALLET'",
        refundParams
      )).rows[0].amount;
      const finalized=(await pool.query(
        requestedCustomerId === null
          ? "SELECT customer_id,refund_amount,method,status FROM session_review_refunds WHERE manager_id=$1 AND session_id=$2 ORDER BY customer_id"
          : "SELECT customer_id,refund_amount,method,status FROM session_review_refunds WHERE manager_id=$1 AND session_id=$2 AND customer_id=$3 ORDER BY customer_id",
        refundParams
      )).rows;
      const unusedPool=selectedParticipants.filter(p=>p.is_payer).reduce((n,p)=>n+Number(p.unused_prepayment||0),0);
      const responseParticipants=selectedParticipants.map(p=>{
        const billingCustomerId=p.customer_id!==null
          ? Number(p.customer_id)
          : -(guestParticipants.findIndex(g=>g.participant_key===p.participant_key)+1);
        return {...p,billing_customer_id:billingCustomerId};
      });
      return res.json({success:true,session:{...session,game_cost:String(gameCost),buffet_cost:String(buffetCost),total_cost:String(gameCost+buffetCost)},participants:responseParticipants,totalPrepayment:String(poolAmount),sessionTotalPrepayment:String(sessionTotalPrepayment),gameCost:String(gameCost),buffetCost:String(buffetCost),unusedPool:String(unusedPool),refundedPrepayment:String(refunded),remainingRefundable:String(Math.max(0,unusedPool-Number(refunded||0))),finalized});
    }catch(e){console.error('[settlement-review]',e?.message||e);return res.status(500).json({error:'Settlement review lookup failed'});}
  });

  app.post('/api/v1/manager/sessions/:sessionId/review-finalize', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    const c=await pool.connect();
    try{
      const mid=manager(req), sid=String(req.params.sessionId||''), decisions=Array.isArray(req.body?.decisions)?req.body.decisions:[];
      if(!sid || decisions.length===0) return res.status(400).json({error:'decisions are required'});
      await c.query('BEGIN');
      const session=(await c.query("SELECT * FROM game_sessions WHERE id=$1 AND manager_id=$2 FOR UPDATE",[sid,mid])).rows[0];
      if(!session){await c.query('ROLLBACK');return res.status(404).json({error:'Session not found'});}
      const config=await getManagerConfiguration(c,mid);
      const settings=config.settings||{};
      // Normal session rewards use one authoritative rule for both ledgers:
      // default game = 1 GN + 1 LP per 1,000 Toman;
      // default buffet = 0.5 GN + 0.5 LP per 1,000 Toman.
      // Manager may change the GN-side rates in Settings; GN and LP must remain
      // mathematically identical for the same eligible spend.
      const gameRewardPer10000=Math.max(0,Number(settings.policy_game_reward_rate??10));
      const buffetRewardPer10000=Math.max(0,Number(settings.policy_buffet_reward_rate??5));
      const participants=(await c.query("SELECT customer_id,participant_key,participant_name,is_guest,is_payer,prepayment_amount FROM session_participants WHERE session_id=$1 AND manager_id=$2 AND is_payer=TRUE FOR UPDATE",[sid,mid])).rows;
      const authoritativeBuffet=Number((await c.query("SELECT COALESCE(SUM(line_total),0) amount FROM session_orders WHERE session_id=$1 AND manager_id=$2",[sid,mid])).rows[0]?.amount||0);
      const byId=new Map(participants.filter(p=>p.customer_id).map(p=>[Number(p.customer_id),p]));
      const guestPayers=participants.filter(p=>!p.customer_id && p.is_guest && p.is_payer);
      const snapshot=session.pricing_snapshot && typeof session.pricing_snapshot==='object'?session.pricing_snapshot:{};
      const snapPre=snapshot.customerPrepayments && typeof snapshot.customerPrepayments==='object'?snapshot.customerPrepayments:{};
      const invoiceRowsForSession=(await c.query("SELECT id,invoice_number,customer_id,game_cost,buffet_cost,total_amount,paid_amount,status,customer_snapshot FROM invoices WHERE session_id=$1 AND manager_id=$2 ORDER BY id",[sid,mid])).rows;
      const guestInvoiceByKey=new Map();
      invoiceRowsForSession.filter(r=>r.customer_id===null).forEach(r=>{
        const key=String(r.customer_snapshot?.participantKey||'');
        if(key) guestInvoiceByKey.set(key,r);
        const m=String(r.invoice_number||'').match(/-((?:guest|walk-in|customer)[-_].*)$/i);
        if(m) {
          guestInvoiceByKey.set(m[1],r);
          guestInvoiceByKey.set(m[1].replace(/_/g,':'),r);
        }
      });
      const results=[];
      for(const d of decisions){
        const cid=Number(d.customerId||0), method=String(d.refundMethod||'NONE').toUpperCase(), refund=Math.max(0,Math.trunc(Number(d.refundAmount||0))), status=String(d.status||'REVIEWED').toUpperCase();
        const isGuestDecision=cid<=0;
        const participant=isGuestDecision
          ? guestPayers[Math.max(0,(-cid)-1)] || (cid===0 ? guestPayers[0] : null)
          : byId.get(cid);
        if(!participant){await c.query('ROLLBACK');return res.status(422).json({error:'Customer is not a payer in this session',customerId:cid});}
        const priorReview=(await c.query("SELECT refund_amount,method,status FROM session_review_refunds WHERE manager_id=$1 AND session_id=$2 AND customer_id=$3 LIMIT 1",[mid,sid,cid])).rows[0];
        if(priorReview){
          const rg=Number((await c.query("SELECT COALESCE(SUM(amount),0) amount FROM gn_ledger WHERE manager_id=$1 AND customer_id=$2 AND reference_type='SESSION_REVIEW' AND reference_id=$3 AND type='CREDIT'",[mid,cid,sid])).rows[0].amount||0);
          const rl=Number((await c.query("SELECT COALESCE(SUM(amount),0) amount FROM lp_ledger WHERE manager_id=$1 AND customer_id=$2 AND reference_type='SESSION_REVIEW' AND reference_id=$3 AND type='CREDIT'",[mid,cid,sid])).rows[0].amount||0);
          results.push({customerId:cid,customerName:participant.participant_name,refundAmount:String(priorReview.refund_amount||0),refundMethod:String(priorReview.method||'NONE'),earnedGn:rg,earnedLp:rl,status:String(priorReview.status||'REVIEWED'),idempotent:true});
          continue;
        }
        const solePayerFallback=(participants.length===1 && participant.is_payer)?Number(snapshot.initialPrepaymentAmount||0):0;
        const prepayment=Math.max(0,Number(participant.prepayment_amount||0)||(isGuestDecision?Number(snapPre[String(participant.participant_key)]||0):Number(snapPre[String(cid)]||0))||solePayerFallback);
        let inv;
        if (isGuestDecision) {
          inv=guestInvoiceByKey.get(String(participant.participant_key||'')) || (await c.query("SELECT id,game_cost,buffet_cost,total_amount,paid_amount,status FROM invoices WHERE session_id=$1 AND manager_id=$2 AND customer_id IS NULL AND customer_snapshot->>'name'=$3 ORDER BY id DESC LIMIT 1 FOR UPDATE",[sid,mid,participant.participant_name])).rows[0];
        } else {
          inv=(await c.query("SELECT id,game_cost,buffet_cost,total_amount,paid_amount,status FROM invoices WHERE session_id=$1 AND manager_id=$2 AND customer_id=$3 ORDER BY id DESC LIMIT 1 FOR UPDATE",[sid,mid,cid])).rows[0];
        }
        if(!inv){
          if(!isGuestDecision){await c.query('ROLLBACK');return res.status(422).json({error:'Invoice not found for customer',customerId:cid});}
          const guestInvoiceNumber='GN-'+sid+'-'+String(participant.participant_key||('guest-'+(-cid))).replace(/[^A-Za-z0-9_-]/g,'_');
          inv=(await c.query("INSERT INTO invoices(invoice_number,manager_id,customer_id,session_id,station_id,status,currency,game_cost,buffet_cost,total_amount,paid_amount,settlement_idempotency_key,pricing_snapshot,customer_snapshot,manager_snapshot) VALUES($1,$2,NULL,$3,$4,'UNPAID','IRT',$5,$6,$7,0,$8,$9::jsonb,$10::jsonb,$11::jsonb) ON CONFLICT(manager_id,invoice_number) DO UPDATE SET game_cost=EXCLUDED.game_cost,buffet_cost=EXCLUDED.buffet_cost,total_amount=EXCLUDED.total_amount,updated_at=NOW() RETURNING id,game_cost,buffet_cost,total_amount,paid_amount,status",[guestInvoiceNumber,mid,sid,session.station_id,String(session.game_cost||0),String(session.buffet_cost||0),String(session.total_cost||0),'review_guest_'+sid+'_'+String(participant.participant_key||cid),JSON.stringify(snapshot),JSON.stringify({id:null,name:participant.participant_name,isGuest:true,participantKey:participant.participant_key}),JSON.stringify({id:mid})])).rows[0];
        }
        const gameCost=Number(inv.game_cost||0);
        const buffetCost=participants.length===1 ? authoritativeBuffet : Number(inv.buffet_cost||0);
        const total=gameCost+buffetCost;
        if(participants.length===1 && (Number(inv.buffet_cost||0)!==buffetCost || Number(inv.total_amount||0)!==total)){
          await c.query("UPDATE invoices SET buffet_cost=$1,total_amount=$2,updated_at=NOW() WHERE id=$3 AND manager_id=$4",[buffetCost,total,inv.id,mid]);
        }
        const unused=Math.max(0,prepayment-(gameCost+buffetCost));
        if(refund>unused){await c.query('ROLLBACK');return res.status(422).json({error:'Refund exceeds unused initial payment',customerId:cid,unused:String(unused),requested:String(refund)});}
        if(method!=='NONE' && method!=='WALLET' && method!=='CASH'){await c.query('ROLLBACK');return res.status(422).json({error:'Invalid refund method'});}
        if(isGuestDecision && method==='WALLET' && refund>0){await c.query('ROLLBACK');return res.status(422).json({error:'Guest customer cannot receive wallet refund',customerId:cid});}
        if(method==='WALLET' && refund>0 && !isGuestDecision){
          const key='session-review-wallet:'+sid+':'+cid;
          const ins=await c.query("INSERT INTO session_review_refunds(manager_id,session_id,customer_id,refund_amount,method,status,idempotency_key) VALUES($1,$2,$3,$4,'WALLET','FINALIZED',$5) ON CONFLICT(manager_id,session_id,customer_id) DO UPDATE SET refund_amount=EXCLUDED.refund_amount,method='WALLET',status='FINALIZED' RETURNING id",[mid,sid,cid,refund,key]);
          const tx=await c.query("INSERT INTO wallet_transactions(manager_id,customer_id,amount,type,reference_type,reference_id,idempotency_key) VALUES($1,$2,$3,'CREDIT','SESSION_PREPAYMENT_REFUND',$4,$5) ON CONFLICT(idempotency_key) DO NOTHING RETURNING id",[mid,cid,refund,sid,key]);
          if(tx.rowCount>0) await c.query("UPDATE customers SET wallet_balance=wallet_balance+$1,updated_at=NOW() WHERE id=$2 AND manager_id=$3",[refund,cid,mid]);
        } else {
          const key='session-review-refund:'+sid+':'+cid;
          await c.query("INSERT INTO session_review_refunds(manager_id,session_id,customer_id,refund_amount,method,status,idempotency_key) VALUES($1,$2,$3,$4,$5,'FINALIZED',$6) ON CONFLICT(manager_id,session_id,customer_id) DO UPDATE SET refund_amount=EXCLUDED.refund_amount,method=EXCLUDED.method,status='FINALIZED'",[mid,sid,cid,refund,method,key]);
        }
        // Rewards are based on the authoritative final invoice components.
        // Rates are configurable as points per 10,000 Toman; floor is intentional
        // so no partial GN/LP is fabricated in the integer ledgers.
        const gameReward=Math.floor((gameCost/10000)*gameRewardPer10000);
        const buffetReward=Math.floor((buffetCost/10000)*buffetRewardPer10000);
        const earnedGn=Math.max(0,gameReward+buffetReward);
        const earnedLp=Math.max(0,gameReward+buffetReward);
        const reviewKey='session-review:'+sid+':'+cid;
        if(!isGuestDecision && earnedGn>0){const g=await c.query("INSERT INTO gn_ledger(manager_id,customer_id,amount,type,reference_type,reference_id,idempotency_key) VALUES($1,$2,$3,'CREDIT','SESSION_REVIEW',$4,$5) ON CONFLICT(idempotency_key) DO NOTHING RETURNING id",[mid,cid,earnedGn,sid,reviewKey+':GN']);if(g.rowCount>0) await c.query('UPDATE customers SET gn_balance=gn_balance+$1,updated_at=NOW() WHERE id=$2 AND manager_id=$3',[earnedGn,cid,mid]);}
        if(!isGuestDecision && earnedLp>0){const l=await c.query("INSERT INTO lp_ledger(manager_id,customer_id,amount,type,reference_type,reference_id,idempotency_key) VALUES($1,$2,$3,'CREDIT','SESSION_REVIEW',$4,$5) ON CONFLICT(idempotency_key) DO NOTHING RETURNING id",[mid,cid,earnedLp,sid,reviewKey+':LP']);if(l.rowCount>0) await c.query('UPDATE customers SET lp_balance=lp_balance+$1 WHERE id=$2 AND manager_id=$3',[earnedLp,cid,mid]);}
        const requestedStatus=status==='PARTIAL'?'PARTIAL':(status==='DEBTOR'?'DEBTOR':'REVIEWED');
        const finalStatus=requestedStatus==='PARTIAL'?'DEBTOR':requestedStatus;
        const requestedPaid=Math.max(0,Math.trunc(Number(d.paidAmount||0)));
        const minimumPaid=Math.min(total,prepayment);
        const paidAmount=requestedStatus==='REVIEWED'?total:(requestedStatus==='DEBTOR'?minimumPaid:Math.min(total,Math.max(minimumPaid,requestedPaid)));
        if(requestedStatus==='PARTIAL' && paidAmount<=minimumPaid){await c.query('ROLLBACK');return res.status(422).json({error:'Partial payment must be greater than the applied initial payment',customerId:cid});}
        await c.query("UPDATE invoices SET status=$1,paid_amount=$2,updated_at=NOW() WHERE id=$3 AND manager_id=$4",[finalStatus,paidAmount,inv.id,mid]);
        if (isGuestDecision) {
          await c.query("UPDATE customer_transactions SET status=$1,paid_amount=$2,session_id=COALESCE(session_id,$4),updated_at=NOW() WHERE manager_id=$3 AND session_id IS NOT DISTINCT FROM $4 AND customer_id IS NULL AND customer_name=$5 AND status<>'DELETED'",[finalStatus,paidAmount,mid,sid,participant.participant_name]);
        } else {
          await c.query("UPDATE customer_transactions SET status=$1,paid_amount=$2,earned_gn=$3,earned_lp=$4,session_id=COALESCE(session_id,$6),updated_at=NOW() WHERE manager_id=$5 AND session_id IS NOT DISTINCT FROM $6 AND customer_id=$7 AND status<>'DELETED'",[finalStatus,paidAmount,earnedGn,earnedLp,mid,sid,cid]);
        }
        if(finalStatus==='DEBTOR' && !isGuestDecision){
          const outstanding=Math.max(0,total-paidAmount);
          if(outstanding>0) await c.query("UPDATE customers SET debt=GREATEST(0,COALESCE(debt,0)+$1),updated_at=NOW() WHERE id=$2 AND manager_id=$3",[outstanding,cid,mid]);
        }
        results.push({customerId:cid,customerName:participant.participant_name,refundAmount:String(refund),refundMethod:method,earnedGn,earnedLp,status:finalStatus});
      }
      await c.query('COMMIT');
      return res.json({success:true,sessionId:sid,results});
    }catch(e){try{await c.query('ROLLBACK')}catch(_){}console.error('[settlement-review-finalize]',e?.message||e);return res.status(500).json({error:'Settlement review finalization failed'});}finally{c.release();}
  });

  app.post('/api/v1/manager/club/lp-ledger', requireManagerAuth, requireActiveEntitlement, async(req,res)=>{
    const c=await pool.connect();
    try{
      const b=req.body||{}, mid=manager(req), customerId=Number(b.customerId||0), amount=Math.max(0,Math.trunc(Number(b.lpAmount||b.amount||0)));
      const key=String(req.headers['idempotency-key']||b.idempotencyKey||'').trim();
      if(!customerId||amount<=0||!key)return res.status(400).json({error:'Valid customerId, amount and idempotency key are required'});
      await c.query('BEGIN');
      const customer=(await c.query('SELECT id FROM customers WHERE id=$1 AND manager_id=$2 FOR UPDATE',[customerId,mid])).rows[0];
      if(!customer){await c.query('ROLLBACK');return res.status(404).json({error:'Customer not found'});}
      const prior=(await c.query('SELECT * FROM lp_ledger WHERE manager_id=$1 AND idempotency_key=$2 LIMIT 1 FOR UPDATE',[mid,key])).rows[0];
      if(prior){await c.query('COMMIT');return res.json({...prior,idempotent:true});}
      const q=await c.query("INSERT INTO lp_ledger(manager_id,customer_id,amount,type,reference_type,reference_id,idempotency_key) VALUES($1,$2,$3,'CREDIT',$4,$5,$6) RETURNING *",[mid,customerId,amount,String(b.referenceType||'SESSION_PAYMENT'),String(b.referenceId||''),key]);
      await c.query('UPDATE customers SET lp_balance=lp_balance+$1,updated_at=NOW() WHERE id=$2 AND manager_id=$3',[amount,customerId,mid]);
      await c.query('COMMIT');return res.json(q.rows[0]);
    }catch(e){try{await c.query('ROLLBACK')}catch(_){}return res.status(500).json({error:'LP ledger update failed'});}
    finally{c.release();}
  });

  app.post('/api/v1/coupons/validate', async(req,res)=>res.json({valid:false,discountPercent:0,message:'کد تخفیف معتبر یافت نشد.'}));
};
