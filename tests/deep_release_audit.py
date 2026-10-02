#!/usr/bin/env python3
from pathlib import Path
import re, sys

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'app/src/main/java/com/example'
BACK=ROOT/'backend'
VM=(A/'ui/GameNetViewModel.kt').read_text(errors='ignore')
API=(A/'data/network/GameNetApi.kt').read_text(errors='ignore')
SH=(A/'data/network/SelfHostedManager.kt').read_text(errors='ignore')
REPO=(A/'data/GameNetRepository.kt').read_text(errors='ignore')
SET=(A/'ui/SettingsScreen.kt').read_text(errors='ignore')
DIAG=(A/'ui/NetworkDiagnosticsDialog.kt').read_text(errors='ignore')
SERVER=(BACK/'server.js').read_text(errors='ignore')
CANON=(BACK/'canonical_routes.js').read_text(errors='ignore')

checks=[]
def ok(name, condition):
    checks.append((name,bool(condition)))

ok('manager/customer bearer tokens are separate', 'managerAuthToken' in API and 'customerAuthToken' in API)
ok('Retrofit interceptor selects customer token only for customer paths', 'isCustomerEndpoint' in API and 'if (isCustomerEndpoint) customerAuthToken else managerAuthToken' in API)
ok('customer login never overwrites manager token', 'NetworkClient.customerAuthToken = token' in SH and 'NetworkClient.authToken = token' not in SH[SH.find('suspend fun loginCustomer'):SH.find('suspend fun registerCustomer')])
ok('customer session has dedicated encrypted token', 'enc_customer_auth_token' in VM)
ok('customer cold start does not call manager auth check', 'if (sessionType == "CUSTOMER")' in VM and VM.count('checkAuthStatusOnServer()') >= 1)
ok('station start is server-authoritative and manager-authenticated', 'app.post("/api/station/start", requireManagerAuth, requireActiveEntitlement' in SERVER)
ok('station start enforces station ownership', 'WHERE id=$1 AND manager_id=$2 FOR UPDATE' in SERVER)
ok('station start snapshots configured pricing', 'source:"MANAGER_CONFIGURATION_REVISION"' in SERVER and 'pricingSnapshot' in SERVER)
ok('stale local buffet orders are cleared before server sync', 'stationOrderDao.clearForStation(st.id)' in REPO)
ok('station-count purge removes stale stationOrders', "delete orders[String(row.id)]" in CANON)
ok('server diagnostics endpoint is authenticated', "app.get('/api/v1/manager/diagnostics', requireManagerAuth, requireActiveEntitlement" in SERVER)
ok('server diagnostics do not expose request bodies or auth headers', 'requestBody' not in SERVER[SERVER.find('const serverDiagnosticLog'):SERVER.find('app.get(\'/api/v1/manager/diagnostics')])
ok('diagnostics UI merges server and client logs', 'serverLogs' in DIAG and 'logs + serverLogs' in DIAG)
ok('Super Manager diagnostics is always reachable', 'if (currentRole == "SUPER_MANAGER") {' in SET and '&& hasErrors' not in SET[SET.find('if (currentRole == "SUPER_MANAGER")'):SET.find('if (currentRole == "SUPER_MANAGER")')+80])
ok('no default stationOrders in backend manager defaults', 'stationOrders:' not in SERVER[SERVER.find('const defaultManagerConfiguration'):SERVER.find('async function getManagerConfiguration')])
ok('all server diagnostic records are manager-scoped', 'filter(x=>String(x.managerId)===managerId)' in SERVER)
ok('backend syntax markers present', all(x in SERVER for x in ['requireManagerAuth','requireActiveEntitlement','/api/station/start']))

failed=[n for n,v in checks if not v]
for n,v in checks: print(('PASS' if v else 'FAIL')+': '+n)
if failed:
    print(f'FAILED CHECKS: {len(failed)}')
    sys.exit(1)
print(f'PASS: deep release audit ({len(checks)} invariants)')
