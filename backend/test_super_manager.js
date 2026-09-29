const { Pool } = require('pg');
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

async function check() {
  const client = await pool.connect();
  try {
    const res = await client.query("SELECT * FROM managers WHERE role = 'SUPER_MANAGER'");
    console.log("SUPER_MANAGERS:", res.rows);
  } finally {
    client.release();
    pool.end();
  }
}
check();
