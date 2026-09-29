const { Pool } = require('pg');
const bcrypt = require('bcrypt');

const pool = new Pool({
    connectionString: process.env.DATABASE_URL || (() => { throw new Error('DATABASE_URL is required; refusing legacy PostgreSQL fallback'); })()
});

async function bootstrap() {
    const superAdminPassword = process.env.SUPER_ADMIN_PASSWORD;
    const superAdminUsername = process.env.SUPER_ADMIN_USERNAME || 'superadmin';

    const client = await pool.connect();
    try {
        const existing = await client.query(
            "SELECT id, username, role FROM managers WHERE id = $1 LIMIT 1",
            ['mgr_super_admin']
        );

        // Never overwrite an existing Super Manager credential during container startup.
        // A short/missing bootstrap password is only fatal when the account does not exist yet.
        if (existing.rows.length > 0) {
            if (existing.rows[0].role !== 'SUPER_MANAGER') {
                await client.query(
                    "UPDATE managers SET role = 'SUPER_MANAGER' WHERE id = $1",
                    ['mgr_super_admin']
                );
            }
            console.log("Super Manager already exists; existing credentials preserved.");
            return;
        }

        if (!superAdminPassword || superAdminPassword.length < 16) {
            throw new Error('SUPER_ADMIN_PASSWORD must be set and at least 16 characters long when creating the Super Manager');
        }

        const hash = await bcrypt.hash(superAdminPassword, 10);
        await client.query(
            `INSERT INTO managers (id, username, password_hash, role)
             VALUES ($1, $2, $3, 'SUPER_MANAGER')`,
            ['mgr_super_admin', superAdminUsername, hash]
        );
        console.log("Super Manager bootstrapped.");
    } catch (e) {
        console.error("Bootstrap error:", e);
        process.exitCode = 1;
    } finally {
        client.release();
        await pool.end();
    }
}
bootstrap();
