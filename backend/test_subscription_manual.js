const { Pool } = require('pg');
require('dotenv').config();
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

async function runTest() {
    let passed = 0, failed = 0;
    try {
        const res = await fetch('http://localhost:3000/api/license/buy', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({deviceId: 'test_dev', plan: '1_MONTH'})
        });
        const data = await res.json();
        
        if (data.licenseCode && data.licenseCode.startsWith('PENDING_')) {
            console.log("PASS: Manual Payment Creation"); passed++;
        } else {
            console.log("FAIL: Manual Payment Creation"); failed++;
        }
        
        const reqId = data.licenseCode.split('_')[1];
        
        // Confirm
        const res2 = await fetch(`http://localhost:3000/api/v1/super-manager/subscription-requests/${reqId}/confirm`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'}
        });
        const data2 = await res2.json();
        if (data2.status === 'success' && data2.license) {
            console.log("PASS: Manager Confirmation"); passed++;
        } else {
            console.log("FAIL: Manager Confirmation"); failed++;
        }
        
        // Duplicate confirm
        const res3 = await fetch(`http://localhost:3000/api/v1/super-manager/subscription-requests/${reqId}/confirm`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'}
        });
        if (res3.status === 400) {
            console.log("PASS: Duplicate Confirmation Protection"); passed++;
        } else {
            console.log("FAIL: Duplicate Confirmation Protection"); failed++;
        }
        
    } catch (e) {
        console.error(e);
    } finally {
        pool.end();
        console.log(`Tests Result: Passed: ${passed}, Failed: ${failed}`);
    }
}
runTest();
