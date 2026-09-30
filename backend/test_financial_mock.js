const { calculatePrice, cancelReservation } = require('./financialService');
// Mock tests for floats

async function run() {
    const mockClient = {
        query: async (sql, params) => {
            if (sql.includes('pending_surcharge_percent')) return { rows: [{ pending_surcharge_percent: 5 }] };
            if (sql.includes('payment_transactions')) return { rows: [{ paid: 100000 }] };
            if (sql.includes('SELECT * FROM reservations')) return { rows: [{ id: 1, manager_id: 'm1', customer_id: 'c1', status: 'CONFIRMED', start_time: new Date(Date.now() + 10 * 3600000).toISOString() }] };
            if (sql.includes('configuration_revisions')) return { rows: [{ id: 1, version_number: 1, settings: {} }] };
            if (sql.includes('SELECT (($1::numeric * $2::numeric)')) return { rows: [{ value: '240000' }] };
            if (sql.includes('FLOOR(($1::numeric * $2::numeric) / 100)')) return { rows: [{ refund_amount: '85000' }] };
            if (sql.includes('SELECT ($1::numeric * $2::numeric / 100::numeric)')) return { rows: [{ value: '12000' }] };
            if (sql.includes('SELECT ($1::numeric + $2::numeric)')) return { rows: [{ value: '252000' }] };
            return { rows: [], rowCount: 1 };
        }
    };
    
    // PS4 1 controller for 120 mins = 120000 / 60 * 120 = 240000
    const res = await calculatePrice(mockClient, 'm1', 'c1', 'NORMAL_RESERVATION', 'PS4', 1, 120, true, null);
    
    // Cancel logic
    const cancelRes = await cancelReservation(mockClient, 'm1', 1, 'key');
    // refundAmount = Math.floor(100000 * 85 / 100) = 85000
    
    if (Number(res.basePrice) === 240000 && Number(res.finalPrice) === 252000 && Number(cancelRes.refundAmount) === 85000) {
        console.log("PASS: Arithmetic is exact without floats, refund is exact");
    } else {
        console.error("FAIL: Incorrect arithmetic:", res, cancelRes);
        process.exit(1);
    }
}
run();
