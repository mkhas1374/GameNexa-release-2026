const { Pool } = require('pg');
require('dotenv').config();
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

async function runTest() {
    let passed = 0, failed = 0;
    try {
        const res = await fetch('http://127.0.0.1:3000/api/v1/subscriptions/buy', {
            method: 'POST',
            headers: {'Content-Type': 'application/json', 'Idempotency-Key': 'test-subscription-idem-1'},
            body: JSON.stringify({deviceId: 'test_dev', plan: 'MONTHLY', idempotencyKey: 'test-subscription-idem-1'})
        });
        const raw = await res.text(); let data; try { data = JSON.parse(raw); } catch { throw new Error('subscription buy non-JSON response status='+res.status+' body='+raw); }
        
        if (data.licenseCode && data.licenseCode.startsWith('PENDING_')) {
            console.log("PASS: Manual Payment Creation"); passed++;
        } else {
            console.log("FAIL: Manual Payment Creation"); failed++;
        }
        
        if (!data.licenseCode) throw new Error('subscription buy response missing licenseCode: '+JSON.stringify(data));
        const reqId = data.licenseCode.split('_')[1];
        const replay = await fetch('http://127.0.0.1:3000/api/v1/subscriptions/buy', {
            method: 'POST',
            headers: {'Content-Type': 'application/json', 'Idempotency-Key': 'test-subscription-idem-1'},
            body: JSON.stringify({deviceId: 'test_dev', plan: 'MONTHLY', idempotencyKey: 'test-subscription-idem-1'})
        });
        const replayData = await replay.json();
        if (replayData.licenseCode === data.licenseCode && replayData.idempotent === true) { console.log('PASS: Subscription purchase idempotency'); passed++; }
        else { console.log('FAIL: Subscription purchase idempotency', replayData); failed++; }
        
        // Automatic confirmation must remain blocked; manual Super Manager provisioning is required.
        const res2 = await fetch(`http://127.0.0.1:3000/api/v1/super-manager/subscription-requests/${reqId}/confirm`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'}
        });
        if (res2.status === 404) {
            console.log("PASS: legacy automatic confirmation route removed"); passed++;
        } else {
            console.log("FAIL: legacy automatic confirmation route removed"); failed++;
        }
        const pending = await pool.query('SELECT status,provisioned_account FROM subscription_payment_requests WHERE id=$1',[reqId]);
        if (pending.rows[0]?.status === 'PENDING' && pending.rows[0]?.provisioned_account === false) {
            console.log("PASS: Purchase remains pending until manual provisioning"); passed++;
        } else {
            console.log("FAIL: Purchase was auto-provisioned"); failed++;
        }
        
    } catch (e) {
        console.error(e);
        failed++;
        process.exitCode = 1;
    } finally {
        try { await pool.query("DELETE FROM subscription_payment_requests WHERE buyer_device_id='test_dev'"); } catch {}
        pool.end();
        console.log(`Tests Result: Passed: ${passed}, Failed: ${failed}`);
    }
}
runTest();
