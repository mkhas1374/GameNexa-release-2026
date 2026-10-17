const assert = require('assert');
const { Pool } = require('pg');
const bcrypt = require('bcrypt');

const baseUrl = process.env.TEST_API_URL || `http://127.0.0.1:${process.env.PORT || 3000}`;
const pool = new Pool({ connectionString: process.env.DATABASE_URL });
const toPersianDigits = value => String(value).replace(/[0-9]/g, d => '۰۱۲۳۴۵۶۷۸۹'[Number(d)]);

async function main() {
    const suffix = String(Date.now());
    const managerId = `test_customer_login_${suffix}`;
    const phone = `0999${suffix.slice(-7)}`;
    const password = `GN-Test!${suffix.slice(-6)}x`;
    let customerId = null;
    let managerCreated = false;
    let stationId = null;
    let sessionId = null;
    try {
        const hash = await bcrypt.hash(password, 10);
        await pool.query(
            "INSERT INTO managers(id,username,password_hash,role) VALUES($1,$1,$2,'MANAGER')",
            [managerId, hash]
        );
        managerCreated = true;
        const created = await pool.query(
            'INSERT INTO customers(manager_id,phone_number,full_name,password_hash,gn_balance,lp_balance) VALUES($1,$2,$3,$4,123,77) RETURNING id',
            [managerId, phone, 'GN_TEMP_CUSTOMER_LOGIN_E2E', hash]
        );
        customerId = created.rows[0].id;

        const login = await fetch(`${baseUrl}/api/auth/customer/login`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ phone_number: toPersianDigits(phone), password })
        });
        const auth = await login.json().catch(() => ({}));
        assert.strictEqual(login.status, 200, `valid test customer login should succeed: ${login.status}`);
        assert.ok(auth.token, 'login must return a customer token');
        assert.strictEqual(Number(auth.customerId), Number(customerId), 'login must resolve the correct test customer');
        assert.strictEqual(String(auth.managerId || auth.manager_id), managerId, 'login must return the resolved tenant');

        const profile = await fetch(`${baseUrl}/api/v1/customer/profile`, {
            headers: { Authorization: `Bearer ${auth.token}` }
        });
        const customer = await profile.json().catch(() => ({}));
        assert.strictEqual(profile.status, 200, `customer profile must load after login: ${profile.status}`);
        assert.strictEqual(Number(customer.id), Number(customerId), 'profile must match the authenticated customer');
        assert.strictEqual(customer.phoneNumber, phone, 'profile must preserve the registered phone number');
        assert.strictEqual(Number(customer.availableGn), 123, 'profile must expose GN balance independently');
        assert.strictEqual(Number(customer.points), 123, 'legacy points field remains GN for Manager-side compatibility');
        assert.strictEqual(Number(customer.lp), 77, 'profile must expose LP balance');
        const customerConfig = await fetch(`${baseUrl}/api/v1/customer/config/gn_payment_cards`, { headers: { Authorization: `Bearer ${auth.token}` } });
        const customerConfigBody = await customerConfig.json().catch(() => ({}));
        assert.strictEqual(customerConfig.status, 200, 'customer payment instructions must use a customer-authenticated endpoint');
        assert.strictEqual(customerConfigBody.key, 'gn_payment_cards', 'customer config must return only the requested allowlisted key');

        stationId = Number(String(Date.now()).slice(-8));
        await pool.query(
            'INSERT INTO stations(id,manager_id,name,controller_capacity,console_type) VALUES($1,$2,$3,2,$4)',
            [stationId, managerId, `GN_TEMP_STATION_${suffix}`, 'PS5']
        );
        const activeSession = await pool.query(
            "INSERT INTO game_sessions(manager_id,station_id,status,console_type,controller_count,started_at) VALUES($1,$2,'ACTIVE','PS5',2,NOW()) RETURNING id",
            [managerId, stationId]
        );
        sessionId = activeSession.rows[0].id;
        await pool.query(
            'INSERT INTO session_participants(manager_id,session_id,customer_id,participant_key,participant_name,is_guest,is_payer,prepayment_amount) VALUES($1,$2,$3,$4,$5,FALSE,TRUE,0)',
            [managerId, sessionId, customerId, `customer:${customerId}`, 'GN_TEMP_CUSTOMER_LOGIN_E2E']
        );
        await pool.query(
            "INSERT INTO gn_ledger(manager_id,customer_id,amount,type,reference_type,reference_id,idempotency_key) VALUES($1,$2,25,'CREDIT','E2E','gn_test',$3)",
            [managerId, customerId, `gn_e2e_${suffix}`]
        );
        await pool.query(
            "INSERT INTO lp_ledger(manager_id,customer_id,amount,type,reference_type,reference_id,idempotency_key) VALUES($1,$2,7,'CREDIT','E2E','lp_test',$3)",
            [managerId, customerId, `lp_e2e_${suffix}`]
        );
        const live = await fetch(`${baseUrl}/api/v1/customer/live-session`, { headers: { Authorization: `Bearer ${auth.token}` } });
        const liveBody = await live.json().catch(() => ({}));
        assert.strictEqual(live.status, 200, `customer live-session must succeed with customer token: ${live.status}`);
        assert.strictEqual(liveBody.stations?.length, 1, 'customer should see exactly the active session linked to them');
        assert.strictEqual(Number(liveBody.stations[0]?.stationId), stationId, 'live session must return the correct station');
        assert.strictEqual(liveBody.stations[0]?.status, 'RUNNING', 'active backend session must map to customer RUNNING state');

        const gnLedger = await fetch(`${baseUrl}/api/v1/customer/club/ledger`, { headers: { Authorization: `Bearer ${auth.token}` } });
        const gnRows = await gnLedger.json().catch(() => []);
        assert.strictEqual(gnLedger.status, 200, 'customer GN ledger must be readable');
        assert.ok(Array.isArray(gnRows) && gnRows.some(row => Number(row.amount) === 25 && row.type === 'CREDIT'), 'GN ledger must expose canonical amount/type fields');
        const lpLedger = await fetch(`${baseUrl}/api/v1/customer/club/lp-ledger`, { headers: { Authorization: `Bearer ${auth.token}` } });
        const lpRows = await lpLedger.json().catch(() => []);
        assert.strictEqual(lpLedger.status, 200, 'customer LP ledger must be readable');
        assert.ok(Array.isArray(lpRows) && lpRows.some(row => Number(row.amount) === 7 && row.type === 'CREDIT'), 'LP ledger must expose canonical amount/type fields');

        const wrongPassword = await fetch(`${baseUrl}/api/auth/customer/login`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ phone_number: phone, password: `${password}-wrong` })
        });
        assert.strictEqual(wrongPassword.status, 401, 'wrong password must be rejected');
        console.log('PASS: Persian-digit login, profile GN/LP separation, authenticated live session, GN+LP ledgers, profile endpoint, and wrong-password rejection');
    } finally {
        if (sessionId !== null) await pool.query('DELETE FROM game_sessions WHERE id=$1 AND manager_id=$2', [sessionId, managerId]).catch(() => {});
        if (stationId !== null) await pool.query('DELETE FROM stations WHERE id=$1 AND manager_id=$2', [stationId, managerId]).catch(() => {});
        if (customerId !== null) {
            await pool.query('DELETE FROM gn_ledger WHERE manager_id=$1 AND customer_id=$2', [managerId, customerId]).catch(() => {});
            await pool.query('DELETE FROM lp_ledger WHERE manager_id=$1 AND customer_id=$2', [managerId, customerId]).catch(() => {});
            await pool.query('DELETE FROM customers WHERE id=$1 AND manager_id=$2', [customerId, managerId]).catch(() => {});
        }
        if (managerCreated) await pool.query("DELETE FROM managers WHERE id=$1 AND role='MANAGER'", [managerId]).catch(() => {});
        await pool.end();
    }
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
