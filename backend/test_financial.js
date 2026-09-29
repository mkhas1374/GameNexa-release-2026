const { pool, calculatePrice, verifyPayment, cancelReservation, completeVipReservation } = require('./financialService');

async function runTests() {
    console.log("Starting Financial Engine Tests...");
    let passed = 0, failed = 0;
    
    const client = await pool.connect();
    
    // Setup
    await client.query("INSERT INTO managers (id, username, password_hash, role) VALUES ('mgr_fin', 'test_fin', '\test_finb\mgr_fin0\$k1G1z3y95QpU2X/nUoG5e.N4u/n/zP.5oM0K5i6lX.U7pX3sH.9.K', 'MANAGER') ON CONFLICT DO NOTHING");
    await client.query("INSERT INTO customers (id, manager_id, phone_number, club_tier, wallet_balance, gn_balance, lp_balance) VALUES (777, 'mgr_fin', '07777777777', 'DIAMOND', 0, 1000, 1000) ON CONFLICT (id) DO UPDATE SET wallet_balance=0, gn_balance=1000, lp_balance=1000, restricted_until=NULL, pending_surcharge_percent=0");
    await client.query("INSERT INTO stations (id, manager_id, name, controller_capacity, reservable, active) VALUES (777, 'mgr_fin', 'St1', 2, true, true) ON CONFLICT DO NOTHING");
    
    try {
        // 1. Pricing precision & duration
        const p1 = await calculatePrice(client, 'mgr_fin', 777, 'NORMAL_RESERVATION', 'PS4', 2, 90);
        // PS4 2 controllers = 140,000 / hr -> 90 mins = 210,000
        if (p1.basePrice === 210000) { console.log("PASS: Duration pricing"); passed++; }
        else { console.log("FAIL: Duration pricing", p1); failed++; }

        // 2. VIP Pricing & Deposit
        const pVip = await calculatePrice(client, 'mgr_fin', 777, 'NORMAL_RESERVATION', 'PS5', 1, 180, true);
        // PS5 1 = 180,000/hr -> 180 min = 540,000. VIP * 1.5 = 810,000. Deposit 30% = 243,000
        if (pVip.finalPrice === 810000 && pVip.depositAmount === 243000) { console.log("PASS: VIP pricing & Deposit"); passed++; }
        else { console.log("FAIL: VIP pricing", pVip); failed++; }
        
        // Setup reservation for payment
        const res1 = await client.query(`
            INSERT INTO reservations (manager_id, customer_id, station_id, type, status, start_time, end_time, duration_minutes, snap_base_price, snap_final_price, snap_payable_amount, snap_deposit_amount, snap_vip_policy)
            VALUES ('mgr_fin', 777, 777, 'NORMAL_RESERVATION', 'PENDING', NOW() + INTERVAL '10 hours', NOW() + INTERVAL '12 hours', 120, 280000, 280000, 280000, 84000, '{"isVip":true}')
            RETURNING id
        `);
        const r1Id = res1.rows[0].id;

        // 3. Payment Verification
        try {
            await verifyPayment(client, 'pay_1', 'mgr_fin', 777, r1Id, 'MYKET', 'tx_123', 84000, 'IRT');
            console.log("PASS: Payment verification"); passed++;
        } catch(e) { console.log("FAIL: Payment verification", e); failed++; }

        // 4. Duplicate gateway transaction
        try {
            await verifyPayment(client, 'pay_1', 'mgr_fin', 777, r1Id, 'MYKET', 'tx_123', 84000, 'IRT');
            console.log("FAIL: Allowed duplicate payment"); failed++;
        } catch(e) {
            if(e.message === 'Duplicate gateway transaction') { console.log("PASS: Duplicate callback blocked"); passed++; }
            else { console.log("FAIL: Wrong error for duplicate"); failed++; }
        }

        // 5. Wrong amount (invalid payment)
        try {
            await verifyPayment(client, 'pay_2', 'mgr_fin', 777, r1Id, 'MYKET', 'tx_999', 0, 'IRT');
            console.log("FAIL: Allowed wrong amount"); failed++;
        } catch(e) { console.log("PASS: Wrong amount blocked"); passed++; }

        // 6. Cancellation Exact Refund (T3 -> 10 hours out -> 85% of paid)
        // Paid = 84000. Refund should be 84000 * 0.85 = 71400.
        try {
            const cancelRes = await cancelReservation(client, 'mgr_fin', r1Id, 'canc_1');
            if (cancelRes.actualPaid === 84000 && cancelRes.refundAmount === 71400 && cancelRes.gnLoss === 120) {
                console.log("PASS: Cancellation exact refund & GN deduction boundaries"); passed++;
            } else {
                console.log("FAIL: Cancel refund wrong amounts", cancelRes); failed++;
            }
        } catch(e) { console.log("FAIL: Cancellation", e); failed++; }
        
        // 7. Double refund check
        try {
            await cancelReservation(client, 'mgr_fin', r1Id, 'canc_1');
            console.log("FAIL: Double refund allowed"); failed++;
        } catch(e) { console.log("PASS: Double refund blocked"); passed++; }
        
        // Check Ledgers
        const c1 = await client.query("SELECT wallet_balance, gn_balance, lp_balance FROM customers WHERE id = 777");
        if (c1.rows[0].wallet_balance == 71400 && c1.rows[0].gn_balance == 880 && c1.rows[0].lp_balance == 880) {
            console.log("PASS: Wallet & GN/LP ledgers synced"); passed++;
        } else {
            console.log("FAIL: Ledgers not synced", c1.rows[0]); failed++;
        }
        
        // 8. T5 Restriction & Surcharge
        const res2 = await client.query(`
            INSERT INTO reservations (manager_id, customer_id, station_id, type, status, start_time, end_time, duration_minutes, snap_base_price, snap_final_price, snap_payable_amount, snap_deposit_amount)
            VALUES ('mgr_fin', 777, 777, 'NORMAL_RESERVATION', 'PENDING', NOW() + INTERVAL '1 hour', NOW() + INTERVAL '3 hours', 120, 100, 100, 100, 100)
            RETURNING id
        `);
        const r2Id = res2.rows[0].id;
        
        // Simulate paying full 100
        await verifyPayment(client, 'pay_3', 'mgr_fin', 777, r2Id, 'MYKET', 'tx_333', 100, 'IRT');
        
        // Cancel T5 (1 hour away -> <= 2h)
        const cRes = await cancelReservation(client, 'mgr_fin', r2Id, 'canc_2');
        if (cRes.walletPercent === 70) { console.log("PASS: T5 cancellation boundary"); passed++; }
        else { console.log("FAIL: T5 cancellation boundary", cRes); failed++; }
        
        const c2 = await client.query("SELECT restricted_until, pending_surcharge_percent FROM customers WHERE id = 777");
        if (c2.rows[0].restricted_until && c2.rows[0].pending_surcharge_percent == 5) {
            console.log("PASS: Restriction and Surcharge applied"); passed++;
        } else {
            console.log("FAIL: Restriction/Surcharge missing", c2.rows[0]); failed++;
        }
        
        // 9. Surcharge Pricing application
        const pSur = await calculatePrice(client, 'mgr_fin', 777, 'NORMAL_RESERVATION', 'PS4', 1, 60);
        // Base 120000. 5% surcharge = 6000. Final = 126000
        if (pSur.finalPrice === 126000 && pSur.surchargeAmount === 6000) {
            console.log("PASS: Surcharge pricing calculated"); passed++;
        } else {
            console.log("FAIL: Surcharge pricing", pSur); failed++;
        }
        
        // 10. VIP Reward
        const res3 = await client.query(`
            INSERT INTO reservations (manager_id, customer_id, station_id, type, status, start_time, end_time, duration_minutes, snap_base_price, snap_final_price, snap_payable_amount, snap_deposit_amount, snap_vip_policy)
            VALUES ('mgr_fin', 777, 777, 'NORMAL_RESERVATION', 'CONFIRMED', NOW(), NOW(), 120, 100, 100, 100, 100, '{"isVip":true}')
            RETURNING id
        `);
        await completeVipReservation(client, 'mgr_fin', res3.rows[0].id, 'vip_1');
        
        const c3 = await client.query("SELECT gn_balance FROM customers WHERE id = 777");
        // prev GN was 880 - 250 (from T5 cancel) = 630. Then +300 reward = 930
        if (c3.rows[0].gn_balance == 930) {
            console.log("PASS: VIP Reward added properly"); passed++;
        } else {
            console.log("FAIL: VIP Reward missing", c3.rows[0]); failed++;
        }

    } catch (e) {
        console.error("Test Exception:", e);
    } finally {
        client.release();
    }
    
    console.log(`\nTests Result: Passed: ${passed}, Failed: ${failed}`);
    process.exit(failed > 0 ? 1 : 0);
}

runTests();
