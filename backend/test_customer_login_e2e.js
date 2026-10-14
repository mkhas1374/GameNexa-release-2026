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
    try {
        const hash = await bcrypt.hash(password, 10);
        await pool.query(
            "INSERT INTO managers(id,username,password_hash,role) VALUES($1,$1,$2,'MANAGER')",
            [managerId, hash]
        );
        managerCreated = true;
        const created = await pool.query(
            'INSERT INTO customers(manager_id,phone_number,full_name,password_hash) VALUES($1,$2,$3,$4) RETURNING id',
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

        const wrongPassword = await fetch(`${baseUrl}/api/auth/customer/login`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ phone_number: phone, password: `${password}-wrong` })
        });
        assert.strictEqual(wrongPassword.status, 401, 'wrong password must be rejected');
        console.log('PASS: test customer can login with Persian-digit phone, receives scoped token, loads Android profile endpoint, and wrong password is rejected');
    } finally {
        if (customerId !== null) await pool.query('DELETE FROM customers WHERE id=$1 AND manager_id=$2', [customerId, managerId]);
        if (managerCreated) await pool.query('DELETE FROM managers WHERE id=$1 AND role=\'MANAGER\'', [managerId]);
        await pool.end();
    }
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
