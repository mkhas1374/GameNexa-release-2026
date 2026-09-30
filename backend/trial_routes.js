app.post('/api/v1/super-manager/check-trial', async (req, res) => {
    const { device_id, deviceId, deviceName } = req.body;
    const finalDeviceId = device_id || deviceId;
    if (!finalDeviceId) return res.status(400).json({ status: "error", message: "Device ID required" });

    try {
        let result = await pool.query('SELECT * FROM trial_devices WHERE device_id = $1', [finalDeviceId]);
        let trial = result.rows[0];

        if (!trial) {
            const insertResult = await pool.query(
                `INSERT INTO trial_devices (device_id, device_name, started_at, expires_at) 
                 VALUES ($1, $2, NOW(), NOW() + INTERVAL '24 hours') RETURNING *`,
                [finalDeviceId, deviceName || 'Unknown']
            );
            trial = insertResult.rows[0];
        }

        const now = new Date();
        const expiresAt = new Date(trial.expires_at);
        const isExpired = now > expiresAt || trial.status !== 'ACTIVE';
        
        let hoursLeft = 0;
        if (!isExpired) {
            hoursLeft = (expiresAt.getTime() - now.getTime()) / (1000 * 60 * 60);
        }

        res.json({
            status: isExpired ? "expired" : "active",
            isExpired: isExpired,
            hoursLeft: Math.max(0, hoursLeft),
            responseMessage: isExpired ? "مهلت تست ۲۴ ساعته شما به پایان رسیده است." : "تست ۲۴ ساعته فعال است"
        });

    } catch (e) {
        console.error("Trial check error:", e);
        res.status(500).json({ status: "error", message: "Server error" });
    }
});
