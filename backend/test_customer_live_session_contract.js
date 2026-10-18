const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const routes = fs.readFileSync(path.join(__dirname, 'canonical_routes.js'), 'utf8');
const server = fs.readFileSync(path.join(__dirname, 'server.js'), 'utf8');
const managerApi = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/data/network/SelfHostedManager.kt'), 'utf8');
const customerUi = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/ui/CustomerAppContent.kt'), 'utf8');
const vm = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/ui/GameNetViewModel.kt'), 'utf8');

const routeStart = routes.indexOf("app.get('/api/v1/customer/live-session'");
assert(routeStart >= 0, 'customer live-session endpoint must exist');
const routeEnd = routes.indexOf("app.get('/api/v1/customer/reservations'", routeStart);
const liveRoute = routes.slice(routeStart, routeEnd);
assert(liveRoute.includes('so.target_customer_id=$2 OR so.target_customer_id IS NULL'),
  'live feed must include directly assigned orders and shared orders');
assert(liveRoute.includes("'is_shared',(so.target_customer_id IS NULL)"),
  'feed must identify shared buffet lines');
assert(liveRoute.includes('customerBuffetShare') && liveRoute.includes('payerCustomerIdsStr'),
  'feed must return a per-customer allocated buffet cost and payer metadata');
assert(liveRoute.includes('active_session_customer_claims') && liveRoute.includes('session_participants'),
  'feed must find the current session through either participant membership or active claim');
assert(liveRoute.includes("gs.status IN ('ACTIVE','PAUSED')"),
  'closed sessions must never appear as live sessions');
assert(server.includes('splitMode:String(req.body?.splitMode||\'ALL\').toUpperCase()'),
  'online and offline session starts must persist split mode in the server snapshot');
assert(managerApi.includes('put("splitMode", splitMode.uppercase'),
  'Android Manager must send split mode to server');
assert(vm.includes('payerCustomerIds = station.getPayerCustomerIds()'),
  'Android Manager must send selected payers when starting a session');
assert(customerUi.includes('list.clear()') && customerUi.includes('Server snapshots are authoritative'),
  'customer UI must not let stale local order quantities override the live server snapshot');
assert(customerUi.includes('cloudSt.currentBuffetCost.toDouble()'),
  'customer UI must use the server-allocated buffet share instead of total shared order cost');
console.log('PASS: customer live-session identity, shared buffet visibility/allocation, and authoritative order snapshot contract');
