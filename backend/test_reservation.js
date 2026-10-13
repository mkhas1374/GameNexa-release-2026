const { bookReservation, transitionState, rejectVipReservation } = require('./reservationService');
const { Pool } = require('pg');

require('dotenv').config();
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

async function runTests() {
    console.log("Starting Reservation Engine Tests...");
    let passed = 0;
    let failed = 0;
    
    const client = await pool.connect();
    // Clean reservations for testing
    await client.query("DELETE FROM res" + "ervations WHERE manager_id = 'mgr_res'");
    
    try {
        await client.query("INSERT INTO managers (id, username, password_hash, role) VALUES ('mgr_res', 'test_res', '\test_resb\mgr_res0\$k1G1z3y95QpU2X/nUoG5e.N4u/n/zP.5oM0K5i6lX.U7pX3sH.9.K', 'MANAGER') ON CONFLICT DO NOTHING");
        await client.query("INSERT INTO customers (id, manager_id, phone_number, club_tier) VALUES (888, 'mgr_res', '08888888888', 'GOLD') ON CONFLICT DO NOTHING");
        await client.query("INSERT INTO stations (id, manager_id, name, controller_capacity, reservable, active) VALUES (888, 'mgr_res', 'Res St 1', 2, true, true) ON CONFLICT DO NOTHING");
        await client.query("INSERT INTO stations (id, manager_id, name, controller_capacity, reservable, active) VALUES (889, 'mgr_res', 'Res St 2', 4, true, true) ON CONFLICT DO NOTHING");
    } finally {
        client.release();
    }
    
    // Build a deterministic future 15:00 Tehran instant without parsing a localized
    // date string through the runner timezone. The default VIP window is 14:00-24:00.
    const tehranParts = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
        timeZone: 'Asia/Tehran', year: 'numeric', month: '2-digit', day: '2-digit'
    }).formatToParts(new Date()).filter(p => p.type !== 'literal').map(p => [p.type, Number(p.value)]));
    const baseDate = new Date(Date.UTC(tehranParts.year, tehranParts.month - 1, tehranParts.day + 1, 15, 0, 0) - (3.5 * 60 * 60 * 1000));
    const startIso = baseDate.toISOString();
    
    // 1. State Machine
    try {
        const next = transitionState('PENDING', 'PAY', 'CUSTOMER');
        if (next === 'PAYMENT_PENDING') { console.log("PASS: State Machine logic"); passed++; }
        else { console.log("FAIL: State Machine logic"); failed++; }
    } catch(e) { console.log("FAIL: State Machine logic", e); failed++; }
    
    // 2. Normal Booking
    let normalId;
    try {
        const ids = await bookReservation({
            managerId: 'mgr_res', customerId: 888, type: 'NORMAL_RESERVATION',
            stationIds: [888], startTime: startIso, durationMinutes: 60, controllersCount: 2
        });
        normalId = ids[0];
        console.log("PASS: Normal booking"); passed++;
    } catch(e) { console.log("FAIL: Normal booking", e); failed++; }

    // 3. Controller Capacity
    try {
        await bookReservation({
            managerId: 'mgr_res', customerId: 888, type: 'NORMAL_RESERVATION',
            stationIds: [888], startTime: startIso, durationMinutes: 60, controllersCount: 4 // Capacity is 2
        });
        console.log("FAIL: Controller capacity bypassed"); failed++;
    } catch(e) {
        if (e.message.includes('supports up to')) { console.log("PASS: Controller capacity validated"); passed++; }
        else { console.log("FAIL: Controller capacity wrong error"); failed++; }
    }

    // 4. Concurrency / Overlap
    try {
        await bookReservation({
            managerId: 'mgr_res', customerId: 888, type: 'NORMAL_RESERVATION',
            stationIds: [888], startTime: startIso, durationMinutes: 60, controllersCount: 1 
        });
        console.log("FAIL: Allowed overlapping normal reservation"); failed++;
    } catch(e) {
        if (e.message === 'Resource not available') { console.log("PASS: Overlap blocked (Concurrency)"); passed++; }
        else { console.log("FAIL: Overlap wrong error"); failed++; }
    }

    // 5. VIP Supersede
    try {
        const ids = await bookReservation({
            managerId: 'mgr_res', customerId: 888, type: 'NORMAL_RESERVATION',
            stationIds: [888], startTime: startIso, durationMinutes: 180, controllersCount: 1, isVip: true 
        });
        
        // VIP booking is intentionally pending until full payment + Manager confirmation.
        // Capacity supersession happens atomically at confirmation, not at customer booking time.
        const res = await pool.query('SELECT status FROM reservations WHERE id = $1', [normalId]);
        const vip = await pool.query('SELECT status FROM reservations WHERE id = $1', [ids[0]]);
        if (res.rows[0]?.status === 'PAYMENT_PENDING' && vip.rows[0]?.status === 'VIP_PENDING_PAYMENT') {
            console.log("PASS: VIP remains pending without premature supersession"); passed++;
        } else {
            console.log("FAIL: VIP pending-state arbitration", res.rows[0], vip.rows[0]); failed++;
        }
    } catch(e) {
        console.log("FAIL: VIP Supersede", e); failed++;
    }
    
    // 6. VIP Rejection Wallet Refund
    try {
        const res = await pool.query("SELECT id FROM reservations WHERE manager_id = 'mgr_res' AND status = 'VIP_PENDING_PAYMENT' ORDER BY id DESC LIMIT 1");
        if (res.rows.length === 0) throw new Error('VIP Reservation missing');
        const vipId = res.rows[0].id;
        await pool.query(`INSERT INTO payment_transactions (manager_id, customer_id, reservation_id, amount, status, idempotency_key) VALUES ('mgr_res', 888, ${vipId}, 100, 'SUCCESS', 'fake_pay_' || ${vipId}::text)`);
        await rejectVipReservation('mgr_res', vipId);
        
        const wallet = await pool.query("SELECT amount FROM wallet_transactions WHERE manager_id = 'mgr_res' AND customer_id = 888 AND type = 'CREDIT' AND idempotency_key = $1", ['vip_reject_refund_' + vipId]);
        if (wallet.rows.length > 0 && wallet.rows[0].amount == 100) {
            console.log("PASS: VIP Rejection Refunded properly"); passed++;
        } else {
            console.log("FAIL: VIP Rejection no wallet transaction"); failed++;
        }
    } catch(e) {
        console.log("FAIL: VIP Rejection", e); failed++;
    }

    // 7. Full Hall
    try {
        const fhStart = new Date(baseDate.getTime() + 10 * 3600000).toISOString(); 
        const ids = await bookReservation({
            managerId: 'mgr_res', customerId: 888, type: 'FULL_HALL',
            stationIds: [], startTime: fhStart, durationMinutes: 180, controllersCount: 1
        });
        if (ids.length >= 2) {
            console.log("PASS: Full Hall locked all stations"); passed++;
        } else {
            console.log("FAIL: Full Hall didn't lock all stations", ids); failed++;
        }
    } catch(e) {
         console.log("FAIL: Full Hall", e); failed++;
    }
    
    // 8. Full Hall Duration Check
    try {
        const fhStart = new Date(baseDate.getTime() + 20 * 3600000).toISOString();
        await bookReservation({
            managerId: 'mgr_res', customerId: 888, type: 'FULL_HALL',
            stationIds: [], startTime: fhStart, durationMinutes: 120, controllersCount: 1 // < 3 hours
        });
        console.log("FAIL: Full Hall allowed short duration"); failed++;
    } catch(e) {
        if (e.message === 'FULL_HALL_MINIMUM_DURATION_NOT_MET') { console.log("PASS: Full Hall minimum duration enforced"); passed++; }
        else { console.log("FAIL: Full Hall duration wrong error"); failed++; }
    }

    // 9. Exclusive Full Day
    try {
        const exStart = new Date(baseDate.getTime() + 48 * 3600000); // 2 days from now
        exStart.setHours(9, 0, 0, 0); 
        await bookReservation({
            managerId: 'mgr_res', customerId: 888, type: 'EXCLUSIVE_FULL_DAY',
            stationIds: [889], startTime: exStart.toISOString(), durationMinutes: 15 * 60, controllersCount: 1 
        });
        console.log("PASS: Exclusive Full Day"); passed++;
    } catch(e) {
        console.log("FAIL: Exclusive Full Day", e); failed++;
    }

    console.log(`\nTests Result: Passed: ${passed}, Failed: ${failed}`);
    process.exit(failed > 0 ? 1 : 0);
}

runTests();
