import os
import sqlite3
import psycopg2
from psycopg2.extras import execute_batch

def migrate():
    print("Starting migration...")
    sqlite_conn = sqlite3.connect('/var/www/gamenet-server/gamenet_central.db')
    sqlite_conn.row_factory = sqlite3.Row
    sqlite_cur = sqlite_conn.cursor()

    pg_conn = psycopg2.connect(os.environ.get("DATABASE_URL"))
    pg_cur = pg_conn.cursor()

    try:
        # Migrate managers
        sqlite_cur.execute("SELECT managerId, username, passwordHash, token FROM managers")
        managers = sqlite_cur.fetchall()
        pg_cur.executemany("""
            INSERT INTO managers (id, username, password_hash, token) 
            VALUES (%s, %s, %s, %s) ON CONFLICT (id) DO NOTHING
        """, [(m['managerId'], m['username'], m['passwordHash'], m['token']) for m in managers])
        print(f"Migrated {len(managers)} managers")

        # Before migrating customers/stations, let's collect all unique managerIds and insert placeholder managers if missing
        sqlite_cur.execute("SELECT DISTINCT managerId FROM customers")
        all_m_ids = [r['managerId'] for r in sqlite_cur.fetchall()]
        
        for m_id in all_m_ids:
            if m_id:
                pg_cur.execute("""
                    INSERT INTO managers (id, username, password_hash) 
                    VALUES (%s, %s, 'placeholder') ON CONFLICT (id) DO NOTHING
                """, (m_id, f'auto_{m_id}'))

        # Migrate customers
        sqlite_cur.execute("SELECT id, managerId, phoneNumber, fullName, clubTier, walletBalance, gnPoints, loyaltyPoints FROM customers")
        customers = sqlite_cur.fetchall()
        pg_cur.executemany("""
            INSERT INTO customers (id, manager_id, phone_number, full_name, club_tier, wallet_balance, gn_balance, lp_balance) 
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s) ON CONFLICT (id) DO NOTHING
        """, [(c['id'], c['managerId'], c['phoneNumber'], c['fullName'], c['clubTier'] or 'BRONZE', c['walletBalance'] or 0, c['gnPoints'] or 0, c['loyaltyPoints'] or 0) for c in customers])
        if customers:
            pg_cur.execute("SELECT setval('customers_id_seq', (SELECT MAX(id) FROM customers))")
        print(f"Migrated {len(customers)} customers")

        pg_conn.commit()
        print("Migration successful")
    except Exception as e:
        pg_conn.rollback()
        print(f"Migration failed: {e}")
    finally:
        pg_cur.close()
        pg_conn.close()
        sqlite_cur.close()
        sqlite_conn.close()

if __name__ == '__main__':
    migrate()
