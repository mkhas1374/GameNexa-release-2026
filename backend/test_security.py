import requests

API_URL = 'http://localhost:3000/api'

def run_tests():
    print("Starting Security Tests...")
    passed = 0
    failed = 0

    # 1. Auth bypass
    r = requests.get(f"{API_URL}/manager/customers")
    if r.status_code == 401:
        print("PASS: Blocked access without token")
        passed += 1
    else:
        print(f"FAIL: Allowed access without token (status {r.status_code})")
        failed += 1

    # 2. Login to get tokens
    # Customer Login
    r = requests.post(f"{API_URL}/auth/customer/login", json={"phone_number": "09999999999", "manager_id": "mgr_test_pg"})
    customer_token = None
    if r.status_code == 200:
        customer_token = r.json().get('token')
    else:
        print(f"Customer login failed: {r.status_code} - {r.text}")
        
    # Manager Login
    r = requests.post(f"{API_URL}/auth/manager/login", json={"username": "test_mgr", "password": "override_password"})
    manager_token = None
    if r.status_code == 200:
        manager_token = r.json().get('token')
    else:
        print(f"Manager login failed: {r.status_code} - {r.text}")

    if not customer_token or not manager_token:
        print("Failed to get tokens, skipping some tests")
    else:
        # 3. Auth Bypass (Customer hitting Manager endpoint)
        r = requests.get(f"{API_URL}/manager/customers", headers={"Authorization": f"Bearer {customer_token}"})
        if r.status_code == 403:
            print("PASS: Blocked customer from manager endpoint")
            passed += 1
        else:
            print(f"FAIL: Customer accessed manager endpoint (status {r.status_code})")
            failed += 1

        # 4. IDOR (Customer accessing random reservation)
        r = requests.get(f"{API_URL}/customer/reservations/9999", headers={"Authorization": f"Bearer {customer_token}"})
        if r.status_code == 404:
            print("PASS: Blocked cross-customer reservation access")
            passed += 1
        else:
            print(f"FAIL: Customer accessed another reservation (status {r.status_code})")
            failed += 1

        # 5. Manager IDOR
        r = requests.get(f"{API_URL}/manager/reservations/9999", headers={"Authorization": f"Bearer {manager_token}"})
        if r.status_code == 404:
            print("PASS: Manager isolation for reservations worked")
            passed += 1
        else:
            print(f"FAIL: Manager accessed unknown/other reservation (status {r.status_code})")
            failed += 1

        # 6. Anti-Tampering (Payment Spoofer)
        r = requests.post(f"{API_URL}/customer/reservations/9999/pay", json={"payment_token": "fake_token"}, headers={"Authorization": f"Bearer {customer_token}"})
        if r.status_code in [400, 404]:
            print("PASS: Blocked spoofed payment")
            passed += 1
        else:
            print(f"FAIL: Allowed spoofed payment (status {r.status_code})")
            failed += 1

    print(f"\nTests Result: Passed: {passed}, Failed: {failed}")

if __name__ == '__main__':
    run_tests()
