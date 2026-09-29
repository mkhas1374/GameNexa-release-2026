const axios = require('axios');
async function run() {
    // 1. Get super manager token
    const res = await axios.post('http://localhost:3000/api/auth/manager/login', {
        username: 'superadmin',
        password: process.env.TEST_SUPER_PASSWORD || 'placeholder'
    });
    const token = res.data.token;
    
    // 2. Try to access a super manager route
    try {
        const checkRes = await axios.get('http://localhost:3000/api/v1/super-manager/subscription-requests', {
            headers: { Authorization: `Bearer ${token}` }
        });
        console.log("Access super manager route with SM token: SUCCESS, status:", checkRes.status);
    } catch(e) {
        console.log("Access super manager route with SM token: FAILED", e.response?.status);
    }
}
run();
