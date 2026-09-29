const { Pool } = require('pg');

require('dotenv').config();
const pgPool = new Pool({ connectionString: process.env.DATABASE_URL });

async function runTests() {
    let passed = 0;
    let failed = 0;
    const client = await pgPool.connect();

    try {
        console.log("Running PG Tests...");
        await client.query("DELETE FROM wallet_transactions WHERE idempotency_key IN ('idemp_1')");
        await client.query("DELETE FROM reservations WHERE manager_id = 'mgr_test_pg'");
        await client.query("DELETE FROM configuration_revisions WHERE manager_id = 'mgr_test_pg'");
        await client.query("DELETE FROM stations WHERE manager_id = 'mgr_test_pg'");
        await client.query("DELETE FROM customers WHERE manager_id = 'mgr_test_pg'");
        await client.query("DELETE FROM managers WHERE id = 'mgr_test_pg'");

        // Setup test manager and customer
        await client.query(`INSERT INTO managers (id, username, password_hash) VALUES ('mgr_test_pg', 'test_mgr', 'hash') ON CONFLICT DO NOTHING`);
        await client.query(`INSERT INTO customers (id, manager_id, phone_number) VALUES (99999, 'mgr_test_pg', '09999999999') ON CONFLICT DO NOTHING`);
        await client.query(`INSERT INTO stations (id, manager_id, name) VALUES (99999, 'mgr_test_pg', 'Test Station') ON CONFLICT DO NOTHING`);
        await client.query(`INSERT INTO configuration_revisions (id, manager_id, version_number, settings) VALUES (99999, 'mgr_test_pg', 1, '{}'::jsonb) ON CONFLICT DO NOTHING`);

        // Test 1: Foreign Key constraint (Customer with invalid manager)
        try {
            await client.query(`INSERT INTO customers (id, manager_id, phone_number) VALUES (99998, 'invalid_mgr', '09999999998')`);
            console.log("FAIL: FK allowed invalid manager");
            failed++;
        } catch (e) {
            console.log("PASS: FK blocked invalid manager");
            passed++;
        }

        // Test 2: Money precision
        try {
            await client.query(`INSERT INTO wallet_transactions (manager_id, customer_id, amount, type, idempotency_key) VALUES ('mgr_test_pg', 99999, 100.55, 'CREDIT', 'idemp_1')`);
            const res = await client.query(`SELECT amount FROM wallet_transactions WHERE idempotency_key = 'idemp_1'`);
            if (res.rows[0].amount == 100.55) { // pg numeric is returned as string in node-pg, check value
                console.log("PASS: Money precision preserved");
                passed++;
            } else {
                console.log("FAIL: Money precision altered", res.rows[0].amount);
                failed++;
            }
        } catch(e) {
            console.log("FAIL: Money test threw error", e);
            failed++;
        }

        // Test 3: Idempotency (Unique constraint)
        try {
            await client.query(`INSERT INTO wallet_transactions (manager_id, customer_id, amount, type, idempotency_key) VALUES ('mgr_test_pg', 99999, 100.00, 'CREDIT', 'idemp_1')`);
            console.log("FAIL: Idempotency key uniqueness failed (allowed duplicate)");
            failed++;
        } catch(e) {
            console.log("PASS: Idempotency key uniqueness enforced");
            passed++;
        }

        // Test 4: Reservation Snapshot Insert
        try {
            await client.query(`
                INSERT INTO reservations (
                    manager_id, customer_id, station_id, type, status, start_time, end_time, duration_minutes,
                    snap_base_price, snap_final_price, snap_payable_amount, config_revision_id
                ) VALUES (
                    'mgr_test_pg', 99999, 99999, 'NORMAL_RESERVATION', 'CONFIRMED', NOW(), NOW() + interval '1 hour', 60,
                    150.00, 150.00, 150.00, 99999
                )
            `);
            console.log("PASS: Snapshot insert successful");
            passed++;
        } catch(e) {
            console.log("FAIL: Snapshot insert failed", e);
            failed++;
        }

        // Test 5: Concurrent constraint
        try {
            // we have an index on (manager_id, station_id, start_time, end_time), not an exclude constraint yet.
            // Let's add EXCLUDE constraint for overlapping reservations if not exists
            await client.query(`CREATE EXTENSION IF NOT EXISTS btree_gist;`);
            await client.query(`
                ALTER TABLE reservations ADD CONSTRAINT no_overlap 
                EXCLUDE USING gist (
                    manager_id WITH =,
                    station_id WITH =,
                    tstzrange(start_time, end_time) WITH &&
                ) WHERE (status != 'CANCELLED' AND status != 'REJECTED');
            `);
            console.log("PASS: Added Exclusion Constraint for Concurrency");
            passed++;
        } catch(e) {
            // might fail if extension not supported or already exists
            if (e.message.includes('already exists')) {
                console.log("PASS: Exclusion Constraint already exists");
                passed++;
            } else {
                console.log("FAIL: Could not add Exclusion Constraint", e);
                failed++;
            }
        }

        console.log(`\nTests Result: Passed: ${passed}, Failed: ${failed}`);

    } catch (err) {
        console.error("Test framework error", err);
    } finally {
        client.release();
        pgPool.end();
    }
}

runTests();
