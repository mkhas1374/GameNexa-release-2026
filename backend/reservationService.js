
const { calculatePrice, getReservationConfiguration } = require('./financialService');

const VALID_STATUSES = [
    'PENDING', 'PAYMENT_PENDING', 'PENDING_APPROVAL', 'VIP_PENDING_PAYMENT', 'VIP_PAYMENT_PAID', 'CONFIRMED', 'ACTIVE', 'COMPLETED',
    'CANCELLED', 'EXPIRED', 'NO_SHOW', 'SUPERSEDED_BY_VIP', 'SUPERSEDED_BY_VIP_PRIORITY', 'REJECTED'
];

const TRANSITIONS = {
    'PENDING': {
        'PAY': { next: 'PAYMENT_PENDING', actors: ['CUSTOMER'] },
        'REJECT': { next: 'REJECTED', actors: ['MANAGER'] },
        'CANCEL': { next: 'CANCELLED', actors: ['CUSTOMER', 'MANAGER'] },
        'EXPIRE': { next: 'EXPIRED', actors: ['SYSTEM'] }
    },
    'PAYMENT_PENDING': {
        'PAY': { next: 'PENDING_APPROVAL', actors: ['CUSTOMER'] },
        'CANCEL': { next: 'CANCELLED', actors: ['CUSTOMER', 'MANAGER'] },
        'EXPIRE': { next: 'EXPIRED', actors: ['SYSTEM'] }
    },
    'VIP_PENDING_PAYMENT': {
        'PAY': { next: 'VIP_PAYMENT_PAID', actors: ['SYSTEM', 'MANAGER'] },
        'CANCEL': { next: 'CANCELLED', actors: ['CUSTOMER', 'MANAGER'] },
        'EXPIRE': { next: 'EXPIRED', actors: ['SYSTEM'] }
    },
    'VIP_PAYMENT_PAID': {
        'APPROVE': { next: 'CONFIRMED', actors: ['MANAGER'] },
        'REJECT': { next: 'REJECTED', actors: ['MANAGER'] },
        'CANCEL': { next: 'CANCELLED', actors: ['CUSTOMER', 'MANAGER'] },
        'EXPIRE': { next: 'EXPIRED', actors: ['SYSTEM'] }
    },
    'PENDING_APPROVAL': {
        'APPROVE': { next: 'CONFIRMED', actors: ['MANAGER'] },
        'REJECT': { next: 'REJECTED', actors: ['MANAGER'] },
        'CANCEL': { next: 'CANCELLED', actors: ['CUSTOMER', 'MANAGER'] },
        'EXPIRE': { next: 'EXPIRED', actors: ['SYSTEM'] }
    },
    'CONFIRMED': {
        'START': { next: 'ACTIVE', actors: ['MANAGER', 'SYSTEM'] },
        'CANCEL': { next: 'CANCELLED', actors: ['CUSTOMER', 'MANAGER'] },
        'NO_SHOW': { next: 'NO_SHOW', actors: ['SYSTEM', 'MANAGER'] }
    },
    'ACTIVE': {
        'COMPLETE': { next: 'COMPLETED', actors: ['MANAGER', 'SYSTEM'] }
    }
};

function transitionState(currentStatus, action, actor) {
    if (!VALID_STATUSES.includes(currentStatus)) throw new Error("Invalid current status");
    const allowed = TRANSITIONS[currentStatus]?.[action];
    if (!allowed) throw new Error(`Action ${action} not allowed from ${currentStatus}`);
    if (!allowed.actors.includes(actor)) throw new Error(`Actor ${actor} not permitted to ${action}`);
    return allowed.next;
}

// And restore bookReservation taking (params)


async function rejectVipReservation(managerId, reservationId, idempotencyKey) {
    const { pool } = require('./financialService');
    const client = await pool.connect();
    idempotencyKey = idempotencyKey || 'vip_reject_refund_' + reservationId;
    try {

    const res = await client.query('SELECT * FROM reservations WHERE id = $1 AND manager_id = $2', [reservationId, managerId]);
    if (res.rows.length === 0) throw new Error("VIP Reservation missing");
    
    await client.query('UPDATE reservations SET status = $1, updated_at = NOW() WHERE id = $2 AND manager_id = $3', ['REJECTED', reservationId, managerId]);
    
    // Wallet refund logic
    const payRes = await client.query(`SELECT COALESCE(SUM(amount), 0) as paid FROM payment_transactions WHERE reservation_id = $1 AND status = 'SUCCESS'`, [reservationId]);
    const actualPaid = payRes.rows[0].paid || '0';
    
    if (actualPaid > 0) {
        const refund = await client.query(`
            INSERT INTO wallet_transactions (manager_id, customer_id, amount, type, reference_type, reference_id, idempotency_key)
            VALUES ($1, $2, $3, 'CREDIT', 'CANCELLATION', $4, $5)
            ON CONFLICT DO NOTHING RETURNING id
        `, [managerId, res.rows[0].customer_id, actualPaid, reservationId.toString(), idempotencyKey]);
        if (refund.rowCount > 0) {
            await client.query(`UPDATE customers SET wallet_balance = wallet_balance + $1 WHERE id = $2 AND manager_id = $3`, [actualPaid, res.rows[0].customer_id, managerId]);
        }
    }
    } finally {
        client.release();
    }
}

async function bookReservation(params, externalClient = null) {
    const { 
        managerId, customerId, type, stationIds, startTime, durationMinutes, 
        controllersCount, isVip, actor, timeSlot
    } = params;
    
    const { pool } = require('./financialService');
    const client = externalClient || await pool.connect();
    const ownsTransaction = !externalClient;
    try {
        if (ownsTransaction) await client.query('BEGIN');

        const customerRes = await client.query(
            'SELECT id FROM customers WHERE id = $1 AND manager_id = $2 FOR UPDATE',
            [customerId, managerId]
        );
        if (customerRes.rows.length === 0) throw new Error('Customer not found in manager tenant');

        const configuration = await getReservationConfiguration(client, managerId);
        const rules = configuration.reservationRules;
        const customerTierRes = await client.query('SELECT club_tier, restricted_until, pending_surcharge_percent FROM customers WHERE id=$1 AND manager_id=$2 FOR UPDATE', [customerId, managerId]);
        const customerTier = String(customerTierRes.rows[0]?.club_tier || 'BRONZE').toUpperCase();
        if (actor === 'CUSTOMER' && customerTierRes.rows[0]?.restricted_until && new Date(customerTierRes.rows[0].restricted_until).getTime() > Date.now()) {
            throw new Error('CUSTOMER_RESERVATION_RESTRICTED');
        }
        if (isVip && actor === 'CUSTOMER' && !['GOLD', 'DIAMOND'].includes(customerTier)) {
            throw new Error('VIP_DIRECT_REQUEST_REQUIRES_GOLD_OR_DIAMOND');
        }
        if (isVip && actor !== 'CUSTOMER' && !['MANAGER', 'SYSTEM'].includes(actor || 'MANAGER')) {
            throw new Error('INVALID_VIP_ACTOR');
        }
        
        const entitlementRes = await client.query(`
            SELECT id, entitlement_type, plan_id, max_stations,
                   max_customers, allowed_console_type, metadata
            FROM manager_entitlements
            WHERE manager_id = $1
              AND status = 'ACTIVE'
              AND starts_at <= NOW()
              AND expires_at > NOW()
            ORDER BY expires_at DESC
            LIMIT 1
            FOR UPDATE
        `, [managerId]);

        const entitlement = entitlementRes.rows[0] || null;
        const isTrial = entitlement?.entitlement_type === 'TRIAL'
            && entitlement?.plan_id === 'TRIAL_24H';

        if (isTrial) {
            const trialCustomerIds = Array.isArray(entitlement.metadata?.trial_customer_ids)
                ? entitlement.metadata.trial_customer_ids.map(Number)
                : [];

            if (!trialCustomerIds.includes(Number(customerId))) {
                throw new Error('Trial customer is not allowed');
            }
        }

        let start = new Date(startTime);
        let actualDuration = Number(durationMinutes);
        if (!Number.isFinite(start.getTime()) || start.getTime() <= Date.now()) throw new Error('RESERVATION_START_MUST_BE_IN_FUTURE');
        if (!Number.isFinite(actualDuration) || actualDuration <= 0) throw new Error('INVALID_DURATION');
        if (isVip) {
            const allowedVip = Array.isArray(rules.vipDurationsMinutes) ? rules.vipDurationsMinutes.map(Number) : [];
            const vipMinimum = Number(rules.vipMinDurationMinutes);
            if (!Number.isFinite(vipMinimum) || vipMinimum <= 0) throw new Error('VIP_MINIMUM_DURATION_NOT_CONFIGURED');
            if (actualDuration < vipMinimum) throw new Error('VIP_MINIMUM_DURATION_NOT_MET');
            if (allowedVip.length && !allowedVip.includes(actualDuration)) throw new Error('VIP_DURATION_NOT_ALLOWED');
            const vipStart = String(rules.vipStartTime || '').trim();
            const vipEnd = String(rules.vipEndTime || '').trim();
            if (vipStart && vipEnd) {
                const parts = (value) => {
                    const [h,m] = value.split(':').map(Number);
                    return Number.isFinite(h) && Number.isFinite(m) ? (h * 60 + m) : null;
                };
                const startMinutes = parts(vipStart);
                const endMinutes = parts(vipEnd);
                if (startMinutes != null && endMinutes != null) {
                    const tehran = new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Tehran',hour:'2-digit',minute:'2-digit',hour12:false}).formatToParts(start);
                    const rawHour = Number(tehran.find(x=>x.type==='hour')?.value||0);
                    const currentHour = rawHour === 24 ? 0 : rawHour;
                    const currentMinutes = currentHour*60 + Number(tehran.find(x=>x.type==='minute')?.value||0);
                    const durationEnd = currentMinutes + actualDuration;
                    const normalizedEnd = endMinutes <= startMinutes ? endMinutes + 1440 : endMinutes;
                    const normalizedCurrent = currentMinutes < startMinutes && normalizedEnd > 1440 ? currentMinutes + 1440 : currentMinutes;
                    if (normalizedCurrent < startMinutes || durationEnd > normalizedEnd) throw new Error('VIP_TIME_WINDOW_NOT_ALLOWED');
                }
            }
        } else if (type === 'FULL_HALL') {
            const minimum = Number(rules.fullHallMinDurationMinutes ?? 180);
            if (!Number.isFinite(minimum) || minimum <= 0) throw new Error('FULL_HALL_MINIMUM_DURATION_NOT_CONFIGURED');
            if (actualDuration < minimum) throw new Error('FULL_HALL_MINIMUM_DURATION_NOT_MET');
        } else if (type === 'EXCLUSIVE_FULL_DAY') {
            const required = Number(rules.exclusiveFullDayDurationMinutes ?? 900);
            if (!Number.isFinite(required) || required <= 0) throw new Error('EXCLUSIVE_FULL_DAY_DURATION_NOT_CONFIGURED');
            if (actualDuration !== required) throw new Error('EXCLUSIVE_FULL_DAY_DURATION_NOT_ALLOWED');
        } else {
            const allowedNormal = Array.isArray(rules.normalDurationsMinutes) ? rules.normalDurationsMinutes.map(Number) : [];
            if (allowedNormal.length && !allowedNormal.includes(actualDuration)) throw new Error('RESERVATION_DURATION_NOT_ALLOWED');
        }
        
        let end = new Date(start.getTime() + actualDuration * 60000);

        let targetStations = [];
        if (isVip || type === 'FULL_HALL' || type === 'EXCLUSIVE_FULL_DAY') {
            const stRes = await client.query('SELECT id FROM stations WHERE manager_id = $1 AND active = true', [managerId]);
            targetStations = stRes.rows.map(r => r.id);
        } else {
            targetStations = Array.isArray(stationIds) ? stationIds : [];
        }

        if ((isVip || type === 'FULL_HALL' || type === 'EXCLUSIVE_FULL_DAY') && targetStations.length === 0) {
            throw new Error('NO_RESERVABLE_STATIONS_CONFIGURED');
        }

        if (isTrial) {
            const maxStations = Number(entitlement.max_stations || 0);

            if (!maxStations || targetStations.length > maxStations) {
                throw new Error('Trial station limit exceeded');
            }

            if (targetStations.length === 0) {
                throw new Error('At least one station is required');
            }

            const trialStationIds = Array.isArray(entitlement.metadata?.trial_station_ids)
                ? entitlement.metadata.trial_station_ids.map(Number)
                : [];

            if (targetStations.some(id => !trialStationIds.includes(Number(id)))) {
                throw new Error('Trial station is not allowed');
            }
        }

        if (targetStations && targetStations.length > 0) {
            const stRes = await client.query(
                'SELECT * FROM stations WHERE id = ANY($1) AND manager_id = $2',
                [targetStations, managerId]
            );

            if (stRes.rows.length !== targetStations.length) {
                throw new Error('One or more stations are not available for this manager');
            }

            for (const st of stRes.rows) {
                if (isTrial && st.console_type !== entitlement.allowed_console_type) {
                    throw new Error('Trial console type is not allowed');
                }

                if (controllersCount > st.controller_capacity) {
                    throw new Error(`supports up to ${st.controller_capacity}`);
                }
            }
        }
        
        // Pending/unconfirmed VIP requests do NOT consume or supersede ordinary capacity.
        // Capacity arbitration for a VIP is performed atomically when the Manager confirms it.
        let hasOverlap = false;
        if (!isVip) {
            const fullHallVip = await client.query(`
                SELECT id FROM reservations
                WHERE manager_id=$1
                  AND station_id IS NULL
                  AND snap_vip_policy->>'isVip' = 'true'
                  AND status IN ('CONFIRMED','ACTIVE')
                  AND start_time < $2 AND end_time > $3
                FOR UPDATE
            `, [managerId, end.toISOString(), start.toISOString()]);
            if (fullHallVip.rows.length > 0) hasOverlap = true;

            for (const stId of targetStations) {
                const overlapRes = await client.query(`
                    SELECT id FROM reservations 
                    WHERE manager_id = $1 AND station_id = $2
                    AND status NOT IN (
                        'CANCELLED','EXPIRED','NO_SHOW','REJECTED',
                        'SUPERSEDED_BY_VIP','SUPERSEDED_BY_VIP_PRIORITY',
                        'VIP_PENDING_PAYMENT','VIP_PAYMENT_PAID'
                    )
                    AND start_time < $3 AND end_time > $4
                    FOR UPDATE
                `, [managerId, stId, end.toISOString(), start.toISOString()]);
                if (overlapRes.rows.length > 0) hasOverlap = true;
            }
        } else {
            const fullHallVipOverlap = await client.query(`
                SELECT id FROM reservations
                WHERE manager_id=$1
                  AND station_id IS NULL
                  AND snap_vip_policy->>'isVip' = 'true'
                  AND status IN ('CONFIRMED','ACTIVE')
                  AND start_time < $2 AND end_time > $3
                FOR UPDATE
            `, [managerId, end.toISOString(), start.toISOString()]);
            if (fullHallVipOverlap.rows.length > 0) hasOverlap = true;
        }
        if (hasOverlap) throw new Error('Resource not available');

        // Calculate actual price
        const insertStationIds = isVip ? [null] : targetStations;
        const pricingStationType = type === 'NORMAL_RESERVATION'
            ? (targetStations.length > 0
                ? (await client.query('SELECT console_type FROM stations WHERE id = $1 AND manager_id = $2', [targetStations[0], managerId])).rows[0]?.console_type || ''
                : '')
            : '';
        if (type === 'NORMAL_RESERVATION' && !pricingStationType) {
            throw new Error('Station console type is required for pricing');
        }
        const pricingType = isVip ? 'FULL_HALL' : type;
        const priceDetails = await calculatePrice(client, managerId, customerId, pricingType, pricingStationType, controllersCount || 1, actualDuration, isVip, timeSlot);
        const initialStatus = isVip ? 'VIP_PENDING_PAYMENT' : 'PAYMENT_PENDING';
        const reservationPolicySnapshot = {
            isVip: Boolean(isVip),
            customerTier,
            durationMinutes: actualDuration,
            paymentDeadlineMinutes: Number(rules.paymentDeadlineMinutes ?? 0),
            vipPaymentDeadlineMinutes: Number(rules.vipPaymentDeadlineMinutes ?? 0),
            vipMinDurationMinutes: Number(rules.vipMinDurationMinutes ?? 0),
            fullHallMinDurationMinutes: Number(rules.fullHallMinDurationMinutes ?? 0),
            exclusiveFullDayDurationMinutes: Number(rules.exclusiveFullDayDurationMinutes ?? 0),
            vipGnReward: Number(rules.vipReward?.gn ?? 0),
            vipLpReward: Number(rules.vipReward?.lp ?? 0),
            priority: customerTier,
            price: String(priceDetails.finalPrice),
            messages: rules.messages || {}
        };
        
        const insertedIds = [];
        for (const stId of insertStationIds) {
            const res = await client.query(`
                INSERT INTO reservations (
                    manager_id, customer_id, station_id, type, status, 
                    start_time, end_time, duration_minutes, 
                    snap_base_price, snap_final_price, snap_payable_amount, config_revision_id,
                    snap_cancellation_policy, snap_gn_policy, snap_lp_policy, snap_wallet_policy,
                    snap_restriction, snap_surcharge, snap_vip_policy, snap_pricing_version, created_at, updated_at
                ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, NOW(), NOW()) RETURNING id
            `, [
                managerId, customerId, stId, pricingType, initialStatus,
                start.toISOString(), end.toISOString(), actualDuration,
                priceDetails.basePrice, priceDetails.finalPrice, priceDetails.depositAmount || priceDetails.finalPrice,
                priceDetails.revisionId,
                JSON.stringify({ ...rules.cancellation, lateCancellation: rules.lateCancellation, noShow: rules.noShow }),
                JSON.stringify({ ...rules.cancellation }),
                JSON.stringify({ ...rules.cancellation }),
                JSON.stringify({ ...rules.cancellation }),
                JSON.stringify({days: Number(rules.cancellation.within2h?.restrictionDays || 0)}),
                JSON.stringify({percent: Number(rules.cancellation.within2h?.surchargePercent || 0)}),
                JSON.stringify(reservationPolicySnapshot),
                priceDetails.version
            ]);
            insertedIds.push(res.rows[0].id);
        }
        
        if (ownsTransaction) await client.query('COMMIT');
        return insertedIds;
    } catch(e) {
        if (ownsTransaction) await client.query('ROLLBACK');
        throw e;
    } finally {
        if (ownsTransaction) client.release();
    }
}

module.exports = { transitionState, bookReservation, rejectVipReservation };
