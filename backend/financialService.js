const { Pool } = require('pg');

const pool = new Pool({
  connectionString: process.env.DATABASE_URL
});

const DEFAULT_RESERVATION_CONFIGURATION = {
    version: 1,
    currency: 'IRT',
    reservationRules: {
        normalDurationsMinutes: [60, 120, 180, 240, 300],
        vipDurationsMinutes: [180, 240, 300, 360],
        vipMinDurationMinutes: 180,
        fullHallMinDurationMinutes: 180,
        exclusiveFullDayDurationMinutes: 900,
        vipStartTime: '14:00',
        vipEndTime: '24:00',
        vipPrice: 6300000,
        paymentDeadlineMinutes: 60,
        vipPaymentDeadlineMinutes: 60,
        arrivalWarningMinutes: 10,
        arrivalReminderMinutes: [120, 30, 15, 5],
        cancellation: {
            atOrAbove24h: {thresholdMinutes:1440, gn:50, lp:50, walletPercent:100},
            above15h: {thresholdMinutes:900, gn:70, lp:70, walletPercent:90},
            above5h: {thresholdMinutes:300, gn:120, lp:120, walletPercent:85},
            above2h: {thresholdMinutes:120, gn:200, lp:200, walletPercent:80},
            within2h: {thresholdMinutes:0, gn:250, lp:250, walletPercent:70, restrictionDays:7, surchargePercent:5}
        },
        lateCancellation: {gn:250, lp:250, walletPercent:0, restrictionDays:0, surchargePercent:0},
        noShow: {gn:250, lp:250, walletPercent:0, restrictionDays:0, surchargePercent:0},
        vipReward: {gn:300, lp:300},
        messages: {
            payment: 'مشتری گرامی چنانچه قصد رزرو ({duration}) ساعته جایگاهی را داشته باشید، بعد از ثبت درخواست و ارسال آن به مدیریت، حتماً باید هزینه آن را حداکثر ({payment_deadline}) دقیقه بعد پرداخت و به مدیریت گزارش دهید.',
            arrival: 'توجه، لطفاً ({minutes_before_arrival}) دقیقه قبل از تایم رزرو شده در گیم‌نت حضور داشته باشید، در صورت عدم حضور به‌موقع یا لغو سرخود رزروتان، با کسر ({gn_penalty}) امتیاز GN و ({lp_penalty}) امتیاز LP، جایگاه به مشتری دیگری واگذار خواهد شد. هزینه پرداخت‌شده عودت بانکی ندارد و اعتبار مجاز به Wallet منتقل می‌شود.',
            vip: 'رزرو VIP پس از پرداخت کامل و تأیید مدیریت قطعی خواهد شد.',
            cancel24: 'در صورت لغو با حداقل ({threshold_minutes}) دقیقه زمان باقی‌مانده، ({refund_percent})٪ مبلغ پرداختی به Wallet و ({gn_penalty}) GN و ({lp_penalty}) LP جریمه اعمال می‌شود.',
            cancel15: 'قانون لغو این بازه: ({refund_percent})٪ اعتبار Wallet، ({gn_penalty}) GN و ({lp_penalty}) LP.',
            cancel5: 'قانون لغو این بازه: ({refund_percent})٪ اعتبار Wallet، ({gn_penalty}) GN و ({lp_penalty}) LP.',
            cancel2: 'قانون لغو این بازه: ({refund_percent})٪ اعتبار Wallet، ({gn_penalty}) GN و ({lp_penalty}) LP؛ محرومیت ({restriction_days}) روز و افزایش ({surcharge_percent})٪ برای اولین رزرو بعدی در صورت فعال بودن قانون.',
            lateCancellation: 'لغو در زمان شروع یا بعد از آن طبق قانون لغو دیرهنگام مدیریت اعمال می‌شود.',
            noShow: 'عدم حضور مشتری طبق قانون No Show مدیریت محاسبه و ثبت می‌شود.',
            vipPaymentDeadline: 'مهلت پرداخت کامل VIP: ({payment_deadline}) دقیقه. پرداخت موفق به‌تنهایی به معنی تأیید رزرو نیست.'
        }
    },

    pricing: {
        consoles: {
            PS4: { '1': 120000, '2': 140000, '3': 150000, '4': 180000 },
            PS5: { '1': 180000, '2': 220000, '3': 250000, '4': 280000 },
            SimD: { '1': 220000, '2': 220000, '3': 220000, '4': 220000 }
        },
        reservations: {
            exclusiveFullDay: 15000000
        }
    },
    policies: { reservation: { depositPercent: 30 } }
};

function deepMerge(base, override) {
    if (!override || typeof override !== 'object' || Array.isArray(override)) return base;
    const out = {...base};
    for (const [key, value] of Object.entries(override)) {
        if (value && typeof value === 'object' && !Array.isArray(value) && base[key] && typeof base[key] === 'object' && !Array.isArray(base[key])) out[key] = deepMerge(base[key], value);
        else out[key] = value;
    }
    return out;
}

async function getReservationConfiguration(client, managerId) {
    const result = await client.query(
        "SELECT id,version_number,settings FROM configuration_revisions WHERE manager_id=$1 ORDER BY version_number DESC LIMIT 1",
        [managerId]
    );
    if (!result.rows[0]) {
        const created = await client.query(
            "INSERT INTO configuration_revisions(manager_id,version_number,settings) VALUES($1,1,$2::jsonb) RETURNING id,version_number,settings",
            [managerId, JSON.stringify(DEFAULT_RESERVATION_CONFIGURATION)]
        );
        result.rows.push(created.rows[0]);
    }
    const settings = deepMerge(DEFAULT_RESERVATION_CONFIGURATION, result.rows[0].settings || {});
    return {
        revisionId: result.rows[0].id,
        version: result.rows[0].version_number,
        currency: settings.currency || 'IRT',
        consoles: settings.pricing?.consoles || {},
        reservations: settings.pricing?.reservations || {},
        reservationPolicy: settings.policies?.reservation || {},
        reservationRules: settings.reservationRules
    };
}

async function calculatePrice(client, managerId, customerId, type, stationType, controllers, durationMinutes, isVip = false, timeSlot = null) {
    const config = await getReservationConfiguration(client, managerId);
    const controllerKey = String(controllers || 1);
    let basePrice = '0';

    if (type === 'NORMAL_RESERVATION') {
        const rates = config.consoles[String(stationType || '').trim()] || {};
        const rawRate = String(rates[controllerKey] ?? '').trim();
        if (!/^\d+(\.\d+)?$/.test(rawRate) || Number(rawRate) <= 0) {
            throw new Error('RESERVATION_PRICING_NOT_CONFIGURED');
        }
        const duration = String(durationMinutes ?? '').trim();
        if (!/^\d+(\.\d+)?$/.test(duration) || Number(duration) <= 0) {
            throw new Error('INVALID_DURATION');
        }
        const result = await client.query('SELECT (($1::numeric * $2::numeric) / 60::numeric) AS value', [rawRate, duration]);
        basePrice = result.rows[0].value;
    } else if (type === 'FULL_HALL') {
        const raw = String(config.reservationRules?.vipPrice ?? '').trim();
        if (!/^\d+(\.\d+)?$/.test(raw) || Number(raw) <= 0) throw new Error('VIP_PRICING_NOT_CONFIGURED');
        basePrice = raw;
    } else if (type === 'EXCLUSIVE_FULL_DAY') {
        const raw = String(config.reservations.exclusiveFullDay ?? '').trim();
        if (!/^\d+(\.\d+)?$/.test(raw) || Number(raw) <= 0) throw new Error('EXCLUSIVE_PRICING_NOT_CONFIGURED');
        basePrice = raw;
    } else {
        throw new Error('INVALID_RESERVATION_TYPE');
    }

    let finalPrice = basePrice;
    const appliedRules = [];

    if (isVip) {
        finalPrice = basePrice;
        appliedRules.push('VIP_CONFIGURED_PRICE');
    }

    const res = await client.query(
        'SELECT pending_surcharge_percent FROM customers WHERE id = $1 AND manager_id = $2',
        [customerId, managerId]
    );
    if (res.rows.length === 0) throw new Error('Customer not found in manager tenant');
    const surchargePercent = res.rows[0]?.pending_surcharge_percent || 0;
    let surchargeAmount = '0';
    if (surchargePercent > 0) {
        const surchargeResult = await client.query('SELECT ($1::numeric * $2::numeric / 100::numeric) AS value', [finalPrice, surchargePercent]);
        surchargeAmount = surchargeResult.rows[0].value;
        const totalResult = await client.query('SELECT ($1::numeric + $2::numeric) AS value', [finalPrice, surchargeAmount]);
        finalPrice = totalResult.rows[0].value;
        appliedRules.push(`SURCHARGE_${surchargePercent}%`);
    }

    const depositPercent = String(config.reservationPolicy.depositPercent ?? '').trim();
    if (!/^\d+(\.\d+)?$/.test(depositPercent)) throw new Error('DEPOSIT_POLICY_NOT_CONFIGURED');
    const depositResult = await client.query('SELECT ($1::numeric * $2::numeric / 100::numeric) AS value', [finalPrice, depositPercent]);

    return {
        basePrice,
        finalPrice,
        depositAmount: depositResult.rows[0].value,
        surchargeAmount,
        appliedRules,
        currency: config.currency,
        configurationRevisionId: config.revisionId,
        configurationVersion: config.version
    };
}

async function verifyPayment(client, idempotencyKey, managerId, customerId, reservationId, provider, transactionId, amount, currency) {
    // Basic verification simulation
    if (amount <= 0) throw new Error("Invalid amount");

    const ownership = await client.query(
        'SELECT 1 FROM reservations WHERE id = $1 AND manager_id = $2 AND customer_id = $3 FOR UPDATE',
        [reservationId, managerId, customerId]
    );
    if (ownership.rows.length === 0) throw new Error('Reservation not found in customer tenant');

    // Insert payment transaction securely
    const res = await client.query(`
        INSERT INTO payment_transactions (manager_id, customer_id, reservation_id, provider, gateway_transaction_id, amount, currency, status, idempotency_key, verified_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW())
        ON CONFLICT (idempotency_key) DO NOTHING
        RETURNING id
    `, [managerId, customerId, reservationId, provider, transactionId, amount, currency, 'SUCCESS', idempotencyKey]);
    
    if (res.rowCount === 0) {
        throw new Error("Duplicate gateway transaction");
    }
    
    return true;
}

async function cancelReservation(client, managerId, reservationId, idempotencyKey, customerId = null) {
    const query = customerId
        ? ['SELECT * FROM reservations WHERE id = $1 AND manager_id = $2 AND customer_id = $3 FOR UPDATE', [reservationId, managerId, customerId]]
        : ['SELECT * FROM reservations WHERE id = $1 AND manager_id = $2 FOR UPDATE', [reservationId, managerId]];
    const res = await client.query(query[0], query[1]);
    if (res.rows.length === 0) throw new Error("Reservation not found");
    const r = res.rows[0];

    if (['COMPLETED','EXPIRED','REJECTED','NO_SHOW','SUPERSEDED_BY_VIP','SUPERSEDED_BY_VIP_PRIORITY'].includes(String(r.status))) {
        throw new Error('RESERVATION_CANNOT_BE_CANCELLED_IN_CURRENT_STATUS');
    }
    if (r.status === 'CANCELLED') {
        const prior = await client.query(`SELECT amount,metadata FROM financial_audit_logs WHERE manager_id=$1 AND customer_id=$2 AND reservation_id=$3 AND event_type='RESERVATION_CANCELLED' AND idempotency_key=$4 ORDER BY id DESC LIMIT 1`, [managerId, r.customer_id, r.id, idempotencyKey]);
        if (prior.rows[0]) {
            const metadata = prior.rows[0].metadata || {};
            return {
                actualPaid: metadata.actualPaid ?? null,
                refundAmount: prior.rows[0].amount,
                gnLoss: metadata.gnLoss ?? null,
                lpLoss: metadata.lpLoss ?? null,
                walletPercent: metadata.walletPercent ?? null,
                idempotent: true
            };
        }
        throw new Error("Already cancelled");
    }

    const now = new Date();
    const start = new Date(r.start_time);
    const diffMs = start.getTime() - now.getTime();
    const config = await getReservationConfiguration(client, managerId);
    const liveRules = config.reservationRules || DEFAULT_RESERVATION_CONFIGURATION.reservationRules;
    const snapCancellation = r.snap_cancellation_policy ? (typeof r.snap_cancellation_policy === 'string' ? JSON.parse(r.snap_cancellation_policy) : r.snap_cancellation_policy) : liveRules.cancellation;
    const rules = {
        cancellation: snapCancellation,
        lateCancellation: snapCancellation.lateCancellation || liveRules.lateCancellation,
        noShow: snapCancellation.noShow || liveRules.noShow
    };
    const remainingMinutes = diffMs / 60000;
    const cancellation = rules.cancellation;
    let walletPercent, gnLoss, lpLoss;
    let selectedRule;
    if (remainingMinutes >= Number(cancellation.atOrAbove24h?.thresholdMinutes)) selectedRule = cancellation.atOrAbove24h;
    else if (remainingMinutes > Number(cancellation.above15h?.thresholdMinutes)) selectedRule = cancellation.above15h;
    else if (remainingMinutes > Number(cancellation.above5h?.thresholdMinutes)) selectedRule = cancellation.above5h;
    else if (remainingMinutes > Number(cancellation.above2h?.thresholdMinutes)) selectedRule = cancellation.above2h;
    else if (diffMs > 0) selectedRule = cancellation.within2h;
    else selectedRule = rules.lateCancellation;
    walletPercent = Number(selectedRule?.walletPercent ?? 0);
    gnLoss = Number(selectedRule?.gn ?? 0);
    lpLoss = Number(selectedRule?.lp ?? 0);
    let applySurcharge = false;
    let applyRestriction = false;

    // Policy effects come entirely from the snapshot selected above.
    applySurcharge = diffMs > 0 && Number(selectedRule?.surchargePercent || 0) > 0;
    applyRestriction = diffMs > 0 && Number(selectedRule?.restrictionDays || 0) > 0;

    // We only refund what was actually paid!
    // We check payment_transactions for this reservation to see actual paid amount
    const payRes = await client.query(`SELECT COALESCE(SUM(amount), 0) as paid FROM payment_transactions WHERE reservation_id = $1 AND manager_id = $2 AND customer_id = $3 AND status = 'SUCCESS'`, [r.id, managerId, r.customer_id]);
    const actualPaid = payRes.rows[0].paid;
    const refundAmountResult = await client.query(`SELECT FLOOR(($1::numeric * $2::numeric) / 100) AS refund_amount`, [actualPaid, walletPercent]);
    const refundAmount = refundAmountResult.rows[0].refund_amount;

    // Update reservation
    await client.query("UPDATE reservations SET status = 'CANCELLED', updated_at = NOW() WHERE id = $1 AND manager_id = $2", [r.id, managerId]);
    await client.query("INSERT INTO reservation_audit_logs(manager_id,reservation_id,actor,old_status,new_status,reason) VALUES($1,$2,$3,$4,'CANCELLED',$5)", [managerId, r.id, customerId ? 'CUSTOMER' : 'MANAGER', r.status, `Cancellation policy: ${walletPercent}% wallet refund; GN -${gnLoss}; LP -${lpLoss}`]);
    await client.query("INSERT INTO financial_audit_logs(manager_id,customer_id,reservation_id,event_type,amount,currency,actor,idempotency_key,metadata) VALUES($1,$2,$3,'RESERVATION_CANCELLED',$4,$5,$6,$7,$8::jsonb)", [managerId, r.customer_id, r.id, refundAmount, r.snap_currency || 'IRT', customerId ? 'CUSTOMER' : 'MANAGER', idempotencyKey, JSON.stringify({actualPaid:String(actualPaid),walletPercent,gnLoss,lpLoss})]);

    // Refund Wallet
    if (refundAmount > 0) {
        const walletRefund = await client.query(`
            INSERT INTO wallet_transactions (manager_id, customer_id, amount, type, reference_type, reference_id, idempotency_key)
            VALUES ($1, $2, $3, 'CREDIT', 'CANCELLATION', $4, $5)
            ON CONFLICT DO NOTHING RETURNING id
        `, [managerId, r.customer_id, refundAmount, r.id.toString(), `cancel_wallet_${idempotencyKey}`]);
        if (walletRefund.rowCount > 0) {
            await client.query(`UPDATE customers SET wallet_balance = wallet_balance + $1 WHERE id = $2 AND manager_id = $3`, [refundAmount, r.customer_id, managerId]);
        }
    }

    // Deduct GN/LP
    const gnDebit = await client.query(`
        INSERT INTO gn_ledger (manager_id, customer_id, amount, type, reference_type, reference_id, idempotency_key)
        VALUES ($1, $2, $3, 'DEBIT', 'CANCELLATION', $4, $5) ON CONFLICT DO NOTHING RETURNING id
    `, [managerId, r.customer_id, gnLoss, r.id.toString(), `cancel_gn_${idempotencyKey}`]);
    if (gnDebit.rowCount > 0) {
        await client.query(`UPDATE customers SET gn_balance = GREATEST(0, gn_balance - $1) WHERE id = $2 AND manager_id = $3`, [gnLoss, r.customer_id, managerId]);
    }

    const lpDebit = await client.query(`
        INSERT INTO lp_ledger (manager_id, customer_id, amount, type, reference_type, reference_id, idempotency_key)
        VALUES ($1, $2, $3, 'DEBIT', 'CANCELLATION', $4, $5) ON CONFLICT DO NOTHING RETURNING id
    `, [managerId, r.customer_id, lpLoss, r.id.toString(), `cancel_lp_${idempotencyKey}`]);
    if (lpDebit.rowCount > 0) {
        await client.query(`UPDATE customers SET lp_balance = GREATEST(0, lp_balance - $1) WHERE id = $2 AND manager_id = $3`, [lpLoss, r.customer_id, managerId]);
    }

    // Apply Restriction/Surcharge for T5
    if (applyRestriction) {
        const restrictionDays = Number(selectedRule?.restrictionDays || 0);
        const surchargePercent = Number(selectedRule?.surchargePercent || 0);
        const restrictedUntil = new Date(now.getTime() + restrictionDays * 24 * 3600000).toISOString();
        await client.query(`UPDATE customers SET restricted_until = $1, pending_surcharge_percent = $2 WHERE id = $3 AND manager_id = $4`, [restrictedUntil, surchargePercent, r.customer_id, managerId]);
    }

    return { actualPaid, refundAmount, gnLoss, walletPercent };
}

async function completeVipReservation(client, managerId, reservationId, idempotencyKey) {
    const res = await client.query(`SELECT * FROM reservations WHERE id = $1 AND manager_id = $2 FOR UPDATE`, [reservationId, managerId]);
    const r = res.rows[0];
    const policy = r.snap_vip_policy ? (typeof r.snap_vip_policy === 'string' ? JSON.parse(r.snap_vip_policy) : r.snap_vip_policy) : {};
    
    if (policy.isVip && !['CONFIRMED','ACTIVE','COMPLETED'].includes(String(r.status))) {
        throw new Error('VIP_REWARD_REQUIRES_CONFIRMED_RESERVATION');
    }
    if (policy.isVip) {
        if (String(r.status) === 'COMPLETED') return;
        const vipGn = await client.query(`
            INSERT INTO gn_ledger (manager_id, customer_id, amount, type, reference_type, reference_id, idempotency_key)
            VALUES ($1, $2, $5, 'CREDIT', 'VIP_REWARD', $3, $4) ON CONFLICT DO NOTHING RETURNING id
        `, [managerId, r.customer_id, r.id.toString(), `vip_gn_${idempotencyKey}`, Number(policy.vipGnReward ?? 0)]);
        if (vipGn.rowCount > 0) {
            await client.query(`UPDATE customers SET gn_balance = gn_balance + $1 WHERE id = $2 AND manager_id = $3`, [Number(policy.vipGnReward ?? 0), r.customer_id, managerId]);
        }

        const vipLp = await client.query(`
            INSERT INTO lp_ledger (manager_id, customer_id, amount, type, reference_type, reference_id, idempotency_key)
            VALUES ($1, $2, $5, 'CREDIT', 'VIP_REWARD', $3, $4) ON CONFLICT DO NOTHING RETURNING id
        `, [managerId, r.customer_id, r.id.toString(), `vip_lp_${idempotencyKey}`, Number(policy.vipLpReward ?? 0)]);
        if (vipLp.rowCount > 0) {
            await client.query(`UPDATE customers SET lp_balance = lp_balance + $1 WHERE id = $2 AND manager_id = $3`, [Number(policy.vipLpReward ?? 0), r.customer_id, managerId]);
        }
    }
    
    await client.query(`UPDATE reservations SET status = 'COMPLETED', updated_at = NOW() WHERE id = $1 AND manager_id = $2`, [r.id, managerId]);
}

module.exports = { calculatePrice, cancelReservation, completeVipReservation, pool, DEFAULT_RESERVATION_CONFIGURATION, deepMerge, getReservationConfiguration };
