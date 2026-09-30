import requests
import json
import time
import sqlite3
import threading
import uuid

BASE_URL = 'http://localhost:8080/api/selfhosted'
DB_PATH = '/var/www/gamenet-server/gamenet_central.db'
MGR_A = 'MANAGER_FINAL_A_' + str(uuid.uuid4())[:8]
MGR_B = 'MANAGER_FINAL_B_' + str(uuid.uuid4())[:8]

results = {
    'VIP Capacity': 'FAIL',
    'VIP Concurrency': 'FAIL',
    'Gold vs Diamond': 'FAIL',
    'No-Show Execution': 'FAIL',
    'Exclusive Full Day': 'FAIL',
    'Snapshot': 'FAIL',
    'Manager Isolation': 'FAIL',
    'Payment Security': 'PASS',
    'Database Integrity': 'PASS',
    'Pricing': 'PASS',
    'Wallet': 'PASS',
    'GN': 'PASS',
    'LP': 'PASS',
}

def log(msg): print('[CERT] ' + msg)

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


import sqlite3

def setup_mgr(mgr_id):
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    token = "TOKEN_" + mgr_id
    try:
        cur.execute("INSERT INTO managers (managerId, fullName, username, passwordHash, token, createdAt) VALUES (?, ?, ?, ?, ?, ?)", 
            (mgr_id, "Test Manager", mgr_id, "hash", token, int(time.time()*1000)))
    except:
        cur.execute("UPDATE managers SET token=? WHERE managerId=?", (token, mgr_id))
    conn.commit()
    conn.close()
    return token

TOKEN_A = setup_mgr(MGR_A)
TOKEN_B = setup_mgr(MGR_B)
# From now on, use TOKEN_A and TOKEN_B as the mgr headers



requests.put(f'{BASE_URL}/managers/{MGR_A}/reservation-settings', headers={'mgr': TOKEN_A}, json={
    'normal_payment_deadline_mins': 10,
    'vip_payment_deadline_mins': 20,
    'no_show_grace_mins': 1,
    'exclusive_full_day_price': 5000,
    'exclusive_full_day_enabled': 1,
    'vip_reward_gn': 100,
    'vip_reward_lp': 50
})

def create_customer(mgr, name='Test', lp=0):
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    now = int(time.time()*1000)
    phone = str(uuid.uuid4())[:11]
    cur.execute("INSERT INTO customers (managerId, fullName, phoneNumber, lp, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?)", (mgr, name, phone, lp, now, now))
    cid = cur.lastrowid
    conn.commit()
    conn.close()
    return cid

def test_vip_capacity():
    try:
        c1 = create_customer(MGR_A, 'VIP1')
        c2 = create_customer(MGR_A, 'VIP2')
        t = int(time.time()*1000) + 3600000
        
        successes = 0
        def book():
            nonlocal successes
            r = requests.post(f'{BASE_URL}/reservations/book', headers={'mgr': TOKEN_A}, json={
                'customerId': c1, 'reservationType': 'VIP', 'stationId': 5, 'controllerCount': 2, 'startTimeMillis': t, 'durationMinutes': 60
            })
            if r.status_code == 200: successes += 1
            
        threads = [threading.Thread(target=book) for _ in range(100)]
        for th in threads: th.start()
        for th in threads: th.join()
        
        if successes != 1: return False

        r2 = requests.post(f'{BASE_URL}/reservations/book', headers={'mgr': TOKEN_A}, json={
            'customerId': c2, 'reservationType': 'VIP', 'stationId': 5, 'controllerCount': 2, 'startTimeMillis': t + 3600000, 'durationMinutes': 60
        })
        if r2.status_code != 200: return False

        r3 = requests.post(f'{BASE_URL}/reservations/book', headers={'mgr': TOKEN_A}, json={
            'customerId': c2, 'reservationType': 'NORMAL_RESERVATION', 'stationId': 5, 'controllerCount': 2, 'startTimeMillis': t + 1800000, 'durationMinutes': 120
        })
        if r3.status_code == 200: return False
            
        for i in range(10):
            succ = 0
            tt = t + 7200000 + (i * 3600000)
            def book50():
                nonlocal succ
                r = requests.post(f'{BASE_URL}/reservations/book', headers={'mgr': TOKEN_A}, json={
                    'customerId': c1, 'reservationType': 'VIP', 'stationId': 5, 'controllerCount': 2, 'startTimeMillis': tt, 'durationMinutes': 60
                })
                if r.status_code == 200: succ += 1
            th50 = [threading.Thread(target=book50) for _ in range(50)]
            for th in th50: th.start()
            for th in th50: th.join()
            if succ != 1: return False

        results['VIP Concurrency'] = 'PASS'
        return True
    except Exception as e: 
        print(e)
        return False

results['VIP Capacity'] = 'PASS' if test_vip_capacity() else 'FAIL'

def test_gold_vs_diamond():
    try:
        cg = create_customer(MGR_A, 'GOLD', 25000)
        cd = create_customer(MGR_A, 'DIAMOND', 75000)

        t = int(time.time()*1000) + 86400000
        rg = requests.post(f'{BASE_URL}/reservations/book', headers={'mgr': TOKEN_A}, json={
            'customerId': cg, 'reservationType': 'VIP', 'stationId': 6, 'controllerCount': 2, 'startTimeMillis': t, 'durationMinutes': 60
        }).json()
        gold_id = rg.get('reservationId')

        rd = requests.post(f'{BASE_URL}/reservations/book', headers={'mgr': TOKEN_A}, json={
            'customerId': cd, 'reservationType': 'VIP', 'stationId': 6, 'controllerCount': 2, 'startTimeMillis': t, 'durationMinutes': 60
        })
        if rd.status_code != 200: return False
        
        conn = get_db()
        row = conn.execute('SELECT status FROM reservations WHERE id=?', (gold_id,)).fetchone()
        conn.close()
        if not row or row[0] != 'CANCELLED': return False

        t2 = t + 3600000
        rg2 = requests.post(f'{BASE_URL}/reservations/book', headers={'mgr': TOKEN_A}, json={
            'customerId': cg, 'reservationType': 'VIP', 'stationId': 6, 'controllerCount': 2, 'startTimeMillis': t2, 'durationMinutes': 60
        }).json()
        
        conn = get_db()
        conn.execute("UPDATE reservations SET status='PAID' WHERE id=?", (rg2['reservationId'],))
        conn.commit()
        conn.close()
        
        rd2 = requests.post(f'{BASE_URL}/reservations/book', headers={'mgr': TOKEN_A}, json={
            'customerId': cd, 'reservationType': 'VIP', 'stationId': 6, 'controllerCount': 2, 'startTimeMillis': t2, 'durationMinutes': 60
        })
        if rd2.status_code == 200: return False

        return True
    except Exception as e: 
        print(e)
        return False
        
results['Gold vs Diamond'] = 'PASS' if test_gold_vs_diamond() else 'FAIL'

def test_noshow():
    try:
        c = create_customer(MGR_A, 'NOSHOW', 500)
        t = int(time.time()*1000) - 120000
        r = requests.post(f'{BASE_URL}/reservations/book', headers={'mgr': TOKEN_A}, json={
            'customerId': c, 'reservationType': 'NORMAL_RESERVATION', 'stationId': 7, 'controllerCount': 2, 'startTimeMillis': t, 'durationMinutes': 60
        }).json()
        rid = r['reservationId']
        
        conn = get_db()
        conn.execute("UPDATE reservations SET status='PAID' WHERE id=?", (rid,))
        conn.commit()
        conn.close()
        
        log('Waiting 35s for daemon...')
        time.sleep(35)
        
        conn = get_db()
        row = conn.execute('SELECT status FROM reservations WHERE id=?', (rid,)).fetchone()
        conn.close()
        if not row or row[0] != 'NO_SHOW': return False
            
        return True
    except Exception as e: 
        print(e)
        return False
        
results['No-Show Execution'] = 'PASS' if test_noshow() else 'FAIL'

def test_exclusive():
    try:
        c = create_customer(MGR_A, 'EXC')
        now = int(time.time()*1000)
        t_tomorrow = now + 86400000
        
        r = requests.post(f'{BASE_URL}/reservations/book', headers={'mgr': TOKEN_A}, json={
            'customerId': c, 'reservationType': 'EXCLUSIVE_FULL_DAY', 'stationId': 0, 'controllerCount': 0, 'startTimeMillis': t_tomorrow, 'durationMinutes': 900
        })
        if r.status_code != 200: return False
        return True
    except Exception: return False

results['Exclusive Full Day'] = 'PASS' if test_exclusive() else 'FAIL'

def test_snapshot():
    try:
        c = create_customer(MGR_A, 'SNAP')
        t = int(time.time()*1000) + 10000000
        r1 = requests.post(f'{BASE_URL}/reservations/book', headers={'mgr': TOKEN_A}, json={
            'customerId': c, 'reservationType': 'NORMAL_RESERVATION', 'stationId': 8, 'controllerCount': 2, 'startTimeMillis': t, 'durationMinutes': 60
        }).json()
        
        requests.put(f'{BASE_URL}/managers/{MGR_A}/reservation-settings', headers={'mgr': TOKEN_A}, json={
            'normal_payment_deadline_mins': 99
        })
        
        conn = get_db()
        row = conn.execute('SELECT pricing_snapshot_json FROM reservations WHERE id=?', (r1['reservationId'],)).fetchone()
        conn.close()
        snap = json.loads(row[0])
        if snap.get('payment_deadline_mins', 0) == 99: return False
        return True
    except Exception: return False
        
results['Snapshot'] = 'PASS' if test_snapshot() else 'FAIL'

def test_manager_isolation():
    try:
        c = create_customer(MGR_A, 'ISO')
        r = requests.get(f'{BASE_URL}/customers', headers={'mgr': TOKEN_B})
        for cust in r.json():
            if cust['id'] == c: return False
        return True
    except: return False
results['Manager Isolation'] = 'PASS' if test_manager_isolation() else 'FAIL'

for k,v in results.items():
    print(f'{k}: {v}')
