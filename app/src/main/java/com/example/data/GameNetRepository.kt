package com.example.data

import com.example.data.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class GameNetRepository(private val db: AppDatabase) {
    private val stationStateDao = db.stationStateDao()

    suspend fun getAuthoritativeServerTime(): Long? = withContext(Dispatchers.IO) {
        try {
            NetworkClient.getApi().getServerTime().serverTime
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getAppSetting(key: String): String? = withContext(Dispatchers.IO) {
        appSettingDao.getValue(key)
    }

    suspend fun saveAppSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        appSettingDao.insert(AppSetting(key = key, value = value))
    }

    suspend fun getAllAppSettings(): List<AppSetting> = withContext(Dispatchers.IO) {
        appSettingDao.getAllSync()
    }

    private val consoleTypeDao = db.consoleTypeDao()
    private val productDao = db.productDao()
    private val stationOrderDao = db.stationOrderDao()
    private val sessionHistoryDao = db.sessionHistoryDao()
    private val appSettingDao = db.appSettingDao()
    val customerDao = db.customerDao()
    private val reservationDao = db.reservationDao()
    private val licenseCacheDao = db.licenseCacheDao()
    private val inviteCodeRecordDao = db.inviteCodeRecordDao()
    private val pointLogDao = db.pointLogDao()
    private val operatorAuditLogDao = db.operatorAuditLogDao()
    private val gnLedgerDao = db.gnLedgerDao()
    private val behaviorRuleDao = db.behaviorRuleDao()
    private val behaviorLogDao = db.behaviorLogDao()
    private val referralProgressRecordDao = db.referralProgressRecordDao()

    val allOperatorAuditLogs: Flow<List<OperatorAuditLog>> = operatorAuditLogDao.getAll()
    val allGnLedgerEntries: Flow<List<GnLedgerEntry>> = gnLedgerDao.getAllLedgerEntries()
    val allBehaviorRules: Flow<List<BehaviorRule>> = behaviorRuleDao.getAllRules()
    val allBehaviorLogs: Flow<List<BehaviorLog>> = behaviorLogDao.getAllLogs()

    // Authentication logout must never erase business data. Explicit reset/delete flows are responsible for destructive data removal.
    suspend fun clearAllDataExceptSettings() {
        // Kept for compatibility with older callers. Intentionally a no-op.
    }

    fun getGnLedgerForCustomer(customerId: Long): Flow<List<GnLedgerEntry>> = gnLedgerDao.getByCustomerId(customerId)

    suspend fun getGnLedgerEntryByRef(refId: String): GnLedgerEntry? = gnLedgerDao.getByReferenceId(refId)

    suspend fun addGnLedgerEntry(entry: GnLedgerEntry): Long {
        val id = gnLedgerDao.insert(entry)
        try {
            com.example.data.network.SelfHostedManager.addGnLedgerEntry(entry.copy(id = id))
        } catch (ignored: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", ignored) }
        return id
    }

    suspend fun updateGnLedgerEntry(entry: GnLedgerEntry) {
        gnLedgerDao.update(entry)
        try {
            com.example.data.network.SelfHostedManager.addGnLedgerEntry(entry)
        } catch (ignored: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", ignored) }
    }

    suspend fun getAllBehaviorRulesList(): List<BehaviorRule> = behaviorRuleDao.getAllRulesList()

    suspend fun addBehaviorRule(rule: BehaviorRule): Long = behaviorRuleDao.insert(rule)

    suspend fun updateBehaviorRule(rule: BehaviorRule) = behaviorRuleDao.update(rule)

    suspend fun deleteBehaviorRule(rule: BehaviorRule) = behaviorRuleDao.delete(rule)

    fun getBehaviorLogsForCustomer(customerId: Long): Flow<List<BehaviorLog>> = behaviorLogDao.getByCustomerId(customerId)

    suspend fun addBehaviorLog(log: BehaviorLog): Long = behaviorLogDao.insert(log)

    fun getReferralRecordsForReferrer(referrerId: Long): Flow<List<ReferralProgressRecord>> = referralProgressRecordDao.getByReferrerId(referrerId)

    suspend fun getReferralRecordForReferred(referredId: Long): ReferralProgressRecord? = referralProgressRecordDao.getByReferredId(referredId)

    suspend fun addReferralRecord(record: ReferralProgressRecord): Long = referralProgressRecordDao.insert(record)

    suspend fun updateReferralRecord(record: ReferralProgressRecord) = referralProgressRecordDao.update(record)

    suspend fun addOperatorAuditLog(log: OperatorAuditLog): Long {
        return operatorAuditLogDao.insert(log)
    }

    suspend fun getRecentAuditLogs(): List<OperatorAuditLog> {
        return operatorAuditLogDao.getRecentList()
    }

    suspend fun clearOperatorAuditLogs() {
        operatorAuditLogDao.clearAll()
    }

    fun getPointLogs(customerId: Long): Flow<List<PointLog>> {
        return pointLogDao.getLogsByCustomerId(customerId)
    }

    fun getAllPointLogs(): Flow<List<PointLog>> {
        return pointLogDao.getAllLogs()
    }

    suspend fun addPointLog(log: PointLog) {
        pointLogDao.insert(log)
        if (com.example.data.network.NetworkClient.isTrialMode) return
        try {
            com.example.data.network.SelfHostedManager.addPointLog(log)
        } catch (ignored: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", ignored) }
    }

    suspend fun getInviteCodeRecord(phone: String, name: String): InviteCodeRecord? {
        return inviteCodeRecordDao.getRecord(phone, name)
    }

    suspend fun saveInviteCodeRecord(record: InviteCodeRecord) {
        inviteCodeRecordDao.insert(record)
    }

    // Server-sync helpers
    suspend fun isSyncModeEnabled(): Boolean {
        return appSettingDao.getValue("server_sync_mode")?.toBoolean() ?: false
    }

    suspend fun getServerUrl(): String {
        val current = appSettingDao.getValue("server_url")
        val secureDefault = "https://api.gamenermayket.ir"
        saveSetting("server_url", secureDefault)
        return "$secureDefault/"
    }

    suspend fun getApi(): com.example.data.network.GameNetApi? {
        return try {
            val url = getServerUrl()
            NetworkClient.getApi(url)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun syncAllWithServer(): Boolean {
        if (!isSyncModeEnabled()) return false
        // Never perform Manager cloud synchronization before a server-authenticated
        // Manager session exists. This prevents startup 401s and false Offline state.
        if (com.example.data.network.NetworkClient.authToken.isNullOrBlank() ||
            com.example.data.network.SelfHostedManager.currentManagerId.isBlank()) return false
        val api = getApi() ?: return false
        var anySyncSucceeded = false

        // 1. Consoles
        try {
            val remoteConsoles = api.getConsoleTypes()
            if (remoteConsoles.isNotEmpty()) {
                consoleTypeDao.clearAll()
                for (c in remoteConsoles) {
                    consoleTypeDao.insert(c)
                }
            } else {
                val localConsoles = consoleTypeDao.getAll().firstOrNull() ?: emptyList()
                val defaultConsoles = listOf(
                    ConsoleType("PlayStation 5", 180000L, 220000L, 250000L, 280000L),
                    ConsoleType("PlayStation 4", 120000L, 140000L, 150000L, 180000L),
                    ConsoleType("شبیه ساز رانندگی", 220000L, 220000L, 220000L, 220000L),
                    ConsoleType("Xbox Series X", 180000L, 220000L, 250000L, 280000L)
                )
                val consolesToSync = if (localConsoles.isNotEmpty()) localConsoles else defaultConsoles
                if (localConsoles.isEmpty()) {
                    for (c in defaultConsoles) consoleTypeDao.insert(c)
                }
                for (c in consolesToSync) {
                    try { api.saveConsoleType(c) } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", e) }
                }
            }
            anySyncSucceeded = true
        } catch (e: Exception) { e.printStackTrace() }

        // 2. Products
        try {
            val remoteProducts = api.getProducts()
            if (remoteProducts.isNotEmpty()) {
                productDao.clearAll()
                for (p in remoteProducts) {
                    productDao.insert(p)
                }
            } else {
                val localProducts = productDao.getAll().firstOrNull() ?: emptyList()
                val defaultProducts = listOf(
                    Product("انرژیزا تی ان تی", 110000L),
                    Product("هایپ", 130000L),
                    Product("ردبول", 140000L),
                    Product("بلوبری", 68000L),
                    Product("لیموناد", 70000L),
                    Product("ویتامین سی", 70000L),
                    Product("کروسان", 50000L),
                    Product("کیک باباجون", 50000L),
                    Product("کیک دو قلو", 40000L),
                    Product("مغز بادام و تخمه", 50000L),
                    Product("آبمیوه", 30000L),
                    Product("آبمعدنی", 15000L),
                    Product("چیپس", 75000L),
                    Product("رانی", 45000L),
                    Product("نسکافه و قهوه", 40000L),
                    Product("چای", 20000L),
                    Product("اسنک و ساندویچ گرم", 85000L)
                )
                val prodsToSync = if (localProducts.isNotEmpty()) localProducts else defaultProducts
                if (localProducts.isEmpty()) {
                    for (p in defaultProducts) productDao.insert(p)
                }
                for (p in prodsToSync) {
                    try { api.saveProduct(p) } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", e) }
                }
            }
            anySyncSucceeded = true
        } catch (e: Exception) { e.printStackTrace() }

        // 3. Stations
        try {
            // station_count is an explicit Manager configuration and is authoritative.
            // Never let the physical station-row count (including stale legacy rows)
            // silently replace the configured hall capacity after app relaunch.
            try {
                val remoteConfiguredCount = com.example.data.network.SelfHostedManager
                    .fetchAppConfig("station_count")
                    ?.toIntOrNull()
                if (remoteConfiguredCount != null && remoteConfiguredCount > 0) {
                    appSettingDao.insert(AppSetting("station_count", remoteConfiguredCount.toString()))
                }
            } catch (ignored: Exception) {
                android.util.Log.w("GameNexa", "Could not refresh authoritative station_count", ignored)
            }
            val configuredStationCount = appSettingDao.getValue("station_count")
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
            val remoteStationsRaw = api.getStations()
            val remoteStations = if (configuredStationCount != null) {
                remoteStationsRaw.sortedBy { it.id }.take(configuredStationCount)
            } else {
                remoteStationsRaw
            }
            if (remoteStations.isNotEmpty()) {
                stationStateDao.clearAll()
                stationStateDao.insertAll(remoteStations)
                // Server is authoritative. Clear the complete local buffet-order cache first.
                // A FREE/disabled station must never display or upload an old order.
                for (st in remoteStations) stationOrderDao.clearForStation(st.id)
                // Station rows are the physical resources; station_count is an explicit Manager setting.
                // Do not overwrite the Manager setting from a transient/legacy station-row count.
                for (st in remoteStations) {
                    if (st.status == "FREE" || st.status == "DISABLED") continue
                    try {
                        val orders = api.getOrders(st.id)
                        if (orders.isNotEmpty()) stationOrderDao.insertAll(orders)
                    } catch (ignored: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", ignored) }
                }
            } else {
                val localStations = stationStateDao.getAll().firstOrNull() ?: emptyList()
                if (localStations.isNotEmpty()) {
                    for (st in localStations) {
                        try { api.saveStation(st) } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", e) }
                    }
                } else {
                    val firstConsole = consoleTypeDao.getAll().firstOrNull()?.firstOrNull()?.name ?: "PlayStation 5"
                    recreateStations(10, firstConsole)
                    val created = stationStateDao.getAll().firstOrNull() ?: emptyList()
                    for (st in created) {
                        try { api.saveStation(st) } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", e) }
                    }
                }
            }
            anySyncSucceeded = true
        } catch (e: Exception) { e.printStackTrace() }

        // 4. Customers — server-authoritative reconciliation.
        // A successful GET is authoritative even when it returns an empty list.
        // Do not leave archived/deleted customers visible in Room after sync.
        if (!com.example.data.network.NetworkClient.isTrialMode) {
            try {
                val remoteCustomers = api.getCustomers()
                val localCustomers = customerDao.getAllList()
                for (remote in remoteCustomers) {
                    val existing = localCustomers.find { it.phoneNumber == remote.phoneNumber || it.id == remote.id }
                    if (existing != null) {
                        customerDao.insert(remote.copy(id = existing.id))
                    } else {
                        customerDao.insert(remote)
                    }
                }

                val remotePhones = remoteCustomers.map { it.phoneNumber.trim() }.filter { it.isNotBlank() }.toSet()
                if (remoteCustomers.isEmpty()) {
                    customerDao.clearAll()
                } else if (remotePhones.isNotEmpty()) {
                    customerDao.deleteCustomersMissingFromServerPhones(remotePhones.toList())
                }
                anySyncSucceeded = true
            } catch (e: Exception) { e.printStackTrace() }
        }

        // 5. Reservations
        try {
            val remoteReservations = api.getReservations()
            if (remoteReservations.isNotEmpty()) {
                reservationDao.clearAll()
                for (res in remoteReservations) {
                    reservationDao.insert(res)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // 6. History
        try {
            val remoteHistory = api.getSessionHistory()
            if (remoteHistory.isNotEmpty()) {
                for (h in remoteHistory) {
                    sessionHistoryDao.insert(h)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        return anySyncSucceeded
    }

    // Station States
    val allStationStates: Flow<List<StationState>> = stationStateDao.getAll()
    suspend fun getStationStateByIdLocal(id: Int): StationState? = stationStateDao.getById(id)
    suspend fun getStationStateById(id: Int): StationState? {
        if (isSyncModeEnabled()) {
            try {
                // Online reads are server-authoritative. Only fall back to Room when
                // the server read actually fails, so remote status/pricing changes
                // cannot remain hidden behind a stale local row.
                val remote = getApi()?.getStations()?.find { it.id == id }
                if (remote != null) {
                    val local = stationStateDao.getById(id)
                    // /manager/stations is a configuration resource, not the live
                    // session resource. Its status is derived from `active` and is
                    // therefore FREE for an enabled station. Never let that config
                    // response overwrite a locally tracked RUNNING/PAUSED session.
                    val merged = if (local != null && local.status in setOf("RUNNING", "PAUSED") && remote.status == "FREE") {
                        local.copy(
                            controllerCount = remote.controllerCount,
                            consoleType = remote.consoleType.ifBlank { local.consoleType }
                        )
                    } else {
                        remote
                    }
                    stationStateDao.insert(merged)
                    return merged
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return stationStateDao.getById(id)
    }

    // Local cache writes used by server-authoritative hydration. These methods deliberately
    // do not call the network; hydration must never echo server data back as a mutation.
    suspend fun insertStationStateLocal(state: StationState) {
        stationStateDao.insert(state)
    }

    suspend fun insertStationState(state: StationState) {
        stationStateDao.insert(state)
        // IMPORTANT: station state and buffet orders are separate server resources.
        // Never mirror local Room orders while saving station metadata; a stale local
        // order must not be resurrected merely because a station timer/settings changed.
        if (isSyncModeEnabled()) {
            try {
                getApi()?.saveStation(state)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun insertStationStates(states: List<StationState>) {
        stationStateDao.insertAll(states)
        // Buffet orders are synchronized only by explicit order operations.
        // Saving station metadata must never upload stale local order rows.
        if (isSyncModeEnabled()) {
            try {
                val api = getApi()
                for (state in states) {
                    api?.saveStation(state)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun clearAllStationStates() {
        stationStateDao.clearAll()
    }

    // Console Types
    val allConsoleTypes: Flow<List<ConsoleType>> = consoleTypeDao.getAll()
    suspend fun getConsoleTypeByName(name: String) = consoleTypeDao.getByName(name)
    suspend fun insertConsoleType(consoleType: ConsoleType) {
        consoleTypeDao.insert(consoleType)
        if (isSyncModeEnabled()) {
            try {
                getApi()?.saveConsoleType(consoleType)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun deleteConsoleType(name: String) {
        consoleTypeDao.deleteByName(name)
        if (isSyncModeEnabled()) {
            try {
                getApi()?.deleteConsoleType(name)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun clearConsoleTypes() = consoleTypeDao.clearAll()

    // Products
    val allProducts: Flow<List<Product>> = productDao.getAll()
    suspend fun getProductByName(name: String) = productDao.getByName(name)
    suspend fun insertProduct(product: Product) {
        productDao.insert(product)
        if (isSyncModeEnabled()) {
            try {
                getApi()?.saveProduct(product)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun deleteProduct(name: String) {
        productDao.deleteByName(name)
        if (isSyncModeEnabled()) {
            try {
                getApi()?.deleteProduct(name)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun clearProducts() = productDao.clearAll()

    // Station Orders
    fun getOrdersForStation(stationId: Int): Flow<List<StationOrder>> = stationOrderDao.getOrdersForStation(stationId)
    suspend fun getOrdersForStationSync(stationId: Int): List<StationOrder> {
        return stationOrderDao.getOrdersForStationSync(stationId)
    }

    suspend fun syncStationOrdersToCloud(stationId: Int) {
        try {
            val state = getStationStateByIdLocal(stationId) ?: return
            val orders = getOrdersForStationSync(stationId)
            val ordersArray = org.json.JSONArray()
            for (ord in orders) {
                val p = getProductByName(ord.productName)
                val pJson = org.json.JSONObject()
                pJson.put("product_name", ord.productName)
                pJson.put("quantity", ord.quantity)
                pJson.put("price", p?.price ?: 0L)
                pJson.put("target_customer_id", ord.targetCustomerId ?: 0L)
                pJson.put("target_customer_name", ord.targetCustomerName ?: "")
                ordersArray.put(pJson)
            }
            com.example.data.network.SelfHostedManager.syncStationToCloud(state, ordersArray.toString())
        } catch (ignored: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", ignored) }
    }

    suspend fun insertStationOrderLocal(order: StationOrder) {
        stationOrderDao.insert(order)
    }

    suspend fun clearOrdersForStationLocal(stationId: Int) {
        stationOrderDao.clearForStation(stationId)
    }

    suspend fun insertStationOrder(order: StationOrder) {
        stationOrderDao.insert(order)
        syncStationOrdersToCloud(order.stationId)
        if (isSyncModeEnabled()) {
            try {
                getApi()?.saveOrder(order)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun deleteStationOrder(id: String, stationId: Int? = null) {
        stationOrderDao.deleteById(id)
        if (stationId != null) {
            syncStationOrdersToCloud(stationId)
        }
        if (isSyncModeEnabled()) {
            try {
                getApi()?.deleteOrder(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun clearOrdersForStation(stationId: Int) {
        stationOrderDao.clearForStation(stationId)
        syncStationOrdersToCloud(stationId)
        if (isSyncModeEnabled()) {
            try {
                getApi()?.clearOrders(stationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Session History
    val allHistory: Flow<List<SessionHistory>> = sessionHistoryDao.getAll()
    suspend fun getAllHistorySync(): List<SessionHistory> {
        if (isSyncModeEnabled()) {
            try {
                val remote = getApi()?.getSessionHistory()
                if (remote != null) {
                    sessionHistoryDao.clearAll()
                    for (h in remote) {
                        sessionHistoryDao.insert(h)
                    }
                    return remote
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return sessionHistoryDao.getAllSync()
    }

    suspend fun insertSessionHistory(history: SessionHistory) {
        sessionHistoryDao.insert(history)
        if (isSyncModeEnabled()) {
            try {
                getApi()?.addSessionHistory(history)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun clearHistory() {
        sessionHistoryDao.clearAll()
        if (isSyncModeEnabled()) {
            try {
                getApi()?.clearSessionHistory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // App Settings
    suspend fun getSetting(key: String): String? = appSettingDao.getValue(key)
    suspend fun saveSetting(key: String, value: String) {
        appSettingDao.insert(AppSetting(key, value))
        if (isSyncModeEnabled() && key != "server_sync_mode" && key != "server_url" &&
            !com.example.data.network.NetworkClient.authToken.isNullOrBlank() &&
            com.example.data.network.SelfHostedManager.currentManagerId.isNotBlank()) {
            try {
                com.example.data.network.SelfHostedManager.saveManagerSetting(key, value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Pre-populate data if empty
    suspend fun initializeDatabaseIfEmpty() {
        // Check if settings has the station count
        val stationCountSetting = getSetting("station_count")
        if (stationCountSetting == null) {
            // Save default setting
            saveSetting("station_count", "10")
            saveSetting("notifications_enabled", "true")
            saveSetting("server_sync_mode", "true")
            saveSetting("server_url", "https://api.gamenermayket.ir")

            // Initialize console types
            val defaultConsoles = listOf(
                ConsoleType("PlayStation 5", 180000L, 220000L, 250000L, 280000L),
                ConsoleType("PlayStation 4", 120000L, 140000L, 150000L, 180000L),
                ConsoleType("شبیه ساز رانندگی", 220000L, 220000L, 220000L, 220000L),
                ConsoleType("Xbox Series X", 180000L, 220000L, 250000L, 280000L)
            )
            for (c in defaultConsoles) {
                insertConsoleType(c)
            }

            // Initialize products (full buffet menu)
            val defaultProducts = listOf(
                Product("انرژیزا تی ان تی", 110000L),
                Product("هایپ", 130000L),
                Product("ردبول", 140000L),
                Product("بلوبری", 68000L),
                Product("لیموناد", 70000L),
                Product("ویتامین سی", 70000L),
                Product("کروسان", 50000L),
                Product("کیک باباجون", 50000L),
                Product("کیک دو قلو", 40000L),
                Product("مغز بادام و تخمه", 50000L),
                Product("آبمیوه", 30000L),
                Product("آبمعدنی", 15000L),
                Product("چیپس", 75000L),
                Product("رانی", 45000L),
                Product("نسکافه و قهوه", 40000L),
                Product("چای", 20000L),
                Product("اسنک و ساندویچ گرم", 85000L)
            )
            for (p in defaultProducts) {
                insertProduct(p)
            }

            // Create 10 station states
            recreateStations(10, defaultConsoles.first().name)
            
            saveSetting("gn_payment_cards", "[]")
            saveSetting("gn_payment_gateways", "[]")
            saveSetting("gn_payment_cryptos", "[]")
            saveSetting("gn_contact_sms", "09395773183")
            saveSetting("gn_contact_bale", "@Real_MimKhas")
            saveSetting("gn_game_payment_ratio", "0.3")
            saveSetting("gn_buffet_payment_ratio", "0.5")
            saveSetting("gn_to_toman_ratio", "1000")

            saveSetting("defaults_v2_applied_v3", "true")
        } else {
            // Ensure we have sync settings initialized and securely migrated
            if (getSetting("server_sync_mode") == null) {
                saveSetting("server_sync_mode", "true")
            }
            val existingUrl = getSetting("server_url")
            saveSetting("server_url", "https://api.gamenermayket.ir")
            ensureConsolesAndProductsExist()
        }
    }

        suspend fun ensureConsolesAndProductsExist() {
        val consoles = consoleTypeDao.getAll().firstOrNull() ?: emptyList()
        if (consoles.isEmpty()) {
            val defaultConsoles = listOf(
                ConsoleType("PlayStation 5", 180000L, 220000L, 250000L, 280000L),
                ConsoleType("PlayStation 4", 120000L, 140000L, 150000L, 180000L),
                ConsoleType("شبیه ساز رانندگی", 220000L, 220000L, 220000L, 220000L),
                ConsoleType("Xbox Series X", 180000L, 220000L, 250000L, 280000L)
            )
            for (c in defaultConsoles) {
                insertConsoleType(c)
            }
        }
        val prods = productDao.getAll().firstOrNull() ?: emptyList()
        if (prods.isEmpty()) {
            val defaultProducts = listOf(
                Product("انرژیزا تی ان تی", 110000L),
                Product("هایپ", 130000L),
                Product("ردبول", 140000L),
                Product("بلوبری", 68000L),
                Product("لیموناد", 70000L),
                Product("ویتامین سی", 70000L),
                Product("کروسان", 50000L),
                Product("کیک باباجون", 50000L),
                Product("کیک دو قلو", 40000L),
                Product("مغز بادام و تخمه", 50000L),
                Product("آبمیوه", 30000L),
                Product("آبمعدنی", 15000L),
                Product("چیپس", 75000L),
                Product("رانی", 45000L),
                Product("نسکافه و قهوه", 40000L),
                Product("چای", 20000L),
                Product("اسنک و ساندویچ گرم", 85000L)
            )
            for (p in defaultProducts) {
                insertProduct(p)
            }
        }
        
        val hasStations = stationStateDao.getAll().firstOrNull()?.isNotEmpty() == true
        if (!hasStations) {
            val count = getSetting("station_count")?.toIntOrNull() ?: 10
            val firstConsole = consoleTypeDao.getAll().firstOrNull()?.firstOrNull()?.name ?: "PlayStation 5"
            recreateStations(count, firstConsole)
        }
    }

    suspend fun ensureTrialDataExists() {
        // Trial must never destroy or overwrite paid Manager-local data.
        // If the fresh installation has no stations yet, create the two Trial stations;
        // otherwise the existing Manager stations remain untouched.
        if (stationStateDao.getAll().firstOrNull()?.isEmpty() != false) {
            stationStateDao.insertAll(listOf(
                StationState(id = 1, controllerCount = 4, consoleType = "PlayStation 5"),
                StationState(id = 2, controllerCount = 4, consoleType = "PlayStation 5")
            ))
        }

        if (productDao.getAll().firstOrNull()?.isEmpty() != false) {
            listOf(
                Product("انرژیزا تی ان تی", 110000L),
                Product("هایپ", 130000L),
                Product("آبمعدنی", 15000L)
            ).forEach { insertProduct(it) }
        }

        // Trial customer state lives in Room and survives process recreation/reload.
        if (customerDao.getAllList().isEmpty()) {
            customerDao.insertAll(listOf(
                Customer(id = 1L, fullName = "مشتری تستی 1", phoneNumber = "09120000001", description = "__GN_TRIAL_TEST_CONTACT__"),
                Customer(id = 2L, fullName = "مشتری تستی 2", phoneNumber = "09120000002", credit = 50000L, description = "__GN_TRIAL_TEST_CONTACT__"),
                Customer(id = 3L, fullName = "مشتری تستی 3", phoneNumber = "09120000003", debt = 35000L, description = "__GN_TRIAL_TEST_CONTACT__"),
                Customer(id = 4L, fullName = "مشتری تستی 4", phoneNumber = "09120000004", description = "__GN_TRIAL_TEST_CONTACT__")
            ))
        }
    }
    suspend fun recreateStations(count: Int, defaultConsole: String) {
        // Find existing ones
        val currentStates = stationStateDao.getAll().firstOrNull() ?: emptyList()
        val currentMap = currentStates.associateBy { it.id }

        val newStates = ArrayList<StationState>()
        for (id in 1..count) {
            val existing = currentMap[id]
            if (existing != null) {
                newStates.add(existing)
            } else {
                newStates.add(StationState(id = id, consoleType = defaultConsole))
            }
        }

        // Clear current states and insert the exact count
        stationStateDao.clearAll()
        stationStateDao.insertAll(newStates)
    }

    // Customers
    fun getMockTrialCustomers(): List<Customer> {
        // Compatibility only; Trial UI now reads the persistent Room customer table.
        return emptyList()
    }
    val allCustomers: Flow<List<Customer>> = flow {
        customerDao.getAll().collect { emit(it) }
    }
    suspend fun getAllCustomersLocal(): List<Customer> = customerDao.getAllList()
    suspend fun getCustomerById(id: Long): Customer? {
        if (com.example.data.network.NetworkClient.isTrialMode) {
            return customerDao.getById(id)
        }
        if (isSyncModeEnabled()) {
            try {
                val remote = getApi()?.getCustomers()?.find { it.id == id }
                if (remote != null) {
                    customerDao.insert(remote)
                    return remote
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return customerDao.getById(id)
    }

    suspend fun insertLocalCustomers(customers: List<Customer>) {
        if (com.example.data.network.NetworkClient.isTrialMode) return
        if (customers.isEmpty()) return
        val existingLocal = customerDao.getAllList()
        for (cloud in customers) {
            val trimmedPhone = cloud.phoneNumber.trim()
            val trimmedName = cloud.fullName.trim()

            val matchByPhone = if (trimmedPhone.isNotBlank()) existingLocal.find { it.phoneNumber.trim() == trimmedPhone } else null
            val matchById = existingLocal.find { it.id == cloud.id && cloud.id > 0L }
            val matchByName = if (trimmedName.isNotBlank()) existingLocal.find { it.fullName.trim().equals(trimmedName, ignoreCase = true) } else null

            val targetLocal = matchByPhone ?: matchById ?: matchByName
            if (targetLocal != null) {
                val updated = targetLocal.copy(
                    fullName = if (cloud.fullName.isNotBlank()) cloud.fullName else targetLocal.fullName,
                    phoneNumber = if (cloud.phoneNumber.isNotBlank()) cloud.phoneNumber else targetLocal.phoneNumber,
                    debt = cloud.debt,
                    credit = cloud.credit,
                    points = cloud.points,
                    availableGn = cloud.availableGn,
                    pendingGn = cloud.pendingGn,
                    lp = cloud.lp,
                    tier = if (cloud.tier.isNotBlank()) cloud.tier else targetLocal.tier,
                    lastActivityTimestamp = cloud.lastActivityTimestamp,
                    totalQualifiedSpend = cloud.totalQualifiedSpend,
                    totalVisitsCount = cloud.totalVisitsCount,
                    lastTierReviewTimestamp = cloud.lastTierReviewTimestamp,
                    inviteCode = if (cloud.inviteCode.isNotBlank()) cloud.inviteCode else targetLocal.inviteCode,
                    invitedByCode = if (cloud.invitedByCode.isNotBlank()) cloud.invitedByCode else targetLocal.invitedByCode,
                    invitePointsAwarded = cloud.invitePointsAwarded,
                    rewardsConsumed = cloud.rewardsConsumed,
                    description = if (cloud.description.isNotBlank()) cloud.description else targetLocal.description
                )
                customerDao.insert(updated)
            } else {
                customerDao.insert(cloud)
            }
        }

        // Deduplicate local customers by phone number
        val allCurrent = customerDao.getAllList()
        val groupedByPhone = allCurrent.filter { it.phoneNumber.isNotBlank() }.groupBy { it.phoneNumber.trim() }
        for (entry in groupedByPhone) {
            val group = entry.value
            if (group.size > 1) {
                val sorted = group.sortedByDescending { (if (it.credit > 0 || it.debt > 0) 5 else 0) - it.id }
                val toDelete = sorted.drop(1)
                for (dupe in toDelete) {
                    customerDao.delete(dupe)
                }
            }
        }
    }

    suspend fun insertCustomer(customer: Customer): Long {
        val localId = customerDao.insert(customer)
        val finalCust = if (customer.id == 0L) customer.copy(id = localId) else customer
        if (com.example.data.network.NetworkClient.isTrialMode) return if (customer.id == 0L) localId else customer.id
        try {
            com.example.data.network.SelfHostedManager.upsertCustomer(finalCust)
        } catch (ignored: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", ignored) }
        if (isSyncModeEnabled()) {
            try {
                getApi()?.saveCustomer(finalCust)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return localId
    }

    suspend fun deleteCustomer(customer: Customer) = withContext(Dispatchers.IO) {
        if (com.example.data.network.NetworkClient.isTrialMode) return@withContext
        try {
            // Normal customer deletion is an archive, not a purge. Keep every local
            // reservation, invoice/transaction, point and GN record intact; only remove the
            // active customer row from the active directory.
            customerDao.delete(customer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            com.example.data.network.SelfHostedManager.deleteCustomer(customer.id, customer.phoneNumber)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (isSyncModeEnabled()) {
            try {
                getApi()?.deleteCustomer(customer.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun purgeCustomerLocally(customer: Customer) = withContext(Dispatchers.IO) {
        if (com.example.data.network.NetworkClient.isTrialMode) return@withContext
        val originalPhone = Regex("original_phone=([^ ]+)").find(customer.description)?.groupValues?.getOrNull(1).orEmpty()
        customerDao.delete(customer)
        customerTransactionDao.deleteByCustomerId(customer.id)
        pointLogDao.deleteByCustomerId(customer.id)
        gnLedgerDao.deleteByCustomerId(customer.id)
        behaviorLogDao.deleteByCustomerId(customer.id)
        referralProgressRecordDao.deleteByCustomerId(customer.id)
        if (customer.phoneNumber.isNotBlank()) reservationDao.deleteByPhone(customer.phoneNumber)
        if (originalPhone.isNotBlank()) reservationDao.deleteByPhone(originalPhone)
    }

    suspend fun deleteCustomersBatch(customers: List<Customer>) = withContext(Dispatchers.IO) {
        if (com.example.data.network.NetworkClient.isTrialMode) return@withContext
        customers.forEach { customer ->
            try {
                customerDao.delete(customer)
                customerTransactionDao.deleteByCustomerId(customer.id)
                if (customer.phoneNumber.isNotBlank()) {
                    reservationDao.deleteByPhone(customer.phoneNumber)
                }
                pointLogDao.deleteByCustomerId(customer.id)
                gnLedgerDao.deleteByCustomerId(customer.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        try {
            val ids = customers.map { it.id }
            com.example.data.network.SelfHostedManager.deleteCustomersBatch(ids)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (isSyncModeEnabled()) {
            try {
                val ids = customers.map { it.id }
                getApi()?.deleteCustomerBatch(mapOf("customerIds" to ids))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun clearCustomers() {
        // customerDao.clearAll() // Removed to prevent wiping unsynced local customers
    }

    // Reservations
    val allReservations: Flow<List<Reservation>> = reservationDao.getAll()
    suspend fun getReservationById(id: Long): Reservation? {
        if (isSyncModeEnabled()) {
            try {
                val remote = getApi()?.getReservations()?.find { it.id == id }
                if (remote != null) {
                    reservationDao.insert(remote)
                    return remote
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return reservationDao.getById(id)
    }

    suspend fun insertReservation(reservation: Reservation): Long {
        val localId = reservationDao.insert(reservation)
        // SelfHostedManager will handle the sync explicitly in ViewModel
        return localId
    }

    suspend fun deleteReservation(reservation: Reservation) {
        reservationDao.delete(reservation)
        if (isSyncModeEnabled()) {
            try {
                getApi()?.deleteReservation(reservation.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun clearReservations() {
        reservationDao.clearAll()
    }

    private val customerTransactionDao = db.customerTransactionDao()

    // Customer Transactions
    val allCustomerTransactions: Flow<List<CustomerTransaction>> = flow {
        if (com.example.data.network.NetworkClient.isTrialMode) {
            emit(emptyList())
        } else {
            customerTransactionDao.getAll().collect { emit(it) }
        }
    }
    suspend fun getAllCustomerTransactionsLocal(): List<CustomerTransaction> = customerTransactionDao.getAllList()
    suspend fun syncCustomerTransactionsFromServer(): Boolean = withContext(Dispatchers.IO) {
        val remote = com.example.data.network.SelfHostedManager.fetchManagerCustomerTransactions()
            ?: return@withContext false
        // Replace local history only after a successful authenticated response.
        customerTransactionDao.clearAll()
        if (remote.isNotEmpty()) customerTransactionDao.insertAll(remote)
        true
    }


    fun getTransactionsByCustomerId(customerId: Long): Flow<List<CustomerTransaction>> =
        customerTransactionDao.getByCustomerId(customerId)

    suspend fun insertCustomerTransaction(transaction: CustomerTransaction): Long =
        customerTransactionDao.insert(transaction)

    suspend fun updateCustomerTransaction(transaction: CustomerTransaction) =
        customerTransactionDao.update(transaction)

    suspend fun deleteCustomerTransactionLocal(transaction: CustomerTransaction) = withContext(Dispatchers.IO) {
        customerTransactionDao.delete(transaction)
    }

    suspend fun restoreCustomerTransactionLocal(transaction: CustomerTransaction) = withContext(Dispatchers.IO) {
        customerTransactionDao.insert(transaction)
    }

    suspend fun deleteCustomerTransaction(transaction: CustomerTransaction): Boolean = withContext(Dispatchers.IO) {
        if (!com.example.data.network.NetworkClient.isTrialMode && transaction.id > 0L) {
            if (!com.example.data.network.SelfHostedManager.deleteManagerCustomerTransaction(transaction.id)) return@withContext false
        }
        customerTransactionDao.delete(transaction)
        true
    }

    // License Cache
    suspend fun getLicenseCache(): LicenseCacheEntity? = licenseCacheDao.getLicenseCache()
    suspend fun saveLicenseCache(cache: LicenseCacheEntity) = licenseCacheDao.saveLicenseCache(cache)

    suspend fun getManagers(): List<com.example.data.network.AdminManagerDto> = withContext(Dispatchers.IO) {
        val candidatePaths = listOf("api/v1/super-manager/managers")
        val baseUrl = com.example.data.network.SelfHostedManager.SERVER_URL.trimEnd('/')
        val client = com.example.data.network.SelfHostedManager.client
        val headers = com.example.data.network.SelfHostedManager.getBaseHeaders()
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.network.AdminManagerDto::class.java)
        val listAdapter = moshi.adapter<List<com.example.data.network.AdminManagerDto>>(listType)

        for (path in candidatePaths) {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$baseUrl/$path")
                    .headers(headers)
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()?.trim() ?: ""
                        if (body.startsWith("[")) {
                            val parsed = listAdapter.fromJson(body)
                            if (parsed != null) return@withContext parsed
                        } else if (body.startsWith("{")) {
                            val jsonObj = org.json.JSONObject(body)
                            val array = jsonObj.optJSONArray("data") ?: jsonObj.optJSONArray("managers") ?: jsonObj.optJSONArray("items")
                            if (array != null) {
                                val parsed = listAdapter.fromJson(array.toString())
                                if (parsed != null) return@withContext parsed
                            }
                        }
                    }
                }
            } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", e) }
        }

        return@withContext com.example.data.network.NetworkClient.getApi().getSuperManagers()
    }

    suspend fun createManager(req: com.example.data.network.CreateManagerRequestDto): com.example.data.network.ManagerProfileDto = withContext(Dispatchers.IO) {
        val candidatePaths = listOf("api/v1/super-manager/add-manager")
        val baseUrl = com.example.data.network.SelfHostedManager.SERVER_URL.trimEnd('/')
        val client = com.example.data.network.SelfHostedManager.client
        val headers = com.example.data.network.SelfHostedManager.getBaseHeaders()
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        val jsonObject = org.json.JSONObject().apply {
            put("phone", req.phone)
            put("username", if (req.phone.isNotBlank()) req.phone else "admin_${System.currentTimeMillis()}")
            put("full_name", req.fullName)
            put("fullName", req.fullName)
            put("name", req.fullName)
            put("gamenet_name", req.gameneName)
            put("gameneName", req.gameneName)
            put("gameNetName", req.gameneName)
            put("password", req.password)
            put("plan_name", if (req.planType.isNotBlank()) req.planType else "پلن 3 ماهه")
            put("planType", req.planType)
            put("plan_type", req.planType)
            put("amount_paid", req.paymentAmount.toLong())
            put("paymentAmount", req.paymentAmount)
            put("payment_amount", req.paymentAmount)
            put("durationDays", req.durationDays)
            put("duration_days", req.durationDays)
            put("maxDevices", req.maxDevices)
            put("max_devices", req.maxDevices)
            put("paymentStatus", "PAID")
            put("payment_status", "PAID")
            put("status", "ACTIVE")
        }

        val requestBody = jsonObject.toString().toRequestBody(jsonMediaType)
        var lastStatusCode = 0
        var lastErrorMessage = ""
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val managerAdapter = moshi.adapter(com.example.data.network.ManagerProfileDto::class.java)

        val defaultStart = System.currentTimeMillis()
        val defaultEnd = defaultStart + (req.durationDays * 86400000L)

        for (path in candidatePaths) {
            try {
                val okReq = okhttp3.Request.Builder()
                    .url("$baseUrl/$path")
                    .headers(headers)
                    .post(requestBody)
                    .build()
                client.newCall(okReq).execute().use { resp ->
                    lastStatusCode = resp.code
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()?.trim() ?: ""
                        if (body.startsWith("{")) {
                            val parsed = managerAdapter.fromJson(body)
                            val jsonObj = org.json.JSONObject(body)
                            val stringId = jsonObj.optString("id", jsonObj.optString("manager_id", jsonObj.optString("managerId", "")))
                            val license = jsonObj.optString("licenseCode", jsonObj.optString("license_code", ""))
                            val longId = jsonObj.optLong("id", 0L)
                            
                            val actDate = jsonObj.optString("activation_date", jsonObj.optString("created_at", ""))
                            val expDate = jsonObj.optString("expiry_date", jsonObj.optString("expires_at", ""))
                            
                            val startMs = parseIsoOrTimestamp(actDate, defaultStart)
                            val endMs = parseIsoOrTimestamp(expDate, defaultEnd)

                            if (parsed != null) {
                                return@withContext parsed.copy(
                                    managerId = if (stringId.isNotBlank()) stringId else parsed.stringId,
                                    subscriptionStatus = "ACTIVE",
                                    paymentStatus = "PAID",
                                    subscriptionStart = if (startMs > 0) startMs else parsed.subscriptionStart,
                                    subscriptionEnd = if (endMs > 0) endMs else parsed.subscriptionEnd
                                )
                            }
                            throw Exception("Failed to parse manager response")
                        }
                        throw Exception("Failed to parse manager response")
                    } else {
                        lastErrorMessage = resp.body?.string() ?: ""
                    }
                }
            } catch (e: Exception) {
                lastErrorMessage = e.message ?: "خطا در شبکه"
            }
        }

        // Try Retrofit super manager endpoint as last attempt
        try {
            val responseBody = com.example.data.network.NetworkClient.getApi().createSuperManager(req)
            val jsonStr = responseBody.string().trim()
            if (jsonStr.startsWith("{")) {
                val parsed = managerAdapter.fromJson(jsonStr)
                if (parsed != null) return@withContext parsed
            }
            throw Exception("Failed to parse manager response")
        } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in GameNetRepository.kt", e) }

        if (lastStatusCode in 200..299) {
            throw Exception("Failed to parse manager response")
        }

        throw Exception(if (lastStatusCode == 404) "خطا: مسیر ثبت مدیر در سرور یافت نشد (کد 404). لطفاً از در دسترس بودن /api/v1/super-manager/add-manager در بک‌اند مطمئن شوید." else "خطای سرور ($lastStatusCode): $lastErrorMessage")
    }

    private fun parseIsoOrTimestamp(value: String, defaultTime: Long): Long {
        if (value.isBlank()) return defaultTime
        value.toLongOrNull()?.let { return it }
        return try {
            val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            df.parse(value)?.time ?: defaultTime
        } catch (_: Exception) {
            defaultTime
        }
    }
    suspend fun updateManagerStatus(id: String, req: com.example.data.network.UpdateManagerRequestDto) {
        com.example.data.network.NetworkClient.getApi().updateManagerStatus(id, req)
    }

}