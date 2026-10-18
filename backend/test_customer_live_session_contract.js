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
assert(liveRoute.includes("'eventType',se.event_type") && liveRoute.includes("AS live_events") &&
  liveRoute.includes("type==='SEGMENT_PAUSE'||type==='SEGMENT_CONTINUE'") &&
  liveRoute.includes("committedSegmentCost:Number(committedSegmentCost||0)") &&
  liveRoute.includes("elapsedPlayingTimeMillis:Math.max(0,segmentElapsedMs)"),
  'live feed must preserve committed segment costs and calculate the currently open segment from pause/resume/segment events');
assert(liveRoute.includes("gs.status IN ('ACTIVE','PAUSED')"),
  'closed sessions must never appear as live sessions');
assert(liveRoute.includes('(newer.started_at,newer.id) > (gs.started_at,gs.id)') &&
  liveRoute.includes('newer_sp.customer_id=$2') && liveRoute.includes('newer_claim.customer_id=$2') &&
  !liveRoute.includes('newer.station_id=gs.station_id'),
  'the live feed must return only the newest active session for this customer across station changes and tied timestamps');
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
assert(customerUi.includes('(currentLp - levelStartLp).toFloat() / (targetLp - levelStartLp).toFloat()'),
  'loyalty progress must use floating-point division instead of integer division');
assert(managerApi.includes('Never leave an old server snapshot on the customer profile after a') && managerApi.includes('_allLiveStations.value = emptyList()'),
  'failed customer live-session refresh must not preserve a stale active-session snapshot');
assert(managerApi.includes('sessionId = obj.optString(\"sessionId\", obj.optString(\"session_id\", \"\"))') &&
  customerUi.includes('sessionId = activeLiveSession?.sessionId.orEmpty()') &&
  customerUi.includes('remember(station.id, sessionId, station.startTimeMillis') &&
  customerUi.includes('LaunchedEffect(station.id, sessionId, station.startTimeMillis'),
  'customer live timer must reset on session identity change even if station/time keys collide');
assert(managerApi.includes('val managerToken = NetworkClient.managerAuthToken?.takeIf { it.isNotBlank() }') && managerApi.includes('.add("Authorization", "Bearer $managerToken")'),
  'Manager diagnostics must use an actual Manager token and skip requests from Customer sessions');
const startGate = vm.indexOf('Do not let the optimistic local UI start a replacement session');
const optimisticStart = vm.indexOf('repository.insertStationStateLocal(optimisticState)', startGate);
assert(startGate >= 0 && optimisticStart > startGate,
  'new session must reconcile pending prior settlement before optimistic local start');
assert(vm.includes('httpCode !in 200..299'),
  'new session must be blocked unless prior settlement receives a confirmed successful HTTP response');
assert(vm.includes('if (pendingStation?.optString("sessionId") == sessionId)') && vm.includes('repository.saveSetting(pendingStationKey, "")'),
  'successful stop settlement must clear its matching station-level pending marker');
assert(vm.includes('Starting a NEW session from a paused station') &&
  vm.includes('if (!isTrialUser && station.status == "PAUSED")') &&
  vm.includes('flushSessionOutbox()') &&
  vm.includes('queueOrSettleSession(priorSessionId, stationId, endedAt)') &&
  vm.includes('httpCode !in 200..299'),
  'starting a new session from a paused station must flush its segment events and confirm prior-session settlement before replacing the session');
assert(vm.includes('elapsedPlayingTimeMillis = if (commitSegment) 0L else elapsed') &&
  vm.includes('double-counts time after pause/resume'),
  'ordinary pause must preserve accumulated current-segment time exactly once; segment commit must reset it');
assert(vm.includes('overrides.values.firstOrNull') && vm.includes('Room/server refresh can re-key a legacy transaction'),
  'finalized invoice status must reconcile by session + payer identity when numeric local IDs change');
assert(vm.includes('decisionCustomerId > 0L') && vm.includes('decisionCustomerName'),
  'invoice UI overlay must match a finalization result by payer identity when the local transaction ID is unavailable');
const historyStart = routes.indexOf("app.get('/api/v1/customer/session-history'");
assert(historyStart >= 0, 'customer session-history endpoint must exist');
const historyEnd = routes.indexOf("app.get('/api/v1/customer/reservations'", historyStart);
const historyRoute = routes.slice(historyStart, historyEnd);
assert(historyRoute.includes('requireCustomerAuth') && historyRoute.includes("gs.status NOT IN ('ACTIVE','PAUSED')"),
  'history must be authenticated as the customer and must not duplicate active sessions');
assert(historyRoute.includes('session_participants') && historyRoute.includes('controller_count') &&
  historyRoute.includes('session_events') && historyRoute.includes("se.payload->'customerIds'") && historyRoute.includes('session_orders') &&
  historyRoute.includes('ORDER BY COALESCE(gs.ended_at,gs.started_at) DESC'),
  'history must return distinct sessions with controllers, participant roster, timeline events and buffet orders');
assert(managerApi.includes('/api/v1/customer/session-history') && managerApi.includes('fetchCustomerSessionHistory'),
  'Android must load history using the customer-scoped endpoint');
assert(customerUi.includes('سابقه نشست‌های شما') && customerUi.includes('هم‌بازی‌ها:') &&
  customerUi.includes('اتفاقات نشست:') && customerUi.includes('history.controllerCount'),
  'customer profile must show active session above historical sessions with per-session details');
assert(customerUi.includes('committedSegmentGameCost') && customerUi.includes('currentSegmentGameCost') &&
  customerUi.includes('بخش‌های ثبت‌شده + جاری') && customerUi.includes('cloudSt?.committedSegmentCost'),
  'customer live profile must show accumulated committed segments separately from the resettable current segment');
assert(managerApi.includes('committedSegmentCost = obj.optLong("committedSegmentCost"') &&
  managerApi.includes('elapsedPlayingTimeMillis = obj.optLong("elapsedPlayingTimeMillis"'),
  'customer client must parse the server segment aggregate and current-segment timer');
assert(customerUi.includes('sessionHistoryExpanded by remember(customer.id)') &&
  customerUi.includes('clickable { sessionHistoryExpanded = !sessionHistoryExpanded }') &&
  customerUi.includes('AnimatedVisibility(visible = sessionHistoryExpanded)') &&
  customerUi.includes('Icons.Default.KeyboardArrowDown'),
  'customer session history must be collapsed by default and expandable from a downward-arrow header');
console.log('PASS: customer live-session identity, customer session history, per-session participants/controller counts/timeline, shared buffet visibility/allocation, and invoice reconciliation');
