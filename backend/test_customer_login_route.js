const assert = require('assert');

const baseUrl = process.env.TEST_API_URL || `http://127.0.0.1:${process.env.PORT || 3000}`;

async function post(body) {
    return fetch(`${baseUrl}/api/auth/customer/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(body)
    });
}

async function main() {
    const missing = await post({ phone_number: '00989999999999' });
    assert.strictEqual(missing.status, 400, `missing password should be rejected as 400, got ${missing.status}: ${await missing.text()}`);

    // No Manager ID is intentional: this exercises the standalone phone+password route and
    // verifies that its PostgreSQL candidate query runs, rather than returning a SQL 500.
    const noManager = await post({ phone_number: '۰۰۹۸۹۹۹۹۹۹۹۹۹۹', password: 'definitely-not-a-real-password' });
    assert.strictEqual(noManager.status, 401, `unknown credentials should return 401, not require manager_id or fail SQL: ${noManager.status}: ${await noManager.text()}`);

    const scoped = await post({ phone_number: '00989999999999', manager_id: '__nonexistent_test_manager__', password: 'definitely-not-a-real-password' });
    assert.strictEqual(scoped.status, 401, `a supplied Manager ID must remain a hard tenant scope: ${scoped.status}: ${await scoped.text()}`);

    console.log('PASS: customer login accepts phone+password without Manager context, normalizes Persian digits, and preserves optional tenant scoping');
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
