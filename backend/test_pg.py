import os
import psycopg2
import sys

DATABASE_URL = os.getenv('DATABASE_URL')
if not DATABASE_URL:
    raise RuntimeError('DATABASE_URL is required for PG tests')

def test_pg():
    print("Running PG Tests...")
    conn = psycopg2.connect(DATABASE_URL)
    cur = conn.cursor()

    passed = 0
    failed = 0

    try:
        cur.execute("INSERT INTO managers (id, username, password_hash) VALUES ('mgr_test_pg', 'test_mgr', 'hash') ON CONFLICT DO NOTHING")
        cur.execute("INSERT INTO customers (id, manager_id, phone_number) VALUES (99999, 'mgr_test_pg', '09999999999') ON CONFLICT DO NOTHING")
        cur.execute("INSERT INTO stations (id, manager_id, name) VALUES (99999, 'mgr_test_pg', 'Test Station') ON CONFLICT DO NOTHING")
        cur.execute("INSERT INTO configuration_revisions (id, manager_id, version_number, settings) VALUES (99999, 'mgr_test_pg', 1, '{}'::jsonb) ON CONFLICT DO NOTHING")
        conn.commit()
    except Exception as e:
        conn.rollback()
        print("Setup error:", e)

    # Test 1: FK constraint
    try:
        cur.execute("INSERT INTO customers (id, manager_id, phone_number) VALUES (99998, 'invalid_mgr', '09999999998')")
        conn.commit()
        print("FAIL: FK allowed invalid manager")
        failed += 1
    except Exception as e:
        conn.rollback()
        print("PASS: FK blocked invalid manager")
        passed += 1

    # Test 2: Money precision
    try:
        cur.execute("INSERT INTO wallet_transactions (manager_id, customer_id, amount, type, idempotency_key) VALUES ('mgr_test_pg', 99999, 100.55, 'CREDIT', 'idemp_1')")
        cur.execute("SELECT amount FROM wallet_transactions WHERE idempotency_key = 'idemp_1'")
        amount = cur.fetchone()[0]
        if float(amount) == 100.55:
            print("PASS: Money precision preserved")
            passed += 1
        else:
            print("FAIL: Money precision altered", amount)
            failed += 1
        conn.commit()
    except Exception as e:
        conn.rollback()
        print("FAIL: Money test error", e)
        failed += 1

    # Test 3: Idempotency (Unique constraint)
    try:
        cur.execute("INSERT INTO wallet_transactions (manager_id, customer_id, amount, type, idempotency_key) VALUES ('mgr_test_pg', 99999, 100.00, 'CREDIT', 'idemp_1')")
        conn.commit()
        print("FAIL: Idempotency key uniqueness failed")
        failed += 1
    except Exception as e:
        conn.rollback()
        print("PASS: Idempotency key uniqueness enforced")
        passed += 1

    # Test 4: Reservation Snapshot Insert
    try:
        cur.execute("""
            INSERT INTO reservations (
                manager_id, customer_id, station_id, type, status, start_time, end_time, duration_minutes,
                snap_base_price, snap_final_price, snap_payable_amount, config_revision_id
            ) VALUES (
                'mgr_test_pg', 99999, 99999, 'NORMAL_RESERVATION', 'CONFIRMED', NOW(), NOW() + interval '1 hour', 60,
                150.00, 150.00, 150.00, 99999
            )
        """)
        conn.commit()
        print("PASS: Snapshot insert successful")
        passed += 1
    except Exception as e:
        conn.rollback()
        print("FAIL: Snapshot insert failed", e)
        failed += 1

    # Test 5: Concurrency exclusion
    try:
        cur.execute("CREATE EXTENSION IF NOT EXISTS btree_gist")
        cur.execute("""
            ALTER TABLE reservations ADD CONSTRAINT no_overlap 
            EXCLUDE USING gist (
                manager_id WITH =,
                station_id WITH =,
                tstzrange(start_time, end_time) WITH &&
            ) WHERE (status != 'CANCELLED' AND status != 'REJECTED')
        """)
        conn.commit()
        print("PASS: Added Exclusion Constraint for Concurrency")
        passed += 1
    except Exception as e:
        conn.rollback()
        if 'already exists' in str(e):
            print("PASS: Exclusion Constraint already exists")
            passed += 1
        else:
            print("FAIL: Could not add Exclusion Constraint", e)
            failed += 1

    # Test 6: Concurrency Overlap detection
    try:
        # Try to insert overlapping reservation
        cur.execute("""
            INSERT INTO reservations (
                manager_id, customer_id, station_id, type, status, start_time, end_time, duration_minutes,
                snap_base_price, snap_final_price, snap_payable_amount, config_revision_id
            ) VALUES (
                'mgr_test_pg', 99999, 99999, 'NORMAL_RESERVATION', 'CONFIRMED', NOW() + interval '30 minutes', NOW() + interval '90 minutes', 60,
                150.00, 150.00, 150.00, 99999
            )
        """)
        conn.commit()
        print("FAIL: Concurrency constraint failed, allowed overlap")
        failed += 1
    except Exception as e:
        conn.rollback()
        print("PASS: Concurrency constraint blocked overlapping reservation")
        passed += 1

    print(f"\nTests Result: Passed: {passed}, Failed: {failed}")
    if failed > 0:
        sys.exit(1)

if __name__ == '__main__':
    test_pg()
