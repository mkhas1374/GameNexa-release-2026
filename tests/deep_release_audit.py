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
LOGGER=(A/'data/network/NetworkLogger.kt').read_text(errors='ignore')
MAIN=(A/'ui/MainScreen.kt').read_text(errors='ignore')
CUSTOMERS_UI=(A/'ui/CustomersReservationsScreen.kt').read_text(errors='ignore')
CUSTOMER_DIALOGS=(A/'ui/CustomerDialogs.kt').read_text(errors='ignore')
ACTIVITY=(A/'MainActivity.kt').read_text(errors='ignore')
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
ok('stale local buffet orders are cleared before server sync', 'for (st in remoteStations) stationOrderDao.clearForStation(st.id)' in REPO)
ok('station metadata save cannot resurrect buffet orders', 'syncStationToCloud(state, ordersArray.toString())' not in REPO[REPO.find('suspend fun insertStationState'):REPO.find('suspend fun clearAllStationStates')])
ok('idle station UI hides stale buffet orders', 'station.status == "RUNNING" || station.status == "PAUSED"' in (A/'ui/MainScreen.kt').read_text(errors='ignore'))
ok('station count purge is manager-ranked, not global-id based', 'ROW_NUMBER() OVER (ORDER BY id)' in CANON and 'id>$2' not in CANON[CANON.find('stations/purge-extra'):CANON.find('stations/purge-extra')+3000])
ok('station-count purge removes stale stationOrders', 'delete orders[String(id)]' in CANON)
ok('server diagnostics endpoint is authenticated', "app.get('/api/v1/manager/diagnostics', requireManagerAuth, requireActiveEntitlement" in SERVER)
ok('server diagnostics do not expose request bodies or auth headers', 'requestBody' not in SERVER[SERVER.find('const serverDiagnosticLog'):SERVER.find('app.get(\'/api/v1/manager/diagnostics')])
ok('reservation placeholders support double-brace templates', 'replaceToken(out, "minutes_before_arrival"' in (A/'ui/ReservationSettingsScreen.kt').read_text(errors='ignore'))
ok('start action rehydrates Manager identity', 'decryptSetting("enc_manager_id")' in VM and 'NetworkClient.managerAuthToken' in VM)
ok('server diagnostics include a safe route code', 'diagnosticCode' in SERVER and 'diagnosticMessage' in SERVER)
ok('Super Manager diagnostics is always reachable', 'if (currentRole == "SUPER_MANAGER") {' in SET and '&& hasErrors' not in SET[SET.find('if (currentRole == "SUPER_MANAGER")'):SET.find('if (currentRole == "SUPER_MANAGER")')+80])
ok('no default stationOrders in backend manager defaults', 'stationOrders:' not in SERVER[SERVER.find('const defaultManagerConfiguration'):SERVER.find('async function getManagerConfiguration')])
ok('all server diagnostic records are manager-scoped', 'filter(x=>String(x.managerId)===managerId)' in SERVER)
ok('backend syntax markers present', all(x in SERVER for x in ['requireManagerAuth','requireActiveEntitlement','/api/station/start']))
ok('live network log buffer is 150 entries', 'MAX_LOGS = 150' in LOGGER)
ok('HTTP failures increment failed-request counter', 'failedRequests = current.failedRequests + if (failed) 1 else 0' in LOGGER)
ok('server diagnostics return 150 entries', '.slice(0,150)' in SERVER)
ok('server diagnostics auto-refresh while open', 'delay(2000)' in DIAG)
ok('unreviewed invoice card uses floating-safe money formatting', '"%,.0f تومان".format(Locale.US, if (hasDiscount) finalAmount else origTotal)' in CUSTOMERS_UI)
ok('start payload carries payment, duration and customer prepayments', 'prepaymentAmount' in SH and 'durationLimitMinutes' in SH and 'customerPrepayments' in SH)
ok('start uses current UI customer selection, not only persisted Room state', 'selectedCustomerIds: List<Long>? = null' in VM and 'effectiveCustomerIds = selectedCustomerIds ?: station.getCustomerIds()' in VM)
ok('custom duration can be used without initial payment', 'station.prepaymentAmount > 0L || station.durationLimitMinutes > 0' in MAIN)
ok('customer selection is not capped by controller count', 'selectedMap[cust.id] = cust' in CUSTOMER_DIALOGS and 'selectedMap.size < maxControllers' not in CUSTOMER_DIALOGS)
ok('Super Manager sees offline warning banner too', 'if (!isServerConnected && !isTrialUser && !isGracePeriodExpired)' in ACTIVITY)

failed=[n for n,v in checks if not v]
for n,v in checks: print(('PASS' if v else 'FAIL')+': '+n)
if failed:
    print(f'FAILED CHECKS: {len(failed)}')
    sys.exit(1)
print(f'PASS: deep release audit ({len(checks)} invariants)')
