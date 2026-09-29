const axios = require('axios');

const API_URL = 'http://localhost:3000/api';


const { Pool } = require('pg');
require('dotenv').config();
if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required for security tests');
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

async function runSecurityTests() {
    const client = await pool.connect();
    try {
        await client.query("INSERT INTO managers (id, username, password_hash, role) VALUES ('mgr_test_pg', 'test', '\testb\mgr_test_pg0\$k1G1z3y95QpU2X/nUoG5e.N4u/n/zP.5oM0K5i6lX.U7pX3sH.9.K', 'MANAGER') ON CONFLICT DO NOTHING");
        await client.query("INSERT INTO customers (id, manager_id, phone_number, club_tier) VALUES (99999, 'mgr_test_pg', '09999999999', 'BRONZE') ON CONFLICT DO NOTHING");
    } finally {
        client.release();
    }

    console.log("Starting Security Tests...");
    let passed = 0;
    let failed = 0;

    try {
        // We assume test manager 'mgr_test_pg' and customer '99999' exists from Prompt 2 tests.
        // Let's create another manager and customer for isolation testing
        // For testing we will just create a script that accesses DB directly to set up.
        // Or we can just try to hit the API with invalid tokens
        
        console.log("1. Authentication Bypass (No Token)");
        try {
            await axios.get(`${API_URL}/manager/customers`);
            console.log("FAIL: Allowed access without token");
            failed++;
        } catch (e) {
            if (e.response && e.response.status === 401) {
                console.log("PASS: Blocked access without token");
                passed++;
            } else {
                console.log("FAIL: Wrong error for no token");
                failed++;
            }
        }

        console.log("2. Authorization Bypass (Customer accessing Manager endpoint)");
        // Let's get a customer token
        let customerToken;
        try {
            const loginRes = await axios.post(`${API_URL}/auth/customer/login`, {
                phone_number: '09999999999',
                manager_id: 'mgr_test_pg'
            });
            customerToken = loginRes.data.token;
            
            await axios.get(`${API_URL}/manager/customers`, {
                headers: { Authorization: `Bearer ${customerToken}` }
            });
            console.log("FAIL: Customer accessed manager endpoint");
            failed++;
        } catch (e) {
            if (e.response && e.response.status === 403) {
                console.log("PASS: Blocked customer from manager endpoint");
                passed++;
            } else {
                console.log('FAIL: Wrong error for auth bypass', e.response?.status || e.message);
                failed++;
            }
        }

        console.log("3. IDOR / Customer Isolation");
        try {
            // Customer trying to get another customer's reservation (assuming reservation 1 doesn't belong to them)
            await axios.get(`${API_URL}/customer/reservations/1`, {
                headers: { Authorization: `Bearer ${customerToken}` }
            });
            console.log("FAIL: Customer accessed another reservation");
            failed++;
        } catch(e) {
            if (e.response && e.response.status === 404) {
                console.log("PASS: Blocked cross-customer reservation access");
                passed++;
            } else {
                console.log('FAIL: Wrong error for IDOR', e.response?.status || e.message);
                failed++;
            }
        }

        console.log("4. Anti-Tampering (Payment Spoofer)");
        try {
            // Attempt to pay with invalid token
            await axios.post(`${API_URL}/customer/reservations/1/pay`, { payment_token: 'fake_token' }, {
                headers: { Authorization: `Bearer ${customerToken}` }
            });
            console.log("FAIL: Allowed spoofed payment");
            failed++;
        } catch(e) {
            if (e.response && (e.response.status === 400 || e.response.status === 404)) {
                console.log("PASS: Blocked spoofed payment");
                passed++;
            } else {
                console.log('FAIL: Wrong error for payment spoof', e.response?.status || e.message);
                failed++;
            }
        }

    } catch (e) {
        console.error("Test framework error", e.message);
    }

    console.log(`\nTests Result: Passed: ${passed}, Failed: ${failed}`);
}

runSecurityTests();
