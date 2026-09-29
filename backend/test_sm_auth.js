const axios = require('axios');

async function runTests() {
    console.log("Testing Normal Manager Login...");
    try {
        const res = await axios.post('http://localhost:3000/api/auth/manager/login', {
            username: 'admin',
            password: 'placeholder_123'
        });
        console.log("Normal manager login status:", res.status);
    } catch (e) {
        console.log("Normal manager login error:", e.response?.status, e.response?.data);
    }

    console.log("Testing Super Manager Login...");
    try {
        const res = await axios.post('http://localhost:3000/api/auth/manager/login', {
            username: 'superadmin',
            password: process.env.TEST_SUPER_PASSWORD || 'placeholder'
        });
        console.log("Super manager login status:", res.status);
        console.log("Super manager login role:", res.data.role);
    } catch (e) {
        console.log("Super manager login error:", e.response?.status, e.response?.data);
    }
}
runTests();
