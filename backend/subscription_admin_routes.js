app.get('/api/v1/super-manager/subscription-requests', async (req, res) => {
    try {
        const result = await pool.query('SELECT * FROM subscription_payment_requests ORDER BY created_at DESC');
        res.json(result.rows);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.post('/api/v1/super-manager/subscription-requests/:id/confirm', async (req, res) => {
    const { id } = req.params;
    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        const reqResult = await client.query('SELECT * FROM subscription_payment_requests WHERE id = $1 FOR UPDATE', [id]);
        if (reqResult.rows.length === 0) throw new Error("Not found");
        const request = reqResult.rows[0];
        if (request.status !== 'PENDING') throw new Error("Invalid status");
        
        await client.query("UPDATE subscription_payment_requests SET status = 'CONFIRMED', reviewed_at = NOW() WHERE id = $1", [id]);
        
        const now = new Date();
        const expiresAt = new Date();
        if (request.plan_id === '1_MONTH') expiresAt.setMonth(now.getMonth() + 1);
        else if (request.plan_id === 'THREE_MONTHS') expiresAt.setMonth(now.getMonth() + 3);
        else if (request.plan_id === 'YEARLY') expiresAt.setFullYear(now.getFullYear() + 1);
        else expiresAt.setMonth(now.getMonth() + 1);

        const tokenStr = `lic_${request.manager_id}_${Date.now()}`;
        
        await client.query(
            `INSERT INTO configuration_revisions (manager_id, version_number, settings) 
             VALUES ($1, 1, '{}') ON CONFLICT DO NOTHING`,
            [request.manager_id]
        );
        
        await client.query("UPDATE managers SET token = $1 WHERE id = $2", [tokenStr, request.manager_id]);
        
        await client.query('COMMIT');
        res.json({ status: "success", license: tokenStr });
    } catch (e) {
        await client.query('ROLLBACK');
        res.status(400).json({ error: e.message });
    } finally {
        client.release();
    }
});

app.post('/api/v1/super-manager/subscription-requests/:id/reject', async (req, res) => {
    const { id } = req.params;
    const { reason } = req.body;
    try {
        const reqResult = await pool.query('SELECT * FROM subscription_payment_requests WHERE id = $1', [id]);
        if (reqResult.rows.length === 0) return res.status(404).json({ error: "Not found" });
        if (reqResult.rows[0].status !== 'PENDING') return res.status(400).json({ error: "Invalid status" });
        
        await pool.query("UPDATE subscription_payment_requests SET status = 'REJECTED', rejection_reason = $1, reviewed_at = NOW() WHERE id = $2", [reason || '', id]);
        res.json({ status: "success" });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});
