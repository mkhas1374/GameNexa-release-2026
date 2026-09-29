const { calculatePrice, cancelReservation } = require('./financialService');
// Mock tests for floats

async function run() {
    const mockClient = {
        query: async (sql, params) => {
            if (sql.includes('pending_surcharge_percent')) return { rows: [{ pending_surcharge_percent: 5 }] };
            if (sql.includes('payment_transactions')) return { rows: [{ paid: 100000 }] };
            if (sql.includes('SELECT * FROM reservations')) return { rows: [{ id: 1, manager_id: 'm1', customer_id: 'c1', status: 'CONFIRMED', start_time: new Date(Date.now() + 10 * 3600000).toISOString() }] };
            return { rows: [], rowCount: 1 };
        }
    };
    
    // PS4 1 controller for 120 mins = 120000 / 60 * 120 = 240000
    const res = await calculatePrice(mockClient, 'm1', 'c1', 'NORMAL_RESERVATION', 'PS4', 1, 120, true, null);
    
    // Cancel logic
    const cancelRes = await cancelReservation(mockClient, 'm1', 1, 'key');
    // refundAmount = Math.floor(100000 * 85 / 100) = 85000
    
    if (res.basePrice === 240000 && res.finalPrice === 378000 && cancelRes.refundAmount === 85000) {
        console.log("PASS: Arithmetic is exact without floats, refund is exact");
    } else {
        console.error("FAIL: Incorrect arithmetic:", res, cancelRes);
        process.exit(1);
    }
}
run();
